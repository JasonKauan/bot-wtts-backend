package com.agendamento.backend.dto.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Criar uma folga/feriado ou compromisso avulso.
 * dataFim opcional (nulo = só o dia de dataInicio).
 * profissionalId opcional (nulo = vale pro estabelecimento inteiro — V21).
 * horaInicio/horaFim opcionais (nulos = dia inteiro; preenchidos = só a faixa — V24).
 */
public record BloqueioRequest(
        @NotNull LocalDate dataInicio,
        LocalDate dataFim,
        String descricao,
        UUID profissionalId,
        @Pattern(regexp = "\\d{2}:\\d{2}") String horaInicio,
        @Pattern(regexp = "\\d{2}:\\d{2}") String horaFim
) {}
