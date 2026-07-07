package com.agendamento.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenant")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String nome;

    @Column(name = "telefone_whatsapp")
    private String telefoneWhatsapp;

    @Column(name = "webhook_secret", nullable = false)
    private String webhookSecret;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;

    /** Horário de atendimento — antes hardcoded no BotService (Iteração 2). */
    @Column(name = "horario_abertura", nullable = false)
    @Builder.Default
    private int horarioAbertura = 8;

    @Column(name = "horario_fechamento", nullable = false)
    @Builder.Default
    private int horarioFechamento = 18;

    /** Grade horária (Iteração 7) — intervalo entre slots, em minutos. */
    @Column(name = "intervalo_minutos", nullable = false)
    @Builder.Default
    private int intervaloMinutos = 60;

    /** Janela de almoço (horas 0-24) pulada na grade. Nulo = sem almoço. */
    @Column(name = "almoco_inicio")
    private Integer almocoInicio;

    @Column(name = "almoco_fim")
    private Integer almocoFim;

    /** Dias de funcionamento ISO (1=seg ... 7=dom), separados por vírgula. */
    @Column(name = "dias_funcionamento", nullable = false)
    @Builder.Default
    private String diasFuncionamento = "1,2,3,4,5,6,7";

    /** Fila de aprovação: se true, agendamentos do bot entram como PENDENTE e o dono aceita/recusa. */
    @Column(name = "aprovacao_manual", nullable = false)
    @Builder.Default
    private boolean aprovacaoManual = false;

    /** Antecedência mínima (horas) pra cancelar/remarcar pelo bot. 0 = sem regra. */
    @Column(name = "antecedencia_min_horas", nullable = false)
    @Builder.Default
    private int antecedenciaMinHoras = 0;

    /** Resumo diário da agenda no WhatsApp do dono, toda manhã (V20). */
    @Column(name = "resumo_diario", nullable = false)
    @Builder.Default
    private boolean resumoDiario = true;

    /** Última data em que o resumo diário foi enviado (dedupe do job). */
    @Column(name = "resumo_enviado_em")
    private java.time.LocalDate resumoEnviadoEm;

    /** Vendedor que trouxe este cliente (carteira). Nulo = cliente "da casa". */
    @Column(name = "vendedor_id")
    private UUID vendedorId;

    /** Plano de assinatura — Iteração 6. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Plano plano = Plano.TRIAL;

    @Column(name = "trial_expira_em")
    private LocalDateTime trialExpiraEm;

    @Column(name = "assinatura_expira_em")
    private LocalDateTime assinaturaExpiraEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    protected void onCreate() { criadoEm = LocalDateTime.now(); }

    /** TRIAL vale até trial_expira_em; planos pagos até assinatura_expira_em (Iteração 6). */
    public boolean isAssinaturaVencida() {
        LocalDateTime limite = (plano == Plano.TRIAL) ? trialExpiraEm : assinaturaExpiraEm;
        return limite == null || limite.isBefore(LocalDateTime.now());
    }
}
