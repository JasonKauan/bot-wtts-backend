package com.agendamento.backend.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record VendedorDto(
        UUID id,
        String nome,
        String email,
        BigDecimal comissaoPct,
        int comissaoMeses,   // mensalidades comissionadas por cliente; 0 = vitalício
        boolean ativo,
        LocalDateTime criadoEm
) {}
