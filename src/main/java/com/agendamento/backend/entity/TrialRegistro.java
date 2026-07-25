package com.agendamento.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Trial único por número de WhatsApp (V35): registro permanente de todo número que já
 * consumiu um trial — sobrevive à exclusão da conta. Número repetido = conta nasce vencida.
 */
@Entity
@Table(name = "trial_registro")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrialRegistro {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Só dígitos (normalizado). */
    @Column(nullable = false, unique = true)
    private String telefone;

    /** Primeiro tenant que consumiu o trial com esse número (referência, sem FK). */
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    protected void onCreate() { criadoEm = LocalDateTime.now(); }
}
