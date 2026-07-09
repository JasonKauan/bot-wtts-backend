package com.agendamento.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Cliente fixo (V26): "toda quinta às 19h". Um job diário gera o agendamento com antecedência. */
@Entity
@Table(name = "recorrencia")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recorrencia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "cliente_nome", nullable = false)
    private String clienteNome;

    /** Com telefone o cliente recebe lembretes e pode cancelar pelo bot. */
    @Column(name = "cliente_telefone")
    private String clienteTelefone;

    @Column(nullable = false)
    private String servico;

    @Column(name = "profissional_id")
    private UUID profissionalId;

    @Column
    private String profissional;

    /** 7 = semanal, 14 = quinzenal, 28 = mensal (mesmo dia da semana). */
    @Column(name = "frequencia_dias", nullable = false)
    private int frequenciaDias;

    @Column(nullable = false)
    private String hora;   // "HH:mm"

    /** Próxima ocorrência que o job vai gerar. */
    @Column(name = "proxima_data", nullable = false)
    private LocalDate proximaData;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    protected void onCreate() { criadoEm = LocalDateTime.now(); }
}
