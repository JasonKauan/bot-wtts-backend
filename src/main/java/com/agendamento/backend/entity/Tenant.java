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
