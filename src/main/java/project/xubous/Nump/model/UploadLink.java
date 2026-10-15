package project.xubous.Nump.model;

import java.time.LocalDateTime;

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

// Link de coleta (upload reverso): o cliente envia arquivo/log sem login,
// e o arquivo recebido vira propriedade do técnico que criou o link
@Entity
@Table ( name = "table_upload_link" )
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UploadLink
{
    @Id
    @GeneratedValue ( strategy = GenerationType.IDENTITY )
    private long id;

    private String token;
    private String coletaUrl;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private int uploadCount;
    private Integer maxUploads;   // null = sem limite de envios
    private boolean active;

    @Column ( nullable = false )
    private String ownerEmail;
}
