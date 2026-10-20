package project.xubous.Nump.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import project.xubous.Nump.global.LinkIndisponivelException;
import project.xubous.Nump.model.DownloadLog;
import project.xubous.Nump.model.File;
import project.xubous.Nump.model.OrigemArquivo;
import project.xubous.Nump.model.UploadLink;
import project.xubous.Nump.repository.DownloadLogRepository;
import project.xubous.Nump.repository.FileRepository;
import project.xubous.Nump.repository.UploadLinkRepository;

@Service
public class FileService
{
    private final FileRepository fileRepository;
    private final DownloadLogRepository downloadLogRepository;
    private final UploadLinkRepository uploadLinkRepository;
    private final Compactor compactor;
    private final Shortener shortener;
    private final WebhookPublisher webhookPublisher;

    // validade padrão do link de download (temporário) e do link de coleta
    @Value ( "${app.link.expiracao-minutos:5}" )
    private int linkExpiracaoMinutos;

    @Value ( "${app.coleta.expiracao-minutos:120}" )
    private int coletaExpiracaoMinutos;

    public FileService ( FileRepository fileRepository,
                         DownloadLogRepository downloadLogRepository,
                         UploadLinkRepository uploadLinkRepository,
                         Compactor compactor,
                         Shortener shortener,
                         WebhookPublisher webhookPublisher )
    {
        this.fileRepository        = fileRepository;
        this.downloadLogRepository = downloadLogRepository;
        this.uploadLinkRepository  = uploadLinkRepository;
        this.compactor             = compactor;
        this.shortener             = shortener;
        this.webhookPublisher      = webhookPublisher;
    }

    // ── Listagem (dono) ───────────────────────────────────────────────────────

    public List < File > getAllFile ( )
    {
        return fileRepository.findAllByOwnerEmail ( getCurrentUserEmail ( ) );
    }

    public Optional < File > getFileById ( Long id )
    {
        return fileRepository.findByIdAndOwnerEmail ( id, getCurrentUserEmail ( ) );
    }

    // ── Upload pelo dono (técnico) ────────────────────────────────────────────

    public File uploadAndCompress ( MultipartFile multipartFile,
                                    Integer expiraMinutos,
                                    Integer maxDownloads,
                                    String description ) throws IOException
    {
        String token = shortener.generateToken ( );

        Path zipPath = compactor.compactAndSave ( multipartFile, token );

        String downloadUrl = shortener.generateDownloadUrl ( token );

        int validade = expiraMinutos != null ? expiraMinutos : linkExpiracaoMinutos;

        LocalDateTime agora = LocalDateTime.now ( );

        File file = new File ( );
        file.setPath         ( zipPath.toString ( ) );
        file.setSize         ( Files.size ( zipPath ) );
        file.setToken        ( token );
        file.setDownloadUrl  ( downloadUrl );
        file.setOwnerEmail   ( getCurrentUserEmail ( ) );
        file.setOriginalName ( multipartFile.getOriginalFilename ( ) );
        file.setDescription  ( description );
        file.setOrigem       ( OrigemArquivo.UPLOAD );
        file.setCreatedAt    ( agora );
        file.setExpiresAt    ( validade > 0 ? agora.plusMinutes ( validade ) : null );
        file.setMaxDownloads ( maxDownloads );
        file.setDownloadCount ( 0 );
        file.setRevoked      ( false );

        File saved = fileRepository.save ( file );

        webhookPublisher.publicar ( "arquivo_enviado", payloadArquivo ( saved ) );

        return saved;
    }

    // ── Download público (rota /files/r/{token}) ──────────────────────────────

    public Optional < File > getFileByToken ( String token )
    {
        return fileRepository.findByToken ( token );
    }

