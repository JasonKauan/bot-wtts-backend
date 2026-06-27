package com.agendamento.backend.dto.admin;

import com.agendamento.backend.entity.Plano;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Resumo de um tenant para a lista de clientes do painel admin (Fase 1).
 * NÃO inclui status do WhatsApp — esse é buscado sob demanda por linha
 * (1 chamada à Evolution), em /api/admin/clientes/{id}/whatsapp.
 */
public record ClienteResumoDto(
        UUID id,
        String nome,
        String telefoneWhatsapp,
        String emailDono,
        Plano plano,
        boolean ativo,
        boolean vencido,
        LocalDateTime trialExpiraEm,
        LocalDateTime assinaturaExpiraEm,
        LocalDateTime criadoEm
) {}
