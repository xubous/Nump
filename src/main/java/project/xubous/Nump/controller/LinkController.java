package project.xubous.Nump.controller;

import java.net.URI;
import java.util.List;
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

@RestController
@RequestMapping ( "/links" )
public class LinkController 
{
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

    @GetMapping ( "/{id}" )
    public ResponseEntity < Link > getLinkById ( @PathVariable Long id )
    {
        return linkService.getLinkById ( id )
                .map ( ResponseEntity::ok )
                .orElse ( ResponseEntity.notFound ( ).build ( ) );
    }

    @GetMapping ( "/r/{token}" )
    public ResponseEntity < Void > redirect ( @PathVariable String token )
    {
        return linkService.getLinkByToken ( token )
                .map ( link -> ResponseEntity
                        .status ( HttpStatus.FOUND )
                        .location ( URI.create ( link.getUrl ( ) ) )
                        .< Void >build ( ) )
                .orElse ( ResponseEntity.notFound ( ).build ( ) );
    }

    @PostMapping
    public Link createLink ( @RequestBody Link link )
    {
        return linkService.saveLink ( link );
    }

    @DeleteMapping ( "/{id}" )
    public ResponseEntity < Void > deleteLink ( @PathVariable Long id )
    {
        linkService.deleteLink ( id );
        return ResponseEntity.noContent ( ).build ( );
    }
}