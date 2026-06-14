package com.agendamento.backend.security;

import java.util.UUID;

/**
 * ThreadLocal que armazena o tenant_id do request autenticado atual.
 * Populado pelo JwtAuthFilter e limpo ao final do request.
 */
public class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static UUID get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
