package com.agendamento.backend.controller;

import com.agendamento.backend.dto.auth.AuthResponse;
import com.agendamento.backend.dto.auth.LoginRequest;
import com.agendamento.backend.dto.auth.RegisterRequest;
import com.agendamento.backend.security.JwtService;
import com.agendamento.backend.security.TenantContext;
import com.agendamento.backend.service.AuthService;
import com.agendamento.backend.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService  jwtService;
    private final RateLimiterService rateLimiter;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        rateLimit("register", http);
        return authService.register(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        rateLimit("login", http);
        return authService.login(req);
    }

    /** Freia brute force / enumeração por IP (10 tentativas / 15 min). */
    private void rateLimit(String acao, HttpServletRequest http) {
        if (!rateLimiter.permitir(acao + ":" + clientIp(http))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas. Aguarde alguns minutos e tente novamente.");
        }
    }

    private String clientIp(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : http.getRemoteAddr();
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(Authentication auth) {
        UUID tenantId = TenantContext.get();
        UUID userId   = UUID.fromString(auth.getName());
        String role   = auth.getAuthorities().iterator().next()
                            .getAuthority().replace("ROLE_", "");
        return authService.refresh(userId, tenantId, role);
    }
}
