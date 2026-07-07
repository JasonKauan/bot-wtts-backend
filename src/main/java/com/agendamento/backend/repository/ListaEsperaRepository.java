package com.agendamento.backend.repository;

import com.agendamento.backend.entity.ListaEspera;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ListaEsperaRepository extends JpaRepository<ListaEspera, UUID> {

    /** A fila de um dia, por ordem de chegada. */
    List<ListaEspera> findByTenantIdAndDataOrderByCriadoEmAsc(UUID tenantId, LocalDate data);

    /** Já está na fila desse dia? (evita duplicar) */
    boolean existsByTenantIdAndTelefoneAndData(UUID tenantId, String telefone, LocalDate data);

    /** Limpeza: entradas de dias que já passaram. */
    void deleteByDataBefore(LocalDate data);
}
