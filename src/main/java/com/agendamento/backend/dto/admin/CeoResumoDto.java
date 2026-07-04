package com.agendamento.backend.dto.admin;

import java.math.BigDecimal;
import java.util.List;

/** Visão do negócio (painel CEO): mês atual x anterior, ranking e vendas recentes. */
public record CeoResumoDto(
        BigDecimal receitaMes,
        long vendasMes,
        BigDecimal comissoesMes,
        BigDecimal receitaMesAnterior,
        long vendasMesAnterior,
        List<RankingVendedorDto> ranking,
        List<VendaLinhaDto> vendasRecentes
) {}
