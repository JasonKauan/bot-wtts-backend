package com.agendamento.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Substitui a lista hardcoded da Iteração 1.
 * Cada tenant gerencia seus próprios serviços.
 *
 * Iteração 4: adicionar preco (BigDecimal) para exibir no painel.
 */
@Entity
@Table(name = "servico")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Servico {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String nome;

    @Column(name = "duracao_minutos", nullable = false)
    @Builder.Default
    private int duracaoMinutos = 30;

    /** Preço exibido pro cliente (opcional — nulo não mostra). */
    @Column(precision = 10, scale = 2)
    private java.math.BigDecimal preco;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    protected void onCreate() {
        criadoEm = LocalDateTime.now();
    }
}
