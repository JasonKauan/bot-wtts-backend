package com.agendamento.backend.service;

import com.agendamento.backend.entity.Plano;
import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.exception.LimitePlanoException;
import com.agendamento.backend.repository.AgendamentoRepository;
import com.agendamento.backend.repository.ProfissionalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Valida os limites de uso de cada plano (Iteração 6). */
@Service
@RequiredArgsConstructor
public class PlanoService {

    private final AgendamentoRepository agendamentoRepository;
    private final ProfissionalRepository profissionalRepository;

    /** Limite de agendamentos criados no mês corrente (BASICO: 100). */
    public void validarNovoAgendamento(Tenant tenant) {
        int max = tenant.getPlano().getMaxAgendamentosMes();
        if (max == Integer.MAX_VALUE) return;

        LocalDateTime inicioMes = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        long criadosNoMes = agendamentoRepository.countByTenantIdAndCriadoEmGreaterThanEqual(tenant.getId(), inicioMes);
        if (criadosNoMes >= max) {
            throw new LimitePlanoException("Limite de agendamentos atingido. Faça upgrade do plano.");
        }
    }

    /** Limite de profissionais ativos (BASICO: 1, PRO: 5). */
    public void validarNovoProfissional(UUID tenantId, Plano plano) {
        int max = plano.getMaxProfissionais();
        if (max == Integer.MAX_VALUE) return;

        long ativos = profissionalRepository.countByTenantIdAndAtivoTrue(tenantId);
        if (ativos >= max) {
            throw new LimitePlanoException("Plano " + plano + " suporta apenas "
                    + max + " profissional(is). Faça upgrade do plano.");
        }
    }
}
