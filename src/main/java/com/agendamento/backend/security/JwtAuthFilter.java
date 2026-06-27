package com.agendamento.backend.security;

import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.repository.TenantRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TenantRepository tenantRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            try {
                String header = request.getHeader("Authorization");
                if (header != null && header.startsWith("Bearer ")) {
                    String token = header.substring(7);
                    Claims claims = jwtService.extractClaims(token);
                    String role = claims.get("role", String.class);

                    var auth = new UsernamePasswordAuthenticationToken(
                            claims.getSubject(),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);

                    // SUPERADMIN não tem tenant: token sem claim tenant_id. Não seta o
                    // TenantContext e não passa pelos bloqueios do painel (Fase 1 do painel admin).
                    String tenantClaim = claims.get("tenant_id", String.class);
                    if (tenantClaim != null && !tenantClaim.isBlank()) {
                        UUID tenantId = UUID.fromString(tenantClaim);
                        TenantContext.set(tenantId);

                        // Suspensão (admin tirou o acesso → 403) e assinatura vencida (→ 402)
                        // bloqueiam o painel do dono. A resposta já é escrita aqui.
                        if (aplicarBloqueio(request, response, tenantId)) {
                            SecurityContextHolder.clearContext();
                            return;
                        }
                    }
                }
            } catch (JwtException | IllegalArgumentException e) {
                log.warn("JWT inválido: {}", e.getMessage());
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();   // sempre limpa o ThreadLocal, mesmo se o request lançar
        }
    }

    /** Bloqueia o painel do dono se a conta está suspensa (403) ou vencida (402). Escreve a resposta. */
    private boolean aplicarBloqueio(HttpServletRequest request, HttpServletResponse response, UUID tenantId)
            throws IOException {
        String path = request.getRequestURI();
        // /api/auth e /api/admin nunca são bloqueados por estado do tenant.
        if (path.startsWith("/api/auth/") || path.startsWith("/api/admin")) return false;

        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) return false;

        // Suspenso pelo admin: bloqueia TUDO (menos auth/admin) com 403 — "não pode usar mais nada".
        if (!tenant.isAtivo()) {
            log.info("[403] Tenant {} suspenso tentou acessar {}", tenantId, path);
            escreverBloqueio(response, 403, "Conta suspensa. Entre em contato com o suporte.");
            return true;
        }

        // Assinatura vencida: bloqueia, exceto a própria página de assinatura (senão não dá para renovar).
        if (!path.startsWith("/api/assinatura") && tenant.isAssinaturaVencida()) {
            log.info("[402] Tenant {} com assinatura vencida tentou acessar {}", tenantId, path);
            escreverBloqueio(response, 402, "Assinatura vencida. Renove na página de assinatura para continuar.");
            return true;
        }
        return false;
    }

    private void escreverBloqueio(HttpServletResponse response, int status, String mensagem) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + mensagem + "\"}");
    }
}
