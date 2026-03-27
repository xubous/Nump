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

@Entity
@Table ( name = "table_link" )
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Link 
{
    public Link ( String url, String description ) {
        //TODO Auto-generated constructor stub
    }

    @Id
    @GeneratedValue ( strategy = GenerationType.IDENTITY )
    private Long id;
    private String url;
    private String url_reduced;
    private String token;
    private String description;
}
