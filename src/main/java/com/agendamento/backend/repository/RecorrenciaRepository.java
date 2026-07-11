package com.agendamento.backend.repository;

import com.agendamento.backend.entity.Recorrencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RecorrenciaRepository extends JpaRepository<Recorrencia, UUID> {

    List<Recorrencia> findByTenantIdOrderByClienteNome(UUID tenantId);

    /** Job diário: recorrências ativas cuja próxima ocorrência está na janela de geração. */
    List<Recorrencia> findByAtivoTrueAndProximaDataLessThanEqual(LocalDate limite);

    /** Excluir profissional: barra se algum cliente fixo ativo depende dele. */
    boolean existsByTenantIdAndProfissionalIdAndAtivoTrue(UUID tenantId, UUID profissionalId);

    /** Excluir serviço: barra se algum cliente fixo ativo usa esse serviço (guardado por nome). */
    boolean existsByTenantIdAndServicoAndAtivoTrue(UUID tenantId, String servico);
}
