package com.agendamento.backend.entity;

import java.math.BigDecimal;

/**
 * Planos de assinatura — Iteração 6.
 * TRIAL: 30 dias grátis, tudo liberado.
 * BASICO: R$79/mês, 1 profissional, 100 agendamentos/mês.
 * PRO: R$129/mês, 5 profissionais, agendamentos ilimitados.
 * PLUS: R$199/mês, tudo ilimitado.
 */
public enum Plano {
    TRIAL (BigDecimal.ZERO,          Integer.MAX_VALUE, Integer.MAX_VALUE),
    BASICO(new BigDecimal("79.00"),  1,                 100),
    PRO   (new BigDecimal("129.00"), 5,                 Integer.MAX_VALUE),
    PLUS  (new BigDecimal("199.00"), Integer.MAX_VALUE, Integer.MAX_VALUE);

    private final BigDecimal valorMensal;
    private final int maxProfissionais;
    private final int maxAgendamentosMes;

    Plano(BigDecimal valorMensal, int maxProfissionais, int maxAgendamentosMes) {
        this.valorMensal = valorMensal;
        this.maxProfissionais = maxProfissionais;
        this.maxAgendamentosMes = maxAgendamentosMes;
    }

    public BigDecimal getValorMensal()    { return valorMensal; }
    public int getMaxProfissionais()      { return maxProfissionais; }
    public int getMaxAgendamentosMes()    { return maxAgendamentosMes; }
}
