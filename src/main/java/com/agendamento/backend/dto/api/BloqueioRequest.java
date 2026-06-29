package com.agendamento.backend.dto.api;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Criar uma folga/feriado. dataFim opcional (nulo = só o dia de dataInicio). */
public record BloqueioRequest(
        @NotNull LocalDate dataInicio,
        LocalDate dataFim,
        String descricao
) {}
