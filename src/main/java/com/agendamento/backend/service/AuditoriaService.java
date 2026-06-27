package com.agendamento.backend.service;

import com.agendamento.backend.dto.admin.AuditoriaDto;
import com.agendamento.backend.entity.AdminAuditoria;
import com.agendamento.backend.entity.Usuario;
import com.agendamento.backend.repository.AdminAuditoriaRepository;
import com.agendamento.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Registra e lista as ações do back-office (SUPERADMIN). Fase 3. */
@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private final AdminAuditoriaRepository auditoriaRepository;
    private final UsuarioRepository usuarioRepository;

    /** Registra uma ação do admin atual (lido do SecurityContext). */
    @Transactional
    public void registrar(String acao, UUID tenantId, String tenantNome, String detalhe) {
        UUID adminId = null;
        String adminEmail = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            try {
                adminId = UUID.fromString(auth.getName());
                adminEmail = usuarioRepository.findById(adminId).map(Usuario::getEmail).orElse(null);
            } catch (IllegalArgumentException ignored) {
                // subject não-UUID: deixa nulo
            }
        }
        auditoriaRepository.save(AdminAuditoria.builder()
                .adminId(adminId)
                .adminEmail(adminEmail)
                .acao(acao)
                .tenantId(tenantId)
                .tenantNome(tenantNome)
                .detalhe(detalhe)
                .build());
    }

    public List<AuditoriaDto> listar() {
        return auditoriaRepository.findTop200ByOrderByCriadoEmDesc().stream()
                .map(a -> new AuditoriaDto(
                        a.getAdminEmail(), a.getAcao(), a.getTenantNome(), a.getDetalhe(), a.getCriadoEm()))
                .toList();
    }
}
