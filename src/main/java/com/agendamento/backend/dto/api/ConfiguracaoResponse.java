package com.agendamento.backend.dto.api;

import java.util.UUID;

/** Resposta de /api/configuracoes — expõe só o que o painel usa, sem webhookSecret nem campos internos do Tenant. */
public record ConfiguracaoResponse(
        UUID id,
        String nome,
        String telefoneWhatsapp,
        int horarioAbertura,
        int horarioFechamento,
        int intervaloMinutos,
        Integer almocoInicio,
        Integer almocoFim,
        String diasFuncionamento,
        boolean aprovacaoManual,
        int antecedenciaMinHoras,
        boolean resumoDiario) {}
