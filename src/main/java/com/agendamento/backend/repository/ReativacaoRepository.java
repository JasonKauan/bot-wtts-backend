package com.agendamento.backend.repository;

import com.agendamento.backend.entity.Reativacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ReativacaoRepository extends JpaRepository<Reativacao, UUID> {

    /** Dedupe: já mandamos "sentimos sua falta" pra esse cliente recentemente? */
    boolean existsByTenantIdAndTelefoneAndEnviadoEmAfter(UUID tenantId, String telefone, LocalDateTime depois);
}
