package project.xubous.Nump.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import project.xubous.Nump.model.File;
import project.xubous.Nump.repository.FileRepository;

@Service
public class FileService
{
    private final FileRepository fileRepository;
    private final Compactor compactor;
    private final Shortener shortener;

    public FileService ( FileRepository fileRepository, Compactor compactor, Shortener shortener )
    {
        this.fileRepository = fileRepository;
        this.compactor      = compactor;
        this.shortener      = shortener;
    }

    public List < File > getAllFile ( )
    {
        return fileRepository.findAll ( );
    }

    public Optional < File > getFileById ( Long id )
    {
        return fileRepository.findById ( id );
    }

    public File uploadAndCompress ( MultipartFile multipartFile ) throws IOException
    {
        String token = shortener.generateToken ( );

        Path zipPath = compactor.compactAndSave ( multipartFile, token );

        String downloadUrl = shortener.generateDownloadUrl ( token );

        File file = new File ( );
        file.setPath        ( zipPath.toString ( ) );
        file.setSize        ( Files.size ( zipPath ) );
        file.setToken       ( token );
        file.setDownloadUrl ( downloadUrl );

        return fileRepository.save ( file );
    }

    public Optional < File > getFileByToken ( String token )
    {
        return fileRepository.findByToken ( token );
    }

    public byte[] readFileBytes ( String path ) throws IOException
    {
        return Files.readAllBytes ( Path.of ( path ) );
    }

    public void deleteFile ( long id )
    {
        fileRepository.deleteById ( id );
    }
}