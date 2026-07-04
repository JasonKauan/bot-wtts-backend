package com.agendamento.backend.service;

import com.agendamento.backend.dto.admin.CriarClienteRequest;
import com.agendamento.backend.dto.admin.PlanoRequest;
import com.agendamento.backend.dto.admin.ResetSenhaRequest;
import com.agendamento.backend.dto.admin.SenhaResponse;
import com.agendamento.backend.entity.Plano;
import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.entity.Usuario;
import com.agendamento.backend.repository.TenantRepository;
import com.agendamento.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Operações de back-office sobre clientes (tenants) — Fase 2.
 * Tudo restrito a SUPERADMIN (SecurityConfig). Dá ao vendedor poder total de negociação:
 * criar cliente, ativar/estender plano (3 modos), resetar senha do dono e suspender/reativar.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminClienteService {

    // Sem 0/O/1/l/I para não confundir o dono ao receber a senha por mensagem.
    private static final String SENHA_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RND = new SecureRandom();

    private final TenantRepository tenantRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final VendaService vendaService;

    /** Onboard de cliente. vendedorId = carteira (nulo = "da casa"). */
    @Transactional
    public SenhaResponse criar(CriarClienteRequest req, UUID vendedorId) {
        boolean gerada = isBlank(req.getSenha());
        String senha = gerada ? gerarSenha() : req.getSenha();
        int trialDias = req.getTrialDias() != null ? req.getTrialDias() : AuthService.TRIAL_DIAS_PADRAO;

        Tenant tenant = authService.criarTenantComDono(
                req.getNome(), req.getTelefone(), req.getEmail(), senha, trialDias);
        tenant.setVendedorId(vendedorId);   // carimbo da carteira

        // Opcional: já ativa um plano pago no onboard.
        String detalhe = "trial " + trialDias + "d";
        if (req.getPlano() != null) {
            aplicarPlano(tenant, req.getPlano());
            detalhe += " + " + req.getPlano().getPlano();
        }
        tenantRepository.save(tenant);
        if (req.getPlano() != null) {
            vendaService.registrar(tenant, req.getPlano().getPlano(), "MANUAL");
        }
        auditoriaService.registrar("CRIAR_CLIENTE", tenant.getId(), tenant.getNome(), detalhe);
        return new SenhaResponse(tenant.getId(), gerada ? senha : null);
    }

    /** Ativa ou estende o plano com a validade escolhida pelo vendedor. */
    @Transactional
    public void alterarPlano(UUID id, PlanoRequest req) {
        Tenant t = buscar(id);
        aplicarPlano(t, req);
        tenantRepository.save(t);
        vendaService.registrar(t, req.getPlano(), "MANUAL");   // TRIAL é ignorado lá dentro
        log.info("[admin] plano do tenant {} -> {} (modo {})", id, req.getPlano(), req.getModo());
        auditoriaService.registrar("ALTERAR_PLANO", id, t.getNome(), descricaoPlano(req));
    }

    /** Reset de senha do dono. Devolve a senha provisória só se foi gerada. */
    @Transactional
    public SenhaResponse resetarSenha(UUID id, ResetSenhaRequest req) {
        Tenant t = buscar(id); // valida que o cliente existe
        Usuario dono = usuarioRepository.findFirstByTenantId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dono não encontrado."));

        boolean gerada = req == null || isBlank(req.getSenha());
        String senha = gerada ? gerarSenha() : req.getSenha();
        dono.setSenha(passwordEncoder.encode(senha));
        usuarioRepository.save(dono);
        log.info("[admin] senha do dono do tenant {} resetada", id);
        auditoriaService.registrar("RESETAR_SENHA", id, t.getNome(), gerada ? "gerada" : "definida");
        return new SenhaResponse(id, gerada ? senha : null);
    }

    /** Suspende (ativo=false → painel bloqueado e bot mudo) ou reativa o cliente. */
    @Transactional
    public void definirAtivo(UUID id, boolean ativo) {
        Tenant t = buscar(id);
        t.setAtivo(ativo);
        tenantRepository.save(t);
        log.info("[admin] tenant {} {}", id, ativo ? "reativado" : "suspenso");
        auditoriaService.registrar(ativo ? "REATIVAR" : "SUSPENDER", id, t.getNome(), null);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void aplicarPlano(Tenant t, PlanoRequest req) {
        LocalDateTime agora = LocalDateTime.now();
        boolean trial = req.getPlano() == Plano.TRIAL;
        LocalDateTime atual = trial ? t.getTrialExpiraEm() : t.getAssinaturaExpiraEm();
        // Estender soma ao que ainda resta (se futuro); senão começa de agora.
        LocalDateTime ref = (atual != null && atual.isAfter(agora)) ? atual : agora;

        LocalDateTime nova;
        switch (req.getModo() == null ? "" : req.getModo()) {
            case "meses" -> nova = ref.plusMonths(req.getMeses() != null ? req.getMeses() : 1);
            case "dias"  -> nova = ref.plusDays(req.getDias() != null ? req.getDias() : 30);
            case "data"  -> {
                if (req.getData() == null)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a data de expiração.");
                nova = req.getData().atTime(23, 59);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Modo inválido. Use 'meses', 'dias' ou 'data'.");
        }

        t.setPlano(req.getPlano());
        if (trial) t.setTrialExpiraEm(nova);
        else       t.setAssinaturaExpiraEm(nova);
    }

    private String descricaoPlano(PlanoRequest req) {
        String valor = switch (req.getModo() == null ? "" : req.getModo()) {
            case "meses" -> "meses=" + (req.getMeses() != null ? req.getMeses() : 1);
            case "dias"  -> "dias=" + (req.getDias() != null ? req.getDias() : 30);
            case "data"  -> "data=" + req.getData();
            default -> "";
        };
        return "plano=" + req.getPlano() + " modo=" + req.getModo() + " " + valor;
    }

    private Tenant buscar(UUID id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente não encontrado."));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String gerarSenha() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) sb.append(SENHA_CHARS.charAt(RND.nextInt(SENHA_CHARS.length())));
        return sb.toString();
    }
}
