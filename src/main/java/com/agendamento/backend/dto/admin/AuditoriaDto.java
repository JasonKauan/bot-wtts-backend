package com.agendamento.backend.dto.admin;

import java.time.LocalDateTime;

/** Item do histórico de auditoria do admin (Fase 3). */
public record AuditoriaDto(
        String adminEmail,
        String acao,
        String tenantNome,
        String detalhe,
        LocalDateTime criadoEm
) {}
