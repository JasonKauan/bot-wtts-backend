package com.agendamento.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** Uma mensagem da conversa bot ↔ cliente (V28) — o dono audita na tela /conversas. */
@Entity
@Table(name = "bot_mensagem")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BotMensagem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String telefone;

    @Column(name = "cliente_nome")
    private String clienteNome;

    /** true = o cliente mandou; false = o bot respondeu. */
    @Column(name = "de_cliente", nullable = false)
    private boolean deCliente;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texto;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    protected void onCreate() { criadoEm = LocalDateTime.now(); }
}
