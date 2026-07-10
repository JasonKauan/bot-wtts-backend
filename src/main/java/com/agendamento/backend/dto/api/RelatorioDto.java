package com.agendamento.backend.dto.api;

import java.math.BigDecimal;
import java.util.List;

/** Números simples pro dono: próximos 7 dias e os últimos 30 dias. */
public record RelatorioDto(
        long proximos7Dias,      // agendamentos ativos (confirmado+pendente) nos próximos 7 dias
        long realizados30Dias,   // confirmados que aconteceram nos últimos 30 dias
        long faltas30Dias,       // não compareceu nos últimos 30 dias
        long cancelados30Dias,   // cancelados nos últimos 30 dias
        int taxaFaltaPct,        // faltas / (realizados + faltas)
        List<ServicoContagem> servicosTop,  // serviços mais pedidos nos últimos 30 dias
        // Financeiro (estimado pelo preço ATUAL de cada serviço; serviço sem preço conta R$ 0)
        BigDecimal receita30Dias,
        List<FaturamentoLinha> receitaPorServico,
        List<FaturamentoLinha> receitaPorProfissional,
        boolean financeiroLiberado   // false = plano sem o recurso (frontend mostra upgrade)
) {}
