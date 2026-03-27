package project.xubous.Nump.controller;

import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.xubous.Nump.model.Link;
import project.xubous.Nump.service.LinkService;

@RestController
@RequestMapping ( "/links" )
public class LinkController {

    private final LinkService linkService;

    public LinkController ( LinkService linkService )
    {
        this.linkService = linkService;
    }
    
    @GetMapping
    public List < Link > getAllLink ( )
    {
        return linkService.getAllLink ( );
    }

    @PostMapping
    public Link createLink ( @RequestBody Link link )
    {
        return linkService.saveLink ( link ); 
    }

    @DeleteMapping ( "/{id}" )
    public void deleteLink ( @PathVariable Long id )
    {
        linkService.deleteLink ( id );
    }
}

