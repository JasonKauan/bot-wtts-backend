package com.agendamento.backend.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    /**
     * Timeouts explícitos: sem eles a chamada trava indefinidamente quando a
     * Evolution/MP está dormindo (free tier) — o /qr ficava pendurado e o QR "nunca gerava".
     * Read de 60s ainda dá tempo do serviço acordar; quem chama já trata falha/retry.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
    }
}
