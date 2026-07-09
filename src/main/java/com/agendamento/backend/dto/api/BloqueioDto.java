package com.agendamento.backend.dto.api;

import java.time.LocalDate;
import java.util.UUID;

/**
 * profissionalId/nome nulos = vale pro estabelecimento inteiro.
 * horaInicio/horaFim nulos = dia inteiro; preenchidos = compromisso avulso (faixa de horas).
 */
public record BloqueioDto(UUID id, LocalDate dataInicio, LocalDate dataFim, String descricao,
                          UUID profissionalId, String profissionalNome,
                          String horaInicio, String horaFim) {}
