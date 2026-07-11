package com.agendamento.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** Multi-unidade (V34): dá a um usuário acesso a um tenant além do "casa" (usuario.tenantId). */
@Entity
@Table(name = "unidade_vinculo", uniqueConstraints = @UniqueConstraint(
        name = "uq_unidade_vinculo", columnNames = {"usuario_id", "tenant_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnidadeVinculo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    protected void onCreate() { criadoEm = LocalDateTime.now(); }
}
