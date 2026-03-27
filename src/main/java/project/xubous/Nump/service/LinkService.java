package project.xubous.Nump.service;

import java.util.List;
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

    public Link saveLink ( Link link )
    {
        return linkRepository.save ( link  );
    }

    public void deleteLink ( long id )
    {
        linkRepository.deleteById ( null );
    }
}
