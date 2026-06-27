package com.agendamento.backend.repository;

import com.agendamento.backend.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    List<Tenant> findByAtivoTrue();

    /** Lista de clientes do painel admin — mais recentes primeiro. */
    List<Tenant> findAllByOrderByCriadoEmDesc();
}
