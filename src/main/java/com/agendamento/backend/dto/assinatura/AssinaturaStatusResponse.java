package com.agendamento.backend.dto.assinatura;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Situação da assinatura do tenant para o painel. */
public record AssinaturaStatusResponse(
        String plano,
        BigDecimal valorMensal,
        LocalDateTime expiraEm,
        long diasRestantes,
        boolean vencida,
        boolean avisoTrial
) {}
