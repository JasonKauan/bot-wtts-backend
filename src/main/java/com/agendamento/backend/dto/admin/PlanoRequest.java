package com.agendamento.backend.dto.admin;

import com.agendamento.backend.entity.Plano;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * Ativar/estender plano com flexibilidade total pro vendedor negociar (Fase 2).
 * `modo` define como calcular a validade: "meses" (+N, soma ao que resta),
 * "dias" (+N avulsos) ou "data" (data exata de expiração).
 */
@Data
public class PlanoRequest {

    @NotNull
    private Plano plano;

    @NotBlank
    private String modo;       // "meses" | "dias" | "data"

    private Integer meses;     // modo=meses
    private Integer dias;      // modo=dias
    private LocalDate data;    // modo=data
}
