package project.xubous.Nump.service;

import java.io.FileOutputStream;  // abre/cria um arquivo no disco para escrita
import java.io.IOException;        // exceção lançada quando operações de I/O falham
import java.io.InputStream;        // representa um fluxo de bytes de entrada (vindo do upload)
import java.nio.file.Files;        // utilitário do Java para criar diretórios, checar paths, etc.
import java.nio.file.Path;         // representa um caminho no sistema de arquivos
import java.nio.file.Paths;        // fábrica para criar objetos Path a partir de Strings
import java.util.zip.ZipEntry;     // representa um item (arquivo) dentro do .zip
import java.util.zip.ZipOutputStream; // fluxo de escrita que gera o formato .zip

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile; // representa o arquivo recebido no upload HTTP

@Service // o Spring gerencia essa classe — ela pode ser injetada em FileService
public class Compactor
{
    // pasta onde os .zip vão ser salvos no servidor
    // Paths.get("uploads") cria um Path relativo — na prática fica em /uploads dentro do projeto
    private final Path uploadDir = Paths.get ( "uploads" );

    // método principal: recebe o arquivo do upload, compacta para .zip e salva no disco
    // retorna o Path (caminho) do .zip criado para que o FileService possa salvar no banco
    public Path compactAndSave ( MultipartFile file, String token ) throws IOException
    {
        // verifica se a pasta /uploads existe; se não existir, cria ela (incluindo subpastas)
        if ( !Files.exists ( uploadDir ) )
        {
            Files.createDirectories ( uploadDir ); // cria /uploads se não existir
        }

        // monta o nome do arquivo .zip usando o token — garante nome único
        // ex: "uploads/arquivo_a1b2c3d4.zip"
        String zipName = "arquivo_" + token + ".zip";

        // resolve() junta o diretório base com o nome do arquivo, formando o caminho completo
        Path zipPath = uploadDir.resolve ( zipName ); // ex: uploads/arquivo_a1b2c3d4.zip

        // abre o arquivo .zip para escrita no disco
        // FileOutputStream cria/abre o arquivo físico no caminho informado
        // ZipOutputStream envolve o FileOutputStream e escreve no formato .zip
        // try-with-resources: fecha os streams automaticamente ao terminar, mesmo se houver erro
        try ( FileOutputStream fos = new FileOutputStream ( zipPath.toFile ( ) );
              ZipOutputStream zos = new ZipOutputStream ( fos ) )
        {
            // pega o nome original do arquivo que o usuário fez upload
            // ex: "relatorio.pdf", "foto.png"
            String originalName = file.getOriginalFilename ( );

            // cria uma entrada no .zip com o nome do arquivo original
            // ZipEntry define o "slot" dentro do .zip que vai receber os bytes do arquivo
            ZipEntry entry = new ZipEntry ( originalName != null ? originalName : "arquivo" );

            // putNextEntry() inicia a escrita dessa entrada dentro do .zip
            zos.putNextEntry ( entry );

            // pega o stream de bytes do arquivo recebido no upload
            InputStream inputStream = file.getInputStream ( );

            // lê o arquivo em blocos de 1024 bytes e escreve dentro do .zip
            // isso é mais eficiente que carregar o arquivo inteiro na memória de uma vez
            byte[] buffer = new byte [ 1024 ]; // buffer de leitura — 1024 bytes por vez
            int bytesRead;                      // guarda quantos bytes foram lidos em cada iteração

            // read() lê até 1024 bytes e retorna quantos foram lidos; retorna -1 quando acaba
            while ( ( bytesRead = inputStream.read ( buffer ) ) != -1 )
            {
                zos.write ( buffer, 0, bytesRead ); // escreve exatamente os bytes lidos no .zip
            }

            zos.closeEntry ( ); // finaliza a entrada atual dentro do .zip
        }

        return zipPath; // retorna o caminho do .zip gerado para o FileService usar
    }
}