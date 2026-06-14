package com.agendamento.backend.service;

import com.agendamento.backend.entity.Pagamento;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Cliente HTTP para a API do Mercado Pago (PIX) — Iteração 6.
 *
 * Sem MP_ACCESS_TOKEN configurado, opera em MODO MOCK (somente desenvolvimento):
 * gera um PIX fictício e considera "approved" qualquer consulta de pagamento MOCK-*,
 * permitindo simular o webhook de confirmação localmente.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoService {

    private static final String API_URL = "https://api.mercadopago.com";

    @Value("${mercadopago.access-token}")
    private String accessToken;

    /** URL pública para o MP notificar (ngrok em dev). Opcional. */
    @Value("${mercadopago.notification-url}")
    private String notificationUrl;

    private final RestTemplate restTemplate;

    public record PixCriado(String mercadoPagoId, String qrCode, String qrCodeBase64, String ticketUrl) {}

    public boolean isMock() {
        return accessToken == null || accessToken.isBlank();
    }

    public PixCriado criarPix(Pagamento pagamento, String emailPagador, String descricao) {
        if (isMock()) {
            log.warn("[MOCK] MP_ACCESS_TOKEN ausente — gerando PIX fictício para pagamento {}", pagamento.getId());
            return new PixCriado("MOCK-" + pagamento.getId(),
                    "00020126-PIX-MOCK-COPIA-E-COLA-" + pagamento.getId(), null, null);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("transaction_amount", pagamento.getValor());
        body.put("description", descricao);
        body.put("payment_method_id", "pix");
        body.put("payer", Map.of("email", emailPagador));
        body.put("external_reference", pagamento.getId().toString());
        if (notificationUrl != null && !notificationUrl.isBlank()) {
            body.put("notification_url", notificationUrl);
        }

        HttpHeaders headers = headers();
        headers.set("X-Idempotency-Key", pagamento.getId().toString());

        Map<?, ?> resp = restTemplate.postForObject(API_URL + "/v1/payments",
                new HttpEntity<>(body, headers), Map.class);

        String mpId = String.valueOf(resp.get("id"));
        String qrCode = null, qrCodeBase64 = null, ticketUrl = null;
        if (resp.get("point_of_interaction") instanceof Map<?, ?> poi
                && poi.get("transaction_data") instanceof Map<?, ?> td) {
            qrCode       = (String) td.get("qr_code");
            qrCodeBase64 = (String) td.get("qr_code_base64");
            ticketUrl    = (String) td.get("ticket_url");
        }
        log.info("PIX criado no Mercado Pago: {} (pagamento {})", mpId, pagamento.getId());
        return new PixCriado(mpId, qrCode, qrCodeBase64, ticketUrl);
    }

    /** Consulta o status real do pagamento no MP: approved, pending, rejected, cancelled... */
    public String consultarStatus(String mercadoPagoId) {
        if (isMock() || mercadoPagoId.startsWith("MOCK-")) {
            log.warn("[MOCK] consultarStatus({}) → approved", mercadoPagoId);
            return "approved";
        }
        Map<?, ?> resp = restTemplate.exchange(API_URL + "/v1/payments/" + mercadoPagoId,
                HttpMethod.GET, new HttpEntity<>(headers()), Map.class).getBody();
        return resp != null ? (String) resp.get("status") : null;
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken == null ? "" : accessToken);
        return headers;
    }
}
