package com.agendamento.backend.dto.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ConfiguracaoRequest(
        @NotBlank String nome,
        @Min(0) @Max(23) int horarioAbertura,
        @Min(1) @Max(24) int horarioFechamento,
        // Grade horária (Iteração 7)
        @Min(5) @Max(240) int intervaloMinutos,
        @Min(0) @Max(24) Integer almocoInicio,   // nulo = sem almoço
        @Min(0) @Max(24) Integer almocoFim,
        String diasFuncionamento                  // ISO "1,2,3,4,5,6,7"
) {}
