package com.agendamento.backend.dto.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.UUID;

/** Agendamento criado manualmente pelo dono no painel (cliente que ligou/chegou na porta). */
public record NovoAgendamentoRequest(
        @NotBlank String clienteNome,
        String clienteTelefone,          // opcional (sem telefone = sem lembretes)
        @NotNull UUID servicoId,
        UUID profissionalId,             // opcional
        @NotNull LocalDate data,
        @NotBlank @Pattern(regexp = "([01]?\\d|2[0-3]):[0-5]\\d", message = "Horário inválido (use HH:mm).")
        String hora
) {}
