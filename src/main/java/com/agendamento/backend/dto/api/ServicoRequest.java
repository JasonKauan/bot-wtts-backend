package com.agendamento.backend.dto.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record ServicoRequest(
        @NotBlank String nome,
        @Min(1) int duracaoMinutos,
        @DecimalMin(value = "0", message = "O preço não pode ser negativo.") BigDecimal preco // opcional
) {}
