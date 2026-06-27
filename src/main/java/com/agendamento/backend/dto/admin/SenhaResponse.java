package com.agendamento.backend.dto.admin;

import java.util.UUID;

/**
 * Resposta de criação/reset de senha. `senhaProvisoria` só vem preenchida quando o
 * sistema GEROU a senha (pro vendedor repassar); null quando o vendedor a definiu.
 */
public record SenhaResponse(UUID id, String senhaProvisoria) {}
