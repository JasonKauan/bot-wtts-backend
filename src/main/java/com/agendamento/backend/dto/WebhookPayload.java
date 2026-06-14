package com.agendamento.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Payload de webhook da Evolution API v2.
 * Documentação: https://doc.evolution-api.com
 *
 * Iteração 3: validar X-Webhook-Secret por tenant antes de processar.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookPayload {

    private String event;
    private String instance;
    private MessageData data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageData {
        private MessageKey key;
        private String pushName;
        private MessageContent message;
        private String messageType;
        private Long messageTimestamp;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageKey {
        private String remoteJid;
        private Boolean fromMe;
        private String id;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageContent {
        /** Mensagem de texto simples */
        private String conversation;

        /** Mensagem de texto estendida (links, formatação) */
        @JsonProperty("extendedTextMessage")
        private ExtendedTextMessage extendedTextMessage;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtendedTextMessage {
        private String text;
    }

    // ---- Helpers ----

    /** Extrai o texto da mensagem (conversation ou extendedTextMessage). */
    public String extractText() {
        if (data == null || data.getMessage() == null) return "";
        if (data.getMessage().getConversation() != null) {
            return data.getMessage().getConversation().trim();
        }
        if (data.getMessage().getExtendedTextMessage() != null
                && data.getMessage().getExtendedTextMessage().getText() != null) {
            return data.getMessage().getExtendedTextMessage().getText().trim();
        }
        return "";
    }

    /** Extrai o telefone no formato puro (sem @s.whatsapp.net). */
    public String extractPhone() {
        if (data == null || data.getKey() == null || data.getKey().getRemoteJid() == null) return "";
        return data.getKey().getRemoteJid()
                .replace("@s.whatsapp.net", "")
                .replace("@c.us", "");
    }

    /** Mensagem enviada pelo próprio bot (ignorar para não criar loop). */
    public boolean isFromMe() {
        return data != null
                && data.getKey() != null
                && Boolean.TRUE.equals(data.getKey().getFromMe());
    }

    /** Evento de mensagem recebida. */
    public boolean isMensagemRecebida() {
        return "messages.upsert".equalsIgnoreCase(event)
                || "MESSAGES_UPSERT".equalsIgnoreCase(event);
    }
}
