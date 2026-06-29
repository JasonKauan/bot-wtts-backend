package com.agendamento.backend.repository;

import com.agendamento.backend.entity.Bloqueio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface BloqueioRepository extends JpaRepository<Bloqueio, UUID> {

    List<Bloqueio> findByTenantIdOrderByDataInicioAsc(UUID tenantId);

    /** A data cai dentro de algum bloqueio do tenant? (data_inicio <= data <= data_fim) */
    boolean existsByTenantIdAndDataInicioLessThanEqualAndDataFimGreaterThanEqual(
            UUID tenantId, LocalDate inicio, LocalDate fim);
}
