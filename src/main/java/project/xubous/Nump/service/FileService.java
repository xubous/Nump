package project.xubous.Nump.service;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import project.xubous.Nump.model.File;
import project.xubous.Nump.repository.FileRepository;

@Service
public class FileService 
{
    private final FileRepository fileRepository;

    public FileService ( FileRepository fileRepository )
    {
        this.fileRepository = fileRepository;
    }

    public List < File > getAllFile ( )
    {
        return fileRepository.findAll ( );
    }

    public Optional < File > getFileById ( Long id )
    {
        return fileRepository.findById ( id );
    }

    public File saveFile ( File file )
    {
        return fileRepository.save ( file );
    }

    public void deleteFile ( long id )
    {
        fileRepository.deleteById ( id );
    }
}