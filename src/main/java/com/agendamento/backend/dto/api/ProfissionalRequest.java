package com.agendamento.backend.dto.api;

import jakarta.validation.constraints.NotBlank;

public record ProfissionalRequest(@NotBlank String nome) {}
