package project.xubous.Nump.service;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import project.xubous.Nump.model.Link;
import project.xubous.Nump.repository.LinkRepository;

@Service
public class LinkService 
{
    private final LinkRepository linkRepository;
    private final Shortener shortener; // injetado para gerar token e URL curta

    public LinkService ( LinkRepository linkRepository, Shortener shortener )
    {
        this.linkRepository = linkRepository;
        this.shortener      = shortener;
    }

    // retorna todos os links cadastrados no banco
    public List < Link > getAllLink ( )
    {
        return linkRepository.findAll ( );
    }

    // busca um link pelo id — retorna Optional porque pode não existir
    public Optional < Link > getLinkById ( Long id )
    {
        return linkRepository.findById ( id );
    }

    public Link saveLink ( Link link )
    {
        String token = shortener.generateToken ( ); // gera token de 8 chars — ex: "a1b2c3d4"

        // ANTES (bug): passava link.getUrl() pro generateReducedLink → resultado era "https://google.com/...http://localhost..."
        // AGORA: passa o token gerado → resultado correto: "http://localhost:8080/links/r/a1b2c3d4"
        String urlReduced = shortener.generateLinkUrl ( token );

        link.setToken      ( token );      // salva o token na entidade para resolver o redirect depois
        link.setUrlReduced ( urlReduced ); // salva a URL curta gerada na entidade

        return linkRepository.save ( link ); // persiste no banco e retorna com id gerado
    }

    public void deleteLink ( long id )
    {
        linkRepository.deleteById ( id );
    }

    // busca um link pelo token — usado na rota de redirecionamento /links/r/{token}
    // retorna null se não encontrar (tratado no controller)
    public Link getLinkByToken ( String token )
    {
        Optional < Link > linkFound = linkRepository.findByToken ( token );

        // se encontrou, retorna o link; se não, retorna null para o controller devolver 404
        return linkFound.orElse ( null );
    }
}