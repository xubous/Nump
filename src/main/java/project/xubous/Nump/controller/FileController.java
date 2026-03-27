package project.xubous.Nump.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.xubous.Nump.model.File;
import project.xubous.Nump.service.FileService;

@RestController
@RequestMapping ( "/files" )
public class FileController 
{
    private final FileService fileService;

    public FileController ( FileService fileService )
    {
        this.fileService = fileService;
    }

    @GetMapping
    public List < File > getAllFile ( )
    {
        return fileService.getAllFile ( );
    }

    @GetMapping ( "/{id}" )
    public ResponseEntity < File > getFileById ( @PathVariable Long id )
    {
        return fileService.getFileById ( id )
                .map ( ResponseEntity::ok )
                .orElse ( ResponseEntity.notFound ( ).build ( ) );
    }

    @PostMapping
    public File createFile ( @RequestBody File file )
    {
        return fileService.saveFile ( file );
    }

    @DeleteMapping ( "/{id}" )
    public ResponseEntity < Void > deleteFile ( @PathVariable Long id )
    {
        fileService.deleteFile ( id );
        return ResponseEntity.noContent ( ).build ( );
    }
}