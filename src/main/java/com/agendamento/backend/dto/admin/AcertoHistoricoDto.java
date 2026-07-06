package com.agendamento.backend.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Linha do histórico de acertos de comissão (painel CEO). */
public record AcertoHistoricoDto(
        String vendedor,
        BigDecimal valor,
        int vendasQuitadas,
        BigDecimal pendenteApos,
        LocalDateTime criadoEm) {}
