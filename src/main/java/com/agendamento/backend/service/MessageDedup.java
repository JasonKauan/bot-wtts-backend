package com.agendamento.backend.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dedup simples em memória de IDs de mensagem do WhatsApp. A Evolution às vezes
 * reenvia o mesmo webhook (retry por timeout, ou duplicata) — sem isso o bot
 * responde a mesma mensagem várias vezes (spam).
 */
@Component
public class MessageDedup {

    private static final long TTL_MS = 5 * 60 * 1000L; // 5 min
    private static final int  MAX    = 5000;

    private final Map<String, Long> vistos = new ConcurrentHashMap<>();

    /** true se essa mensagem já foi processada recentemente (e registra a atual). */
    public boolean jaProcessada(String id) {
        if (id == null || id.isBlank()) return false;
        long agora = System.currentTimeMillis();
        if (vistos.size() > MAX) {
            vistos.entrySet().removeIf(e -> agora - e.getValue() > TTL_MS);
        }
        Long anterior = vistos.put(id, agora);
        return anterior != null && (agora - anterior) < TTL_MS;
    }
}
