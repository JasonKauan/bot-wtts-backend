package com.agendamento.backend.controller;

import com.agendamento.backend.dto.WebhookPayload;
import com.agendamento.backend.entity.Plano;
import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.entity.TrialRegistro;
import com.agendamento.backend.repository.TenantRepository;
import com.agendamento.backend.repository.TrialRegistroRepository;
import com.agendamento.backend.service.BotService;
import com.agendamento.backend.service.EvolutionApiService;
import com.agendamento.backend.service.MessageDedup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Iteração 3: identifica o tenant pelo campo `instance` do payload
 * (instance name = tenant UUID) e valida o X-Webhook-Secret.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final BotService       botService;
    private final TenantRepository tenantRepository;
    private final MessageDedup     messageDedup;
    private final TrialRegistroRepository trialRegistroRepository;
    private final EvolutionApiService evolutionApiService;

    /** Aviso "assinatura vencida" pro dono, no máx 1x/dia por tenant (em memória, single-node). */
    private final Map<UUID, LocalDate> avisoVencidaEnviado = new ConcurrentHashMap<>();

    @PostMapping("/api/webhook/whatsapp")
    public ResponseEntity<Void> receberMensagem(
            @RequestHeader(value = "X-Webhook-Secret", required = false) String secret,
            @RequestBody WebhookPayload payload) {

        if (payload.isFromMe() || !payload.isMensagemRecebida()) {
            return ResponseEntity.ok().build();
        }

        // Identificar tenant pelo instance name (= tenant UUID)
        Tenant tenant = resolverTenant(payload.getInstance());
        if (tenant == null) {
            log.warn("Nenhum tenant encontrado para instância: {}", payload.getInstance());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // Validar secret (comparação constant-time contra timing attack)
        if (secret == null || !MessageDigest.isEqual(
                secret.getBytes(StandardCharsets.UTF_8),
                tenant.getWebhookSecret().getBytes(StandardCharsets.UTF_8))) {
            log.warn("Secret inválido para tenant: {}", tenant.getId());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Dedup SÓ DEPOIS do secret: senão qualquer um enche o mapa (memória) e
        // pode "queimar" IDs de mensagem sem estar autenticado.
        if (messageDedup.jaProcessada(payload.messageId())) {
            return ResponseEntity.ok().build();
        }

        // Cliente suspenso pelo admin: bot fica mudo (não responde nada).
        if (!tenant.isAtivo()) {
            log.info("Tenant {} suspenso — bot silenciado, mensagem ignorada.", tenant.getId());
            return ResponseEntity.ok().build();
        }

        // Trial único (V35): o número REAL pareado é a identidade que não dá pra falsificar.
        // 1º uso em trial registra; se já queimou trial em OUTRA conta, encerra este trial na hora.
        aplicarTrialUnico(tenant, payload);

        // Assinatura vencida: bot PAUSA (senão trial vencido continuaria agendando de graça
        // pra sempre — só o painel era bloqueado). O dono é avisado 1x/dia.
        if (tenant.isAssinaturaVencida()) {
            avisarDonoVencida(tenant);
            return ResponseEntity.ok().build();
        }

        String telefone  = payload.extractPhone();
        String mensagem  = payload.extractText();
        String pushName  = payload.getData() != null ? payload.getData().getPushName() : null;

        if (telefone.isEmpty() || mensagem.isEmpty()) {
            return ResponseEntity.ok().build();
        }

        botService.processMessage(telefone, mensagem, pushName, tenant);
        return ResponseEntity.ok().build();
    }

    /** Registra o número real pareado no 1º uso do trial; corta trial reciclado por outra conta. */
    private void aplicarTrialUnico(Tenant tenant, WebhookPayload payload) {
        if (tenant.getPlano() != Plano.TRIAL) return;
        String donoFone = payload.extractSenderPhone();
        if (donoFone.isEmpty()) return;   // payload sem sender: check é pulado, sem risco

        TrialRegistro reg = trialRegistroRepository.findByTelefone(donoFone).orElse(null);
        if (reg == null) {
            try {
                trialRegistroRepository.save(TrialRegistro.builder()
                        .telefone(donoFone).tenantId(tenant.getId()).build());
            } catch (Exception e) { /* corrida com outro registro simultâneo: ignora */ }
        } else if (!tenant.getId().equals(reg.getTenantId()) && !tenant.isAssinaturaVencida()) {
            tenant.setTrialExpiraEm(LocalDateTime.now());
            tenantRepository.save(tenant);
            log.warn("[TrialUnico] Número {} já consumiu trial no tenant {} — trial do tenant {} encerrado",
                    donoFone, reg.getTenantId(), tenant.getId());
        }
    }

    /** Bot pausado por vencimento: avisa o dono no WhatsApp (1x/dia, best-effort). */
    private void avisarDonoVencida(Tenant tenant) {
        String donoFone = tenant.getTelefoneWhatsapp();
        if (donoFone == null || donoFone.isBlank()) return;
        LocalDate hoje = LocalDate.now();
        if (hoje.equals(avisoVencidaEnviado.get(tenant.getId()))) return;
        avisoVencidaEnviado.put(tenant.getId(), hoje);
        try {
            evolutionApiService.enviarMensagemNaInstancia(tenant.getId().toString(), donoFone,
                    "⏸️ Sua assinatura venceu e o bot está *pausado* — seus clientes estão mandando "
                    + "mensagem e não recebem resposta.\n\nRenove no painel, aba *Assinatura* "
                    + "(PIX na hora), que eu volto a atender no mesmo minuto 😉");
        } catch (Exception e) {
            log.warn("[Vencida] Falha ao avisar o dono do tenant {}: {}", tenant.getId(), e.getMessage());
        }
    }

    private Tenant resolverTenant(String instanceName) {
        if (instanceName == null) return null;
        try {
            UUID tenantId = UUID.fromString(instanceName);
            return tenantRepository.findById(tenantId).orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
