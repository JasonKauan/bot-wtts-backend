package com.agendamento.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lista de espera (V22): cliente que quis um dia lotado e pediu pra ser avisado.
 * Quando um agendamento desse dia é cancelado/remarcado, o primeiro da fila é chamado
 * e a entrada é removida.
 */
@Entity
@Table(name = "lista_espera")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListaEspera {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String telefone;

    @Column(name = "cliente_nome")
    private String clienteNome;

    /** Preferências que o cliente já tinha escolhido na conversa (podem ser nulas). */
    @Column
    private String servico;

    @Column(name = "profissional_id")
    private UUID profissionalId;

    @Column
    private String profissional;

    /** O dia lotado que o cliente quer. */
    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    protected void onCreate() { criadoEm = LocalDateTime.now(); }
}
