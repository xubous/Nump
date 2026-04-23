package project.xubous.Nump.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping ( "/upload" )
    public ResponseEntity < File > uploadFile ( @RequestParam ( "file" ) MultipartFile file ) throws IOException
    {
        File saved = fileService.uploadAndCompress ( file );
        return ResponseEntity.ok ( saved );
    }

    @GetMapping ( "/r/{token}" )
    public ResponseEntity < byte[] > download ( @PathVariable String token ) throws IOException
    {
        return fileService.getFileByToken ( token )
                .map ( file ->
                {
                    try
                    {
                        byte[] bytes = fileService.readFileBytes ( file.getPath ( ) );

                        HttpHeaders headers = new HttpHeaders ( );
                        headers.setContentType ( MediaType.parseMediaType ( "application/zip" ) );
                        headers.setContentDispositionFormData ( "attachment", "arquivo_" + token + ".zip" );

                        return ResponseEntity.ok ( ).headers ( headers ).body ( bytes );
                    }
                    catch ( IOException e )
                    {
                        return ResponseEntity.internalServerError ( ). < byte[] >build ( );
                    }
                } )
                .orElse ( ResponseEntity.notFound ( ).build ( ) );
    }

    @DeleteMapping ( "/{id}" )
    public ResponseEntity < Void > deleteFile ( @PathVariable Long id )
    {
        fileService.deleteFile ( id );
        return ResponseEntity.noContent ( ).build ( );
    }
}