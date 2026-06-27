package com.agendamento.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Camada de IA (OpenRouter, API compatível com OpenAI) usada como fallback de
 * linguagem natural no bot. Determinístico vem primeiro; isto só entra quando o
 * parse falha. Qualquer erro/timeout devolve "sem resposta" e o bot degrada para
 * o fluxo de menu — nunca quebra.
 */
@Service
@Slf4j
public class AiService {

    private final RestTemplate http;

    @Value("${ai.api.url:https://generativelanguage.googleapis.com/v1beta/openai/chat/completions}")
    private String url;

    @Value("${ai.api.key:}")
    private String apiKey;

    @Value("${ai.api.model:gemini-2.0-flash}")
    private String model;

    public AiService(RestTemplateBuilder builder,
                     @Value("${ai.api.timeout-ms:8000}") long timeoutMs) {
        // timeout curto: o bot não pode travar esperando a IA num webhook síncrono.
        this.http = builder
                .setConnectTimeout(Duration.ofMillis(timeoutMs))
                .setReadTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    public boolean ativo() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Chamada de chat. Devolve o texto da resposta, ou null em qualquer falha. */
    public String chat(String system, String user) {
        if (!ativo()) return null;
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "temperature", 0.1,
                    "max_tokens", 24,
                    "messages", List.of(
                            Map.of("role", "system", "content", system),
                            Map.of("role", "user", "content", user)
                    )
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            headers.set("X-Title", "AgendaBot");

            Map<?, ?> resp = http.postForObject(url, new HttpEntity<>(body, headers), Map.class);
            if (resp != null && resp.get("choices") instanceof List<?> choices && !choices.isEmpty()
                    && choices.get(0) instanceof Map<?, ?> c && c.get("message") instanceof Map<?, ?> m
                    && m.get("content") != null) {
                return String.valueOf(m.get("content")).trim();
            }
            return null;
        } catch (Exception e) {
            log.warn("IA indisponível: {}", e.getMessage());
            return null;
        }
    }

    /** Índice 1-based da opção que o cliente quis, ou 0 se nenhuma / IA off. */
    public int escolherOpcao(String mensagemCliente, List<String> opcoes) {
        if (opcoes.isEmpty() || !ativo()) return 0;
        StringBuilder lista = new StringBuilder();
        for (int i = 0; i < opcoes.size(); i++) {
            lista.append(i + 1).append(". ").append(opcoes.get(i)).append("\n");
        }
        String resp = chat(
                "Voce e um atendente de agendamento. A partir da mensagem do cliente, identifique qual opcao da lista ele quer. " +
                "Responda SOMENTE com o numero da opcao (1, 2, 3...). Se nenhuma corresponder, responda 0.",
                "Opcoes:\n" + lista + "\nMensagem do cliente: " + mensagemCliente);
        Integer n = primeiroNumero(resp);
        return (n != null && n >= 1 && n <= opcoes.size()) ? n : 0;
    }

    /** Interpreta uma data em linguagem natural, ou null. */
    public LocalDate interpretarData(String mensagemCliente, LocalDate hoje) {
        if (!ativo()) return null;
        String resp = chat(
                "Voce extrai a data que o cliente quer agendar. Hoje e " + hoje + " (formato AAAA-MM-DD). " +
                "Responda SOMENTE com a data no formato AAAA-MM-DD. Se nao houver data clara, responda NENHUMA.",
                "Mensagem do cliente: " + mensagemCliente);
        if (resp == null) return null;
        Matcher m = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})").matcher(resp);
        if (m.find()) {
            try { return LocalDate.parse(m.group()); } catch (Exception e) { return null; }
        }
        return null;
    }

    private Integer primeiroNumero(String s) {
        if (s == null) return null;
        Matcher m = Pattern.compile("\\d+").matcher(s);
        return m.find() ? Integer.valueOf(m.group()) : null;
    }
}
