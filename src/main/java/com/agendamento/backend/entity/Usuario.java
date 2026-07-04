package com.agendamento.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "usuario")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Null para SUPERADMIN (papel sem tenant — painel admin, Fase 1). */
    @Column(name = "tenant_id")
    private UUID tenantId;

    /** Nome de exibição (vendedor no ranking, etc.). Opcional. */
    @Column
    private String nome;

    /** % de comissão por venda — só faz sentido para role VENDEDOR. */
    @Column(name = "comissao_pct", precision = 5, scale = 2)
    private java.math.BigDecimal comissaoPct;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String senha;

    /** "OWNER" (dono do estabelecimento) ou "SUPERADMIN" (back-office, sem tenant). */
    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    protected void onCreate() {
        criadoEm = LocalDateTime.now();
    }
}
