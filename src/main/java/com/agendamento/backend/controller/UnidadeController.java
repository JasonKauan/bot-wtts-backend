package com.agendamento.backend.controller;

import com.agendamento.backend.entity.Plano;
import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.entity.UnidadeVinculo;
import com.agendamento.backend.entity.Usuario;
import com.agendamento.backend.repository.TenantRepository;
import com.agendamento.backend.repository.UnidadeVinculoRepository;
import com.agendamento.backend.repository.UsuarioRepository;
import com.agendamento.backend.security.JwtService;
import com.agendamento.backend.security.TenantContext;
import com.agendamento.backend.service.AuthService;
import com.agendamento.backend.service.PlanoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Multi-unidade (V34, Diamond): o dono cria/acessa 2+ estabelecimentos com o mesmo login.
 * Cada unidade é um tenant completo (WhatsApp, agenda e ASSINATURA próprios — a nova nasce
 * em trial). Trocar de unidade = reemitir o JWT apontando pro outro tenant.
 */
@RestController
@RequestMapping("/api/unidades")
@RequiredArgsConstructor
public class UnidadeController {

    private final UsuarioRepository usuarioRepository;
    private final TenantRepository tenantRepository;
    private final UnidadeVinculoRepository vinculoRepository;
    private final AuthService authService;
    private final PlanoService planoService;
    private final JwtService jwtService;

    public record UnidadeDto(UUID tenantId, String nome, String plano, boolean atual) {}
    public record CriarUnidadeRequest(@NotBlank String nome, String telefoneWhatsapp) {}
    public record TrocarUnidadeRequest(@NotNull UUID tenantId) {}

    /** Unidades do usuário logado: a "casa" + as vinculadas. */
    @GetMapping
    public List<UnidadeDto> listar(Authentication auth) {
        Usuario u = donoLogado(auth);
        UUID atual = TenantContext.get();

        List<UUID> ids = new ArrayList<>();
        ids.add(u.getTenantId());
        vinculoRepository.findByUsuarioId(u.getId()).forEach(v -> ids.add(v.getTenantId()));

        return ids.stream().distinct()
                .map(id -> tenantRepository.findById(id).orElse(null))
                .filter(t -> t != null && t.isAtivo())
                .map(t -> new UnidadeDto(t.getId(), t.getNome(), t.getPlano().getNomeBonito(),
                        t.getId().equals(atual)))
                .toList();
    }

    /** Cria a 2ª (3ª...) unidade — recurso Diamond da unidade ATUAL. A nova nasce em trial. */
    @PostMapping
    @Transactional
    public UnidadeDto criar(Authentication auth, @Valid @RequestBody CriarUnidadeRequest req) {
        Usuario u = donoLogado(auth);
        planoService.exigir(TenantContext.get(), Plano.Recurso.MULTI_UNIDADE);

        // Teto anti-abuso: cada unidade abre uma instância Evolution (recurso compartilhado).
        if (vinculoRepository.countByUsuarioId(u.getId()) >= 10) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Você atingiu o limite de unidades. Fale com o suporte se precisar de mais.");
        }

        Tenant nova = authService.criarUnidade(req.nome().trim(), req.telefoneWhatsapp());
        vinculoRepository.save(UnidadeVinculo.builder()
                .usuarioId(u.getId()).tenantId(nova.getId()).build());
        return new UnidadeDto(nova.getId(), nova.getNome(), nova.getPlano().getNomeBonito(), false);
    }

    /** Troca a unidade ativa: valida o acesso e reemite o token pro outro tenant. */
    @PostMapping("/trocar")
    public Map<String, String> trocar(Authentication auth, @Valid @RequestBody TrocarUnidadeRequest req) {
        Usuario u = donoLogado(auth);
        boolean temAcesso = req.tenantId().equals(u.getTenantId())
                || vinculoRepository.existsByUsuarioIdAndTenantId(u.getId(), req.tenantId());
        if (!temAcesso) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não tem acesso a essa unidade.");
        }
        Tenant destino = tenantRepository.findById(req.tenantId())
                .filter(Tenant::isAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unidade não encontrada."));

        String token = jwtService.generateToken(u.getId(), destino.getId(), u.getRole());
        return Map.of("token", token, "nome", destino.getNome());
    }

    /** Só OWNER (com tenant) usa unidades — admin/vendedor não tem esse conceito. */
    private Usuario donoLogado(Authentication auth) {
        Usuario u = usuarioRepository.findById(UUID.fromString(auth.getName()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (u.getTenantId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Recurso exclusivo do painel do dono.");
        }
        return u;
    }
}
