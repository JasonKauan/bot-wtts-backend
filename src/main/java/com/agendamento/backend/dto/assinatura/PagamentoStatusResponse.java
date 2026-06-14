package com.agendamento.backend.dto.assinatura;

import java.math.BigDecimal;
import java.util.UUID;

/** Resposta do polling do painel: PENDENTE, APROVADO, REJEITADO. */
public record PagamentoStatusResponse(
        UUID id,
        String status,
        String plano,
        BigDecimal valor
) {}
