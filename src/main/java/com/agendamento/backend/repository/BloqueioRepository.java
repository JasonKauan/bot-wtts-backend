package com.agendamento.backend.repository;

import com.agendamento.backend.entity.Bloqueio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface BloqueioRepository extends JpaRepository<Bloqueio, UUID> {

    List<Bloqueio> findByTenantIdOrderByDataInicioAsc(UUID tenantId);

    /** Bloqueios do tenant que cobrem a data (o chamador decide se vale pro profissional). */
    List<Bloqueio> findByTenantIdAndDataInicioLessThanEqualAndDataFimGreaterThanEqual(
            UUID tenantId, LocalDate inicio, LocalDate fim);
}
