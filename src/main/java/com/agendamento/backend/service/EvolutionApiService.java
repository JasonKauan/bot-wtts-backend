package com.agendamento.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Cliente HTTP para a Evolution API.
 * Retry automático com backoff: 3 tentativas com 30s de intervalo se a API estiver fora.
 * TODO Iteração 7: logar tempo de resposta por tenant_id.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EvolutionApiService {

    private static final int MAX_TENTATIVAS = 3;
    private static final long BACKOFF_MS    = 30_000L;

    @Value("${evolution.api.url}")
    private String apiUrl;

    @Value("${evolution.api.key}")
    private String apiKey;

    @Value("${evolution.api.instance}")
    private String defaultInstance;

    @Value("${app.backend-url}")
    private String backendUrl;

    private final RestTemplate restTemplate;

    // ── Mensagens ──────────────────────────────────────────────────────────────

    public void enviarMensagem(String telefone, String texto) {
        enviarMensagemNaInstancia(defaultInstance, telefone, texto);
    }

    public void enviarMensagemNaInstancia(String instanceName, String telefone, String texto) {
        String url = apiUrl + "/message/sendText/" + instanceName;
        Map<String, Object> body = Map.of("number", telefone, "text", texto);

        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS; tentativa++) {
            try {
                long inicio = System.currentTimeMillis();
                ResponseEntity<String> resp = post(url, body);
                long ms = System.currentTimeMillis() - inicio;
                log.info("[{}] Mensagem enviada → telefone: {} | status: {} | {}ms",
                        instanceName, telefone, resp.getStatusCode(), ms);
                return;
            } catch (RestClientException e) {
                log.warn("[{}] Tentativa {}/{} falhou ao enviar mensagem: {}",
                        instanceName, tentativa, MAX_TENTATIVAS, e.getMessage());
                if (tentativa < MAX_TENTATIVAS) {
                    try { Thread.sleep(BACKOFF_MS); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                } else {
                    log.error("[{}] Todas as {} tentativas falharam. Mensagem não enviada para {}.",
                            instanceName, MAX_TENTATIVAS, telefone);
                }
            }
        }
    }

    // ── Gestão de instâncias ───────────────────────────────────────────────────

    public void criarInstancia(String instanceName) {
        String url = apiUrl + "/instance/create";
        Map<String, Object> body = Map.of(
                "instanceName", instanceName,
                "integration", "WHATSAPP-BAILEYS"
        );
        ResponseEntity<String> resp = post(url, body);
        log.info("Instância criada: {} | status: {}", instanceName, resp.getStatusCode());
    }

    public void configurarWebhook(String instanceName, String secret) {
        String url = apiUrl + "/webhook/set/" + instanceName;
        String webhookUrl = backendUrl + "/api/webhook/whatsapp";

        Map<String, Object> body = Map.of("webhook", Map.of(
                "enabled", true,
                "url", webhookUrl,
                "webhook_by_events", false,
                "webhook_base64", false,
                "events", new String[]{"MESSAGES_UPSERT"},
                "headers", Map.of("X-Webhook-Secret", secret)
        ));
        ResponseEntity<String> resp = post(url, body);
        log.info("Webhook configurado: {} | status: {}", instanceName, resp.getStatusCode());
    }

    // ── Conexão / QR (WhatsApp pelo painel) ────────────────────────────────────

    /** Estado da conexão da instância: "open", "connecting" ou "close". */
    public String estadoConexao(String instanceName) {
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    apiUrl + "/instance/connectionState/" + instanceName,
                    HttpMethod.GET, new HttpEntity<>(apiHeaders()), Map.class);
            if (resp.getBody() != null && resp.getBody().get("instance") instanceof Map<?, ?> inst
                    && inst.get("state") != null) {
                return String.valueOf(inst.get("state"));
            }
            return "close";
        } catch (RestClientException e) {
            return "close";
        }
    }

    /** Dispara o connect e devolve o QR (data URI base64), ou null se ainda não houver. */
    public String obterQrCode(String instanceName) {
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    apiUrl + "/instance/connect/" + instanceName,
                    HttpMethod.GET, new HttpEntity<>(apiHeaders()), Map.class);
            if (resp.getBody() != null && resp.getBody().get("base64") != null) {
                return String.valueOf(resp.getBody().get("base64"));
            }
            return null;
        } catch (RestClientException e) {
            return null;
        }
    }

    /**
     * Logout: purga as credenciais da sessão do WhatsApp. É a cura da "sessão zumbi"
     * (device_removed/401): sem isso o connect fica revivendo uma sessão morta e o
     * QR gerado nunca conecta. Best-effort — erro aqui não pode travar o fluxo.
     */
    public void logout(String instanceName) {
        try {
            restTemplate.exchange(apiUrl + "/instance/logout/" + instanceName,
                    HttpMethod.DELETE, new HttpEntity<>(apiHeaders()), String.class);
            log.info("Logout da instância {} efetuado (sessão resetada)", instanceName);
        } catch (RestClientException e) {
            log.warn("Logout da instância {} falhou (seguindo mesmo assim): {}", instanceName, e.getMessage());
        }
    }

    public boolean instanciaExiste(String instanceName) {
        try {
            restTemplate.exchange(apiUrl + "/instance/connectionState/" + instanceName,
                    HttpMethod.GET, new HttpEntity<>(apiHeaders()), Map.class);
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private HttpHeaders apiHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", apiKey);
        return headers;
    }

    private ResponseEntity<String> post(String url, Object body) {
        return restTemplate.postForEntity(url, new HttpEntity<>(body, apiHeaders()), String.class);
    }
}
