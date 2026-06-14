package com.agendamento.backend.repository;

import com.agendamento.backend.entity.Profissional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfissionalRepository extends JpaRepository<Profissional, UUID> {
    List<Profissional> findByTenantIdOrderByNome(UUID tenantId);
    List<Profissional> findByTenantIdAndAtivoTrue(UUID tenantId);

    /** Iteração 6: profissionais ativos contam para o limite do plano. */
    long countByTenantIdAndAtivoTrue(UUID tenantId);
}
