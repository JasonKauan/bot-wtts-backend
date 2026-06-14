package com.agendamento.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pagamento")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** ID do pagamento no Mercado Pago (null até a cobrança ser criada lá). */
    @Column(name = "mercado_pago_id")
    private String mercadoPagoId;

    @Column(nullable = false)
    private BigDecimal valor;

    /** PENDENTE, APROVADO, REJEITADO */
    @Column(nullable = false)
    @Builder.Default
    private String status = "PENDENTE";

    /** Plano comprado — necessário para o webhook saber o que ativar no tenant. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Plano plano;

    /** Mês de referência da mensalidade, ex.: "2026-06". */
    @Column(name = "mes_referencia", nullable = false)
    private String mesReferencia;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    protected void onCreate() { criadoEm = LocalDateTime.now(); }
}
