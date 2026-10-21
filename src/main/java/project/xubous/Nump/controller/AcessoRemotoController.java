package project.xubous.Nump.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import project.xubous.Nump.model.AcessoRemoto;
import project.xubous.Nump.service.AcessoRemotoService;

@RestController
@RequestMapping ( "/acessos" )
public class AcessoRemotoController
{
    private final AcessoRemotoService acessoRemotoService;

    public AcessoRemotoController ( AcessoRemotoService acessoRemotoService )
    {
        this.acessoRemotoService = acessoRemotoService;
    }

    // POST /acessos — cria uma sessão de consentimento (técnico autenticado)
    @PostMapping
    public ResponseEntity < AcessoRemoto > criarAcesso ( @RequestParam ( required = false ) String clienteEmail,
                                                         @RequestParam ( required = false ) Integer expiraMinutos )
    {
        return ResponseEntity.ok ( acessoRemotoService.criarAcesso ( clienteEmail, expiraMinutos ) );
    }

    // GET /acessos — lista as sessões do usuário (dono)
    @GetMapping
    public List < AcessoRemoto > getAllAcessos ( )
    {
        return acessoRemotoService.getAllAcessos ( );
    }

    // DELETE /acessos/{id} — cancela a sessão se ainda estiver pendente (dono)
    @DeleteMapping ( "/{id}" )
    public ResponseEntity < Void > cancelarAcesso ( @PathVariable Long id )
    {
        acessoRemotoService.cancelarAcesso ( id );
        return ResponseEntity.noContent ( ).build ( );
    }

    // ── Rotas públicas do cliente ─────────────────────────────────────────────

    // GET /acessos/{token}/encerrar.bat — .bat de encerramento (rota pública).
    // O cliente roda no fim do atendimento para REVOGAR o acesso (senha/serviço),
    // mantendo o RustDesk instalado para futuros atendimentos.
    @GetMapping ( "/{token}/encerrar.bat" )
    public ResponseEntity < byte [ ] > baixarEncerramento ( @PathVariable String token )
    {
        String bat = acessoRemotoService.gerarEncerramento ( token );

        return ResponseEntity.ok ( )
                .header ( HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Encerrar atendimento.bat\"" )
                .body ( bat.getBytes ( StandardCharsets.UTF_8 ) );
    }

    // GET /acessos/{token}/desinstalar.bat — .bat de desinstalação completa (rota pública).
    // Diferente do encerrar: REMOVE o RustDesk da máquina de vez. Para máquinas que
    // quase não recebem acesso remoto.
    @GetMapping ( "/{token}/desinstalar.bat" )
    public ResponseEntity < byte [ ] > baixarDesinstalacao ( @PathVariable String token )
    {
        String bat = acessoRemotoService.gerarDesinstalacao ( token );

        return ResponseEntity.ok ( )
                .header ( HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Desinstalar RustDesk.bat\"" )
                .body ( bat.getBytes ( StandardCharsets.UTF_8 ) );
    }

    // GET /acessos/{token} — página pública de consentimento (rota pública).
    // Serve a página estática /consentimento.html, que lê o token do query string.
    @GetMapping ( "/{token}" )
    public ResponseEntity < Void > getPaginaConsentimento ( @PathVariable String token )
    {
        return ResponseEntity.status ( 302 )
                .header ( HttpHeaders.LOCATION, "/consentimento.html?token=" + token )
                .build ( );
    }

    // GET /acessos/ferramenta/{token}.bat — baixa a ferramenta RustDesk (rota pública).
    // O .bat é gerado na hora com a senha temporária da sessão já consentida.
    @GetMapping ( "/ferramenta/{token}.bat" )
    public ResponseEntity < byte [ ] > baixarFerramenta ( @PathVariable String token )
    {
        String bat = acessoRemotoService.gerarFerramenta ( token );

        return ResponseEntity.ok ( )
                .header ( HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Suporte Nump.bat\"" )
                .body ( bat.getBytes ( StandardCharsets.UTF_8 ) );
    }

    // GET /acessos/ferramenta/{fToken}/senha — troca o token de uso único do .bat
    // pela senha temporária (rota pública, consumo único). Devolve texto puro para
    // o .bat capturar com um `for /f`, sem precisar de parser de JSON.
    @GetMapping ( value = "/ferramenta/{fToken}/senha", produces = "text/plain;charset=UTF-8" )
    public ResponseEntity < String > obterSenhaFerramenta ( @PathVariable String fToken )
    {
        return ResponseEntity.ok ( acessoRemotoService.obterSenhaPorFerramentaToken ( fToken ) );
    }

    // POST /acessos/{token}/consentir — registra a autorização do cliente (rota pública).
    // Devolve o que o n8n responder (ex.: ID do RustDesk) + a senha gerada pelo Nump.
    @PostMapping ( "/{token}/consentir" )
    public ResponseEntity < Map < String, Object > > consentir ( @PathVariable String token,
                                                                 @RequestBody ( required = false ) Map < String, Object > body,
                                                                 HttpServletRequest request )
    {
        String ip            = ipDoRequest ( request );
        String userAgent     = request.getHeader ( "User-Agent" );
        String clienteEmail  = body != null && body.get ( "clienteEmail" ) != null
                ? body.get ( "clienteEmail" ).toString ( ) : null;

        Map < String, Object > resultado =
                acessoRemotoService.consentir ( token, ip, userAgent, clienteEmail );

        return ResponseEntity.ok ( resultado );
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
