package com.agendamento.backend.repository;

import com.agendamento.backend.entity.Servico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ServicoRepository extends JpaRepository<Servico, UUID> {

    List<Servico> findByTenantIdAndAtivoTrue(UUID tenantId);
}
