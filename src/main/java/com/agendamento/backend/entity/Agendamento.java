package com.agendamento.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agendamento")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Agendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "cliente_nome")
    private String clienteNome;

    @Column(name = "cliente_telefone", nullable = false)
    private String clienteTelefone;

    @Column(nullable = false)
    private String servico;

    /** Nome do profissional (desnormalizado para facilitar leitura). */
    private String profissional;

    @Column(name = "profissional_id")
    private UUID profissionalId;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    /** Duração em minutos (snapshot do serviço na criação). Nulo = usa o intervalo da grade do tenant. */
    @Column(name = "duracao_minutos")
    private Integer duracaoMinutos;

    /** "CONFIRMADO" | "CANCELADO" | "NAO_COMPARECEU" */
    @Column(nullable = false)
    private String status;

    /** Quem criou (V27): BOT (cliente pelo WhatsApp) | MANUAL (dono no painel) | FIXO (recorrência). */
    @Column(nullable = false)
    @Builder.Default
    private String origem = "MANUAL";

    @Column(name = "lembrete_enviado", nullable = false)
    @Builder.Default
    private boolean lembreteEnviado = false;

    /** Lembrete do dia (algumas horas antes) — separado do de 24h. */
    @Column(name = "lembrete_dia_enviado", nullable = false)
    @Builder.Default
    private boolean lembreteDiaEnviado = false;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    protected void onCreate() { criadoEm = LocalDateTime.now(); }
}
