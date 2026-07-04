package com.agendamento.backend.repository;

import com.agendamento.backend.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Iteração 6: e-mail do dono para constar como pagador no Mercado Pago. */
    Optional<Usuario> findFirstByTenantId(UUID tenantId);

    /** Gestão de vendedores (painel CEO). */
    java.util.List<Usuario> findByRoleOrderByCriadoEmDesc(String role);
}
