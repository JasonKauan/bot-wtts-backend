package com.agendamento.backend.dto.api;

import java.math.BigDecimal;
import java.util.List;

/** botAgendamentos30d/botReceita30d (V27): ROI do bot — quanto ele agendou/rendeu nos últimos 30 dias. */
public record DashboardDto(
        List<AgendamentoDto> agendamentos,
        long pendentes,
        long botAgendamentos30d,
        BigDecimal botReceita30d) {}
