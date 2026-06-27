package com.agendamento.backend.controller;

import com.agendamento.backend.dto.WebhookPayload;
import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.repository.TenantRepository;
import com.agendamento.backend.service.BotService;
import com.agendamento.backend.service.MessageDedup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

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

    @PostMapping("/api/webhook/whatsapp")
    public ResponseEntity<Void> receberMensagem(
            @RequestHeader(value = "X-Webhook-Secret", required = false) String secret,
            @RequestBody WebhookPayload payload) {

        if (payload.isFromMe() || !payload.isMensagemRecebida()) {
            return ResponseEntity.ok().build();
        }
        // Dedup: a Evolution reenvia o mesmo webhook às vezes → não responder em duplicidade.
        if (messageDedup.jaProcessada(payload.messageId())) {
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

        String telefone  = payload.extractPhone();
        String mensagem  = payload.extractText();
        String pushName  = payload.getData() != null ? payload.getData().getPushName() : null;

        if (telefone.isEmpty() || mensagem.isEmpty()) {
            return ResponseEntity.ok().build();
        }

        botService.processMessage(telefone, mensagem, pushName, tenant);
        return ResponseEntity.ok().build();
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
