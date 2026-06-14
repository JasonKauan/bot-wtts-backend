package com.agendamento.backend.dto.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ServicoRequest(@NotBlank String nome, @Min(1) int duracaoMinutos) {}
