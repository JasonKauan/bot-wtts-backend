package com.agendamento.backend.controller;

import com.agendamento.backend.dto.admin.AdminLoginRequest;
import com.agendamento.backend.dto.admin.AuditoriaDto;
import com.agendamento.backend.dto.admin.ClienteResumoDto;
import com.agendamento.backend.dto.admin.CriarClienteRequest;
import com.agendamento.backend.dto.admin.PlanoRequest;
import com.agendamento.backend.dto.admin.ResetSenhaRequest;
import com.agendamento.backend.dto.admin.SenhaResponse;
import com.agendamento.backend.dto.auth.AuthResponse;
import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.repository.TenantRepository;
import com.agendamento.backend.repository.UsuarioRepository;
import com.agendamento.backend.service.AdminAuthService;
import com.agendamento.backend.service.AdminClienteService;
import com.agendamento.backend.service.AuditoriaService;
import com.agendamento.backend.service.EvolutionApiService;
import com.agendamento.backend.service.LembreteService;
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
    private final AdminClienteService adminClienteService;
    private final AuditoriaService auditoriaService;
    private final LembreteService lembreteService;
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

    /** Onboard de cliente. Devolve a senha provisória quando gerada (pro vendedor repassar). */
    @PostMapping("/clientes")
    public SenhaResponse criarCliente(@Valid @RequestBody CriarClienteRequest req) {
        return adminClienteService.criar(req);
    }

    /** Ativar/estender plano (modo meses|dias|data). */
    @PostMapping("/clientes/{id}/plano")
    public void alterarPlano(@PathVariable UUID id, @Valid @RequestBody PlanoRequest req) {
        adminClienteService.alterarPlano(id, req);
    }

    /** Resetar a senha do dono. Body opcional: se vazio, gera e devolve. */
    @PostMapping("/clientes/{id}/senha")
    public SenhaResponse resetarSenha(@PathVariable UUID id,
                                      @RequestBody(required = false) ResetSenhaRequest req) {
        return adminClienteService.resetarSenha(id, req);
    }

    /** Suspende o cliente (painel bloqueado + bot mudo). */
    @PostMapping("/clientes/{id}/suspender")
    public void suspender(@PathVariable UUID id) {
        adminClienteService.definirAtivo(id, false);
    }

    /** Reativa o cliente. */
    @PostMapping("/clientes/{id}/reativar")
    public void reativar(@PathVariable UUID id) {
        adminClienteService.definirAtivo(id, true);
    }

    /** Histórico de ações do back-office (mais recentes primeiro). */
    @GetMapping("/auditoria")
    public List<AuditoriaDto> auditoria() {
        return auditoriaService.listar();
    }

    /** Dispara o job de lembretes na hora (teste): manda os lembretes da janela 23–25h agora. */
    @PostMapping("/lembretes/disparar")
    public Map<String, Object> dispararLembretes() {
        int enviados = lembreteService.enviarLembretes();
        return Map.of("enviados", enviados);
    }

    private ClienteResumoDto toResumo(Tenant t) {
        String emailDono = usuarioRepository.findFirstByTenantId(t.getId())
                .map(u -> u.getEmail()).orElse(null);
        return new ClienteResumoDto(
                t.getId(), t.getNome(), t.getTelefoneWhatsapp(), emailDono,
                t.getPlano(), t.isAtivo(), t.isAssinaturaVencida(),
                t.getTrialExpiraEm(), t.getAssinaturaExpiraEm(), t.getCriadoEm());
    }

    private String clientIp(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : http.getRemoteAddr();
    }
}
