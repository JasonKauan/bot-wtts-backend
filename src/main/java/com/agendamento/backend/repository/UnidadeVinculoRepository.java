package com.agendamento.backend.repository;

import com.agendamento.backend.entity.UnidadeVinculo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UnidadeVinculoRepository extends JpaRepository<UnidadeVinculo, UUID> {

    List<UnidadeVinculo> findByUsuarioId(UUID usuarioId);

    boolean existsByUsuarioIdAndTenantId(UUID usuarioId, UUID tenantId);

    /** Teto de unidades por usuário (anti-abuso de criação em massa). */
    long countByUsuarioId(UUID usuarioId);
}
