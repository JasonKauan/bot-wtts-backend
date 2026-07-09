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

    // ── Compromisso avulso (V24): bloqueia só uma faixa do dia. Nulos = dia inteiro ──

    @Column(name = "hora_inicio")
    private String horaInicio;   // "HH:mm"

    @Column(name = "hora_fim")
    private String horaFim;      // "HH:mm"

    /** Bloqueio de dia inteiro (folga/feriado) ou só de uma faixa de horas? */
    public boolean isDiaInteiro() {
        return horaInicio == null || horaFim == null;
    }

    @Column
    private String descricao;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    protected void onCreate() { criadoEm = LocalDateTime.now(); }
}
