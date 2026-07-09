package com.agendamento.backend.service;

import com.agendamento.backend.entity.Agendamento;
import com.agendamento.backend.entity.Recorrencia;
import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.exception.LimitePlanoException;
import com.agendamento.backend.repository.AgendamentoRepository;
import com.agendamento.backend.repository.RecorrenciaRepository;
import com.agendamento.backend.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cliente fixo (V26): job diário gera os agendamentos das recorrências com até 7 dias
 * de antecedência. Horário ocupado ou limite de plano → NÃO cria, avisa o dono e segue
 * pra próxima ocorrência (a recorrência nunca trava).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecorrenciaService {

    // Janela MENOR que a frequência mínima (7): depois de avançar proxima_data, a mesma
    // recorrência nunca volta a cair na janela no mesmo dia (o cron roda várias vezes de manhã).
    private static final int JANELA_DIAS = 6;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");

    private final RecorrenciaRepository recorrenciaRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final TenantRepository tenantRepository;
    private final DisponibilidadeService disponibilidadeService;
    private final EvolutionApiService evolutionApiService;
    private final PlanoService planoService;

    /** Roda de manhã cedo, todo dia (janela larga: o Render free pode acordar tarde). */
    @Scheduled(cron = "0 45 4-9 * * *")
    @Transactional
    public int gerarAgendamentos() {
        LocalDate hoje = LocalDate.now();
        LocalDate limite = hoje.plusDays(JANELA_DIAS);
        Map<UUID, Tenant> tenants = tenantRepository.findByAtivoTrue()
                .stream().collect(Collectors.toMap(Tenant::getId, t -> t));

        int criados = 0;
        for (Recorrencia r : recorrenciaRepository.findByAtivoTrueAndProximaDataLessThanEqual(limite)) {
            Tenant t = tenants.get(r.getTenantId());
            if (t == null || t.isAssinaturaVencida()) continue;   // suspenso/vencido não gera

            // Catch-up: se o servidor ficou fora e a data passou, pula pra próxima futura.
            while (r.getProximaData().isBefore(hoje)) {
                r.setProximaData(r.getProximaData().plusDays(r.getFrequenciaDias()));
            }
            if (r.getProximaData().isAfter(limite)) {
                recorrenciaRepository.save(r);
                continue;
            }

            LocalDateTime dataHora = r.getProximaData().atTime(LocalTime.parse(r.getHora()));
            int dur = disponibilidadeService.duracaoServico(t, r.getServico());

            if (disponibilidadeService.conflita(t, r.getProfissionalId(), dataHora, dur)) {
                avisarDono(t, "⚠️ Não consegui renovar o horário fixo de *" + r.getClienteNome()
                        + "* (" + r.getServico() + ") em *" + dataHora.format(FMT)
                        + "* — o horário está ocupado. Encaixe na mão pelo painel, se quiser.");
            } else {
                try {
                    planoService.validarNovoAgendamento(t);
                    agendamentoRepository.save(Agendamento.builder()
                            .tenantId(t.getId())
                            .clienteNome(r.getClienteNome())
                            .clienteTelefone(r.getClienteTelefone() != null ? r.getClienteTelefone() : "")
                            .servico(r.getServico())
                            .profissional(r.getProfissional())
                            .profissionalId(r.getProfissionalId())
                            .duracaoMinutos(dur)
                            .dataHora(dataHora)
                            .status("CONFIRMADO")
                            .build());
                    criados++;
                    log.info("[Recorrencia] Gerado {} em {} (tenant {})", r.getClienteNome(), dataHora, t.getId());
                } catch (LimitePlanoException e) {
                    avisarDono(t, "⚠️ Não consegui renovar o horário fixo de *" + r.getClienteNome()
                            + "* em *" + dataHora.format(FMT) + "*: " + e.getMessage());
                }
            }

            r.setProximaData(r.getProximaData().plusDays(r.getFrequenciaDias()));
            recorrenciaRepository.save(r);
        }
        if (criados > 0) log.info("[Recorrencia] {} agendamento(s) fixos gerados", criados);
        return criados;
    }

    private void avisarDono(Tenant t, String texto) {
        if (t.getTelefoneWhatsapp() == null || t.getTelefoneWhatsapp().isBlank()) return;
        try {
            evolutionApiService.enviarMensagemNaInstancia(t.getId().toString(), t.getTelefoneWhatsapp(), texto);
        } catch (Exception e) {
            log.warn("[Recorrencia] Falha ao avisar o dono do tenant {}: {}", t.getId(), e.getMessage());
        }
    }
}
