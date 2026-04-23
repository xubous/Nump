package project.xubous.Nump.model;

import jakarta.persistence.Column;
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
    @Id
    @GeneratedValue ( strategy = GenerationType.IDENTITY )
    private long id;

    private String path;
    private long size;
    private String token;
    private String downloadUrl;

    @Column(nullable = false)
    private String ownerEmail;
}