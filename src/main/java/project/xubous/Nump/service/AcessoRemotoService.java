package project.xubous.Nump.service;

import java.security.SecureRandom;
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

import project.xubous.Nump.global.LinkIndisponivelException;
import project.xubous.Nump.model.AcessoRemoto;
import project.xubous.Nump.model.StatusAcessoRemoto;
import project.xubous.Nump.repository.AcessoRemotoRepository;

@Service
public class AcessoRemotoService
{
    private final AcessoRemotoRepository acessoRemotoRepository;
    private final Shortener shortener;
    private final WebhookPublisher webhookPublisher;

    // validade padrão do link de consentimento (rápido de propósito: é acesso à máquina)
    @Value ( "${app.acesso.expiracao-minutos:15}" )
    private int acessoExpiracaoMinutos;

    // instalador do RustDesk que a ferramenta baixa na máquina do cliente
    @Value ( "${app.rustdesk.download-url:https://github.com/rustdesk/rustdesk/releases/download/1.5.0/rustdesk-1.5.0-x86_64.exe}" )
    private String rustdeskDownloadUrl;

    public AcessoRemotoService ( AcessoRemotoRepository acessoRemotoRepository,
                                 Shortener shortener,
                                 WebhookPublisher webhookPublisher )
    {
        this.acessoRemotoRepository = acessoRemotoRepository;
        this.shortener               = shortener;
        this.webhookPublisher        = webhookPublisher;
    }

    // ── Criação da sessão (técnico) ───────────────────────────────────────────

    public AcessoRemoto criarAcesso ( String clienteEmail, Integer expiraMinutos )
    {
        String token = shortener.generateToken ( );

        int validade = expiraMinutos != null ? expiraMinutos : acessoExpiracaoMinutos;

        LocalDateTime agora = LocalDateTime.now ( );

        AcessoRemoto acesso = new AcessoRemoto ( );
        acesso.setToken        ( token );
        acesso.setConsentUrl   ( shortener.generateConsentUrl ( token ) );
        acesso.setOwnerEmail   ( getCurrentUserEmail ( ) );
        acesso.setClienteEmail ( clienteEmail );
        acesso.setCreatedAt    ( agora );
        acesso.setExpiresAt    ( validade > 0 ? agora.plusMinutes ( validade ) : null );
        acesso.setStatus       ( StatusAcessoRemoto.PENDENTE );

        return acessoRemotoRepository.save ( acesso );
    }

    public List < AcessoRemoto > getAllAcessos ( )
    {
        return acessoRemotoRepository.findAllByOwnerEmailOrderByCreatedAtDesc ( getCurrentUserEmail ( ) );
    }

    public Optional < AcessoRemoto > getAcessoById ( Long id )
    {
        return acessoRemotoRepository.findByIdAndOwnerEmail ( id, getCurrentUserEmail ( ) );
    }

    @Transactional
    public void cancelarAcesso ( long id )
    {
        if ( acessoRemotoRepository.existsByIdAndOwnerEmail ( id, getCurrentUserEmail ( ) ) )
        {
            acessoRemotoRepository.findByIdAndOwnerEmail ( id, getCurrentUserEmail ( ) )
                    .ifPresent ( ( acesso ) ->
                    {
                        // só cancela se ainda estiver pendente
                        if ( acesso.getStatus ( ) == StatusAcessoRemoto.PENDENTE )
                        {
                            acesso.setStatus ( StatusAcessoRemoto.CANCELADO );
                            acessoRemotoRepository.save ( acesso );
                        }
                    } );
        }
    }

    // ── Consentimento (cliente, rota pública) ─────────────────────────────────

    public Optional < AcessoRemoto > getAcessoByToken ( String token )
    {
        return acessoRemotoRepository.findByToken ( token );
    }

