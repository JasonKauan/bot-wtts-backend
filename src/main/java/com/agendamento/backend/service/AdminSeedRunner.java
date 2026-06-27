package com.agendamento.backend.service;

import com.agendamento.backend.entity.Usuario;
import com.agendamento.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Cria o 1º SUPERADMIN no boot a partir das vars de ambiente SUPERADMIN_EMAIL /
 * SUPERADMIN_PASSWORD (a senha NUNCA entra no git — fica só no painel do Render).
 * Idempotente: não faz nada se as vars estiverem vazias ou se o e-mail já existir.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeedRunner implements ApplicationRunner {

    @Value("${app.superadmin.email:}")
    private String email;

    @Value("${app.superadmin.password:}")
    private String password;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            log.info("[seed admin] SUPERADMIN_EMAIL/PASSWORD não setados — pulando seed.");
            return;
        }
        if (usuarioRepository.existsByEmail(email)) {
            log.info("[seed admin] já existe usuário com e-mail {} — pulando seed.", email);
            return;
        }

        Usuario admin = Usuario.builder()
                .tenantId(null)
                .email(email)
                .senha(passwordEncoder.encode(password))
                .role(AdminAuthService.ROLE_SUPERADMIN)
                .ativo(true)
                .build();
        usuarioRepository.save(admin);
        log.info("[seed admin] SUPERADMIN criado: {}", email);
    }
}
