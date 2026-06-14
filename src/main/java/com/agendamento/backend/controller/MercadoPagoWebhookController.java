package com.agendamento.backend.controller;

import com.agendamento.backend.service.AssinaturaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Webhook de pagamento do Mercado Pago — Iteração 6.
 * O MP envia o id tanto em query string (?type=payment&data.id=123)
 * quanto no corpo ({"type":"payment","data":{"id":"123"}}).
 * Responde sempre 200 para o MP não reenviar indefinidamente.
 */
@RestController
@RequestMapping("/api/webhook/mercadopago")
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoWebhookController {

    private final AssinaturaService assinaturaService;

    @PostMapping
    public ResponseEntity<Void> receber(@RequestBody(required = false) Map<String, Object> body,
                                        @RequestParam(value = "type", required = false) String typeParam,
                                        @RequestParam(value = "data.id", required = false) String dataIdParam) {
        String type = typeParam;
        String id = dataIdParam;

        if (body != null) {
            if (type == null && body.get("type") instanceof String s) type = s;
            if (id == null && body.get("data") instanceof Map<?, ?> data && data.get("id") != null) {
                id = String.valueOf(data.get("id"));
            }
        }

        log.info("Webhook Mercado Pago recebido: type={} id={}", type, id);
        if ("payment".equalsIgnoreCase(type) && id != null) {
            assinaturaService.processarWebhook(id);
        }
        return ResponseEntity.ok().build();
    }
}
