package com.agendamento.backend.service;

import com.agendamento.backend.dto.auth.AuthResponse;
import com.agendamento.backend.dto.auth.LoginRequest;
import com.agendamento.backend.dto.auth.RegisterRequest;
import com.agendamento.backend.entity.Plano;
import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.entity.Usuario;
import com.agendamento.backend.repository.TenantRepository;
import com.agendamento.backend.repository.UsuarioRepository;
import com.agendamento.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    /** Duração padrão do trial no cadastro (self-service e onboard do admin). */
    public static final int TRIAL_DIAS_PADRAO = 14;

    private final TenantRepository tenantRepository;
    private final UsuarioRepository usuarioRepository;
    private final EvolutionApiService evolutionApiService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        Tenant tenant = criarTenantComDono(
                req.getNomeEstabelecimento(), req.getTelefoneWhatsapp(),
                req.getEmail(), req.getSenha(), TRIAL_DIAS_PADRAO);
        Usuario dono = usuarioRepository.findFirstByTenantId(tenant.getId()).orElseThrow();
        String token = jwtService.generateToken(dono.getId(), tenant.getId(), dono.getRole());
        return new AuthResponse(token, tenant.getId().toString());
    }

    /**
     * Núcleo de criação de cliente — Tenant (TRIAL) + Usuário OWNER + instância Evolution + webhook.
     * Reusado pelo cadastro self-service e pelo onboard do admin (Fase 2). Não devolve token.
     */
    @Transactional
    public Tenant criarTenantComDono(String nomeEstabelecimento, String telefoneWhatsapp,
                                     String email, String senhaPlana, int trialDias) {
        if (usuarioRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado.");
        }

        Tenant tenant = Tenant.builder()
                .nome(nomeEstabelecimento)
                .telefoneWhatsapp(telefoneWhatsapp)
                .webhookSecret(UUID.randomUUID().toString().replace("-", ""))
                .ativo(true)
                .plano(Plano.TRIAL)
                .trialExpiraEm(LocalDateTime.now().plusDays(trialDias))
                .build();
        tenantRepository.save(tenant);

        Usuario usuario = Usuario.builder()
                .tenantId(tenant.getId())
                .email(email)
                .senha(passwordEncoder.encode(senhaPlana))
                .role("OWNER")
                .ativo(true)
                .build();
        usuarioRepository.save(usuario);

        // Instância na Evolution API (instance name = tenant UUID)
        String instanceName = tenant.getId().toString();
        try {
            evolutionApiService.criarInstancia(instanceName);
            evolutionApiService.configurarWebhook(instanceName, tenant.getWebhookSecret());
        } catch (Exception e) {
            log.error("Erro ao criar instância na Evolution API para tenant {}: {}", tenant.getId(), e.getMessage());
            // Não falha o cadastro — owner pode conectar manualmente depois
        }

        log.info("Tenant cadastrado: {} ({})", tenant.getNome(), tenant.getId());
        return tenant;
    }

    public AuthResponse login(LoginRequest req) {
        Usuario usuario = usuarioRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas."));

        if (!passwordEncoder.matches(req.getSenha(), usuario.getSenha())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas.");
        }

        if (!usuario.isAtivo()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário inativo.");
        }

        // SUPERADMIN não tem tenant e não loga pelo painel do dono — deve usar /api/admin/login.
        // (Sem isto, generateToken faria NPE em tenantId.toString() → 500.)
        if (usuario.getTenantId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas.");
        }

        String token = jwtService.generateToken(usuario.getId(), usuario.getTenantId(), usuario.getRole());
        return new AuthResponse(token, null);
    }

    public AuthResponse refresh(UUID userId, UUID tenantId, String role) {
        String token = jwtService.generateToken(userId, tenantId, role);
        return new AuthResponse(token, null);
    }
}
