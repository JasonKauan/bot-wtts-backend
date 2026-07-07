package com.agendamento.backend.dto.api;

import java.time.LocalDate;
import java.util.UUID;

/** profissionalId/nome nulos = folga do estabelecimento inteiro. */
public record BloqueioDto(UUID id, LocalDate dataInicio, LocalDate dataFim, String descricao,
                          UUID profissionalId, String profissionalNome) {}
