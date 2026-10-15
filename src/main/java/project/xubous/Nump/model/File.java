package project.xubous.Nump.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    // ── Controle do link (arquivo temporário) ─────────────────────────────────
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;      // null = sem expiração (linhas antigas)
    private Integer maxDownloads;         // null = sem limite de downloads
    private long downloadCount;
    private boolean revoked;

    // ── Metadados ─────────────────────────────────────────────────────────────
    private String originalName;
    private String description;

    @Enumerated ( EnumType.STRING )
    private OrigemArquivo origem = OrigemArquivo.UPLOAD;
}
