package project.xubous.Nump.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import project.xubous.Nump.model.DownloadLog;
import project.xubous.Nump.model.File;
import project.xubous.Nump.model.UploadLink;
import project.xubous.Nump.service.FileService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping ( "/files" )
public class FileController
{
    private final FileService fileService;

    public FileController ( FileService fileService )
    {
        this.fileService = fileService;
    }

    // GET /files — FileService já filtra pelo email do usuário autenticado via SecurityContextHolder
    @GetMapping
    public List < File > getAllFile ( )
    {
        return fileService.getAllFile ( );
    }

    @GetMapping ( "/{id}" )
    public ResponseEntity < File > getFileById ( @PathVariable Long id )
    {
        return fileService.getFileById ( id )
                .map ( ResponseEntity::ok )
                .orElse ( ResponseEntity.notFound ( ).build ( ) );
    }

    // POST /files/upload — parâmetros opcionais controlam o link temporário
    @PostMapping ( "/upload" )
    public ResponseEntity < File > uploadFile ( @RequestParam ( "file" ) MultipartFile file,
                                                @RequestParam ( required = false ) Integer expiraMinutos,
                                                @RequestParam ( required = false ) Integer maxDownloads,
                                                @RequestParam ( required = false ) String description ) throws IOException
    {
        File saved = fileService.uploadAndCompress ( file, expiraMinutos, maxDownloads, description );
        return ResponseEntity.ok ( saved );
    }

    // GET /files/{id}/downloads — histórico de downloads do arquivo (dono)
    @GetMapping ( "/{id}/downloads" )
    public ResponseEntity < List < DownloadLog > > getDownloads ( @PathVariable Long id )
    {
        return fileService.getFileById ( id )
                .map ( ( file ) -> ResponseEntity.ok ( fileService.getDownloadLogs ( file.getId ( ) ) ) )
                .orElse ( ResponseEntity.notFound ( ).build ( ) );
    }

    // POST /files/{id}/revogar — desativa o link sem apagar o arquivo (dono)
    @PostMapping ( "/{id}/revogar" )
    public ResponseEntity < Void > revogar ( @PathVariable Long id )
    {
        if ( !fileService.setRevoked ( id, true ) )
        {
            return ResponseEntity.notFound ( ).build ( );
        }
        return ResponseEntity.noContent ( ).build ( );
    }

    // POST /files/{id}/reativar — volta a deixar o link funcionar (dono)
    @PostMapping ( "/{id}/reativar" )
    public ResponseEntity < Void > reativar ( @PathVariable Long id )
    {
        if ( !fileService.setRevoked ( id, false ) )
        {
            return ResponseEntity.notFound ( ).build ( );
        }
        return ResponseEntity.noContent ( ).build ( );
    }

    // ── Coleta (upload reverso) ───────────────────────────────────────────────

    // POST /files/coletas — cria um link de coleta para o cliente enviar de volta
    @PostMapping ( "/coletas" )
    public ResponseEntity < UploadLink > criarColeta ( @RequestParam ( required = false ) Integer expiraMinutos,
                                                       @RequestParam ( required = false ) Integer maxUploads,
                                                       @RequestParam ( required = false ) String descricao )
    {
        return ResponseEntity.ok ( fileService.criarColeta ( expiraMinutos, maxUploads, descricao ) );
    }

    // GET /files/coletas — lista os links de coleta do usuário (dono)
    @GetMapping ( "/coletas" )
    public List < UploadLink > getAllColetas ( )
    {
        return fileService.getAllColetas ( );
    }

    // DELETE /files/coletas/{id} — remove o link de coleta (dono)
    @DeleteMapping ( "/coletas/{id}" )
    public ResponseEntity < Void > deleteColeta ( @PathVariable Long id )
    {
        fileService.deleteColeta ( id );
        return ResponseEntity.noContent ( ).build ( );
    }

    // GET /files/c/{token} — página pública onde o cliente envia o arquivo (rota pública).
    // Serve a página estática /coleta.html, que lê o token do query string.
    @GetMapping ( "/c/{token}" )
    public ResponseEntity < Void > getPaginaColeta ( @PathVariable String token )
    {
        return ResponseEntity.status ( 302 )
                .header ( HttpHeaders.LOCATION, "/coleta.html?token=" + token )
                .build ( );
    }

    // POST /files/c/{token} — recebe o arquivo do cliente sem login (rota pública)
    @PostMapping ( "/c/{token}" )
    public ResponseEntity < File > receberColeta ( @PathVariable String token,
                                                   @RequestParam ( "file" ) MultipartFile file,
                                                   HttpServletRequest request ) throws IOException
    {
        String ip = ipDoRequest ( request );
        return ResponseEntity.ok ( fileService.receberColeta ( token, file, ip ) );
    }

    // ── Download público ──────────────────────────────────────────────────────

    // GET /files/r/{token} — rota pública, liberada no SecurityConfig.
    // Envia o zip em fluxo (streaming): não carrega o arquivo inteiro na RAM.
    @GetMapping ( "/r/{token}" )
    public ResponseEntity < Resource > download ( @PathVariable String token, HttpServletRequest request ) throws IOException
    {
        return fileService.getFileByToken ( token )
                .map ( file ->
                {
                    try
                    {
                        // valida link (expirado/revogado/limite) e incrementa contador
                        fileService.autorizarDownload ( file );

                        Path caminho = Path.of ( file.getPath ( ) );

                        // registra log + webhook de confirmação de recebimento
                        fileService.registrarDownload ( file, ipDoRequest ( request ), request.getHeader ( "User-Agent" ) );

                        HttpHeaders headers = new HttpHeaders ( );
                        headers.setContentLength ( Files.size ( caminho ) );
                        headers.setContentType ( MediaType.parseMediaType ( "application/zip" ) );
                        // nome do zip preserva o nome original do arquivo (ex.: relatorio.zip)
                        headers.setContentDisposition ( ContentDisposition.attachment ( )
                                .filename ( fileService.nomeDownload ( file ), StandardCharsets.UTF_8 ).build ( ) );

                        return ResponseEntity.ok ( ).headers ( headers )
                                . < Resource > body ( new FileSystemResource ( caminho ) );
                    }
                    catch ( IOException e )
                    {
                        return ResponseEntity.internalServerError ( ). < Resource > build ( );
                    }
                } )
                .orElse ( ResponseEntity.notFound ( ).build ( ) );
    }

    // DELETE /files/{id} — FileService verifica se o arquivo pertence ao usuário antes de deletar
    @DeleteMapping ( "/{id}" )
    public ResponseEntity < Void > deleteFile ( @PathVariable Long id )
    {
        fileService.deleteFile ( id );
        return ResponseEntity.noContent ( ).build ( );
    }

    // Extrai o IP real considerando proxy reverso (X-Forwarded-For)
    private String ipDoRequest ( HttpServletRequest request )
    {
        String forwarded = request.getHeader ( "X-Forwarded-For" );
        if ( forwarded != null && !forwarded.isBlank ( ) )
        {
            return forwarded.split ( "," )[0].trim ( );
        }
        return request.getRemoteAddr ( );
    }
}