    // Registra o consentimento e devolve o que o n8n responder (ex.: ID/senha AnyDesk).
    // Uso único: depois de consentido, o link não pode ser reutilizado.
    @Transactional
    public Map < String, Object > consentir ( String token, String ip, String userAgent, String clienteEmail )
    {
        AcessoRemoto acesso = acessoRemotoRepository.findByToken ( token )
                .orElseThrow ( ( ) -> new IllegalArgumentException ( "Link de acesso inválido" ) );

        if ( acesso.getStatus ( ) == StatusAcessoRemoto.CONSENTIDO )
        {
            throw new LinkIndisponivelException ( "Este link já foi utilizado" );
        }

        if ( acesso.getStatus ( ) == StatusAcessoRemoto.CANCELADO )
        {
            throw new LinkIndisponivelException ( "Este link foi cancelado pelo técnico" );
        }

        if ( acesso.getExpiresAt ( ) != null && acesso.getExpiresAt ( ).isBefore ( LocalDateTime.now ( ) ) )
        {
            acesso.setStatus ( StatusAcessoRemoto.EXPIRADO );
            acessoRemotoRepository.save ( acesso );
            throw new LinkIndisponivelException ( "Este link expirou" );
        }

        // ── Registra a trilha de auditoria do consentimento ────────────────────
        acesso.setConsentAt        ( LocalDateTime.now ( ) );
        acesso.setConsentIp        ( ip );
        acesso.setConsentUserAgent ( userAgent );
        if ( clienteEmail != null && !clienteEmail.isBlank ( ) )
        {
            acesso.setClienteEmail ( clienteEmail );
        }
        acesso.setStatus ( StatusAcessoRemoto.CONSENTIDO );

        acessoRemotoRepository.save ( acesso );

        // ── Avisa o n8n e lê a resposta (ID/senha do RustDesk/AnyDesk, etc.) ──
        Map < String, Object > payload = new HashMap <> ( );
        payload.put ( "token", acesso.getToken ( ) );
        payload.put ( "consentUrl", acesso.getConsentUrl ( ) );
        payload.put ( "ownerEmail", acesso.getOwnerEmail ( ) );
        payload.put ( "clienteEmail", acesso.getClienteEmail ( ) );
        payload.put ( "consentAt", acesso.getConsentAt ( ).toString ( ) );
        payload.put ( "ip", ip );
        payload.put ( "userAgent", userAgent );

        Map < String, Object > respostaN8n = webhookPublisher.publicarESperar ( "acesso_consentido", payload );

        // ── Dados RustDesk: o n8n se responder, senão o Nump gera a senha ─────
        // (só aceitamos chaves específicas do rustdesk — chaves genéricas como
        //  "id"/"senha" podem ser do AnyDesk e não devem virar dados RustDesk)
        String rustdeskId     = texto ( respostaN8n, "rustdeskId", "rustdesk_id" );
        String rustdeskSenha  = texto ( respostaN8n, "rustdeskSenha", "rustdesk_senha",
                                                "rustdeskPassword", "rustdesk_password" );
        String rustdeskServer = texto ( respostaN8n, "rustdeskServer", "rustdesk_server" );

        if ( rustdeskSenha == null )
        {
            // fallback: senha temporária aleatória (funciona sem n8n ligado)
            rustdeskSenha = gerarSenhaTemporaria ( );
        }

        acesso.setRustdeskId     ( rustdeskId );
        acesso.setRustdeskSenha  ( rustdeskSenha );
        acesso.setRustdeskServer ( rustdeskServer );
        acessoRemotoRepository.save ( acesso );

        // payload devolvido à página do cliente
        Map < String, Object > resultado = new HashMap <> ( );
        resultado.put ( "autorizado", true );
        resultado.put ( "token", acesso.getToken ( ) );
        resultado.put ( "consentAt", acesso.getConsentAt ( ).toString ( ) );

        // dados RustDesk (página usa para exibir e para baixar a ferramenta .bat)
        Map < String, Object > rustdesk = new HashMap <> ( );
        if ( rustdeskId != null )
        {
            rustdesk.put ( "id", rustdeskId );
        }
        rustdesk.put ( "senha", rustdeskSenha );
        if ( rustdeskServer != null )
        {
            rustdesk.put ( "server", rustdeskServer );
        }
        resultado.put ( "rustdesk", rustdesk );

        // se o n8n devolveu dados de sessão (ex.: anydeskId/anydeskSenha), repassa
        if ( respostaN8n != null && !respostaN8n.isEmpty ( ) )
        {
            resultado.put ( "n8n", respostaN8n );
        }

        return resultado;
    }

    // ── Ferramenta RustDesk para o cliente baixar ─────────────────────────────

