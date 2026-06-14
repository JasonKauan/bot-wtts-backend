package com.agendamento.backend.dto.api;

import java.time.LocalDateTime;
import java.util.UUID;

public record AgendamentoDto(
        UUID id,
        String clienteNome,
        String clienteTelefone,
        String servico,
        String profissional,
        LocalDateTime dataHora,
        String status
) {}
