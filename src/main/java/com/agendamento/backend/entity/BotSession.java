package com.agendamento.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bot_session", uniqueConstraints = @UniqueConstraint(
    name = "bot_session_telefone_tenant_uq", columnNames = {"telefone", "tenant_id"}
))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class BotSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String telefone;

    /** SERVICO → PROFISSIONAL → DATA → HORA → CONFIRMACAO */
    @Column(nullable = false)
    private String etapa;

    @Column(name = "servico_escolhido")
    private String servicoEscolhido;

    @Column(name = "profissional_id")
    private UUID profissionalId;

    @Column(name = "profissional_escolhido")
    private String profissionalEscolhido;

    @Column(name = "data_escolhida")
    private LocalDate dataEscolhida;

    @Column(name = "hora_escolhida")
    private String horaEscolhida;

    /** Quando a conversa é uma REMARCAÇÃO: id do agendamento que será movido. Null = agendamento novo. */
    @Column(name = "remarcando_id")
    private UUID remarcandoId;

    /** Último dia informado como lotado (V22): um "avisa" do cliente entra na fila desse dia. */
    @Column(name = "espera_data")
    private LocalDate esperaData;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "ultima_interacao", nullable = false)
    private LocalDateTime ultimaInteracao;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    protected void onCreate() { criadoEm = LocalDateTime.now(); }
}
