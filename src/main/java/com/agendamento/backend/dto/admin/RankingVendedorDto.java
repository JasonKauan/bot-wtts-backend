package com.agendamento.backend.dto.admin;

import java.math.BigDecimal;

/** Linha do ranking do mês (painel CEO). */
public record RankingVendedorDto(
        String vendedor,        // nome/email, ou "Casa"
        long vendas,
        BigDecimal receita,
        BigDecimal comissao
) {}
