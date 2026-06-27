package com.agendamento.backend.service;

import com.agendamento.backend.dto.admin.AdminLoginRequest;
import com.agendamento.backend.dto.auth.AuthResponse;
import com.agendamento.backend.entity.Usuario;
import com.agendamento.backend.repository.UsuarioRepository;
import com.agendamento.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Autenticação do back-office (SUPERADMIN). Login próprio, separado do painel do dono. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuthService {

    public static final String ROLE_SUPERADMIN = "SUPERADMIN";

    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthResponse login(AdminLoginRequest req) {
        // Mensagem genérica em todos os casos (não revela se é admin ou senha errada).
        Usuario usuario = usuarioRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas."));

        if (!passwordEncoder.matches(req.getSenha(), usuario.getSenha())
                || !ROLE_SUPERADMIN.equals(usuario.getRole())
                || !usuario.isAtivo()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas.");
        }

        String token = jwtService.generateAdminToken(usuario.getId(), usuario.getRole());
        log.info("Login admin: {} ({})", usuario.getEmail(), usuario.getId());
        return new AuthResponse(token, null);
    }
}
