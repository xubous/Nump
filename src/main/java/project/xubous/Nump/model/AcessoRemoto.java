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

// Sessão de acesso remoto: link temporário em que o cliente autoriza o AnyDesk.
// Guarda a trilha de auditoria do consentimento (quem, quando, de qual IP/agente).
@Entity
@Table ( name = "table_acesso_remoto" )
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AcessoRemoto
{
    @Id
    @GeneratedValue ( strategy = GenerationType.IDENTITY )
    private long id;

    private String token;
    private String consentUrl;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;      // validade do link (padrão 15 min)

    @Enumerated ( EnumType.STRING )
    private StatusAcessoRemoto status = StatusAcessoRemoto.PENDENTE;

    // ── Trilha de auditoria do consentimento ──────────────────────────────────
    private LocalDateTime consentAt;
    private String consentIp;
    private String consentUserAgent;
    private String clienteEmail;          // email informado pelo cliente (opcional)

    @Column ( nullable = false )
    private String ownerEmail;
}