    // Nome do zip baixado: preserva o nome original do arquivo (ex.: relatorio.zip)
    // e remove caracteres que quebrariam o header Content-Disposition.
    public String nomeDownload ( File file )
    {
        String nome = file.getOriginalName ( ) != null ? file.getOriginalName ( ) : "arquivo";

        int ponto = nome.lastIndexOf ( '.' );
        if ( ponto > 0 )
        {
            nome = nome.substring ( 0, ponto );
        }

        nome = nome.replaceAll ( "[^a-zA-Z0-9._-]", "_" );

        return nome.isBlank ( ) ? "arquivo.zip" : nome + ".zip";
    }

    // Valida o link e incrementa o contador de downloads (transacional)
    @Transactional
    public void autorizarDownload ( File file )
    {
        if ( file.isRevoked ( ) )
        {
            throw new LinkIndisponivelException ( "Este link foi revogado pelo remetente" );
        }

        if ( file.getExpiresAt ( ) != null && file.getExpiresAt ( ).isBefore ( LocalDateTime.now ( ) ) )
        {
            throw new LinkIndisponivelException ( "Este link expirou" );
        }

        if ( file.getMaxDownloads ( ) != null && file.getDownloadCount ( ) >= file.getMaxDownloads ( ) )
        {
            throw new LinkIndisponivelException ( "Este link atingiu o limite de downloads" );
        }

        file.setDownloadCount ( file.getDownloadCount ( ) + 1 );
        fileRepository.save ( file );
    }

    // Registra o download (log + webhook de confirmação de recebimento)
    public void registrarDownload ( File file, String ip, String userAgent )
    {
        DownloadLog log = new DownloadLog ( );
        log.setFileId       ( file.getId ( ) );
        log.setToken        ( file.getToken ( ) );
        log.setDownloadedAt ( LocalDateTime.now ( ) );
        log.setIp           ( ip );
        log.setUserAgent    ( userAgent );
        log.setOwnerEmail   ( file.getOwnerEmail ( ) );

        downloadLogRepository.save ( log );

        Map < String, Object > payload = payloadArquivo ( file );
        payload.put ( "ip", ip );
        payload.put ( "userAgent", userAgent );
        payload.put ( "downloadCount", file.getDownloadCount ( ) );

        webhookPublisher.publicar ( "arquivo_baixado", payload );
    }

    public List < DownloadLog > getDownloadLogs ( Long fileId )
    {
        return downloadLogRepository.findAllByFileIdOrderByDownloadedAtDesc ( fileId );
    }

    // ── Revogação /reativação do link ─────────────────────────────────────────

    // Devolve false se o arquivo não existe ou não pertence ao usuário (→ 404)
    @Transactional
    public boolean setRevoked ( Long id, boolean revoked )
    {
        Optional < File > optional = fileRepository.findByIdAndOwnerEmail ( id, getCurrentUserEmail ( ) );

        if ( optional.isEmpty ( ) )
        {
            return false;
        }

        File file = optional.get ( );
        file.setRevoked ( revoked );
        fileRepository.save ( file );

        return true;
    }

    // ── Remoção ───────────────────────────────────────────────────────────────

    @Transactional
    public void deleteFile ( long id )
    {
        if ( fileRepository.existsByIdAndOwnerEmail ( id, getCurrentUserEmail ( ) ) )
        {
            fileRepository.deleteByIdAndOwnerEmail ( id, getCurrentUserEmail ( ) );
        }
    }

    // ── Coleta (upload reverso pelo cliente) ──────────────────────────────────

    public UploadLink criarColeta ( Integer expiraMinutos, Integer maxUploads, String descricao )
    {
        String token = shortener.generateToken ( );

        int validade = expiraMinutos != null ? expiraMinutos : coletaExpiracaoMinutos;

        LocalDateTime agora = LocalDateTime.now ( );

        UploadLink coleta = new UploadLink ( );
        coleta.setToken       ( token );
        coleta.setColetaUrl   ( shortener.generateColetaUrl ( token ) );
        coleta.setOwnerEmail  ( getCurrentUserEmail ( ) );
        coleta.setCreatedAt   ( agora );
        coleta.setExpiresAt   ( validade > 0 ? agora.plusMinutes ( validade ) : null );
        coleta.setMaxUploads  ( maxUploads );
        coleta.setUploadCount ( 0 );
        coleta.setActive      ( true );

        return uploadLinkRepository.save ( coleta );
    }

