package project.xubous.Nump.service;

import java.util.List;
import org.springframework.stereotype.Service;
import project.xubous.Nump.model.File;
import project.xubous.Nump.repository.FileRepository;

@Service
public class FileService 
{
    private final FileRepository fileRepository;;

    public FileService (  FileRepository fileRepository )
    {
        this.fileRepository = fileRepository;
    }

    public List < File > getAllFile ( )
    {
        return fileRepository.findAll ( );
    }

    public File saveFile ( File File )
    {
        return fileRepository.save ( File  );
    }

    public void deleteFile ( long id )
    {
        fileRepository.deleteById ( null );
    }
}
