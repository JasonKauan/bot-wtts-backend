package com.agendamento.backend.repository;

import com.agendamento.backend.entity.BotMensagem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface BotMensagemRepository extends JpaRepository<BotMensagem, UUID> {

    /** Visão geral: últimas mensagens do tenant (agrupadas por telefone em memória). */
    List<BotMensagem> findTop500ByTenantIdOrderByCriadoEmDesc(UUID tenantId);

    /** Conversa de um cliente (mais recentes primeiro; o front inverte). */
    List<BotMensagem> findTop100ByTenantIdAndTelefoneOrderByCriadoEmDesc(UUID tenantId, String telefone);

    /** Retenção: apaga o que passou da janela. */
    void deleteByCriadoEmBefore(LocalDateTime limite);
}
