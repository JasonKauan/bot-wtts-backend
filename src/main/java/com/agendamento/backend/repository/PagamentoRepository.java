package com.agendamento.backend.repository;

import com.agendamento.backend.entity.Pagamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PagamentoRepository extends JpaRepository<Pagamento, UUID> {

    Optional<Pagamento> findByMercadoPagoId(String mercadoPagoId);

    Optional<Pagamento> findByIdAndTenantId(UUID id, UUID tenantId);
}
