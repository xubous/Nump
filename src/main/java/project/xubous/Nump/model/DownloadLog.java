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

// Log de cada download público de um arquivo — "quem baixou, quando e de onde"
@Entity
@Table ( name = "table_download_log" )
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DownloadLog
{
    @Id
    @GeneratedValue ( strategy = GenerationType.IDENTITY )
    private long id;

    private long fileId;
    private String token;
    private LocalDateTime downloadedAt;
    private String ip;
    private String userAgent;

    @Column ( nullable = false )
    private String ownerEmail;
}
