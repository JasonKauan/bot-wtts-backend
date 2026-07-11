package com.agendamento.backend.repository;

import com.agendamento.backend.entity.Aniversario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AniversarioRepository extends JpaRepository<Aniversario, UUID> {

    List<Aniversario> findByTenantId(UUID tenantId);

    Optional<Aniversario> findByTenantIdAndTelefone(UUID tenantId, String telefone);

    /** Job diário: todos os aniversariantes de hoje (o job filtra tenant/plano/ano). */
    List<Aniversario> findByDiaAndMes(int dia, int mes);
}
