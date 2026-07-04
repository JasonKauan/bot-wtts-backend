package com.agendamento.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Uma ativação/renovação de plano pago = uma venda (manual pelo painel ou PIX do cliente). */
@Entity
@Table(name = "venda")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "tenant_nome")
    private String tenantNome;

    /** Nulo = venda "da casa" (cliente sem vendedor). */
    @Column(name = "vendedor_id")
    private UUID vendedorId;

    @Column(name = "vendedor_email")
    private String vendedorEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Plano plano;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    /** Snapshot da % do vendedor no momento da venda. */
    @Column(name = "comissao_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal comissaoPct;

    @Column(name = "comissao_valor", nullable = false, precision = 10, scale = 2)
    private BigDecimal comissaoValor;

    /** MANUAL (painel) | PIX (cliente pagou sozinho). */
    @Column(nullable = false)
    private String origem;

    /** Comissão já acertada (paga) pro vendedor? */
    @Column(nullable = false)
    @Builder.Default
    private boolean pago = false;

    @Column(name = "pago_em")
    private LocalDateTime pagoEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    protected void onCreate() { criadoEm = LocalDateTime.now(); }
}
