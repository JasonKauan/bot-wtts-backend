package com.agendamento.backend.repository;

import com.agendamento.backend.entity.AcertoComissao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AcertoComissaoRepository extends JpaRepository<AcertoComissao, UUID> {

    /** Histórico do painel CEO (mais recentes primeiro). */
    List<AcertoComissao> findTop50ByOrderByCriadoEmDesc();
}
