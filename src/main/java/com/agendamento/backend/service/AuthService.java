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

    private final TenantRepository tenantRepository;
    private final UsuarioRepository usuarioRepository;
    private final EvolutionApiService evolutionApiService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (usuarioRepository.existsByEmail(req.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado.");
        }

        // 1. Criar Tenant — começa no TRIAL de 30 dias (Iteração 6)
        Tenant tenant = Tenant.builder()
                .nome(req.getNomeEstabelecimento())
                .telefoneWhatsapp(req.getTelefoneWhatsapp())
                .webhookSecret(UUID.randomUUID().toString().replace("-", ""))
                .ativo(true)
                .plano(Plano.TRIAL)
                .trialExpiraEm(LocalDateTime.now().plusDays(30))
                .build();
        tenantRepository.save(tenant);

        // 2. Criar Usuário OWNER
        Usuario usuario = Usuario.builder()
                .tenantId(tenant.getId())
                .email(req.getEmail())
                .senha(passwordEncoder.encode(req.getSenha()))
                .role("OWNER")
                .ativo(true)
                .build();
        usuarioRepository.save(usuario);

        // 3. Criar instância na Evolution API (instance name = tenant UUID)
        String instanceName = tenant.getId().toString();
        try {
            evolutionApiService.criarInstancia(instanceName);
            evolutionApiService.configurarWebhook(instanceName, tenant.getWebhookSecret());
        } catch (Exception e) {
            log.error("Erro ao criar instância na Evolution API para tenant {}: {}", tenant.getId(), e.getMessage());
            // Não falha o cadastro — owner pode conectar manualmente depois
        }

        String token = jwtService.generateToken(usuario.getId(), tenant.getId(), usuario.getRole());
        log.info("Tenant cadastrado: {} ({})", tenant.getNome(), tenant.getId());
        return new AuthResponse(token, instanceName);
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

        String token = jwtService.generateToken(usuario.getId(), usuario.getTenantId(), usuario.getRole());
        return new AuthResponse(token, null);
    }

    public AuthResponse refresh(UUID userId, UUID tenantId, String role) {
        String token = jwtService.generateToken(userId, tenantId, role);
        return new AuthResponse(token, null);
    }
}
