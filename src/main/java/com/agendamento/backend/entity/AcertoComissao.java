package com.agendamento.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Um acerto de comissão (total ou parcial) pago a um vendedor — vira histórico no painel CEO. */
@Entity
@Table(name = "acerto_comissao")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcertoComissao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vendedor_id", nullable = false)
    private UUID vendedorId;

    /** Snapshot do nome (ou email) do vendedor na hora do acerto. */
    @Column(name = "vendedor_nome")
    private String vendedorNome;

    /** Quanto foi pago neste acerto. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    /** Vendas cuja comissão foi quitada por inteiro neste acerto. */
    @Column(name = "vendas_quitadas", nullable = false)
    @Builder.Default
    private int vendasQuitadas = 0;

    /** Quanto ainda ficou devendo pro vendedor depois deste acerto. */
    @Column(name = "pendente_apos", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal pendenteApos = BigDecimal.ZERO;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    protected void onCreate() { criadoEm = LocalDateTime.now(); }
}
