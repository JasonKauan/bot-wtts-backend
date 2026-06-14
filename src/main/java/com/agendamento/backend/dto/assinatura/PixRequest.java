package com.agendamento.backend.dto.assinatura;

import com.agendamento.backend.entity.Plano;
import jakarta.validation.constraints.NotNull;

public record PixRequest(@NotNull Plano plano) {}
