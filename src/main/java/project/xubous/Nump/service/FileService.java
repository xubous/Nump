package project.xubous.Nump.service;

import java.io.IOException;
import java.nio.file.Files; // utilitário para ler bytes de um arquivo do disco
import java.nio.file.Path;  // representa um caminho no sistema de arquivos
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile; // representa o arquivo recebido no upload

import project.xubous.Nump.model.File;
import project.xubous.Nump.repository.FileRepository;

@Service
public class FileService 
{
    private final FileRepository fileRepository;
    private final Compactor compactor;   // responsável por compactar o arquivo em .zip
    private final Shortener shortener;   // responsável por gerar token e URL curta

    // o Spring injeta as três dependências automaticamente via construtor
    public FileService ( FileRepository fileRepository, Compactor compactor, Shortener shortener )
    {
        this.fileRepository = fileRepository;
        this.compactor      = compactor;
        this.shortener      = shortener;
    }

    // retorna todos os arquivos cadastrados no banco
    public List < File > getAllFile ( )
    {
        return fileRepository.findAll ( );
    }

    // busca um arquivo pelo id — retorna Optional porque pode não existir
    public Optional < File > getFileById ( Long id )
    {
        return fileRepository.findById ( id );
    }

    // fluxo principal de upload:
    // 1) gera token único
    // 2) compacta o arquivo recebido em .zip via Compactor
    // 3) monta a entidade File com path, size, token e downloadUrl
    // 4) salva no banco e retorna
    public File uploadAndCompress ( MultipartFile multipartFile ) throws IOException
    {
        String token = shortener.generateToken ( ); // ex: "a1b2c3d4"

        // passa o arquivo e o token pro Compactor — ele salva o .zip no disco e retorna o caminho
        Path zipPath = compactor.compactAndSave ( multipartFile, token );

        String downloadUrl = shortener.generateFileUrl ( token ); // ex: http://localhost:8080/files/r/a1b2c3d4

        File file = new File ( ); // cria entidade vazia para popular os campos
        file.setPath        ( zipPath.toString ( ) ); // caminho do .zip no disco — ex: uploads/arquivo_a1b2c3d4.zip
        file.setSize        ( Files.size ( zipPath ) ); // lê o tamanho real do .zip em bytes após compactar
        file.setToken       ( token );       // token de 8 chars para resolver o download depois
        file.setDownloadUrl ( downloadUrl ); // URL curta completa para o usuário compartilhar

        return fileRepository.save ( file ); // persiste no banco e retorna com id gerado
    }

    // busca um arquivo pelo token — usado na rota de download /files/r/{token}
    // retorna Optional porque o token pode não existir no banco
    public Optional < File > getFileByToken ( String token )
    {
        return fileRepository.findByToken ( token );
    }

    // lê os bytes do .zip do disco para devolver na resposta HTTP de download
    // recebe o path salvo no banco e retorna os bytes brutos do arquivo
    public byte[] readFileBytes ( String path ) throws IOException
    {
        return Files.readAllBytes ( Path.of ( path ) ); // lê o arquivo inteiro do disco de uma vez
    }

    public void deleteFile ( long id )
    {
        fileRepository.deleteById ( id );
    }
}