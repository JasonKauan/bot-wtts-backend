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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bot de agendamento com slot-filling: a IA lê a mensagem INTEIRA e preenche
 * serviço, profissional, data e hora de uma vez — o bot só pergunta o que faltar.
 * Quem reserva (disponibilidade, conflito, limite de plano) continua determinístico.
 * Sem IA, degrada para o fluxo de menu por número.
 *
 * TODO Iteração 7: grade horária por profissional (horários ainda hardcoded).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BotService {

    private static final int MAX_TENTATIVAS = 3;

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
            iniciarSessao(telefone, tenant, clienteNome);
            return;
        }
        if ("ajuda".equals(norm)) {
            enviar(tenant, telefone,
                    "Para agendar, basta mandar *oi*! " +
                    "Para cancelar, responda *cancelar* a qualquer momento. 😊");
            return;
        }

        // 3. Sessão
        BotSession session = botSessionRepository
                .findByTelefoneAndTenantId(telefone, tenant.getId()).orElse(null);
        if (session == null) {
            // Resposta a lembrete (sem sessão ativa)
            if ("sim".equals(norm) || "s".equals(norm) || "nao".equals(norm) || "n".equals(norm)) {
                handleRespostaLembrete(telefone, norm, clienteNome, tenant);
                return;
            }
            // Primeira mensagem (não é saudação): cria a sessão e já tenta ENTENDER a frase inteira.
            session = BotSession.builder()
                    .tenantId(tenant.getId()).telefone(telefone)
                    .etapa("SERVICO").tentativas(0)
                    .ultimaInteracao(LocalDateTime.now()).build();
            botSessionRepository.save(session);
        }

        // 4. Timeout 30 min
        if (session.getUltimaInteracao().isBefore(LocalDateTime.now().minusMinutes(30))) {
            botSessionRepository.delete(session);
            enviar(tenant, telefone, "Sua sessão expirou. Mande *oi* para recomeçar! 😊");
            return;
        }

        session.setUltimaInteracao(LocalDateTime.now());

        // CONFIRMAÇÃO é à parte: espera sim/não.
        if ("CONFIRMACAO".equals(session.getEtapa())) {
            handleConfirmacao(session, mensagem, norm, telefone, clienteNome, tenant);
            return;
        }

        // ── Slot-filling ─────────────────────────────────────────────────────
        // Entende a mensagem inteira (serviço, profissional, data e hora) e só
        // pergunta o que ficar faltando.
        String slotAntes = proximoSlot(session, tenant);
        preencherSlots(session, mensagem, norm, tenant);
        String slotDepois = proximoSlot(session, tenant);

        if (slotDepois.equals(slotAntes)) {
            // Não conseguimos usar a mensagem para o que faltava → ajuda específica do passo.
            ajudarSlot(session, slotAntes, mensagem, norm, telefone, tenant);
        } else {
            session.setTentativas(0);
            askSlot(session, slotDepois, telefone, tenant);
        }
    }

    // ── Início / saudação ─────────────────────────────────────────────────────

    private BotSession iniciarSessao(String telefone, Tenant tenant, String clienteNome) {
        List<Servico> servicos = servicoRepository.findByTenantIdAndAtivoTrue(tenant.getId());
        BotSession session = BotSession.builder()
                .tenantId(tenant.getId()).telefone(telefone)
                .etapa("SERVICO").tentativas(0)
                .ultimaInteracao(LocalDateTime.now()).build();
        botSessionRepository.save(session);

        String nome = primeiroNome(clienteNome);
        if (servicos.isEmpty()) {
            enviar(tenant, telefone, "Oi" + (nome != null ? ", " + nome : "")
                    + "! 👋 Ainda não temos serviços configurados por aqui. Tente mais tarde! 😊");
        } else {
            String saudacao = aiService.redigir(
                    "Cumprimente " + (nome != null ? "o cliente " + nome : "o cliente")
                    + " que acabou de chamar o estabelecimento \"" + tenant.getNome()
                    + "\" no WhatsApp, diga que vai ajudar a agendar e pergunte qual servico ele quer.");
            if (saudacao == null)
                saudacao = "Oi" + (nome != null ? ", " + nome : "") + "! 👋 Aqui é da *" + tenant.getNome()
                        + "*, vou te ajudar a agendar 😊\n\nQual serviço você quer?";
            enviar(tenant, telefone, saudacao + "\n\n" + formatarServicos(servicos));
        }
        return session;
    }

    /** Primeiro nome (capitalizado) a partir do pushName do WhatsApp; null se vazio/numérico. */
    private String primeiroNome(String clienteNome) {
        if (clienteNome == null || clienteNome.isBlank()) return null;
        String primeiro = clienteNome.trim().split("\\s+")[0];
        if (primeiro.isBlank() || primeiro.matches(".*\\d.*")) return null;  // evita "5511..." como nome
        return primeiro.substring(0, 1).toUpperCase() + primeiro.substring(1).toLowerCase();
    }

    // ── Slot-filling: preencher ───────────────────────────────────────────────

    /** Preenche o que conseguir da mensagem: seleção do passo atual + IA da frase inteira. */
    private void preencherSlots(BotSession session, String msg, String norm, Tenant tenant) {
        List<Servico> servicos = servicoRepository.findByTenantIdAndAtivoTrue(tenant.getId());
        List<Profissional> profs = profissionalRepository.findByTenantIdAndAtivoTrue(tenant.getId());

        // (a) Seleção explícita do passo atual (número do menu, nome exato, data digitada).
        aplicarSelecao(session, msg, norm, servicos, profs, tenant);

        // (b) IA: entende a mensagem inteira e preenche o que faltar (pula números puros).
        if (aiService.ativo() && !msg.trim().matches("\\d+")) {
            AiService.Extracao ex = aiService.extrair(msg,
                    servicos.stream().map(Servico::getNome).toList(),
                    profs.stream().map(Profissional::getNome).toList(),
                    LocalDate.now());
            if (ex != null) aplicarExtracao(session, ex, servicos, profs, tenant);
        }

        botSessionRepository.save(session);
    }

    /** Seleção determinística referente ao passo que foi perguntado por último. */
    private void aplicarSelecao(BotSession s, String msg, String norm,
                                List<Servico> servicos, List<Profissional> profs, Tenant tenant) {
        switch (s.getEtapa()) {
            case "SERVICO" -> {
                if (s.getServicoEscolhido() != null) return;
                Integer op = parsearOpcao(msg, servicos.size());
                if (op == null)
                    for (int i = 0; i < servicos.size(); i++)
                        if (normalizar(servicos.get(i).getNome()).equals(norm)) { op = i + 1; break; }
                if (op != null) s.setServicoEscolhido(servicos.get(op - 1).getNome());
            }
            case "PROFISSIONAL" -> {
                if (s.getProfissionalId() != null) return;
                Integer op = parsearOpcao(msg, profs.size());
                if (op != null) {
                    Profissional p = profs.get(op - 1);
                    s.setProfissionalId(p.getId());
                    s.setProfissionalEscolhido(p.getNome());
                }
            }
            case "DATA" -> {
                if (s.getDataEscolhida() != null) return;
                LocalDate d = parsearData(norm);
                if (d != null && !d.isBefore(LocalDate.now()) && !d.isAfter(LocalDate.now().plusDays(30)))
                    s.setDataEscolhida(d);
            }
            case "HORA" -> {
                if (s.getHoraEscolhida() != null || s.getDataEscolhida() == null) return;
                List<String> disp = horariosDisponiveis(tenant, s.getDataEscolhida(), s.getProfissionalId());
                Integer op = parsearOpcao(msg, disp.size());
                if (op != null) s.setHoraEscolhida(disp.get(op - 1));
            }
        }
    }

    /** Preenche, a partir do que a IA extraiu, só os campos ainda vazios e válidos. */
    private void aplicarExtracao(BotSession s, AiService.Extracao ex,
                                 List<Servico> servicos, List<Profissional> profs, Tenant tenant) {
        if (s.getServicoEscolhido() == null && ex.servico != null)
            servicos.stream().filter(sv -> nomeBate(sv.getNome(), ex.servico)).findFirst()
                    .ifPresent(sv -> s.setServicoEscolhido(sv.getNome()));

        if (s.getProfissionalId() == null && ex.profissional != null)
            profs.stream().filter(p -> nomeBate(p.getNome(), ex.profissional)).findFirst()
                    .ifPresent(p -> { s.setProfissionalId(p.getId()); s.setProfissionalEscolhido(p.getNome()); });

        if (s.getDataEscolhida() == null && ex.data != null
                && !ex.data.isBefore(LocalDate.now()) && !ex.data.isAfter(LocalDate.now().plusDays(30)))
            s.setDataEscolhida(ex.data);

        if (s.getHoraEscolhida() == null && ex.hora != null && s.getDataEscolhida() != null
                && horariosDisponiveis(tenant, s.getDataEscolhida(), s.getProfissionalId()).contains(ex.hora))
            s.setHoraEscolhida(ex.hora);
    }

    /** Primeiro campo ainda faltando, ou CONFIRMACAO se tudo pronto. */
    private String proximoSlot(BotSession s, Tenant tenant) {
        if (s.getServicoEscolhido() == null) return "SERVICO";
        if (s.getProfissionalId() == null
                && !profissionalRepository.findByTenantIdAndAtivoTrue(tenant.getId()).isEmpty()) return "PROFISSIONAL";
        if (s.getDataEscolhida() == null) return "DATA";
        if (s.getHoraEscolhida() == null) return "HORA";
        return "CONFIRMACAO";
    }

    // ── Slot-filling: perguntar o próximo passo ───────────────────────────────

    private void askSlot(BotSession s, String slot, String telefone, Tenant tenant) {
        s.setEtapa(slot);
        botSessionRepository.save(s);
        switch (slot) {
            case "SERVICO" -> enviar(tenant, telefone,
                    "Qual serviço você quer? 😊\n\n" + formatarServicos(servicoRepository.findByTenantIdAndAtivoTrue(tenant.getId())));
            case "PROFISSIONAL" -> enviar(tenant, telefone,
                    resumoParcial(s) + "👤 Com qual profissional?\n\n" + formatarProfissionais(profissionalRepository.findByTenantIdAndAtivoTrue(tenant.getId())));
            case "DATA" -> enviar(tenant, telefone,
                    resumoParcial(s) + "📅 Pra qual dia? _(hoje, amanhã ou dd/mm)_");
            case "HORA" -> {
                List<String> disp = horariosDisponiveis(tenant, s.getDataEscolhida(), s.getProfissionalId());
                if (disp.isEmpty()) { trocarParaProximaData(s, telefone, tenant); return; }
                enviar(tenant, telefone, "🗓️ *" + formatarData(s.getDataEscolhida()) + "* — horários:\n\n" + formatarLista(disp));
            }
            case "CONFIRMACAO" -> enviar(tenant, telefone, resumoConfirmacao(s));
        }
    }

    /** A data escolhida ficou sem vaga: limpa e sugere a próxima com horário livre. */
    private void trocarParaProximaData(BotSession s, String telefone, Tenant tenant) {
        LocalDate cheia = s.getDataEscolhida();
        s.setDataEscolhida(null);
        s.setEtapa("DATA");
        botSessionRepository.save(s);
        LocalDate proxima = proximaDataComVaga(tenant, cheia.plusDays(1), s.getProfissionalId());
        String sugestao = (proxima != null)
                ? " A próxima com vaga é *" + formatarData(proxima) + "* — é só me mandar ela (ou outra) 😊"
                : " Me diz outra data, por favor.";
        enviar(tenant, telefone, "Pra *" + formatarData(cheia) + "* não tenho mais horário 😕" + sugestao);
    }

    // ── Slot-filling: ajuda quando não entendeu o passo atual ─────────────────

    private void ajudarSlot(BotSession s, String slot, String msg, String norm, String telefone, Tenant tenant) {
        switch (slot) {
            case "SERVICO" -> erroComTentativa(s, telefone, tenant,
                    "Me diz qual serviço você quer (pode ser pelo número ou pelo nome) 😊\n\n"
                    + formatarServicos(servicoRepository.findByTenantIdAndAtivoTrue(tenant.getId())));
            case "PROFISSIONAL" -> erroComTentativa(s, telefone, tenant,
                    "Com qual profissional você prefere? 😊\n\n"
                    + formatarProfissionais(profissionalRepository.findByTenantIdAndAtivoTrue(tenant.getId())));
            case "DATA" -> ajudarData(s, norm, telefone, tenant);
            case "HORA" -> ajudarHora(s, msg, telefone, tenant);
        }
    }

    private void ajudarData(BotSession s, String norm, String telefone, Tenant tenant) {
        LocalDate d = parsearData(norm);
        if (d == null) d = aiService.interpretarData(norm, LocalDate.now());
        if (d != null && d.isBefore(LocalDate.now())) {
            erroComTentativa(s, telefone, tenant, "Essa data já passou 😅 Qual outra você prefere?");
        } else if (d != null && d.isAfter(LocalDate.now().plusDays(30))) {
            erroComTentativa(s, telefone, tenant, "Só agendo com até 30 dias de antecedência. Qual outra data?");
        } else {
            erroComTentativa(s, telefone, tenant,
                    "Não consegui entender a data 😅 Pode ser *hoje*, *amanhã*, um dia da semana ou *dd/mm* (ex: 30/06).");
        }
    }

    private void ajudarHora(BotSession s, String msg, String telefone, Tenant tenant) {
        List<String> disp = horariosDisponiveis(tenant, s.getDataEscolhida(), s.getProfissionalId());
        if (disp.isEmpty()) { trocarParaProximaData(s, telefone, tenant); return; }

        // Tentou um horário específico que não está livre? Sugere o(s) mais próximo(s) da lista real.
        String desejado = aiService.interpretarHorario(msg);
        if (desejado != null && !disp.contains(desejado)) {
            List<String> sug = sugerirProximos(desejado, disp, 2);
            if (!sug.isEmpty()) {
                erroComTentativa(s, telefone, tenant, "Poxa, *" + desejado + "* não tá livre 😕 " + (sug.size() == 1
                        ? "Mas tenho *" + sug.get(0) + "* — serve?"
                        : "Mas tenho *" + sug.get(0) + "* e *" + sug.get(1) + "*, qual prefere?"));
                return;
            }
        }

        // Mencionou um período (manhã/tarde/noite)?
        List<String> doPeriodo = filtrarPorPeriodo(disp, msg);
        if (doPeriodo != null) {
            erroComTentativa(s, telefone, tenant, doPeriodo.isEmpty()
                    ? "Nesse período não tenho vaga nesse dia 😕 Mas tenho " + juntarHorarios(disp) + " — qual rola?"
                    : "Tenho " + juntarHorarios(doPeriodo) + " — qual fica bom? 😊");
            return;
        }

        erroComTentativa(s, telefone, tenant, "Tenho estes horários — qual fica melhor pra você? 😊\n\n" + formatarLista(disp));
    }

    // ── Confirmação ───────────────────────────────────────────────────────────

    private void handleConfirmacao(BotSession session, String msg, String norm, String telefone,
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

            // Re-checa conflito (por profissional): alguém pode ter reservado durante a conversa.
            if (slotOcupado(tenant.getId(), session.getProfissionalId(), dataHora)) {
                session.setHoraEscolhida(null);
                session.setEtapa("HORA");
                session.setTentativas(0);
                botSessionRepository.save(session);
                List<String> disp = horariosDisponiveis(tenant, session.getDataEscolhida(), session.getProfissionalId());
                enviar(tenant, telefone, disp.isEmpty()
                        ? "Ihh, esse horário acabou de ser reservado 😕 E não sobrou mais nada nesse dia. Qual outra data?"
                        : "Ihh, esse horário acabou de ser reservado 😕 Mas ainda tenho " + juntarHorarios(disp) + " — qual prefere?");
                return;
            }

            Agendamento ag = Agendamento.builder()
                    .tenantId(tenant.getId())
                    .clienteNome(clienteNome != null ? clienteNome : telefone)
                    .clienteTelefone(telefone)
                    .servico(session.getServicoEscolhido())
                    .profissional(session.getProfissionalEscolhido())
                    .profissionalId(session.getProfissionalId())
                    .dataHora(dataHora).status("CONFIRMADO").build();
            agendamentoRepository.save(ag);
            String servicoOk = session.getServicoEscolhido();
            botSessionRepository.delete(session);
            log.info("[{}] Agendamento salvo — {}", tenant.getId(), telefone);
            String ok = aiService.redigir("O agendamento de *" + servicoOk
                    + "* foi confirmado com sucesso. Agradeca rapidamente e diga que espera o cliente.");
            enviar(tenant, telefone, ok != null ? ok : "✅ Agendado! Até lá 😊");

        } else if ("nao".equals(norm) || "n".equals(norm)) {
            botSessionRepository.delete(session);
            enviar(tenant, telefone, "Tudo bem! Mande *oi* para recomeçar. 😊");
        } else {
            // Não foi sim/não: pode ser uma correção ("não, queria 16h", "muda pra terça").
            // Tenta re-extrair e ajustar; se ajustou algo, re-pergunta o que faltar ou re-confirma.
            if (tentarEditarNaConfirmacao(session, msg, tenant)) {
                session.setTentativas(0);
                askSlot(session, proximoSlot(session, tenant), telefone, tenant);
                return;
            }
            erroComTentativa(session, telefone, tenant,
                    "Só pra confirmar: responda *sim* 👍 pra agendar, ou *não* pra cancelar.\n" +
                    "_(ou me diga o que mudar, ex.: \"muda pra 16h\" ou \"na verdade quero terça\")_");
        }
    }

    /**
     * Cliente mexeu em algo no passo de confirmação. Re-extrai a frase e SOBRESCREVE os campos
     * citados (serviço/profissional/data/hora). Devolve true se algo mudou.
     */
    private boolean tentarEditarNaConfirmacao(BotSession s, String msg, Tenant tenant) {
        if (!aiService.ativo() || msg.trim().matches("\\d+")) return false;

        List<Servico> servicos = servicoRepository.findByTenantIdAndAtivoTrue(tenant.getId());
        List<Profissional> profs = profissionalRepository.findByTenantIdAndAtivoTrue(tenant.getId());
        AiService.Extracao ex = aiService.extrair(msg,
                servicos.stream().map(Servico::getNome).toList(),
                profs.stream().map(Profissional::getNome).toList(),
                LocalDate.now());
        if (ex == null) return false;

        boolean mudou = false;

        if (ex.servico != null) {
            var match = servicos.stream().filter(sv -> nomeBate(sv.getNome(), ex.servico)).findFirst();
            if (match.isPresent() && !match.get().getNome().equals(s.getServicoEscolhido())) {
                s.setServicoEscolhido(match.get().getNome());
                mudou = true;
            }
        }
        if (ex.profissional != null) {
            var match = profs.stream().filter(p -> nomeBate(p.getNome(), ex.profissional)).findFirst();
            if (match.isPresent() && !match.get().getId().equals(s.getProfissionalId())) {
                s.setProfissionalId(match.get().getId());
                s.setProfissionalEscolhido(match.get().getNome());
                mudou = true;
            }
        }
        if (ex.data != null && !ex.data.isBefore(LocalDate.now()) && !ex.data.isAfter(LocalDate.now().plusDays(30))
                && !ex.data.equals(s.getDataEscolhida())) {
            s.setDataEscolhida(ex.data);
            mudou = true;
            // a hora atual pode não existir mais na nova data → limpa pra reperguntar
            if (s.getHoraEscolhida() != null && !horariosDisponiveis(tenant, ex.data, s.getProfissionalId()).contains(s.getHoraEscolhida()))
                s.setHoraEscolhida(null);
        }
        if (ex.hora != null && s.getDataEscolhida() != null && !ex.hora.equals(s.getHoraEscolhida())) {
            if (horariosDisponiveis(tenant, s.getDataEscolhida(), s.getProfissionalId()).contains(ex.hora)) {
                s.setHoraEscolhida(ex.hora);   // hora pedida está livre
            } else {
                s.setHoraEscolhida(null);      // pediu hora indisponível → volta pro passo HORA
            }
            mudou = true;
        }

        return mudou;
    }

    private void handleRespostaLembrete(String telefone, String norm, String clienteNome, Tenant tenant) {
        LocalDateTime agora = LocalDateTime.now();
        var agOpt = agendamentoRepository.findTopByClienteTelefoneAndStatusAndDataHoraBetweenOrderByDataHora(
                telefone, "CONFIRMADO", agora, agora.plusHours(26));

        if (agOpt.isEmpty()) {
            // Sem agendamento próximo — tratar como início de conversa
            iniciarSessao(telefone, tenant, clienteNome);
            return;
        }

        var ag = agOpt.get();
        if ("sim".equals(norm) || "s".equals(norm)) {
            enviar(tenant, telefone,
                    "✅ Ótimo! Te esperamos em *"
                    + ag.getDataHora().format(DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm"))
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

    private List<String> horariosDisponiveis(Tenant tenant, LocalDate data, UUID profissionalId) {
        if (!diaFunciona(tenant, data)) return List.of();   // estabelecimento fechado nesse dia da semana
        LocalDateTime agora = LocalDateTime.now();
        return gerarGrade(tenant).stream()
                .filter(h -> {
                    LocalDateTime slot = LocalDateTime.of(data, LocalTime.parse(h));
                    // não oferecer horário que já passou (relevante quando a data é hoje)
                    return slot.isAfter(agora) && !slotOcupado(tenant.getId(), profissionalId, slot);
                })
                .toList();
    }

    /**
     * Horário ocupado? Com profissional definido, o conflito é POR profissional (dois profissionais
     * podem atender no mesmo horário). Sem profissional (tenant sem equipe), conflito por estabelecimento.
     */
    private boolean slotOcupado(UUID tenantId, UUID profissionalId, LocalDateTime slot) {
        return profissionalId != null
                ? agendamentoRepository.existsByTenantIdAndProfissionalIdAndDataHoraAndStatus(tenantId, profissionalId, slot, "CONFIRMADO")
                : agendamentoRepository.existsByTenantIdAndDataHoraAndStatus(tenantId, slot, "CONFIRMADO");
    }

    /** Gera a grade do dia: de abertura a fechamento, de intervalo em intervalo, pulando o almoço. */
    private List<String> gerarGrade(Tenant t) {
        int intervalo = t.getIntervaloMinutos() > 0 ? t.getIntervaloMinutos() : 60;
        int inicioMin = t.getHorarioAbertura() * 60;
        int fimMin    = t.getHorarioFechamento() * 60;
        Integer almIni = t.getAlmocoInicio() != null ? t.getAlmocoInicio() * 60 : null;
        Integer almFim = t.getAlmocoFim()    != null ? t.getAlmocoFim()    * 60 : null;

        List<String> slots = new ArrayList<>();
        for (int m = inicioMin; m < fimMin; m += intervalo) {
            // pula slots dentro da janela de almoço [inicio, fim)
            if (almIni != null && almFim != null && m >= almIni && m < almFim) continue;
            slots.add(String.format("%02d:%02d", m / 60, m % 60));
        }
        return slots;
    }

    /** O estabelecimento funciona nesse dia da semana? (dias ISO 1=seg..7=dom em diasFuncionamento) */
    private boolean diaFunciona(Tenant t, LocalDate data) {
        String dias = t.getDiasFuncionamento();
        if (dias == null || dias.isBlank()) return true; // sem config = todos os dias
        String alvo = String.valueOf(data.getDayOfWeek().getValue());
        for (String p : dias.split(",")) {
            if (p.trim().equals(alvo)) return true;
        }
        return false;
    }

    /** Prefixo com o que já sabemos: "*Corte* com *Raphael* 👍\n\n". Vazio se nada definido. */
    private String resumoParcial(BotSession s) {
        String serv = s.getServicoEscolhido();
        String prof = s.getProfissionalEscolhido();
        if (serv == null && prof == null) return "";
        StringBuilder sb = new StringBuilder();
        if (serv != null) sb.append("*").append(serv).append("*");
        if (prof != null) sb.append(serv != null ? " com *" : "*").append(prof).append("*");
        return sb.append(" 👍\n\n").toString();
    }

    private String resumoConfirmacao(BotSession s) {
        String profTexto = s.getProfissionalEscolhido() != null
                ? "\n👤 *Profissional:* " + s.getProfissionalEscolhido() : "";
        return "Confirmar agendamento?\n\n✂️ *Serviço:* " + s.getServicoEscolhido()
                + profTexto
                + "\n📅 *Data:* " + formatarData(s.getDataEscolhida())
                + "\n⏰ *Horário:* " + s.getHoraEscolhida()
                + "\n\nPosso confirmar? Responda *sim* 👍 ou *não*.";
    }

    /** Dois nomes "batem" se forem iguais ou um contiver o outro (ex.: "Raphael" ~ "Raphael Silva"). */
    private boolean nomeBate(String a, String b) {
        if (a == null || b == null) return false;
        String x = normalizar(a), y = normalizar(b);
        return x.equals(y) || x.contains(y) || y.contains(x);
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
    private LocalDate proximaDataComVaga(Tenant tenant, LocalDate inicio, UUID profissionalId) {
        LocalDate limite = LocalDate.now().plusDays(30);
        for (LocalDate d = inicio; !d.isAfter(limite); d = d.plusDays(1)) {
            if (!horariosDisponiveis(tenant, d, profissionalId).isEmpty()) return d;
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
