package project.xubous.Nump.controller;

import java.net.URI;   // representa uma URI — usado para montar o cabeçalho Location do redirect
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.xubous.Nump.model.Link;
import project.xubous.Nump.service.LinkService;

@RestController              // combina @Controller + @ResponseBody — respostas viram JSON automaticamente
@RequestMapping ( "/links" ) // todas as rotas desse controller começam com /links
public class LinkController 
{
    private final LinkService linkService;

    // o Spring injeta o LinkService automaticamente via construtor
    public LinkController ( LinkService linkService )
    {
        this.linkService = linkService;
    }

    // GET /links — lista todos os links cadastrados no banco
    @GetMapping
    public List < Link > getAllLink ( )
    {
        return linkService.getAllLink ( );
    }

    // GET /links/{id} — busca um link específico pelo id
    @GetMapping ( "/{id}" )
    public ResponseEntity < Link > getLinkById ( @PathVariable Long id )
    {
        Optional < Link > link = linkService.getLinkById ( id );

        if ( link.isPresent ( ) )
        {
            return ResponseEntity.ok ( link.get ( ) ); // 200 OK com o link encontrado
        }
        else
        {
            return ResponseEntity.notFound ( ).build ( ); // 404 se não existir
        }
    }

    // GET /links/r/{token} — resolve o token e redireciona para a URL original
    // essa é a rota que o usuário acessa quando clica no link curto
    @GetMapping ( "/r/{token}" )
    public ResponseEntity < Void > redirect ( @PathVariable String token )
    {
        Link link = linkService.getLinkByToken ( token ); // busca o link pelo token no banco

        if ( link != null )
        {
            return ResponseEntity
                   .status ( HttpStatus.FOUND )               // 302 Found — código HTTP padrão de redirecionamento temporário
                   .location ( URI.create ( link.getUrl ( ) ) ) // cabeçalho Location com a URL original — o navegador segue esse cabeçalho
                   .build ( );
        }
        else
        {
            return ResponseEntity.notFound ( ).build ( ); // 404 se o token não existir no banco
        }
    }

    // POST /links — cria um novo link encurtado
    // @RequestBody lê o JSON do corpo da requisição e converte para objeto Link
    @PostMapping
    public Link createLink ( @RequestBody Link link )
    {
        // o LinkService gera o token e a URL curta antes de salvar
        return linkService.saveLink ( link );
    }

    // DELETE /links/{id} — remove o link do banco
    // devolve 204 No Content — sucesso sem corpo na resposta
    @DeleteMapping ( "/{id}" )
    public ResponseEntity < Void > deleteLink ( @PathVariable Long id )
    {
        linkService.deleteLink ( id );
        return ResponseEntity.noContent ( ).build ( );
    }
}