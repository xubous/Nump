package project.xubous.Nump.controller;

import java.util.List;
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

    @PostMapping
    public File createFile ( @RequestBody File File )
    {
        return fileService.saveFile ( File ); 
    }

    @DeleteMapping ( "/{id}" )
    public void deleteFile ( @PathVariable Long id )
    {
        fileService.deleteFile ( id );
    }
}
