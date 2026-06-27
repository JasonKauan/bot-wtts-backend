package com.agendamento.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class  BackendApplication {

    public static void main(String[] args) {
        // O servidor de produção (Render) roda em UTC. O app opera no fuso do Brasil,
        // então fixamos o default aqui (afeta LocalDateTime.now(), horários, lembretes).
        // TODO: por-tenant no futuro, se houver clientes em outros fusos.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        SpringApplication.run(BackendApplication.class, args);
    }
}
