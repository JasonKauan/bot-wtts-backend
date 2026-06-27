package com.agendamento.backend.repository;

import com.agendamento.backend.entity.AdminAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AdminAuditoriaRepository extends JpaRepository<AdminAuditoria, UUID> {

    /** Histórico para a tela de auditoria — mais recentes primeiro. */
    List<AdminAuditoria> findTop200ByOrderByCriadoEmDesc();
}
