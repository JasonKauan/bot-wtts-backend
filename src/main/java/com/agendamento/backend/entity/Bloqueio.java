package com.agendamento.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Folga/feriado: período (data_inicio..data_fim, inclusivo) em que o estabelecimento não atende.
 * Com {@code profissionalId} preenchido (V21), a folga vale só pra esse profissional.
 */
@Entity
@Table(name = "bloqueio")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bloqueio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Nulo = folga do estabelecimento inteiro. */
    @Column(name = "profissional_id")
    private UUID profissionalId;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim", nullable = false)
    private LocalDate dataFim;

    @Column
    private String descricao;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    protected void onCreate() { criadoEm = LocalDateTime.now(); }
}
