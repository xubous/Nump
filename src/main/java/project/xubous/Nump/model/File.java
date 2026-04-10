package project.xubous.Nump.model;

// Importações do JPA — dizem ao Spring como mapear essa classe para uma tabela no banco
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Lombok — gera automaticamente getters, setters e construtores sem você escrever
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity                          // diz ao JPA que essa classe representa uma tabela no banco
@Table ( name = "table_file" )   // define o nome real da tabela no banco como "table_file"
@Getter                          // Lombok gera automaticamente todos os métodos get()
@Setter                          // Lombok gera automaticamente todos os métodos set()
@NoArgsConstructor               // Lombok gera um construtor vazio — obrigatório pro JPA funcionar
@AllArgsConstructor              // Lombok gera um construtor com todos os campos como parâmetros
public class File
{
    @Id                                                    // marca esse campo como chave primária da tabela
    @GeneratedValue ( strategy = GenerationType.IDENTITY ) // o banco auto-incrementa o id (1, 2, 3...)
    private long id;

    private String path;        // caminho do arquivo .zip no disco — ex: /uploads/arquivo_a1b2c3d4.zip
    private long size;          // tamanho do arquivo em bytes depois de compactado
    private String token;       // token de 8 chars gerado pelo Shortener — serve como chave de download
    private String downloadUrl; // URL curta completa — ex: http://localhost:8080/files/r/a1b2c3d4
}