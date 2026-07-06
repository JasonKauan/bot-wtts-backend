package com.agendamento.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "profissional")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Profissional {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;

    // ── Grade individual (V18) — nulo = herda o valor do estabelecimento ──────

    @Column(name = "horario_abertura")
    private Integer horarioAbertura;

    @Column(name = "horario_fechamento")
    private Integer horarioFechamento;

    @Column(name = "almoco_inicio")
    private Integer almocoInicio;

    @Column(name = "almoco_fim")
    private Integer almocoFim;

    /** Dias de trabalho ISO (1=seg ... 7=dom), separados por vírgula. Nulo = dias do estabelecimento. */
    @Column(name = "dias_trabalho")
    private String diasTrabalho;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    protected void onCreate() { criadoEm = LocalDateTime.now(); }

    /** Este profissional tem alguma grade própria configurada? */
    public boolean temGradePropria() {
        return horarioAbertura != null || horarioFechamento != null
                || almocoInicio != null || almocoFim != null
                || (diasTrabalho != null && !diasTrabalho.isBlank());
    }
}
