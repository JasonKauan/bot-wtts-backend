package com.agendamento.backend.service;

import com.agendamento.backend.entity.Plano;
import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Régua de cobrança (V37): avisa o dono no WhatsApp ANTES da assinatura vencer.
 *
 * Por que existe: a cobrança é por PIX, que não debita sozinho — o dono precisa voltar
 * no painel e pagar todo mês. Sem lembrete, cliente satisfeito cancela por esquecimento.
 * O aviso de "já venceu" (bot pausado) fica no WebhookController; aqui é o que vem antes.
 *
 * Marcos: 5, 3 e 1 dia antes, e no dia do vencimento. Cada marco dispara no máximo uma vez
 * por ciclo — `tenant.avisoRenovacaoMarco` guarda o menor já enviado e volta a 99 na renovação.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CobrancaService {

    private static final int[] MARCOS = {5, 3, 1, 0};
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM");

    private final TenantRepository tenantRepository;
    private final EvolutionApiService evolutionApiService;

    /** Roda de manhã; janela larga porque o Render free pode acordar tarde. */
    @Scheduled(cron = "0 10 8-11 * * *")
    @Transactional
    public int avisarVencimentos() {
        LocalDateTime agora = LocalDateTime.now();
        int enviados = 0;

        for (Tenant t : tenantRepository.findByAtivoTrue()) {
            LocalDateTime expira = (t.getPlano() == Plano.TRIAL) ? t.getTrialExpiraEm() : t.getAssinaturaExpiraEm();
            if (expira == null || t.getTelefoneWhatsapp() == null || t.getTelefoneWhatsapp().isBlank()) continue;

            long dias = ChronoUnit.DAYS.between(agora.toLocalDate(), expira.toLocalDate());
            if (dias < 0) continue;   // já venceu: quem cuida é o aviso de bot pausado

            Integer marco = marcoDe(dias);
            if (marco == null || marco >= t.getAvisoRenovacaoMarco()) continue;   // ainda não chegou, ou já avisei

            try {
                evolutionApiService.enviarMensagemNaInstancia(
                        t.getId().toString(), t.getTelefoneWhatsapp(), montar(t, dias, expira));
                t.setAvisoRenovacaoMarco(marco);
                tenantRepository.save(t);
                enviados++;
            } catch (Exception e) {
                log.warn("[Cobranca] Falha ao avisar tenant {}: {}", t.getId(), e.getMessage());
            }
        }
        if (enviados > 0) log.info("[Cobranca] {} aviso(s) de vencimento enviados", enviados);
        return enviados;
    }

    /** O maior marco que os dias restantes já alcançaram (5 dias → marco 5; 2 dias → marco 1). */
    private Integer marcoDe(long dias) {
        for (int m : MARCOS) {
            if (dias >= m) return m;
        }
        return null;
    }

    private String montar(Tenant t, long dias, LocalDateTime expira) {
        boolean trial = t.getPlano() == Plano.TRIAL;
        String quando = dias == 0 ? "*hoje*" : dias == 1 ? "*amanhã*" : "em *" + dias + " dias* (" + expira.format(FMT) + ")";

        if (trial) {
            return "⏳ Seu teste grátis do Chadbot termina " + quando + ".\n\n"
                    + "Nesses dias eu atendi seus clientes, marquei horário e mandei lembrete — pra continuar,"
                    + " escolha um plano na aba *Assinatura* do painel. O PIX cai na hora e nada se perde 😉\n\n"
                    + "Planos a partir de *R$ 39,90/mês*.";
        }

        String base = "🔔 Sua assinatura do Chadbot vence " + quando + ".\n\n"
                + "Renove pelo painel, aba *Assinatura* — PIX na hora, leva 1 minuto.";
        if (dias <= 1) {
            base += "\n\n⚠️ Se vencer, eu *paro de atender* seus clientes até a renovação — e eles ficam sem resposta.";
        }
        return base;
    }
}
