package com.agendamento.backend.controller;

import com.agendamento.backend.dto.admin.AdminLoginRequest;
import com.agendamento.backend.dto.admin.AuditoriaDto;
import com.agendamento.backend.dto.admin.ClienteResumoDto;
import com.agendamento.backend.dto.admin.CriarClienteRequest;
import com.agendamento.backend.dto.admin.MinhasVendasDto;
import com.agendamento.backend.dto.admin.PlanoRequest;
import com.agendamento.backend.dto.admin.ResetSenhaRequest;
import com.agendamento.backend.dto.admin.SenhaResponse;
import com.agendamento.backend.dto.admin.VendaLinhaDto;
import com.agendamento.backend.dto.auth.AuthResponse;
import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.entity.Usuario;
import com.agendamento.backend.entity.Venda;
import com.agendamento.backend.repository.TenantRepository;
import com.agendamento.backend.repository.UsuarioRepository;
import com.agendamento.backend.repository.VendaRepository;
import com.agendamento.backend.service.AdminAuthService;
import com.agendamento.backend.service.AdminClienteService;
import com.agendamento.backend.service.AuditoriaService;
import com.agendamento.backend.service.EvolutionApiService;
import com.agendamento.backend.service.LembreteService;
import com.agendamento.backend.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
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
    private final VendaRepository vendaRepository;
    private final EvolutionApiService evolutionApiService;
    private final RateLimiterService rateLimiter;

    // ── Papel/escopo ─────────────────────────────────────────────────────────

    private UUID adminId(Authentication auth) { return UUID.fromString(auth.getName()); }

    private boolean isCeo(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_SUPERADMIN".equals(a.getAuthority()));
    }

    /** VENDEDOR só mexe em cliente da própria carteira; CEO em todos. */
    private Tenant exigirAcesso(UUID tenantId, Authentication auth) {
        Tenant t = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente não encontrado."));
        if (!isCeo(auth) && !adminId(auth).equals(t.getVendedorId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esse cliente não está na sua carteira.");
        }
        return t;
    }

    /** Quem sou eu (o frontend usa pra montar a tela por papel). */
    @GetMapping("/me")
    public Map<String, Object> me(Authentication auth) {
        Usuario u = usuarioRepository.findById(adminId(auth))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Map<String, Object> out = new HashMap<>();
        out.put("nome", u.getNome());
        out.put("email", u.getEmail());
        out.put("role", u.getRole());
        out.put("comissaoPct", u.getComissaoPct());
        return out;
    }

    /** Vendas e comissões do vendedor logado: mês corrente + o que ainda tem a receber. */
    @GetMapping("/minhas-vendas")
    public MinhasVendasDto minhasVendas(Authentication auth) {
        var inicioMes = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        List<Venda> vendas = vendaRepository
                .findByVendedorIdAndCriadoEmGreaterThanEqualOrderByCriadoEmDesc(adminId(auth), inicioMes);
        BigDecimal comissao = vendas.stream().map(Venda::getComissaoValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pendente = vendaRepository.findByVendedorIdAndPagoFalse(adminId(auth)).stream()
                .map(Venda::comissaoDevida)   // desconta acertos parciais (V19)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<VendaLinhaDto> linhas = vendas.stream()
                .map(v -> new VendaLinhaDto(v.getTenantNome(), null, v.getPlano().name(),
                        v.getValor(), v.getComissaoValor(), v.getOrigem(), v.isPago(), v.getCriadoEm()))
                .toList();
        return new MinhasVendasDto(vendas.size(), comissao, pendente, linhas);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AdminLoginRequest req, HttpServletRequest http) {
        if (!rateLimiter.permitir("admin-login:" + clientIp(http))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas. Aguarde alguns minutos e tente novamente.");
        }
        return adminAuthService.login(req);
    }

    /** Lista os clientes: CEO vê todos; vendedor vê só a própria carteira. */
    @GetMapping("/clientes")
    public List<ClienteResumoDto> clientes(Authentication auth) {
        boolean ceo = isCeo(auth);
        UUID meuId = adminId(auth);
        return tenantRepository.findAllByOrderByCriadoEmDesc().stream()
                .filter(t -> ceo || meuId.equals(t.getVendedorId()))
                .map(this::toResumo)
                .toList();
    }

    /** Status do WhatsApp de um cliente — sob demanda (1 chamada à Evolution). */
    @GetMapping("/clientes/{id}/whatsapp")
    public Map<String, Object> whatsapp(@PathVariable UUID id, Authentication auth) {
        exigirAcesso(id, auth);
        String estado = evolutionApiService.estadoConexao(id.toString());
        return Map.of("estado", estado, "conectado", "open".equalsIgnoreCase(estado));
    }

    /** Onboard de cliente. Vendedor cria já carimbando a própria carteira. */
    @PostMapping("/clientes")
    public SenhaResponse criarCliente(@Valid @RequestBody CriarClienteRequest req, Authentication auth) {
        UUID vendedorId = isCeo(auth) ? null : adminId(auth);   // CEO cria "da casa"
        return adminClienteService.criar(req, vendedorId);
    }

    /** Ativar/estender plano (modo meses|dias|data). */
    @PostMapping("/clientes/{id}/plano")
    public void alterarPlano(@PathVariable UUID id, @Valid @RequestBody PlanoRequest req, Authentication auth) {
        exigirAcesso(id, auth);
        adminClienteService.alterarPlano(id, req);
    }

    /** Resetar a senha do dono. Body opcional: se vazio, gera e devolve. */
    @PostMapping("/clientes/{id}/senha")
    public SenhaResponse resetarSenha(@PathVariable UUID id,
                                      @Valid @RequestBody(required = false) ResetSenhaRequest req,
                                      Authentication auth) {
        exigirAcesso(id, auth);
        return adminClienteService.resetarSenha(id, req);
    }

    /** Suspende o cliente (painel bloqueado + bot mudo). */
    @PostMapping("/clientes/{id}/suspender")
    public void suspender(@PathVariable UUID id, Authentication auth) {
        exigirAcesso(id, auth);
        adminClienteService.definirAtivo(id, false);
    }

    /** Reativa o cliente. */
    @PostMapping("/clientes/{id}/reativar")
    public void reativar(@PathVariable UUID id, Authentication auth) {
        exigirAcesso(id, auth);
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
        int enviados = lembreteService.enviarLembretes() + lembreteService.enviarLembretesDoDia();
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
        // Última posição do X-Forwarded-For = adicionada pelo proxy confiável (Render).
        String xff = http.getHeader("X-Forwarded-For");
        if (xff == null || xff.isBlank()) return http.getRemoteAddr();
        String[] partes = xff.split(",");
        return partes[partes.length - 1].trim();
    }
}
