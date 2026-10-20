package project.xubous.Nump.service;

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

    // AnyDesk portátil que a ferramenta baixa na máquina do cliente (sem instalar)
    @Value ( "${app.anydesk.download-url:https://download.anydesk.com/AnyDesk.exe}" )
    private String anydeskDownloadUrl;

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

        // ── Avisa o n8n e lê a resposta (ID/senha do AnyDesk, etc.) ───────────
        Map < String, Object > payload = new HashMap <> ( );
        payload.put ( "token", acesso.getToken ( ) );
        payload.put ( "consentUrl", acesso.getConsentUrl ( ) );
        payload.put ( "ownerEmail", acesso.getOwnerEmail ( ) );
        payload.put ( "clienteEmail", acesso.getClienteEmail ( ) );
        payload.put ( "consentAt", acesso.getConsentAt ( ).toString ( ) );
        payload.put ( "ip", ip );
        payload.put ( "userAgent", userAgent );

        Map < String, Object > respostaN8n = webhookPublisher.publicarESperar ( "acesso_consentido", payload );

        // ── Dados AnyDesk: vêm do n8n quando ele responde (opcional) ─────────
        // Sem n8n (ou se ele não responder), o fluxo continua igual: o .bat baixa
        // o AnyDesk portátil e o cliente autoriza a conexão clicando em "Aceitar".
        String anydeskId    = texto ( respostaN8n, "anydeskId", "anydesk_id" );
        String anydeskSenha = texto ( respostaN8n, "anydeskSenha", "anydesk_senha",
                                                "anydeskPassword", "anydesk_password" );
        String anydeskLink  = texto ( respostaN8n, "anydeskUrl", "anydesk_url" );

        acesso.setAnydeskId    ( anydeskId );
        acesso.setAnydeskSenha ( anydeskSenha );
        acessoRemotoRepository.save ( acesso );

        // payload devolvido à página do cliente
        Map < String, Object > resultado = new HashMap <> ( );
        resultado.put ( "autorizado", true );
        resultado.put ( "token", acesso.getToken ( ) );
        resultado.put ( "consentAt", acesso.getConsentAt ( ).toString ( ) );

        // dados AnyDesk (a página exibe quando o n8n devolveu)
        Map < String, Object > anydesk = new HashMap <> ( );
        if ( anydeskId != null )
        {
            anydesk.put ( "id", anydeskId );
        }
        if ( anydeskSenha != null )
        {
            anydesk.put ( "senha", anydeskSenha );
        }
        if ( anydeskLink != null )
        {
            anydesk.put ( "link", anydeskLink );
        }
        if ( !anydesk.isEmpty ( ) )
        {
            resultado.put ( "anydesk", anydesk );
        }

        // se o n8n devolveu algo não mapeado, repassa o JSON cru para a página exibir
        if ( respostaN8n != null && !respostaN8n.isEmpty ( ) )
        {
            resultado.put ( "n8n", respostaN8n );
        }

        return resultado;
    }

    // ── Ferramenta AnyDesk para o cliente baixar ─────────────────────────────

    // Gera o .bat que o cliente executa na máquina dele: baixa o AnyDesk
    // portátil (sem instalar, sem pedir admin), abre e mostra o ID para o
    // técnico conectar. Se o download automático falhar, o .bat tenta de novo
    // com o curl nativo do Windows e, em último caso, abre a página oficial do
    // AnyDesk no navegador — nunca deixa o cliente sem caminho.
    public String gerarFerramenta ( String token )
    {
        AcessoRemoto acesso = acessoRemotoRepository.findByToken ( token )
                .orElseThrow ( ( ) -> new IllegalArgumentException ( "Link de acesso inválido" ) );

        if ( acesso.getStatus ( ) != StatusAcessoRemoto.CONSENTIDO )
        {
            throw new LinkIndisponivelException ( "Autorize o acesso antes de baixar a ferramenta" );
        }

        // valores sanitizados: só entram no .bat caracteres seguros (sem injeção)
        String url = sanitizar ( anydeskDownloadUrl );

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
        bat.append ( "set \"APP=%PASTA%\\AnyDesk.exe\"\r\n" );
        bat.append ( "if not exist \"%PASTA%\" mkdir \"%PASTA%\"\r\n" );
        bat.append ( "if not exist \"%APP%\" (\r\n" );
        bat.append ( "  echo  Baixando o AnyDesk (e portatil, nao precisa instalar), aguarde...\r\n" );
        bat.append ( "  powershell -NoProfile -ExecutionPolicy Bypass -Command \"[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '" + url + "' -OutFile '%APP%'\"\r\n" );
        bat.append ( ")\r\n" );
        bat.append ( "if not exist \"%APP%\" (\r\n" );
        bat.append ( "  echo  Tentando de novo com o download nativo do Windows...\r\n" );
        bat.append ( "  curl -L -s -o \"%APP%\" \"" + url + "\"\r\n" );
        bat.append ( ")\r\n" );
        bat.append ( "if not exist \"%APP%\" goto navegador\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  Abrindo o AnyDesk...\r\n" );
        bat.append ( "start \"\" \"%APP%\"\r\n" );
        bat.append ( "timeout /t 8 >nul\r\n" );
        bat.append ( "pushd \"%PASTA%\"\r\n" );
        bat.append ( "for /f \"delims=\" %%i in ('AnyDesk.exe --get-id') do set \"ID=%%i\"\r\n" );
        bat.append ( "popd\r\n" );
        bat.append ( "if not defined ID goto idmanual\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  --------------------------------------------\r\n" );
        bat.append ( "echo   Compartilhe este ID com o tecnico: %ID%\r\n" );
        bat.append ( "echo  --------------------------------------------\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  Mantenha o AnyDesk aberto. Quando o tecnico\r\n" );
        bat.append ( "echo  conectar, clique em \"Aceitar\" para liberar.\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "pause\r\n" );
        bat.append ( "exit /b 0\r\n" );
        bat.append ( "\r\n" );
        bat.append ( ":idmanual\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  --------------------------------------------\r\n" );
        bat.append ( "echo   Abra a janela do AnyDesk e envie ao tecnico\r\n" );
        bat.append ( "echo   o ID grande exibido no topo da tela.\r\n" );
        bat.append ( "echo  --------------------------------------------\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  Mantenha o AnyDesk aberto e clique em \"Aceitar\"\r\n" );
        bat.append ( "echo  quando o tecnico conectar.\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "pause\r\n" );
        bat.append ( "exit /b 0\r\n" );
        bat.append ( "\r\n" );
        bat.append ( ":navegador\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  O download automatico nao funcionou. Abrindo a\r\n" );
        bat.append ( "echo  pagina oficial do AnyDesk para voce baixar...\r\n" );
        bat.append ( "start \"\" \"" + url + "\"\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  1. Salve o AnyDesk.exe e abra-o (nao precisa instalar).\r\n" );
        bat.append ( "echo  2. Envie ao tecnico o ID grande exibido no topo.\r\n" );
        bat.append ( "echo  3. Mantenha o AnyDesk aberto e clique em \"Aceitar\"\r\n" );
        bat.append ( "echo     quando o tecnico conectar.\r\n" );
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

    // Mantém só caracteres seguros para entrar dentro do .bat (evita injeção).
    // Permite "/" e ":" para a URL de download do AnyDesk ficar íntegra.
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
