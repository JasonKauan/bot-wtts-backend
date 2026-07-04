package com.agendamento.backend.dto.admin;

import java.math.BigDecimal;
import java.util.UUID;

/** Comissões pendentes de acerto por vendedor (painel CEO). */
public record AcertoDto(
        UUID vendedorId,
        String vendedor,
        long vendas,
        BigDecimal comissaoPendente
) {}
