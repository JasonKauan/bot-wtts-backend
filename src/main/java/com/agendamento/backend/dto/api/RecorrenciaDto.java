package com.agendamento.backend.dto.api;

import java.time.LocalDate;
import java.util.UUID;

public record RecorrenciaDto(
        UUID id,
        String clienteNome,
        String clienteTelefone,
        String servico,
        String profissional,
        int frequenciaDias,
        String hora,
        LocalDate proximaData,
        boolean ativo) {}
