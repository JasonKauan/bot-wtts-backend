package com.agendamento.backend.dto.api;

import java.time.LocalDateTime;

/** Cliente do estabelecimento (agregado dos agendamentos) — aba Clientes. */
public record ClienteCrmDto(
        String nome,
        String telefone,
        long visitas,            // confirmados que já aconteceram
        long faltas,             // não compareceu
        LocalDateTime ultimaVisita,
        LocalDateTime proximoAgendamento,
        String aniversario       // "dd/mm" (V33) ou nulo
) {}
