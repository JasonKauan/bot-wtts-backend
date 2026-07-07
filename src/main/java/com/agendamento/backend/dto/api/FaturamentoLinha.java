package com.agendamento.backend.dto.api;

import java.math.BigDecimal;

/** Linha do relatório financeiro: receita estimada por serviço ou por profissional. */
public record FaturamentoLinha(String nome, long atendimentos, BigDecimal receita) {}
