package com.agendamento.backend.service;

import com.agendamento.backend.entity.*;
import com.agendamento.backend.exception.LimitePlanoException;
import com.agendamento.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * Fluxo: SERVICO → PROFISSIONAL → DATA → HORA → CONFIRMACAO
 * (PROFISSIONAL é pulado se o tenant não tiver profissionais cadastrados)
 *
 * TODO Iteração 5: lembretes automáticos (scheduler).
 * TODO Iteração 7: grade horária por profissional (horários ainda hardcoded).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BotService {

    private static final int MAX_TENTATIVAS = 3;

    // TODO Iteração 7: grade por profissional — virá do banco
    private static final List<String> HORARIOS = List.of(
            "09:00", "10:00", "11:00", "14:00", "15:00", "16:00"
    );

    private final BotSessionRepository    botSessionRepository;
    private final AgendamentoRepository   agendamentoRepository;
    private final ServicoRepository       servicoRepository;
    private final ProfissionalRepository  profissionalRepository;
    private final EvolutionApiService     evolutionApiService;
    private final PlanoService            planoService;

    @Transactional
    public void processMessage(String telefone, String mensagem, String clienteNome, Tenant tenant) {
        log.info("[{}] Mensagem de {}: {}", tenant.getId(), telefone, mensagem);

        // 1. Horário de atendimento (usa config do tenant)
        int hora = LocalDateTime.now().getHour();
        if (hora < tenant.getHorarioAbertura() || hora >= tenant.getHorarioFechamento()) {
            enviar(tenant, telefone,
                    "Nosso horário de atendimento é das " + tenant.getHorarioAbertura() + "h às "
                    + tenant.getHorarioFechamento() + "h. Mande *oi* amanhã para agendar! 😊");
            return;
        }

        String norm = normalizar(mensagem);

        // 2. Palavras-chave globais
        if (isEncerrar(norm)) {
            botSessionRepository.findByTelefoneAndTenantId(telefone, tenant.getId())
                    .ifPresent(botSessionRepository::delete);
            enviar(tenant, telefone, "Tudo bem! Mande *oi* quando quiser agendar. 😊");
            return;
        }
        if (isReiniciar(norm)) {
            botSessionRepository.findByTelefoneAndTenantId(telefone, tenant.getId())
                    .ifPresent(botSessionRepository::delete);
            iniciarSessao(telefone, tenant);
            return;
        }
        if ("ajuda".equals(norm)) {
            enviar(tenant, telefone,
                    "Para agendar, basta mandar *oi*! " +
                    "Para cancelar, responda *cancelar* a qualquer momento. 😊");
            return;
        }

        // 3. Resposta a lembrete (sem sessão ativa)
        BotSession session = botSessionRepository
                .findByTelefoneAndTenantId(telefone, tenant.getId()).orElse(null);
        if (session == null) {
            if ("sim".equals(norm) || "s".equals(norm) || "nao".equals(norm) || "n".equals(norm)) {
                handleRespostaLembrete(telefone, norm, tenant);
                return;
            }
            iniciarSessao(telefone, tenant);
            return;
        }

        // 4. Timeout 30 min
        if (session.getUltimaInteracao().isBefore(LocalDateTime.now().minusMinutes(30))) {
            botSessionRepository.delete(session);
            enviar(tenant, telefone, "Sua sessão expirou. Mande *oi* para recomeçar! 😊");
            return;
        }

        session.setUltimaInteracao(LocalDateTime.now());

        switch (session.getEtapa()) {
            case "SERVICO"      -> handleServico(session, mensagem, norm, telefone, tenant);
            case "PROFISSIONAL" -> handleProfissional(session, mensagem, telefone, tenant);
            case "DATA"         -> handleData(session, norm, telefone, tenant);
            case "HORA"         -> handleHora(session, mensagem, telefone, tenant);
            case "CONFIRMACAO"  -> handleConfirmacao(session, norm, telefone, clienteNome, tenant);
            default -> { botSessionRepository.delete(session); iniciarSessao(telefone, tenant); }
        }
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private BotSession iniciarSessao(String telefone, Tenant tenant) {
        List<Servico> servicos = servicoRepository.findByTenantIdAndAtivoTrue(tenant.getId());
        BotSession session = BotSession.builder()
                .tenantId(tenant.getId()).telefone(telefone)
                .etapa("SERVICO").tentativas(0)
                .ultimaInteracao(LocalDateTime.now()).build();
        botSessionRepository.save(session);

        if (servicos.isEmpty()) {
            enviar(tenant, telefone, "Olá! 👋 Ainda não há serviços configurados. Tente mais tarde!");
        } else {
            enviar(tenant, telefone,
                    "Olá! 👋 Bem-vindo a *" + tenant.getNome() + "*!\n\nEscolha o serviço:\n\n"
                    + formatarServicos(servicos));
        }
        return session;
    }

    private void handleServico(BotSession session, String msg, String norm, String telefone, Tenant tenant) {
        List<Servico> servicos = servicoRepository.findByTenantIdAndAtivoTrue(tenant.getId());
        Integer opcao = parsearOpcao(msg, servicos.size());
        if (opcao == null)
            for (int i = 0; i < servicos.size(); i++)
                if (normalizar(servicos.get(i).getNome()).equals(norm)) { opcao = i + 1; break; }

        if (opcao == null) {
            erroComTentativa(session, telefone, tenant,
                    "Não entendi. Responda com o número do serviço:\n\n" + formatarServicos(servicos));
            return;
        }

        session.setServicoEscolhido(servicos.get(opcao - 1).getNome());
        session.setTentativas(0);

        List<Profissional> profs = profissionalRepository.findByTenantIdAndAtivoTrue(tenant.getId());
        if (profs.isEmpty()) {
            session.setEtapa("DATA");
            botSessionRepository.save(session);
            enviar(tenant, telefone,
                    "Ótimo! *" + session.getServicoEscolhido() + "* ✂️\n\n" +
                    "📅 Qual data você prefere?\n_Ex: hoje, amanhã, 15/06_");
        } else {
            session.setEtapa("PROFISSIONAL");
            botSessionRepository.save(session);
            enviar(tenant, telefone,
                    "Ótimo! *" + session.getServicoEscolhido() + "* ✂️\n\n" +
                    "👤 Com qual profissional?\n\n" + formatarProfissionais(profs));
        }
    }

    private void handleProfissional(BotSession session, String msg, String telefone, Tenant tenant) {
        List<Profissional> profs = profissionalRepository.findByTenantIdAndAtivoTrue(tenant.getId());
        Integer opcao = parsearOpcao(msg, profs.size());

        if (opcao == null) {
            erroComTentativa(session, telefone, tenant,
                    "Não entendi. Responda com o número do profissional:\n\n" + formatarProfissionais(profs));
            return;
        }

        Profissional prof = profs.get(opcao - 1);
        session.setProfissionalId(prof.getId());
        session.setProfissionalEscolhido(prof.getNome());
        session.setEtapa("DATA");
        session.setTentativas(0);
        botSessionRepository.save(session);

        enviar(tenant, telefone,
                "👤 *" + prof.getNome() + "*\n\n📅 Qual data você prefere?\n_Ex: hoje, amanhã, 15/06_");
    }

    private void handleData(BotSession session, String norm, String telefone, Tenant tenant) {
        LocalDate data = parsearData(norm);
        if (data == null) { erroComTentativa(session, telefone, tenant, "Não entendi a data 😕\nTente: *hoje*, *amanhã* ou *dd/mm*"); return; }
        if (data.isBefore(LocalDate.now())) { erroComTentativa(session, telefone, tenant, "Essa data já passou. Qual outra data você prefere?"); return; }
        if (data.isAfter(LocalDate.now().plusDays(30))) { erroComTentativa(session, telefone, tenant, "Só agendamos com até 30 dias de antecedência. Qual outra data?"); return; }

        List<String> horarios = horariosDisponiveis(tenant.getId(), data);
        if (horarios.isEmpty()) {
            erroComTentativa(session, telefone, tenant,
                    "Não há horários disponíveis para *" + formatarData(data) + "* 😕\nQual outra data você prefere?");
            return;
        }

        session.setDataEscolhida(data);
        session.setEtapa("HORA");
        session.setTentativas(0);
        botSessionRepository.save(session);

        enviar(tenant, telefone,
                "🗓️ *" + formatarData(data) + "* — horários disponíveis:\n\n" + formatarLista(horarios));
    }

    private void handleHora(BotSession session, String msg, String telefone, Tenant tenant) {
        List<String> disp = horariosDisponiveis(tenant.getId(), session.getDataEscolhida());
        Integer opcao = parsearOpcao(msg, disp.size());

        if (opcao == null) { erroComTentativa(session, telefone, tenant, "Não entendi. Responda com o número do horário:\n\n" + formatarLista(disp)); return; }

        String hora = disp.get(opcao - 1);
        LocalDateTime dataHora = LocalDateTime.of(session.getDataEscolhida(), LocalTime.parse(hora));

        if (agendamentoRepository.existsByTenantIdAndDataHoraAndStatus(tenant.getId(), dataHora, "CONFIRMADO")) {
            List<String> atualizados = horariosDisponiveis(tenant.getId(), session.getDataEscolhida());
            if (atualizados.isEmpty()) {
                enviar(tenant, telefone, "Não há mais horários disponíveis. Qual outra data?");
                session.setEtapa("DATA"); session.setTentativas(0);
            } else {
                enviar(tenant, telefone, "Esse horário acabou de ser reservado 😕\n\nDisponíveis:\n\n" + formatarLista(atualizados));
            }
            botSessionRepository.save(session); return;
        }

        session.setHoraEscolhida(hora);
        session.setEtapa("CONFIRMACAO");
        session.setTentativas(0);
        botSessionRepository.save(session);

        String profTexto = session.getProfissionalEscolhido() != null
                ? "\n👤 *Profissional:* " + session.getProfissionalEscolhido() : "";
        enviar(tenant, telefone,
                "Confirmar agendamento?\n\n✂️ *Serviço:* " + session.getServicoEscolhido()
                + profTexto
                + "\n📅 *Data:* " + formatarData(session.getDataEscolhida())
                + "\n⏰ *Horário:* " + hora
                + "\n\nResponda *SIM* ou *NÃO*.");
    }

    private void handleConfirmacao(BotSession session, String norm, String telefone,
                                   String clienteNome, Tenant tenant) {
        if ("sim".equals(norm) || "s".equals(norm)) {
            try {
                planoService.validarNovoAgendamento(tenant); // Iteração 6: limite do plano
            } catch (LimitePlanoException e) {
                botSessionRepository.delete(session);
                log.warn("[{}] Agendamento bloqueado pelo limite do plano: {}", tenant.getId(), e.getMessage());
                enviar(tenant, telefone,
                        "😔 No momento não conseguimos registrar novos agendamentos por aqui. " +
                        "Por favor, entre em contato direto com o estabelecimento.");
                return;
            }
            LocalDateTime dataHora = LocalDateTime.of(session.getDataEscolhida(), LocalTime.parse(session.getHoraEscolhida()));
            Agendamento ag = Agendamento.builder()
                    .tenantId(tenant.getId())
                    .clienteNome(clienteNome != null ? clienteNome : telefone)
                    .clienteTelefone(telefone)
                    .servico(session.getServicoEscolhido())
                    .profissional(session.getProfissionalEscolhido())
                    .profissionalId(session.getProfissionalId())
                    .dataHora(dataHora).status("CONFIRMADO").build();
            agendamentoRepository.save(ag);
            botSessionRepository.delete(session);
            log.info("[{}] Agendamento salvo — {}", tenant.getId(), telefone);
            enviar(tenant, telefone, "✅ Agendado! Até lá 😊");

        } else if ("nao".equals(norm) || "n".equals(norm)) {
            botSessionRepository.delete(session);
            enviar(tenant, telefone, "Tudo bem! Mande *oi* para recomeçar. 😊");
        } else {
            erroComTentativa(session, telefone, tenant, "Responda *SIM* para confirmar ou *NÃO* para cancelar.");
        }
    }

    private void handleRespostaLembrete(String telefone, String norm, Tenant tenant) {
        LocalDateTime agora = LocalDateTime.now();
        var agOpt = agendamentoRepository.findTopByClienteTelefoneAndStatusAndDataHoraBetweenOrderByDataHora(
                telefone, "CONFIRMADO", agora, agora.plusHours(26));

        if (agOpt.isEmpty()) {
            // Sem agendamento próximo — tratar como início de conversa
            iniciarSessao(telefone, tenant);
            return;
        }

        var ag = agOpt.get();
        if ("sim".equals(norm) || "s".equals(norm)) {
            enviar(tenant, telefone,
                    "✅ Ótimo! Te esperamos em *"
                    + ag.getDataHora().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm"))
                    + "*. 😊");
        } else {
            ag.setStatus("CANCELADO");
            agendamentoRepository.save(ag);
            log.info("[{}] Agendamento {} cancelado via lembrete", tenant.getId(), ag.getId());
            enviar(tenant, telefone,
                    "Tudo bem! Agendamento cancelado. 😊\n\nQuer reagendar? Mande *oi* para recomeçar.");
        }
    }

    // ── Tentativas ────────────────────────────────────────────────────────────

    private void erroComTentativa(BotSession session, String telefone, Tenant tenant, String msg) {
        session.setTentativas(session.getTentativas() + 1);
        if (session.getTentativas() >= MAX_TENTATIVAS) {
            botSessionRepository.delete(session);
            enviar(tenant, telefone, "Desculpe, não consegui te entender. Mande *oi* para recomeçar. 😊");
        } else {
            botSessionRepository.save(session);
            enviar(tenant, telefone, msg);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void enviar(Tenant tenant, String telefone, String texto) {
        evolutionApiService.enviarMensagemNaInstancia(tenant.getId().toString(), telefone, texto);
    }

    private List<String> horariosDisponiveis(UUID tenantId, LocalDate data) {
        LocalDateTime agora = LocalDateTime.now();
        return HORARIOS.stream()
                .filter(h -> {
                    LocalDateTime slot = LocalDateTime.of(data, LocalTime.parse(h));
                    // não oferecer horário que já passou (relevante quando a data é hoje)
                    return slot.isAfter(agora)
                            && !agendamentoRepository.existsByTenantIdAndDataHoraAndStatus(tenantId, slot, "CONFIRMADO");
                })
                .toList();
    }

    private String formatarServicos(List<Servico> servicos) {
        var sb = new StringBuilder();
        for (int i = 0; i < servicos.size(); i++) sb.append(i+1).append(". ").append(servicos.get(i).getNome()).append("\n");
        return sb.toString().trim();
    }

    private String formatarProfissionais(List<Profissional> profs) {
        var sb = new StringBuilder();
        for (int i = 0; i < profs.size(); i++) sb.append(i+1).append(". ").append(profs.get(i).getNome()).append("\n");
        return sb.toString().trim();
    }

    private String formatarLista(List<String> items) {
        var sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) sb.append(i+1).append(". ").append(items.get(i)).append("\n");
        return sb.toString().trim();
    }

    private String formatarData(LocalDate d) { return d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")); }

    private Integer parsearOpcao(String msg, int max) {
        try { int n = Integer.parseInt(msg.trim()); return (n >= 1 && n <= max) ? n : null; }
        catch (NumberFormatException e) { return null; }
    }

    private LocalDate parsearData(String norm) {
        if ("hoje".equals(norm)) return LocalDate.now();
        if ("amanha".equals(norm)) return LocalDate.now().plusDays(1);
        try {
            if (norm.matches("\\d{1,2}/\\d{1,2}")) {
                String[] p = norm.split("/");
                return LocalDate.parse(String.format("%02d/%02d/%d", Integer.parseInt(p[0]), Integer.parseInt(p[1]), LocalDate.now().getYear()), DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            }
            if (norm.matches("\\d{1,2}/\\d{1,2}/\\d{4}")) {
                String[] p = norm.split("/");
                return LocalDate.parse(String.format("%02d/%02d/%s", Integer.parseInt(p[0]), Integer.parseInt(p[1]), p[2]), DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            }
        } catch (DateTimeParseException | NumberFormatException ignored) {}
        return null;
    }

    private boolean isEncerrar(String n) { return "cancelar".equals(n) || "sair".equals(n); }
    private boolean isReiniciar(String n) { return "oi".equals(n) || "ola".equals(n) || "bom dia".equals(n) || "boa tarde".equals(n) || "boa noite".equals(n); }

    private String normalizar(String texto) {
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "").toLowerCase();
    }
}