    public List < UploadLink > getAllColetas ( )
    {
        return uploadLinkRepository.findAllByOwnerEmailOrderByCreatedAtDesc ( getCurrentUserEmail ( ) );
    }

    @Transactional
    public void deleteColeta ( long id )
    {
        if ( uploadLinkRepository.existsByIdAndOwnerEmail ( id, getCurrentUserEmail ( ) ) )
        {
            uploadLinkRepository.deleteByIdAndOwnerEmail ( id, getCurrentUserEmail ( ) );
        }
    }

    // Recebe arquivo enviado pelo cliente via link de coleta (sem login).
    // O arquivo recebido vira propriedade do técnico que criou o link.
    @Transactional
    public File receberColeta ( String token, MultipartFile multipartFile, String ip ) throws IOException
    {
        UploadLink coleta = uploadLinkRepository.findByToken ( token )
                .orElseThrow ( ( ) -> new IllegalArgumentException ( "Link de coleta inválido" ) );

        if ( !coleta.isActive ( ) )
        {
            throw new LinkIndisponivelException ( "Este link de coleta foi desativado" );
        }

        if ( coleta.getExpiresAt ( ) != null && coleta.getExpiresAt ( ).isBefore ( LocalDateTime.now ( ) ) )
        {
            throw new LinkIndisponivelException ( "Este link de coleta expirou" );
        }

        if ( coleta.getMaxUploads ( ) != null && coleta.getUploadCount ( ) >= coleta.getMaxUploads ( ) )
        {
            throw new LinkIndisponivelException ( "Este link de coleta atingiu o limite de envios" );
        }

        String tokenArquivo = shortener.generateToken ( );

        Path zipPath = compactor.compactAndSave ( multipartFile, tokenArquivo );

        File file = new File ( );
        file.setPath         ( zipPath.toString ( ) );
        file.setSize         ( Files.size ( zipPath ) );
        file.setToken        ( tokenArquivo );
        file.setDownloadUrl  ( shortener.generateDownloadUrl ( tokenArquivo ) );
        file.setOwnerEmail   ( coleta.getOwnerEmail ( ) );
        file.setOriginalName ( multipartFile.getOriginalFilename ( ) );
        file.setDescription  ( "recebido via coleta" );
        file.setOrigem       ( OrigemArquivo.COLETA );
        file.setCreatedAt    ( LocalDateTime.now ( ) );
        file.setExpiresAt    ( null );  // fica guardado para o técnico baixar quando quiser
        file.setMaxDownloads ( null );
        file.setDownloadCount ( 0 );
        file.setRevoked      ( false );

        coleta.setUploadCount ( coleta.getUploadCount ( ) + 1 );
        uploadLinkRepository.save ( coleta );

        File saved = fileRepository.save ( file );

        Map < String, Object > payload = payloadArquivo ( saved );
        payload.put ( "tokenColeta", coleta.getToken ( ) );
        payload.put ( "ipRemetente", ip );

        webhookPublisher.publicar ( "coleta_recebida", payload );

        return saved;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map < String, Object > payloadArquivo ( File file )
    {
        Map < String, Object > payload = new HashMap <> ( );
        payload.put ( "token", file.getToken ( ) );
        payload.put ( "downloadUrl", file.getDownloadUrl ( ) );
        payload.put ( "ownerEmail", file.getOwnerEmail ( ) );
        payload.put ( "originalName", file.getOriginalName ( ) );
        payload.put ( "size", file.getSize ( ) );
        payload.put ( "expiresAt", file.getExpiresAt ( ) == null ? null : file.getExpiresAt ( ).toString ( ) );
        return payload;
    }

    private String getCurrentUserEmail ( )
    {
        Authentication auth = SecurityContextHolder.getContext ( ).getAuthentication ( );
        return auth == null ? "" : auth.getName ( );
    }
}
