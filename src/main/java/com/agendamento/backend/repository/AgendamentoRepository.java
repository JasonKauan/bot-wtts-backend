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

    /** Para tratar resposta SIM/NÃO ao lembrete: próximo agendamento confirmado nas próximas 26h. */
    Optional<Agendamento> findTopByClienteTelefoneAndStatusAndDataHoraBetweenOrderByDataHora(
            String clienteTelefone, String status, LocalDateTime inicio, LocalDateTime fim);

    /** Último agendamento confirmado que o cliente criou (ainda futuro) — usado no cancelamento pelo bot. */
    Optional<Agendamento> findTopByClienteTelefoneAndStatusAndDataHoraAfterOrderByCriadoEmDesc(
            String clienteTelefone, String status, LocalDateTime dataHora);

    /** Iteração 6: agendamentos criados no mês corrente, para o limite do plano. */
    long countByTenantIdAndCriadoEmGreaterThanEqual(UUID tenantId, LocalDateTime inicio);
}
