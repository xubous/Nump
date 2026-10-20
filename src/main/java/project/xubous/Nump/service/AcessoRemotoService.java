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

    // Instalador do RustDesk que o script baixa na máquina do cliente
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

    // Registra o consentimento e devolve o que o n8n responder (ex.: ID do RustDesk).
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

        // ── Avisa o n8n e lê a resposta (ID/servidor do RustDesk, etc.) ──────
        Map < String, Object > payload = new HashMap <> ( );
        payload.put ( "token", acesso.getToken ( ) );
        payload.put ( "consentUrl", acesso.getConsentUrl ( ) );
        payload.put ( "ownerEmail", acesso.getOwnerEmail ( ) );
        payload.put ( "clienteEmail", acesso.getClienteEmail ( ) );
        payload.put ( "consentAt", acesso.getConsentAt ( ).toString ( ) );
        payload.put ( "ip", ip );
        payload.put ( "userAgent", userAgent );

        Map < String, Object > respostaN8n = webhookPublisher.publicarESperar ( "acesso_consentido", payload );

        // ── Senha temporária: SEMPRE gerada pelo Nump ────────────────────────
        // É ela que o script aplica na máquina do cliente, dando acesso não
        // atendido (o técnico conecta sem o cliente aceitar de novo).
        // Aceitamos ID/servidor do n8n; a senha do n8n é ignorada de propósito.
        String rustdeskId     = texto ( respostaN8n, "rustdeskId", "rustdesk_id",
                                                 "anydeskId", "anydesk_id" );
        String rustdeskServer = texto ( respostaN8n, "rustdeskServer", "rustdesk_server" );
        String rustdeskSenha  = gerarSenhaTemporaria ( );

        acesso.setRustdeskId     ( rustdeskId );
        acesso.setRustdeskSenha  ( rustdeskSenha );
        acesso.setRustdeskServer ( rustdeskServer );
        acessoRemotoRepository.save ( acesso );

        // payload devolvido à página do cliente
        Map < String, Object > resultado = new HashMap <> ( );
        resultado.put ( "autorizado", true );
        resultado.put ( "token", acesso.getToken ( ) );
        resultado.put ( "consentAt", acesso.getConsentAt ( ).toString ( ) );

        // dados RustDesk (a página exibe a senha; o ID vem do script)
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

        // se o n8n devolveu algo não mapeado, repassa o JSON cru para a página exibir
        if ( respostaN8n != null && !respostaN8n.isEmpty ( ) )
        {
            resultado.put ( "n8n", respostaN8n );
        }

        return resultado;
    }

    // ── Ferramenta RustDesk para o cliente baixar ─────────────────────────────

    // Gera o .bat que o cliente executa na máquina dele. Fluxo:
    //   1. baixa o RustDesk RÁPIDO (curl com barra de progresso, não o lento
    //      Invoke-WebRequest do PowerShell; BITS/fallback se o curl faltar);
    //   2. instala em silêncio com a senha temporária já aplicada
    //      (cliente clica 1x "Sim" no UAC);
    //   3. abre o RustDesk e mostra o ID para o técnico.
    // Resultado: ACESSO NÃO ATENDIDO — o técnico conecta direto com ID + senha,
    // sem o cliente precisar aceitar a conexão de novo.
    public String gerarFerramenta ( String token )
    {
        AcessoRemoto acesso = acessoRemotoRepository.findByToken ( token )
                .orElseThrow ( ( ) -> new IllegalArgumentException ( "Link de acesso inválido" ) );

        if ( acesso.getStatus ( ) != StatusAcessoRemoto.CONSENTIDO )
        {
            throw new LinkIndisponivelException ( "Autorize o acesso antes de baixar a ferramenta" );
        }

        // valores sanitizados: só entram no .bat caracteres seguros (sem injeção)
        String url      = sanitizar ( rustdeskDownloadUrl );
        String senha    = sanitizar ( acesso.getRustdeskSenha ( ) );
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
        bat.append ( "set \"INST=%PASTA%\\rustdesk_setup.exe\"\r\n" );
        bat.append ( "if not exist \"%PASTA%\" mkdir \"%PASTA%\"\r\n" );
        bat.append ( "if not exist \"%INST%\" (\r\n" );
        bat.append ( "  echo  Baixando o RustDesk (~26 MB). A barra abaixo mostra o progresso.\r\n" );
        bat.append ( "  curl -L --progress-bar -o \"%INST%\" \"" + url + "\"\r\n" );
        bat.append ( ")\r\n" );
        bat.append ( "if not exist \"%INST%\" (\r\n" );
        bat.append ( "  echo  Tentando de novo com o download do Windows (BITS)...\r\n" );
        bat.append ( "  powershell -NoProfile -Command \"Import-Module BitsTransfer; Start-BitsTransfer -Source '" + url + "' -Destination '%INST%'\"\r\n" );
        bat.append ( ")\r\n" );
        bat.append ( "if not exist \"%INST%\" goto falha\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  Instalando o RustDesk com a senha de atendimento...\r\n" );
        bat.append ( "echo  O Windows vai pedir permissao (UAC) - clique em \"Sim\".\r\n" );
        bat.append ( "start /wait \"\" \"%INST%\" --silent-install --password " + senha + "\r\n" );
        bat.append ( "set \"APP=\"\r\n" );
        bat.append ( "if exist \"C:\\Program Files\\RustDesk\\rustdesk.exe\" set \"APP=C:\\Program Files\\RustDesk\\rustdesk.exe\"\r\n" );
        bat.append ( "if not defined APP if exist \"%LOCALAPPDATA%\\Programs\\RustDesk\\rustdesk.exe\" set \"APP=%LOCALAPPDATA%\\Programs\\RustDesk\\rustdesk.exe\"\r\n" );
        bat.append ( "if not defined APP if exist \"%ProgramFiles(x86)%\\RustDesk\\rustdesk.exe\" set \"APP=%ProgramFiles(x86)%\\RustDesk\\rustdesk.exe\"\r\n" );
        bat.append ( "if not defined APP goto falha\r\n" );
        if ( servidor != null )
        {
            bat.append ( "set \"RUSTDESK_API_HOST=" + servidor + "\"\r\n" );
        }
        bat.append ( "start \"\" \"%APP%\"\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  Aguardando o RustDesk conectar ao servidor...\r\n" );
        bat.append ( "set \"ID=\"\r\n" );
        bat.append ( "set /a TENTATIVAS=0\r\n" );
        bat.append ( ":espera\r\n" );
        bat.append ( "timeout /t 3 >nul\r\n" );
        bat.append ( "set /a TENTATIVAS+=1\r\n" );
        bat.append ( "for /f \"delims=\" %%i in ('\"%APP%\" --get-id 2^>nul ^| findstr /r \"[0-9][0-9][0-9][0-9]\"') do if not defined ID set \"ID=%%i\"\r\n" );
        bat.append ( "if defined ID goto mostraid\r\n" );
        bat.append ( "if %TENTATIVAS% LSS 15 goto espera\r\n" );
        bat.append ( "goto idmanual\r\n" );
        bat.append ( "\r\n" );
        bat.append ( ":mostraid\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  --------------------------------------------\r\n" );
        bat.append ( "echo   ID do RustDesk: %ID%\r\n" );
        bat.append ( "echo  --------------------------------------------\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  Envie este ID ao tecnico. Nao precisa aceitar\r\n" );
        bat.append ( "echo  nada: a senha de atendimento ja esta aplicada.\r\n" );
        bat.append ( "echo  Mantenha o RustDesk aberto ate o atendimento acabar.\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "pause\r\n" );
        bat.append ( "exit /b 0\r\n" );
        bat.append ( "\r\n" );
        bat.append ( ":idmanual\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  O RustDesk ainda nao mostrou o ID aqui. Faca assim:\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  1. Abra o RustDesk (procure no menu Iniciar).\r\n" );
        bat.append ( "echo  2. Envie ao tecnico o ID exibido na janela.\r\n" );
        bat.append ( "echo  3. Mantenha aberto ate o atendimento acabar.\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "pause\r\n" );
        bat.append ( "exit /b 0\r\n" );
        bat.append ( "\r\n" );
        bat.append ( ":falha\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  Nao foi possivel preparar o acesso remoto.\r\n" );
        bat.append ( "echo  Baixe o RustDesk em: https://rustdesk.com\r\n" );
        bat.append ( "echo  Instale, abra e informe o ID exibido ao tecnico.\r\n" );
        bat.append ( "echo.\r\n" );
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

    // Senha temporária (SecureRandom) gerada pelo Nump no consentimento.
    // É aplicada na máquina do cliente pelo script — dá o acesso não atendido.
    private String gerarSenhaTemporaria ( )
    {
        // sem 0/O e 1/I para evitar confusão ao digitar
        String alfabeto = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

        StringBuilder senha  = new StringBuilder ( );
        SecureRandom random  = new SecureRandom ( );

        for ( int i = 0; i < 12; i++ )
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
