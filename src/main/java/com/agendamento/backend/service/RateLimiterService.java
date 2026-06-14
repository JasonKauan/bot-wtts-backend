package com.agendamento.backend.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter simples em memória (janela fixa) para frear brute force / enumeração
 * nos endpoints de auth. É por instância — suficiente para deploy single-node.
 * Para escala horizontal, trocar por um backend compartilhado (ex.: Redis).
 */
@Service
public class RateLimiterService {

    private record Janela(int contador, long resetEm) {}

    private static final int  MAX_TENTATIVAS = 10;
    private static final long JANELA_MS      = 15 * 60 * 1000L; // 15 min

    private final Map<String, Janela> mapa = new ConcurrentHashMap<>();

    /** true se a tentativa é permitida; false se estourou o limite na janela atual. */
    public boolean permitir(String chave) {
        long agora = System.currentTimeMillis();
        Janela atual = mapa.compute(chave, (k, j) ->
                (j == null || agora >= j.resetEm())
                        ? new Janela(1, agora + JANELA_MS)
                        : new Janela(j.contador() + 1, j.resetEm()));
        return atual.contador() <= MAX_TENTATIVAS;
    }
}
