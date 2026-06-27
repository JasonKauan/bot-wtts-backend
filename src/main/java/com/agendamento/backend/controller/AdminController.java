package com.agendamento.backend.controller;

import com.agendamento.backend.dto.admin.AdminLoginRequest;
import com.agendamento.backend.dto.admin.ClienteResumoDto;
import com.agendamento.backend.dto.auth.AuthResponse;
import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.repository.TenantRepository;
import com.agendamento.backend.repository.UsuarioRepository;
import com.agendamento.backend.service.AdminAuthService;
import com.agendamento.backend.service.EvolutionApiService;
import com.agendamento.backend.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Back-office do vendedor (SUPERADMIN). Fase 1: login + lista de clientes.
 * Segurança: /api/admin/login é público; o resto exige ROLE_SUPERADMIN (SecurityConfig).
 * Tokens de admin não têm tenant_id, então não passam pelo isolamento por tenant nem pelo 402.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminAuthService adminAuthService;
    private final TenantRepository tenantRepository;
    private final UsuarioRepository usuarioRepository;
    private final EvolutionApiService evolutionApiService;
    private final RateLimiterService rateLimiter;

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AdminLoginRequest req, HttpServletRequest http) {
        if (!rateLimiter.permitir("admin-login:" + clientIp(http))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas. Aguarde alguns minutos e tente novamente.");
        }
        return adminAuthService.login(req);
    }

    /** Lista todos os clientes (tenants), mais recentes primeiro. Sem status do WhatsApp (rápido). */
    @GetMapping("/clientes")
    public List<ClienteResumoDto> clientes() {
        return tenantRepository.findAllByOrderByCriadoEmDesc().stream()
                .map(this::toResumo)
                .toList();
    }

    /** Status do WhatsApp de um cliente — sob demanda (1 chamada à Evolution). */
    @GetMapping("/clientes/{id}/whatsapp")
    public Map<String, Object> whatsapp(@PathVariable UUID id) {
        String estado = evolutionApiService.estadoConexao(id.toString());
        return Map.of("estado", estado, "conectado", "open".equalsIgnoreCase(estado));
    }

    private ClienteResumoDto toResumo(Tenant t) {
        String emailDono = usuarioRepository.findFirstByTenantId(t.getId())
                .map(u -> u.getEmail()).orElse(null);
        return new ClienteResumoDto(
                t.getId(), t.getNome(), t.getTelefoneWhatsapp(), emailDono,
                t.getPlano(), t.isAssinaturaVencida(),
                t.getTrialExpiraEm(), t.getAssinaturaExpiraEm(), t.getCriadoEm());
    }

    private String clientIp(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : http.getRemoteAddr();
    }
}
