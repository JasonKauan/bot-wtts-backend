package com.agendamento.backend.dto.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ConfiguracaoRequest(
        @NotBlank String nome,
        @Min(0) @Max(23) int horarioAbertura,
        @Min(1) @Max(24) int horarioFechamento
) {}
