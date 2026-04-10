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
    private final Shortener shortener;

    public LinkService ( LinkRepository linkRepository, Shortener shortener )
    {
        this.linkRepository = linkRepository;
        this.shortener = shortener;
    }

    public List < Link > getAllLink ( )
    {
        return linkRepository.findAll ( );
    }

    public Optional < Link > getLinkById ( Long id )
    {
        return linkRepository.findById ( id );
    }

    public Link saveLink ( Link link )
    {
        String token = shortener.generateToken ( );
        String urlReduced = shortener.generateReducedLink ( link.getUrl ( ) );

        link.setToken ( token );
        link.setUrlReduced ( urlReduced );

        return linkRepository.save ( link );
    }

    public void deleteLink ( long id )
    {
        linkRepository.deleteById ( id );
    }

    public Link getLinkByToken ( String token )
    {
        Optional < Link > linkFound = linkRepository.findByToken ( token );

        if ( linkFound.isPresent ( ) )
        {
            return linkFound.get ( );
        } else
            {   
                return null;
            }

    }
}