    // Gera o .bat que o cliente executa na máquina dele: baixa o RustDesk,
    // aplica a senha temporária da sessão e mostra o ID para o técnico conectar.
    public String gerarFerramenta ( String token )
    {
        AcessoRemoto acesso = acessoRemotoRepository.findByToken ( token )
                .orElseThrow ( ( ) -> new IllegalArgumentException ( "Link de acesso inválido" ) );

        if ( acesso.getStatus ( ) != StatusAcessoRemoto.CONSENTIDO )
        {
            throw new LinkIndisponivelException ( "Autorize o acesso antes de baixar a ferramenta" );
        }

        // valores sanitizados: só entram no .bat caracteres seguros (sem injeção)
        String url     = sanitizar ( rustdeskDownloadUrl );
        String senha   = sanitizar ( acesso.getRustdeskSenha ( ) );
        String servidor = sanitizar ( acesso.getRustdeskServer ( ) );

        StringBuilder bat = new StringBuilder ( );
        bat.append ( "@echo off\r\n" );
        bat.append ( "chcp 65001 >nul\r\n" );
        bat.append ( "title Atendimento remoto - Nump\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  ============================================\r\n" );
        bat.append ( "echo   Atendimento remoto seguro - Nump\r\n" );
        bat.append ( "echo  ============================================\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "set \"PASTA=%TEMP%\\nump_suporte\"\r\n" );
        bat.append ( "set \"INST=%PASTA%\\rustdesk_instalar.exe\"\r\n" );
        bat.append ( "set \"APP=C:\\Program Files\\RustDesk\\rustdesk.exe\"\r\n" );
        bat.append ( "if not exist \"%PASTA%\" mkdir \"%PASTA%\"\r\n" );
        bat.append ( "if not exist \"%INST%\" (\r\n" );
        bat.append ( "  echo  Baixando a ferramenta de acesso remoto, aguarde...\r\n" );
        bat.append ( "  powershell -NoProfile -ExecutionPolicy Bypass -Command \"[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '" + url + "' -OutFile '%INST%'\"\r\n" );
        bat.append ( ")\r\n" );
        bat.append ( "if not exist \"%INST%\" goto falha\r\n" );
        bat.append ( "if not exist \"%APP%\" (\r\n" );
        bat.append ( "  echo  Instalando o RustDesk. O Windows pode pedir autorizacao...\r\n" );
        bat.append ( "  start /wait \"\" \"%INST%\" --silent-install\r\n" );
        bat.append ( ")\r\n" );
        bat.append ( "if not exist \"%APP%\" (\r\n" );
        bat.append ( "  echo  Instalacao automatica nao concluida. Abrindo o instalador...\r\n" );
        bat.append ( "  start /wait \"\" \"%INST%\"\r\n" );
        bat.append ( ")\r\n" );
        bat.append ( "if not exist \"%APP%\" goto falha\r\n" );
        if ( servidor != null )
        {
            bat.append ( "set \"RUSTDESK_API_HOST=" + servidor + "\"\r\n" );
        }
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  Autorizando a sessao temporaria...\r\n" );
        bat.append ( "start \"\" \"%APP%\" --password " + senha + "\r\n" );
        bat.append ( "timeout /t 6 >nul\r\n" );
        bat.append ( "for /f \"delims=\" %%i in ('\"%APP%\" --get-id') do set \"ID=%%i\"\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  --------------------------------------------\r\n" );
        bat.append ( "echo   Compartilhe este ID com o tecnico: %ID%\r\n" );
        bat.append ( "echo  --------------------------------------------\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "pause\r\n" );
        bat.append ( "exit /b 0\r\n" );
        bat.append ( "\r\n" );
        bat.append ( ":falha\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  Nao foi possivel preparar o acesso remoto.\r\n" );
        bat.append ( "echo  Baixe o RustDesk manualmente em: https://rustdesk.com\r\n" );
        bat.append ( "echo  e informe o ID exibido ao tecnico.\r\n" );
        bat.append ( "pause\r\n" );
        bat.append ( "exit /b 1\r\n" );

        return bat.toString ( );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // Lê o primeiro campo presente no mapa do n8n (só chaves informadas)
    private String texto ( Map < String, Object > mapa, String... chaves )
    {
        if ( mapa == null )
        {
            return null;
        }

        for ( String chave : chaves )
        {
            Object valor = mapa.get ( chave );
            if ( valor != null && !valor.toString ( ).isBlank ( ) )
            {
                return valor.toString ( ).trim ( );
            }
        }

        return null;
    }

    // Senha temporária alfanumérica (SecureRandom) usada quando o n8n não responde
    private String gerarSenhaTemporaria ( )
    {
        // sem 0/O e 1/I para o cliente não confundir ao digitar
        String alfabeto = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

        StringBuilder senha  = new StringBuilder ( );
        SecureRandom random = new SecureRandom ( );

        for ( int i = 0; i < 10; i++ )
        {
            senha.append ( alfabeto.charAt ( random.nextInt ( alfabeto.length ( ) ) ) );
        }

        return senha.toString ( );
    }

    // Mantém só caracteres seguros para entrar dentro do .bat (evita injeção).
    // Permite "/" e ":" para a URL de download do RustDesk ficar íntegra.
    private String sanitizar ( String valor )
    {
        if ( valor == null )
        {
            return null;
        }
        String limpo = valor.replaceAll ( "[^A-Za-z0-9.:_/-]", "" );
        return limpo.isBlank ( ) ? null : limpo;
    }

    private String getCurrentUserEmail ( )
    {
        Authentication auth = SecurityContextHolder.getContext ( ).getAuthentication ( );
        return auth == null ? "" : auth.getName ( );
    }
}
