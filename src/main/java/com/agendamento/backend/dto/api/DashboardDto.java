package com.agendamento.backend.dto.api;

import java.util.List;

public record DashboardDto(List<AgendamentoDto> agendamentos, long pendentes) {}
