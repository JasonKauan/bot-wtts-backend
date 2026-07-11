package com.agendamento.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** Registro de "sentimos sua falta" enviado (V32) — dedupe: 1 por cliente a cada 60 dias. */
@Entity
@Table(name = "reativacao")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reativacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String telefone;

    @Column(name = "enviado_em", nullable = false, updatable = false)
    private LocalDateTime enviadoEm;

    @PrePersist
    protected void onCreate() { enviadoEm = LocalDateTime.now(); }
}
