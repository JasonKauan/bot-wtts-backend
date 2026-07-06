package com.agendamento.backend.dto.admin;

import java.math.BigDecimal;

/** Body do acerto de comissão. {@code valor} nulo = quitar tudo que está pendente. */
public record AcertarRequest(BigDecimal valor) {}
