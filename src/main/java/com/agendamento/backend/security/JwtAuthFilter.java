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
                    // TenantContext e não passa pelo bloqueio 402 (Fase 1 do painel admin).
                    String tenantClaim = claims.get("tenant_id", String.class);
                    if (tenantClaim != null && !tenantClaim.isBlank()) {
                        UUID tenantId = UUID.fromString(tenantClaim);
                        TenantContext.set(tenantId);

                        // Iteração 6: trial/assinatura vencida bloqueia o painel com 402,
                        // exceto auth e a própria página de assinatura (senão não dá para renovar).
                        if (bloqueadoPorAssinatura(request, tenantId)) {
                            SecurityContextHolder.clearContext();
                            response.setStatus(402);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    "{\"message\":\"Assinatura vencida. Renove na página de assinatura para continuar.\"}");
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

    private boolean bloqueadoPorAssinatura(HttpServletRequest request, UUID tenantId) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth/") || path.startsWith("/api/assinatura")
                || path.startsWith("/api/admin")) return false;

        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null || !tenant.isAssinaturaVencida()) return false;

        log.info("[402] Tenant {} com assinatura vencida tentou acessar {}", tenantId, path);
        return true;
    }
}
