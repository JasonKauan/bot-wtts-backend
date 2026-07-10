package com.agendamento.backend.service;

import com.agendamento.backend.entity.Plano;
import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.exception.LimitePlanoException;
import com.agendamento.backend.repository.ProfissionalRepository;
import com.agendamento.backend.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Limites e recursos por plano. Agendamentos são ilimitados em todos os planos
 * (decisão 2026-07-10); o que varia é o nº de profissionais e os recursos (Plano.Recurso).
 */
@Service
@RequiredArgsConstructor
public class PlanoService {

    private final ProfissionalRepository profissionalRepository;
    private final TenantRepository tenantRepository;

    /** Limite de profissionais ativos (GOLD: 2, PLATINUM: 5, DIAMOND/TRIAL: ilimitado). */
    public void validarNovoProfissional(UUID tenantId, Plano plano) {
        int max = plano.getMaxProfissionais();
        if (max == Integer.MAX_VALUE) return;

        long ativos = profissionalRepository.countByTenantIdAndAtivoTrue(tenantId);
        if (ativos >= max) {
            throw new LimitePlanoException("O plano " + plano.getNomeBonito() + " permite até "
                    + max + " profissionais ativos. Faça upgrade na aba Assinatura.");
        }
    }

    /** O tenant tem acesso ao recurso? (não lança — pra decisões silenciosas no bot/jobs) */
    public boolean permite(Tenant tenant, Plano.Recurso recurso) {
        return tenant.getPlano().permite(recurso);
    }

    /** Barra a requisição com 403 + mensagem de upgrade quando o plano não cobre o recurso. */
    public void exigir(UUID tenantId, Plano.Recurso recurso) {
        Tenant t = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!t.getPlano().permite(recurso)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Esse recurso faz parte do plano " + Plano.minimoPara(recurso)
                    + ". Faça upgrade na aba Assinatura 😉");
        }
    }
}
