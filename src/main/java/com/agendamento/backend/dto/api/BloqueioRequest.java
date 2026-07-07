package com.agendamento.backend.dto.api;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Criar uma folga/feriado. dataFim opcional (nulo = só o dia de dataInicio).
 * profissionalId opcional (nulo = folga do estabelecimento inteiro — V21).
 */
public record BloqueioRequest(
        @NotNull LocalDate dataInicio,
        LocalDate dataFim,
        String descricao,
        UUID profissionalId
) {}
