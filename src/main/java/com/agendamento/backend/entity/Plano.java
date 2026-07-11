package com.agendamento.backend.entity;

import java.math.BigDecimal;

/**
 * Planos de assinatura (reestruturados em 2026-07-10).
 * A escada é por NÍVEL: Gold(1) = o essencial de agendamento; Platinum(2) = equipe e
 * proteção da agenda; Diamond(3) = gestão do negócio. TRIAL experimenta tudo (nível 3)
 * — quando acaba, o dono sente falta do que perdeu.
 * Agendamentos são ILIMITADOS em todos os planos (decisão do usuário).
 * Feature nova = uma linha no enum {@link Recurso} com o nível mínimo.
 */
public enum Plano {
    TRIAL   (BigDecimal.ZERO,          Integer.MAX_VALUE, 3, "Teste grátis"),
    GOLD    (new BigDecimal("39.90"),  2,                 1, "Gold"),
    PLATINUM(new BigDecimal("79.90"),  5,                 2, "Platinum"),
    DIAMOND (new BigDecimal("119.90"), Integer.MAX_VALUE, 3, "Diamond");

    /** Recursos com plano mínimo. Nível: 1=Gold, 2=Platinum, 3=Diamond. */
    public enum Recurso {
        GRADE_PROFISSIONAL(2),   // horários individuais por profissional
        FOLGA_PROFISSIONAL(2),   // folga de um profissional só
        COMBOS(2),               // "corte e barba" num agendamento só
        LISTA_ESPERA(2),         // fila de dia lotado no bot
        ESCUDO_FALTAO(2),        // faltão cai na fila de aprovação
        FILA_APROVACAO(2),       // aprovação manual dos agendamentos
        RESUMO_DIARIO(2),        // agenda do dia no WhatsApp do dono
        CRM(2),                  // aba Clientes
        RECORRENCIA(3),          // clientes fixos
        FINANCEIRO(3),           // relatório financeiro + CSV
        CONVERSAS(3),            // histórico das conversas do bot
        PAGINA_PUBLICA(3),       // link público de agendamento (bio do Instagram)
        REATIVACAO(3),           // "sentimos sua falta" pra cliente sumido
        ANIVERSARIO(3),          // parabéns automático com mimo
        MULTI_UNIDADE(3);        // 2+ estabelecimentos numa conta

        private final int nivelMinimo;
        Recurso(int nivelMinimo) { this.nivelMinimo = nivelMinimo; }
        public int getNivelMinimo() { return nivelMinimo; }
    }

    private final BigDecimal valorMensal;
    private final int maxProfissionais;
    private final int nivel;
    private final String nomeBonito;

    Plano(BigDecimal valorMensal, int maxProfissionais, int nivel, String nomeBonito) {
        this.valorMensal = valorMensal;
        this.maxProfissionais = maxProfissionais;
        this.nivel = nivel;
        this.nomeBonito = nomeBonito;
    }

    public BigDecimal getValorMensal() { return valorMensal; }
    public int getMaxProfissionais()   { return maxProfissionais; }
    public int getNivel()              { return nivel; }
    public String getNomeBonito()      { return nomeBonito; }

    /** Este plano tem acesso ao recurso? */
    public boolean permite(Recurso r) { return nivel >= r.getNivelMinimo(); }

    /** Nome bonito do plano mais barato que libera o recurso (pra mensagem de upgrade). */
    public static String minimoPara(Recurso r) {
        for (Plano p : values()) {
            if (p != TRIAL && p.permite(r)) return p.nomeBonito;
        }
        return DIAMOND.nomeBonito;
    }
}
