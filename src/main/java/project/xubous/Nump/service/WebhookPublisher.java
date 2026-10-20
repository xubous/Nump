package project.xubous.Nump.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

// Publica eventos do Nump para o n8n (POST JSON em N8N_WEBHOOK_URL).
// Best-effort: se a URL não estiver configurada ou a chamada falhar, o fluxo
// principal do Nump nunca é afetado — só loga o aviso.
@Service
public class WebhookPublisher
{
    private static final Logger log = LoggerFactory.getLogger ( WebhookPublisher.class );

    private final HttpClient httpClient = HttpClient.newBuilder ( )
            .connectTimeout ( Duration.ofSeconds ( 3 ) )
            .build ( );

    private final ObjectMapper objectMapper = new ObjectMapper ( );

    @Value ( "${n8n.webhook-url:}" )
    private String webhookUrl;

    // ── Fire-and-forget: dispara e não espera resposta ────────────────────────
    public void publicar ( String evento, Map < String, Object > payload )
    {
        if ( !configurado ( ) )
        {
            return;
        }

        montarEvento ( evento, payload );

        try
        {
            HttpRequest request = montarRequest ( payload );

            // dispara sem bloquear o fluxo do usuário
            httpClient.sendAsync ( request, HttpResponse.BodyHandlers.discarding ( ) )
                    .whenComplete ( ( resposta, erro ) ->
                    {
                        if ( erro != null )
                        {
                            log.warn ( "Falha ao enviar webhook '{}' para o n8n: {}", evento, erro.getMessage ( ) );
                        }
                    } );
        }
        catch ( Exception e )
        {
            log.warn ( "Falha ao montar webhook '{}': {}", evento, e.getMessage ( ) );
        }
    }

    // ── Síncrono: dispara e devolve o JSON que o n8n respondeu ────────────────
    // Usado no consentimento de acesso remoto, onde a página exibe o que o n8n
    // devolver (ex.: ID do RustDesk). Em caso de falha devolve mapa vazio.
    public Map < String, Object > publicarESperar ( String evento, Map < String, Object > payload )
    {
        if ( !configurado ( ) )
        {
            return Map.of ( );
        }

        montarEvento ( evento, payload );

        try
        {
            HttpRequest request = montarRequest ( payload );

            HttpResponse < String > resposta = httpClient.send ( request, HttpResponse.BodyHandlers.ofString ( ) );

            if ( resposta.statusCode ( ) < 200 || resposta.statusCode ( ) >= 300 )
            {
                log.warn ( "n8n respondeu {} no webhook '{}'", resposta.statusCode ( ), evento );
                return Map.of ( );
            }

            String corpo = resposta.body ( );
            if ( corpo == null || corpo.isBlank ( ) )
            {
                return Map.of ( );
            }

            // aceita objeto JSON ou objeto embutido em array — pega o primeiro objeto
            String json = corpo.trim ( );
            if ( json.startsWith ( "[" ) )
            {
                json = json.substring ( 1 );
            }

            return objectMapper.readValue ( json, Map.class );
        }
        catch ( Exception e )
        {
            log.warn ( "Falha ao chamar webhook '{}' e ler resposta: {}", evento, e.getMessage ( ) );
            return Map.of ( );
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean configurado ( )
    {
        return webhookUrl != null && !webhookUrl.isBlank ( );
    }

    private void montarEvento ( String evento, Map < String, Object > payload )
    {
        payload.put ( "evento", evento );
        payload.put ( "emitidoEm", java.time.LocalDateTime.now ( ).toString ( ) );
    }

    private HttpRequest montarRequest ( Map < String, Object > payload ) throws Exception
    {
        String body = objectMapper.writeValueAsString ( payload );

        return HttpRequest.newBuilder ( )
                .uri ( URI.create ( webhookUrl ) )
                .header ( "Content-Type", "application/json" )
                .timeout ( Duration.ofSeconds ( 10 ) )
                .POST ( HttpRequest.BodyPublishers.ofString ( body ) )
                .build ( );
    }
}
