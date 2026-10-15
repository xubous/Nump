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

        // ── Avisa o n8n e lê a resposta (ID/senha do AnyDesk, etc.) ────────────
        Map < String, Object > payload = new HashMap <> ( );
        payload.put ( "token", acesso.getToken ( ) );
        payload.put ( "consentUrl", acesso.getConsentUrl ( ) );
        payload.put ( "ownerEmail", acesso.getOwnerEmail ( ) );
        payload.put ( "clienteEmail", acesso.getClienteEmail ( ) );
        payload.put ( "consentAt", acesso.getConsentAt ( ).toString ( ) );
        payload.put ( "ip", ip );
        payload.put ( "userAgent", userAgent );

        Map < String, Object > respostaN8n = webhookPublisher.publicarESperar ( "acesso_consentido", payload );

        // payload devolvido à página do cliente
        Map < String, Object > resultado = new HashMap <> ( );
        resultado.put ( "autorizado", true );
        resultado.put ( "token", acesso.getToken ( ) );
        resultado.put ( "consentAt", acesso.getConsentAt ( ).toString ( ) );

        // se o n8n devolveu dados de sessão (ex.: anydeskId/anydeskSenha), repassa
        if ( respostaN8n != null && !respostaN8n.isEmpty ( ) )
        {
            resultado.put ( "n8n", respostaN8n );
        }

        return resultado;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getCurrentUserEmail ( )
    {
        Authentication auth = SecurityContextHolder.getContext ( ).getAuthentication ( );
        return auth == null ? "" : auth.getName ( );
    }
}
