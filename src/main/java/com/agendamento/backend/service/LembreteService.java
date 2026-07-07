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

import java.time.LocalDate;
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
            if (ag.getClienteTelefone() == null || ag.getClienteTelefone().isBlank()) {
                ag.setLembreteEnviado(true); // manual sem telefone: nada a enviar
                agendamentoRepository.save(ag);
                continue;
            }

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

    /**
     * Lembrete do dia: roda a cada 1h e avisa quem tem horário daqui a ~2-4h
     * (além do lembrete de 24h). Flag própria evita duplicar.
     */
    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public int enviarLembretesDoDia() {
        LocalDateTime agora  = LocalDateTime.now();
        LocalDateTime inicio = agora.plusHours(2);
        LocalDateTime fim    = agora.plusHours(4);

        List<Agendamento> pendentes = agendamentoRepository.findParaLembreteDoDia(inicio, fim);
        if (pendentes.isEmpty()) return 0;

        Map<UUID, Tenant> tenants = tenantRepository.findByAtivoTrue()
                .stream().collect(Collectors.toMap(Tenant::getId, t -> t));

        int enviados = 0;
        for (Agendamento ag : pendentes) {
            Tenant tenant = tenants.get(ag.getTenantId());
            if (tenant == null) continue;
            if (ag.getClienteTelefone() == null || ag.getClienteTelefone().isBlank()) {
                ag.setLembreteDiaEnviado(true); // manual sem telefone: nada a enviar
                agendamentoRepository.save(ag);
                continue;
            }

            evolutionApiService.enviarMensagemNaInstancia(
                    tenant.getId().toString(), ag.getClienteTelefone(), montarMensagemDoDia(ag));
            ag.setLembreteDiaEnviado(true);
            agendamentoRepository.save(ag);
            enviados++;

            log.info("[LembreteDia] Enviado → tenant: {} | telefone: {} | dataHora: {}",
                    tenant.getId(), ag.getClienteTelefone(), ag.getDataHora());
        }
        return enviados;
    }

    /**
     * Resumo do dia pro DONO (V20): a agenda de hoje no WhatsApp dele, toda manhã.
     * Roda a cada 30 min entre 6h e 10h — o Render free dorme e pode acordar tarde,
     * então {@code resumoEnviadoEm} garante no máximo 1 envio por dia.
     * Agenda vazia não gera mensagem (não incomoda à toa).
     */
    @Scheduled(cron = "0 */30 6-9 * * *")
    @Transactional
    public int enviarResumoDiario() {
        LocalDate hoje = LocalDate.now();
        int enviados = 0;
        for (Tenant t : tenantRepository.findByAtivoTrue()) {
            if (!t.isResumoDiario() || t.isAssinaturaVencida()) continue;
            if (hoje.equals(t.getResumoEnviadoEm())) continue;
            if (t.getTelefoneWhatsapp() == null || t.getTelefoneWhatsapp().isBlank()) continue;

            List<Agendamento> doDia = agendamentoRepository
                    .findByTenantIdAndDataHoraBetweenOrderByDataHora(
                            t.getId(), hoje.atStartOfDay(), hoje.plusDays(1).atStartOfDay())
                    .stream().filter(a -> "CONFIRMADO".equals(a.getStatus()) || "PENDENTE".equals(a.getStatus()))
                    .toList();
            if (doDia.isEmpty()) continue;

            try {
                evolutionApiService.enviarMensagemNaInstancia(
                        t.getId().toString(), t.getTelefoneWhatsapp(), montarResumoDiario(doDia));
                t.setResumoEnviadoEm(hoje);
                tenantRepository.save(t);
                enviados++;
                log.info("[ResumoDiario] Enviado → tenant: {} ({} agendamentos)", t.getId(), doDia.size());
            } catch (Exception e) {
                // best-effort: tenta de novo na próxima rodada da manhã
                log.warn("[ResumoDiario] Falha no tenant {}: {}", t.getId(), e.getMessage());
            }
        }
        return enviados;
    }

    private String montarResumoDiario(List<Agendamento> doDia) {
        StringBuilder sb = new StringBuilder("☀️ Bom dia! Sua agenda de hoje ("
                + LocalDate.now().format(FMT_DATA) + "):\n");
        for (Agendamento a : doDia) {
            sb.append("\n⏰ *").append(a.getDataHora().format(FMT_HORA)).append("* — ").append(a.getServico());
            if (a.getProfissional() != null) sb.append(" com ").append(a.getProfissional());
            sb.append(" · ").append(a.getClienteNome());
            if ("PENDENTE".equals(a.getStatus())) sb.append(" _(aguardando aprovação)_");
        }
        sb.append("\n\nTotal: *").append(doDia.size()).append(doDia.size() == 1 ? "* atendimento" : "* atendimentos")
          .append(". Bom trabalho! 💪");
        return sb.toString();
    }

    private String montarMensagemDoDia(Agendamento ag) {
        String profTexto = ag.getProfissional() != null ? " com " + ag.getProfissional() : "";
        return "⏰ Oi " + ag.getClienteNome() + "! Passando pra lembrar do seu horário de *hoje*:\n"
                + "✂️ " + ag.getServico() + profTexto
                + " às " + ag.getDataHora().format(FMT_HORA)
                + "\n\nTe esperamos! 😊\n_(Se não puder vir, responda *cancelar*.)_";
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
