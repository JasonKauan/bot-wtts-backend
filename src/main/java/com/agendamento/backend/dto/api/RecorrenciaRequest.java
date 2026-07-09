package com.agendamento.backend.dto.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.UUID;

/** Criar um cliente fixo. frequenciaDias: 7 semanal, 14 quinzenal, 28 mensal (mínimo 7). */
public record RecorrenciaRequest(
        @NotBlank String clienteNome,
        String clienteTelefone,
        @NotNull UUID servicoId,
        UUID profissionalId,
        @Min(7) @Max(90) int frequenciaDias,
        @NotBlank @Pattern(regexp = "\\d{2}:\\d{2}") String hora,
        @NotNull LocalDate primeiraData
) {}
