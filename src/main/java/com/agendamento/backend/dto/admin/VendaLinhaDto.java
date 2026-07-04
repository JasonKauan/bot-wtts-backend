package com.agendamento.backend.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Uma venda na listagem (CEO ou vendedor). */
public record VendaLinhaDto(
        String tenantNome,
        String vendedor,        // nome/email do vendedor, ou "Casa"
        String plano,
        BigDecimal valor,
        BigDecimal comissaoValor,
        String origem,          // MANUAL | PIX
        LocalDateTime criadoEm
) {}
