package com.agendamento.backend.repository;

import com.agendamento.backend.entity.BotSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BotSessionRepository extends JpaRepository<BotSession, UUID> {

    Optional<BotSession> findByTelefoneAndTenantId(String telefone, UUID tenantId);
}
