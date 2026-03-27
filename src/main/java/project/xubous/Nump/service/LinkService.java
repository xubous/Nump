package project.xubous.Nump.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import project.xubous.Nump.model.Link;
import project.xubous.Nump.repository.LinkRepository;

@Service
public class LinkService 
{
    private final LinkRepository linkRepository;

    public LinkService ( LinkRepository linkRepository )
    {
        this.linkRepository = linkRepository;
    }

    public List < Link > getAllLink ( )
    {
        return linkRepository.findAll ( );
    }

    public Optional < Link > getLinkById ( Long id )
    {
        return linkRepository.findById ( id );
    }

    public Optional < Link > getLinkByToken ( String token )
    {
        return linkRepository.findByToken ( token );
    }

    public Link saveLink ( Link link )
    {
        link.setToken ( UUID.randomUUID ( ).toString ( ).substring ( 0, 8 ) );
        return linkRepository.save ( link );
    }

    public void deleteLink ( long id )
    {
        linkRepository.deleteById ( id ); // ✅ fix
    }
}