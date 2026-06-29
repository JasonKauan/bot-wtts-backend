package com.agendamento.backend.dto.api;

import java.time.LocalDate;
import java.util.UUID;

public record BloqueioDto(UUID id, LocalDate dataInicio, LocalDate dataFim, String descricao) {}
