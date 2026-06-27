package com.agendamento.backend.service;

import com.agendamento.backend.entity.*;
import com.agendamento.backend.exception.LimitePlanoException;
import com.agendamento.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
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
    private final AiService               aiService;

    @Async
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
            enviar(tenant, telefone, "Oi! 👋 Ainda não temos serviços configurados por aqui. Tente mais tarde! 😊");
        } else {
            String saudacao = aiService.redigir(
                    "Cumprimente o cliente que acabou de chamar o estabelecimento \"" + tenant.getNome()
                    + "\" no WhatsApp, diga que vai ajudar a agendar e pergunte qual servico ele quer.");
            if (saudacao == null)
                saudacao = "Oi! 👋 Aqui é da *" + tenant.getNome() + "*, vou te ajudar a agendar 😊\n\nQual serviço você quer?";
            enviar(tenant, telefone, saudacao + "\n\n" + formatarServicos(servicos));
        }
        return session;
    }

    private void handleServico(BotSession session, String msg, String norm, String telefone, Tenant tenant) {
        List<Servico> servicos = servicoRepository.findByTenantIdAndAtivoTrue(tenant.getId());
        Integer opcao = parsearOpcao(msg, servicos.size());
        if (opcao == null)
            for (int i = 0; i < servicos.size(); i++)
                if (normalizar(servicos.get(i).getNome()).equals(norm)) { opcao = i + 1; break; }

        if (opcao == null) { // IA: linguagem natural ("queria fazer a barba")
            int ai = aiService.escolherOpcao(msg, servicos.stream().map(Servico::getNome).toList());
            if (ai > 0) opcao = ai;
        }

        if (opcao == null) {
            erroComTentativa(session, telefone, tenant,
                    "Me diz qual serviço você quer (pode ser pelo número ou pelo nome) 😊\n\n" + formatarServicos(servicos));
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

        if (opcao == null) { // IA: linguagem natural
            int ai = aiService.escolherOpcao(msg, profs.stream().map(Profissional::getNome).toList());
            if (ai > 0) opcao = ai;
        }

        if (opcao == null) {
            erroComTentativa(session, telefone, tenant,
                    "Com qual profissional você prefere? 😊\n\n" + formatarProfissionais(profs));
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
        if (data == null) data = aiService.interpretarData(norm, LocalDate.now()); // IA: "sexta que vem", "dia 20"...
        if (data == null) { erroComTentativa(session, telefone, tenant, "Não consegui entender a data 😅 Pode ser *hoje*, *amanhã*, um dia da semana, ou no formato *dd/mm* (ex: 30/06)."); return; }
        if (data.isBefore(LocalDate.now())) { erroComTentativa(session, telefone, tenant, "Essa data já passou. Qual outra data você prefere?"); return; }
        if (data.isAfter(LocalDate.now().plusDays(30))) { erroComTentativa(session, telefone, tenant, "Só agendamos com até 30 dias de antecedência. Qual outra data?"); return; }

        List<String> horarios = horariosDisponiveis(tenant.getId(), data);
        if (horarios.isEmpty()) {
            LocalDate proxima = proximaDataComVaga(tenant.getId(), data.plusDays(1));
            String sugestao = (proxima != null)
                    ? " A próxima data com vaga é *" + formatarData(proxima) + "* — é só me mandar ela (ou outra) 😊"
                    : " Me diz outra data, por favor.";
            erroComTentativa(session, telefone, tenant,
                    "Pra *" + formatarData(data) + "* não tenho mais horário 😕" + sugestao);
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

        if (opcao == null) { // IA: extrai o horário pedido (ex.: "as 3 da tarde" -> 15:00)
            String desejado = aiService.interpretarHorario(msg);
            if (desejado != null) {
                int idx = disp.indexOf(desejado);
                if (idx >= 0) {
                    opcao = idx + 1; // o horário pedido está livre
                } else {
                    // pedido indisponível: sugere o(s) mais próximo(s) da lista real
                    List<String> sug = sugerirProximos(desejado, disp, 2);
                    if (!sug.isEmpty()) {
                        erroComTentativa(session, telefone, tenant,
                                "Poxa, *" + desejado + "* não tá livre 😕 " + (sug.size() == 1
                                        ? "Mas tenho *" + sug.get(0) + "* — serve?"
                                        : "Mas tenho *" + sug.get(0) + "* e *" + sug.get(1) + "*, qual prefere?"));
                        return;
                    }
                }
            }
        }

        if (opcao == null) { // cliente mencionou um período (manhã/tarde/noite)?
            List<String> doPeriodo = filtrarPorPeriodo(disp, msg);
            if (doPeriodo != null) {
                erroComTentativa(session, telefone, tenant, doPeriodo.isEmpty()
                        ? "Nesse período não tenho vaga nesse dia 😕 Mas tenho " + juntarHorarios(disp) + " — qual rola?"
                        : "Tenho " + juntarHorarios(doPeriodo) + " — qual fica bom? 😊");
                return;
            }
        }

        if (opcao == null) { erroComTentativa(session, telefone, tenant, "Tenho estes horários — qual fica melhor pra você? 😊\n\n" + formatarLista(disp)); return; }

        String hora = disp.get(opcao - 1);
        LocalDateTime dataHora = LocalDateTime.of(session.getDataEscolhida(), LocalTime.parse(hora));

        if (agendamentoRepository.existsByTenantIdAndDataHoraAndStatus(tenant.getId(), dataHora, "CONFIRMADO")) {
            List<String> atualizados = horariosDisponiveis(tenant.getId(), session.getDataEscolhida());
            if (atualizados.isEmpty()) {
                enviar(tenant, telefone, "Não há mais horários disponíveis. Qual outra data?");
                session.setEtapa("DATA"); session.setTentativas(0);
            } else {
                List<String> sug = sugerirProximos(hora, atualizados, 2);
                enviar(tenant, telefone, "Esse horário acabou de ser reservado 😕 " + (sug.size() == 1
                        ? "Mas ainda tenho *" + sug.get(0) + "* — serve?"
                        : "Mas ainda tenho *" + sug.get(0) + "* e *" + sug.get(1) + "*, qual prefere?"));
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
                + "\n\nPosso confirmar? Responda *sim* 👍 ou *não*.");
    }

    private void handleConfirmacao(BotSession session, String norm, String telefone,
                                   String clienteNome, Tenant tenant) {
        // IA: interpreta confirmação natural ("pode ser", "isso", "não quero")
        if (!"sim".equals(norm) && !"s".equals(norm) && !"nao".equals(norm) && !"n".equals(norm)) {
            int ai = aiService.escolherOpcao(norm, List.of("Sim, confirmar o agendamento", "Nao, cancelar"));
            if (ai == 1) norm = "sim";
            else if (ai == 2) norm = "nao";
        }
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
            String ok = aiService.redigir("O agendamento de *" + session.getServicoEscolhido()
                    + "* foi confirmado com sucesso. Agradeca rapidamente e diga que espera o cliente.");
            enviar(tenant, telefone, ok != null ? ok : "✅ Agendado! Até lá 😊");

        } else if ("nao".equals(norm) || "n".equals(norm)) {
            botSessionRepository.delete(session);
            enviar(tenant, telefone, "Tudo bem! Mande *oi* para recomeçar. 😊");
        } else {
            erroComTentativa(session, telefone, tenant, "Só pra confirmar: responda *sim* 👍 pra agendar, ou *não* pra cancelar.");
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
            enviar(tenant, telefone, "Foi mal, acho que me embananei aqui 😅 Manda *oi* que a gente recomeça!");
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

    /** Horários da lista que estão mais próximos do desejado, ordenados por proximidade (até n). */
    private List<String> sugerirProximos(String desejado, List<String> disponiveis, int n) {
        int alvo = emMinutos(desejado);
        return disponiveis.stream()
                .sorted(java.util.Comparator.comparingInt(h -> Math.abs(emMinutos(h) - alvo)))
                .limit(n)
                .toList();
    }

    private int emMinutos(String hhmm) {
        String[] p = hhmm.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    /** Primeira data a partir de 'inicio' (até 30 dias) com ao menos um horário livre. */
    private LocalDate proximaDataComVaga(UUID tenantId, LocalDate inicio) {
        LocalDate limite = LocalDate.now().plusDays(30);
        for (LocalDate d = inicio; !d.isAfter(limite); d = d.plusDays(1)) {
            if (!horariosDisponiveis(tenantId, d).isEmpty()) return d;
        }
        return null;
    }

    /** Filtra os horários pelo período citado (manhã/tarde/noite). null = nenhum período citado. */
    private List<String> filtrarPorPeriodo(List<String> disp, String msg) {
        String n = normalizar(msg);
        int ini, fim;
        if (n.contains("manha"))      { ini = 0;  fim = 12; }
        else if (n.contains("tarde")) { ini = 12; fim = 18; }
        else if (n.contains("noite")) { ini = 18; fim = 24; }
        else return null;
        return disp.stream().filter(h -> { int hh = emMinutos(h) / 60; return hh >= ini && hh < fim; }).toList();
    }

    /** Junta horários em texto: "*09:00*, *10:00* e *11:00*". */
    private String juntarHorarios(List<String> hs) {
        if (hs.isEmpty()) return "";
        if (hs.size() == 1) return "*" + hs.get(0) + "*";
        String resto = hs.subList(0, hs.size() - 1).stream().map(h -> "*" + h + "*")
                .collect(java.util.stream.Collectors.joining(", "));
        return resto + " e *" + hs.get(hs.size() - 1) + "*";
    }

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
