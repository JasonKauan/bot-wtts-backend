package com.agendamento.backend.repository;

import com.agendamento.backend.entity.Venda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface VendaRepository extends JpaRepository<Venda, UUID> {

    /** Visão CEO: vendas desde uma data (mês atual + anterior, agregadas em memória). */
    List<Venda> findByCriadoEmGreaterThanEqualOrderByCriadoEmDesc(LocalDateTime inicio);

    /** Visão do vendedor: as vendas dele desde uma data. */
    List<Venda> findByVendedorIdAndCriadoEmGreaterThanEqualOrderByCriadoEmDesc(UUID vendedorId, LocalDateTime inicio);

    /** Acerto de comissões: tudo que ainda não foi pago (com vendedor). */
    List<Venda> findByPagoFalseAndVendedorIdIsNotNullOrderByCriadoEmDesc();

    List<Venda> findByVendedorIdAndPagoFalse(UUID vendedorId);

    /** Acerto parcial: pendentes do vendedor, da mais antiga pra mais nova (paga na ordem). */
    List<Venda> findByVendedorIdAndPagoFalseOrderByCriadoEmAsc(UUID vendedorId);

    /** Export CSV: histórico completo. */
    List<Venda> findAllByOrderByCriadoEmDesc();
}
