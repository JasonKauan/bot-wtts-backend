package com.agendamento.backend.dto.admin;

import java.math.BigDecimal;
import java.util.List;

/** Visão do vendedor: as vendas e comissões dele no mês. */
public record MinhasVendasDto(
        long vendasMes,
        BigDecimal comissaoMes,
        List<VendaLinhaDto> vendas
) {}
