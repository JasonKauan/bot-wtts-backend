package com.agendamento.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Aniversário de um cliente (V33) — cadastrado pelo dono no CRM; parabéns automático 1x/ano. */
@Entity
@Table(name = "aniversario", uniqueConstraints = @UniqueConstraint(
        name = "uq_aniversario", columnNames = {"tenant_id", "telefone"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Aniversario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String telefone;

    @Column
    private String nome;

    @Column(nullable = false)
    private int dia;

    @Column(nullable = false)
    private int mes;

    /** Último ano em que os parabéns foram enviados (evita repetir no mesmo ano). */
    @Column(name = "ultimo_envio")
    private LocalDate ultimoEnvio;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    protected void onCreate() { criadoEm = LocalDateTime.now(); }
}
