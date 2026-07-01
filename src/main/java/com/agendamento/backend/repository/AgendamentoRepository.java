package com.agendamento.backend.repository;

import com.agendamento.backend.entity.Agendamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgendamentoRepository extends JpaRepository<Agendamento, UUID> {

    List<Agendamento> findByTenantIdAndDataHoraBetweenOrderByDataHora(
            UUID tenantId, LocalDateTime inicio, LocalDateTime fim);

    List<Agendamento> findByTenantIdAndProfissionalIdAndDataHoraBetweenOrderByDataHora(
            UUID tenantId, UUID profissionalId, LocalDateTime inicio, LocalDateTime fim);

    boolean existsByTenantIdAndDataHoraAndStatus(UUID tenantId, LocalDateTime dataHora, String status);

    /** Conflito por profissional: dois profissionais podem ocupar o mesmo horário (salão com vários). */
    boolean existsByTenantIdAndProfissionalIdAndDataHoraAndStatus(
            UUID tenantId, UUID profissionalId, LocalDateTime dataHora, String status);

    /** Conflito considerando vários status (CONFIRMADO + PENDENTE seguram o horário). */
    boolean existsByTenantIdAndDataHoraAndStatusIn(
            UUID tenantId, LocalDateTime dataHora, java.util.Collection<String> status);

    boolean existsByTenantIdAndProfissionalIdAndDataHoraAndStatusIn(
            UUID tenantId, UUID profissionalId, LocalDateTime dataHora, java.util.Collection<String> status);

    /** Fila de solicitações: pendentes futuros do tenant, mais próximos primeiro. */
    List<Agendamento> findByTenantIdAndStatusAndDataHoraAfterOrderByDataHora(
            UUID tenantId, String status, LocalDateTime dataHora);

    /** Para o scheduler de lembretes: agendamentos confirmados sem lembrete enviado dentro da janela 23h-25h. */
    @Query("SELECT a FROM Agendamento a WHERE a.status = 'CONFIRMADO' AND a.lembreteEnviado = false " +
           "AND a.dataHora >= :inicio AND a.dataHora <= :fim")
    List<Agendamento> findParaLembrete(@Param("inicio") LocalDateTime inicio,
                                       @Param("fim") LocalDateTime fim);

    /** Lembrete do dia: confirmados sem o lembrete-do-dia, dentro da janela de poucas horas. */
    @Query("SELECT a FROM Agendamento a WHERE a.status = 'CONFIRMADO' AND a.lembreteDiaEnviado = false " +
           "AND a.dataHora >= :inicio AND a.dataHora <= :fim")
    List<Agendamento> findParaLembreteDoDia(@Param("inicio") LocalDateTime inicio,
                                            @Param("fim") LocalDateTime fim);

    /**
     * Para tratar resposta SIM/NÃO ao lembrete: próximo agendamento confirmado nas próximas 26h.
     * SEMPRE escopado por tenant — sem isso, cliente de duas barbearias mexeria no agendamento da outra.
     */
    Optional<Agendamento> findTopByTenantIdAndClienteTelefoneAndStatusAndDataHoraBetweenOrderByDataHora(
            UUID tenantId, String clienteTelefone, String status, LocalDateTime inicio, LocalDateTime fim);

    /** Último agendamento ATIVO (PENDENTE ou CONFIRMADO) do cliente NESTE tenant — cancelamento/remarcação pelo bot. */
    Optional<Agendamento> findTopByTenantIdAndClienteTelefoneAndStatusInAndDataHoraAfterOrderByCriadoEmDesc(
            UUID tenantId, String clienteTelefone, java.util.Collection<String> status, LocalDateTime dataHora);

    /** "Meus horários" no bot: todos os ativos futuros do cliente neste tenant. */
    List<Agendamento> findByTenantIdAndClienteTelefoneAndStatusInAndDataHoraAfterOrderByDataHora(
            UUID tenantId, String clienteTelefone, java.util.Collection<String> status, LocalDateTime dataHora);

    /** "De novo": último agendamento que o cliente já fez neste tenant (qualquer status, incluindo passados). */
    Optional<Agendamento> findTopByTenantIdAndClienteTelefoneOrderByCriadoEmDesc(UUID tenantId, String clienteTelefone);

    /** Iteração 6: agendamentos criados no mês corrente, para o limite do plano. */
    long countByTenantIdAndCriadoEmGreaterThanEqual(UUID tenantId, LocalDateTime inicio);
}
