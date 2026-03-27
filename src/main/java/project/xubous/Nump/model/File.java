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
@Table ( name = "table_file" )
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class File 
{
    public File ( String path )
    {
        this.path = path;
    }

    @Id
    @GeneratedValue ( strategy = GenerationType.IDENTITY )
    private long id;
    private String path;
    private long size;
    private long hash;
}
