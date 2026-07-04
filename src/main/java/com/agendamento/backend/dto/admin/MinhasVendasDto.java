package com.agendamento.backend.dto.admin;

import java.math.BigDecimal;
import java.util.List;

/** Visão do vendedor: as vendas e comissões dele no mês + o que tem a receber. */
public record MinhasVendasDto(
        long vendasMes,
        BigDecimal comissaoMes,
        BigDecimal comissaoPendente,   // ainda não acertada (todas as datas)
        List<VendaLinhaDto> vendas
) {}
