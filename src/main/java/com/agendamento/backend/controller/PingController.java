package com.agendamento.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Keep-alive/health público: alvo de pingadores externos (UptimeRobot etc.) pra
 * segurar o Render free acordado. Não toca banco nem serviços — custo ~zero.
 */
@RestController
public class PingController {

    @GetMapping("/api/ping")
    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }
}
