package com.agendamento.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** Uma ação do back-office (SUPERADMIN) — trilha de auditoria (Fase 3). */
@Entity
@Table(name = "admin_auditoria")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "admin_id")
    private UUID adminId;

    @Column(name = "admin_email")
    private String adminEmail;

    @Column(nullable = false)
    private String acao;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "tenant_nome")
    private String tenantNome;

    @Column
    private String detalhe;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    protected void onCreate() {
        criadoEm = LocalDateTime.now();
    }
}
