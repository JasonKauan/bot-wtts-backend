package com.agendamento.backend.service;

import com.agendamento.backend.entity.Agendamento;
import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.repository.AgendamentoRepository;
import com.agendamento.backend.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LembreteService {

    private static final DateTimeFormatter FMT_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final AgendamentoRepository agendamentoRepository;
    private final TenantRepository      tenantRepository;
    private final EvolutionApiService   evolutionApiService;

    /**
     * Roda a cada 1 hora.
     * Busca agendamentos confirmados sem lembrete dentro da janela 23h–25h a partir de agora.
     */
    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public int enviarLembretes() {
        LocalDateTime agora  = LocalDateTime.now();
        LocalDateTime inicio = agora.plusHours(23);
        LocalDateTime fim    = agora.plusHours(25);

        List<Agendamento> pendentes = agendamentoRepository.findParaLembrete(inicio, fim);
        if (pendentes.isEmpty()) return 0;

        log.info("[Lembrete] {} agendamento(s) para lembrar na janela {}-{}", pendentes.size(), inicio, fim);

        // Pré-carregar tenants para evitar N+1
        Map<UUID, Tenant> tenants = tenantRepository.findByAtivoTrue()
                .stream().collect(Collectors.toMap(Tenant::getId, t -> t));

        int enviados = 0;
        for (Agendamento ag : pendentes) {
            Tenant tenant = tenants.get(ag.getTenantId());
            if (tenant == null) continue;   // tenant inativo/suspenso não recebe lembrete

            String mensagem = montarMensagem(ag, tenant);
            evolutionApiService.enviarMensagemNaInstancia(
                    tenant.getId().toString(), ag.getClienteTelefone(), mensagem);

            ag.setLembreteEnviado(true);
            agendamentoRepository.save(ag);
            enviados++;

            log.info("[Lembrete] Enviado → tenant: {} | telefone: {} | dataHora: {}",
                    tenant.getId(), ag.getClienteTelefone(), ag.getDataHora());
        }
        return enviados;
    }

    private String montarMensagem(Agendamento ag, Tenant tenant) {
        String profTexto = ag.getProfissional() != null
                ? "\n👤 " + ag.getProfissional() : "";
        return "Olá " + ag.getClienteNome() + "! 👋\n\n"
                + "Lembrando do seu agendamento amanhã:\n"
                + "✂️ " + ag.getServico()
                + profTexto
                + "\n📅 " + ag.getDataHora().format(FMT_DATA)
                + " às " + ag.getDataHora().format(FMT_HORA)
                + "\n\nConfirmar? Responda *SIM* ou *NÃO*\n\n"
                + "_" + tenant.getNome() + "_";
    }
}
