package project.xubous.Nump.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity                          // diz ao JPA que essa classe representa uma tabela no banco
@Table ( name = "table_link" )   // define o nome real da tabela no banco como "table_link"
@Getter                          // Lombok gera automaticamente todos os métodos get()
@Setter                          // Lombok gera automaticamente todos os métodos set()
@NoArgsConstructor               // Lombok gera um construtor vazio — obrigatório pro JPA funcionar
@AllArgsConstructor              // Lombok gera um construtor com todos os campos como parâmetros
public class Link 
{
    // construtor de conveniência — usado quando você quer criar um Link passando só url e descrição
    // antes estava vazio (bug): os campos nunca eram inicializados e ficavam null no banco
    public Link ( String url, String description )
    {
        this.url = url;                 // inicializa o campo url com o valor recebido
        this.description = description; // inicializa o campo description com o valor recebido
    }

    @Id                                                    // marca esse campo como chave primária
    @GeneratedValue ( strategy = GenerationType.IDENTITY ) // o banco auto-incrementa o id
    private Long id;

    private String url;         // URL original longa que o usuário quer encurtar
    private String urlReduced;  // URL curta gerada — ex: http://localhost:8080/links/r/a1b2c3d4
    private String token;       // token de 8 chars — parte final da URL curta
    private String description; // descrição opcional que o usuário passa ao criar o link
}