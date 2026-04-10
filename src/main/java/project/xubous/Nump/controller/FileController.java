package project.xubous.Nump.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;    // permite adicionar cabeçalhos na resposta HTTP
import org.springframework.http.MediaType;      // define o tipo de conteúdo da resposta (ex: application/zip)
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;  // lê parâmetros de formulário/multipart
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile; // representa o arquivo recebido no upload

import project.xubous.Nump.model.File;
import project.xubous.Nump.service.FileService;

@RestController              // combina @Controller + @ResponseBody — todas as respostas viram JSON automaticamente
@RequestMapping ( "/files" ) // todas as rotas desse controller começam com /files
public class FileController 
{
    private final FileService fileService;

    // o Spring injeta o FileService automaticamente via construtor
    public FileController ( FileService fileService )
    {
        this.fileService = fileService;
    }

    // GET /files — lista todos os arquivos cadastrados no banco
    @GetMapping
    public List < File > getAllFile ( )
    {
        return fileService.getAllFile ( );
    }

    // GET /files/{id} — busca um arquivo específico pelo id
    // .map(ResponseEntity::ok) → se existir, devolve 200 OK com o arquivo
    // .orElse(notFound) → se não existir, devolve 404
    @GetMapping ( "/{id}" )
    public ResponseEntity < File > getFileById ( @PathVariable Long id )
    {
        return fileService.getFileById ( id )
                .map ( ResponseEntity::ok )
                .orElse ( ResponseEntity.notFound ( ).build ( ) );
    }

    // POST /files/upload — recebe um arquivo via multipart/form-data, compacta e salva
    // @RequestParam("file") lê o campo "file" do formulário de upload
    // throws IOException porque operações de disco podem falhar
    @PostMapping ( "/upload" )
    public ResponseEntity < File > uploadFile ( @RequestParam ( "file" ) MultipartFile file ) throws IOException
    {
        // chama o serviço que compacta, gera token, gera URL curta e salva no banco
        File saved = fileService.uploadAndCompress ( file );

        // devolve 200 OK com a entidade salva (contém id, path, token, downloadUrl)
        return ResponseEntity.ok ( saved );
    }

    // GET /files/r/{token} — resolve o token e devolve o .zip para download
    // essa é a rota que o usuário acessa quando clica no link curto de download
    @GetMapping ( "/r/{token}" )
    public ResponseEntity < byte[] > download ( @PathVariable String token ) throws IOException
    {
        // busca o File no banco pelo token
        return fileService.getFileByToken ( token )
                .map ( file ->
                {
                    try
                    {
                        // lê os bytes do .zip do disco usando o path salvo no banco
                        byte[] bytes = fileService.readFileBytes ( file.getPath ( ) );

                        // monta os cabeçalhos HTTP da resposta de download
                        HttpHeaders headers = new HttpHeaders ( );

                        // Content-Type: application/zip — avisa o navegador que é um arquivo zip
                        headers.setContentType ( MediaType.parseMediaType ( "application/zip" ) );

                        // Content-Disposition: attachment — força o navegador a baixar em vez de abrir
                        // filename define o nome sugerido para o arquivo salvo
                        headers.setContentDispositionFormData ( "attachment", "arquivo_" + token + ".zip" );

                        // devolve 200 OK com os bytes do .zip e os cabeçalhos configurados
                        return ResponseEntity.ok ( ).headers ( headers ).body ( bytes );
                    }
                    catch ( IOException e )
                    {
                        // se der erro lendo o disco, devolve 500 Internal Server Error
                        return ResponseEntity.internalServerError ( ). < byte[] >build ( );
                    }
                } )
                // se o token não existir no banco, devolve 404
                .orElse ( ResponseEntity.notFound ( ).build ( ) );
    }

    // DELETE /files/{id} — remove o registro do banco (não apaga o .zip do disco ainda)
    // devolve 204 No Content — sucesso sem corpo na resposta
    @DeleteMapping ( "/{id}" )
    public ResponseEntity < Void > deleteFile ( @PathVariable Long id )
    {
        fileService.deleteFile ( id );
        return ResponseEntity.noContent ( ).build ( );
    }
}