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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Random RND = new Random();

    /** Dias da semana (ISO 1=seg..7=dom), com abreviações comuns do WhatsApp. */
    private static final Map<String, Integer> DIA_SEMANA = Map.ofEntries(
            Map.entry("segunda", 1), Map.entry("seg", 1),
            Map.entry("terca", 2),   Map.entry("ter", 2),
            Map.entry("quarta", 3),  Map.entry("qua", 3),
            Map.entry("quinta", 4),  Map.entry("qui", 4),
            Map.entry("sexta", 5),   Map.entry("sex", 5),
            Map.entry("sabado", 6),  Map.entry("sab", 6),
            Map.entry("domingo", 7), Map.entry("dom", 7));

    private final BotSessionRepository    botSessionRepository;
    private final AgendamentoRepository   agendamentoRepository;
    private final ServicoRepository       servicoRepository;
    private final ProfissionalRepository  profissionalRepository;
    private final DisponibilidadeService  disponibilidadeService;
    private final EvolutionApiService     evolutionApiService;
    private final PlanoService            planoService;
    private final AiService               aiService;

    @Async
    @Transactional
    public void processMessage(String telefone, String mensagem, String clienteNome, Tenant tenant) {
        log.info("[{}] Mensagem de {}: {}", tenant.getId(), telefone, mensagem);

        // 0. Modo ATENDENTE HUMANO: o bot fica fora da conversa por 1h (renova a cada mensagem).
        // Vem antes de tudo — inclusive do "fora do horário" — pra não atrapalhar a conversa humana.
        BotSession humano = botSessionRepository.findByTelefoneAndTenantId(telefone, tenant.getId()).orElse(null);
        if (humano != null && "HUMANO".equals(humano.getEtapa())) {
            String n0 = normalizar(mensagem);
            if ("menu".equals(n0)) {
                botSessionRepository.delete(humano);   // cliente pediu o bot de volta
                iniciarSessao(telefone, tenant, clienteNome);
                return;
            }
            if (humano.getUltimaInteracao().isAfter(LocalDateTime.now().minusMinutes(60))) {
                humano.setUltimaInteracao(LocalDateTime.now());
                botSessionRepository.save(humano);
                return;   // silêncio: humano no comando
            }
            botSessionRepository.delete(humano);       // expirou — volta ao fluxo normal, sem alarde
        }

        // 1. Horário de atendimento (usa config do tenant)
        int hora = LocalDateTime.now().getHour();
        if (hora < tenant.getHorarioAbertura() || hora >= tenant.getHorarioFechamento()) {
            int ab = tenant.getHorarioAbertura(), fe = tenant.getHorarioFechamento();
            String ai = aiService.redigir("Estamos fora do horario de atendimento agora. Atendemos das "
                    + ab + "h as " + fe + "h. Avise o cliente com gentileza e peca pra mandar *oi* dentro desse horario.");
            enviar(tenant, telefone, ai != null ? ai : escolher(
                    "Opa! Agora estamos fora do horário 😴 Atendemos das *" + ab + "h às " + fe + "h* — me chama nesse horário que te ajudo! 👋",
                    "No momento estamos fechados! Funcionamos das *" + ab + "h às " + fe + "h*. Manda *oi* nesse horário pra agendar 😊"));
            return;
        }

        String norm = normalizar(mensagem);

        // 2. Palavras-chave globais (ordem importa: intenções específicas antes das genéricas —
        // "remarcar meu horário" contém "meu horário" e não pode cair na listagem)
        // Atendente humano: bot sai da frente e avisa o dono.
        if (isAtendente(norm)) {
            iniciarAtendimentoHumano(telefone, clienteNome, tenant);
            return;
        }
        // Remarcar: mantém serviço/profissional e só pergunta o novo dia/horário.
        if (isRemarcar(norm)) {
            iniciarRemarcacao(telefone, tenant);
            return;
        }
        // Cancelar AGENDAMENTO: busca o próximo confirmado do número e pede confirmação.
        if (isCancelarAgendamento(norm)) {
            iniciarCancelamento(telefone, tenant);
            return;
        }
        // "Meus horários": lista o que o cliente tem marcado neste estabelecimento.
        if (isMeusHorarios(norm)) {
            mostrarMeusHorarios(telefone, tenant);
            return;
        }
        // "De novo": repete o último agendamento (serviço/profissional) e só pergunta o dia/hora.
        if (isDeNovo(norm)) {
            iniciarDeNovo(telefone, tenant);
            return;
        }
        if (isEncerrar(norm)) {
            botSessionRepository.findByTelefoneAndTenantId(telefone, tenant.getId())
                    .ifPresent(botSessionRepository::delete);
            enviar(tenant, telefone, textoDespedida());
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
                    "É bem simples! 😊 Mande *oi* pra ver o menu.\n\n" +
                    "Você também pode falar direto comigo:\n" +
                    "• _\"quero corte amanhã às 15h\"_ pra agendar\n" +
                    "• *meus horários* pra ver o que tem marcado\n" +
                    "• *remarcar* ou *cancelar* pra mudar algo");
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

        // MENU de boas-vindas: opções 1-4; texto livre cai no slot-filling (atalho).
        if ("MENU".equals(session.getEtapa())) {
            if (handleMenu(session, norm, telefone, tenant)) return;
            session.setEtapa("SERVICO"); // não era opção do menu → trata como pedido de agendamento
        }

        // CANCELAMENTO de agendamento: espera sim/não.
        if ("CANCELAMENTO".equals(session.getEtapa())) {
            handleCancelamento(session, norm, telefone, tenant);
            return;
        }

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
            // Menu de boas-vindas: guia quem não sabe o que fazer; texto livre continua
            // funcionando como atalho (cai no slot-filling e pula o menu).
            session.setEtapa("MENU");
            botSessionRepository.save(session);

            String saudacao = aiService.redigir(
                    "Cumprimente " + (nome != null ? "o cliente " + nome : "o cliente")
                    + " que acabou de chamar o estabelecimento \"" + tenant.getNome()
                    + "\" no WhatsApp e pergunte, em UMA frase, como pode ajudar.");
            if (saudacao == null) {
                String ola = "Oi" + (nome != null ? ", " + nome : "") + "!";
                saudacao = escolher(
                        ola + " 👋 Aqui é da *" + tenant.getNome() + "*. Como posso ajudar? 😊",
                        ola + " 😊 Seja bem-vindo(a) à *" + tenant.getNome() + "*! O que você precisa?",
                        ola + " 👋 Que bom te ver por aqui! Em que posso ajudar?");
            }
            enviar(tenant, telefone, saudacao + "\n\n" + MENU_OPCOES);
        }
        return session;
    }

    private static final String MENU_OPCOES =
            "1️⃣ *Agendar* um horário\n" +
            "2️⃣ *Meus horários*\n" +
            "3️⃣ *Serviços e preços*\n" +
            "4️⃣ *Remarcar ou cancelar*\n\n" +
            "Responda o número — ou já me diga direto o que precisa 😊";

    /** Etapa MENU: trata as opções 1-4; devolve false se a mensagem não é opção (→ vira agendamento). */
    private boolean handleMenu(BotSession s, String norm, String telefone, Tenant tenant) {
        if ("1".equals(norm) || "agendar".equals(norm) || "marcar".equals(norm)) {
            s.setTentativas(0);
            askSlot(s, proximoSlot(s, tenant), telefone, tenant);
            return true;
        }
        if ("2".equals(norm)) {
            mostrarMeusHorarios(telefone, tenant);   // mantém o menu aberto
            return true;
        }
        if ("3".equals(norm) || norm.contains("preco") || norm.contains("valor") || norm.contains("lista")) {
            List<Servico> servicos = servicoRepository.findByTenantIdAndAtivoTrue(tenant.getId());
            s.setEtapa("SERVICO");
            botSessionRepository.save(s);
            enviar(tenant, telefone, "💈 *Nossos serviços:*\n\n" + formatarServicos(servicos)
                    + "\n\nQuer agendar? Me diz o número ou o nome do serviço 😊");
            return true;
        }
        if ("4".equals(norm)) {
            enviar(tenant, telefone, "Quer *remarcar* ou *cancelar*? É só responder uma das duas palavras 😊");
            return true;
        }
        return false;
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
                List<String> disp = dispDaSessao(tenant, s.getDataEscolhida(), s);
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
                && dispDaSessao(tenant, s.getDataEscolhida(), s).contains(ex.hora))
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
                    escolher("Qual serviço você quer? 😊", "Me diz o que você quer fazer:", "Pra começar, qual serviço? 😊")
                    + "\n\n" + formatarServicos(servicoRepository.findByTenantIdAndAtivoTrue(tenant.getId())));
            case "PROFISSIONAL" -> enviar(tenant, telefone,
                    resumoParcial(s) + "👤 Com qual profissional?\n\n" + formatarProfissionais(profissionalRepository.findByTenantIdAndAtivoTrue(tenant.getId())));
            case "DATA" -> enviar(tenant, telefone,
                    resumoParcial(s) + "📅 Pra qual dia? _(hoje, amanhã ou dd/mm)_");
            case "HORA" -> {
                List<String> disp = dispDaSessao(tenant, s.getDataEscolhida(), s);
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
        LocalDate proxima = proximaDataComVaga(tenant, cheia.plusDays(1), s);
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
        List<String> disp = dispDaSessao(tenant, s.getDataEscolhida(), s);
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
            // Limite de plano só vale pra agendamento NOVO (remarcar apenas move um existente).
            if (session.getRemarcandoId() == null) {
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
            }
            LocalDateTime dataHora = LocalDateTime.of(session.getDataEscolhida(), LocalTime.parse(session.getHoraEscolhida()));
            int duracao = disponibilidadeService.duracaoServico(tenant, session.getServicoEscolhido());

            // Re-checa conflito (por profissional, ciente de duração): alguém pode ter reservado durante a conversa.
            if (disponibilidadeService.conflita(tenant, session.getProfissionalId(), dataHora, duracao)) {
                session.setHoraEscolhida(null);
                session.setEtapa("HORA");
                session.setTentativas(0);
                botSessionRepository.save(session);
                List<String> disp = dispDaSessao(tenant, session.getDataEscolhida(), session);
                enviar(tenant, telefone, disp.isEmpty()
                        ? "Ihh, esse horário acabou de ser reservado 😕 E não sobrou mais nada nesse dia. Qual outra data?"
                        : "Ihh, esse horário acabou de ser reservado 😕 Mas ainda tenho " + juntarHorarios(disp) + " — qual prefere?");
                return;
            }

            boolean fila = tenant.isAprovacaoManual();

            // REMARCAÇÃO: move o agendamento existente em vez de criar um novo.
            if (session.getRemarcandoId() != null) {
                var antigoOpt = agendamentoRepository.findById(session.getRemarcandoId());
                botSessionRepository.delete(session);
                if (antigoOpt.isEmpty() || !antigoOpt.get().getTenantId().equals(tenant.getId())) {
                    enviar(tenant, telefone, "Hmm, não achei mais o agendamento original 😕 Manda *oi* pra marcar um novo.");
                    return;
                }
                Agendamento antigo = antigoOpt.get();
                antigo.setDataHora(dataHora);
                antigo.setStatus(fila ? "PENDENTE" : "CONFIRMADO");
                antigo.setLembreteEnviado(false);      // novo horário → lembretes valem de novo
                antigo.setLembreteDiaEnviado(false);
                agendamentoRepository.save(antigo);
                log.info("[{}] Agendamento {} remarcado para {} ({})", tenant.getId(), antigo.getId(), dataHora, antigo.getStatus());
                enviar(tenant, telefone, fila
                        ? "📝 Recebi sua remarcação de *" + antigo.getServico() + "* pra *" + formatarDataHora(dataHora) + "*! Vou confirmar e te aviso 👍"
                        : "🔄 Pronto, remarcado! Seu *" + antigo.getServico() + "* agora é em *" + formatarDataHora(dataHora) + "*. Te esperamos 😊");
                if (fila) avisarDonoNovoPedido(tenant, antigo);
                else avisarDono(tenant, "🔄 *" + antigo.getClienteNome() + "* remarcou *" + antigo.getServico()
                        + "* pra *" + formatarDataHora(dataHora) + "*.");
                return;
            }

            // Fila de aprovação: PENDENTE espera o dono aceitar; senão confirma na hora.
            Agendamento ag = Agendamento.builder()
                    .tenantId(tenant.getId())
                    .clienteNome(clienteNome != null ? clienteNome : telefone)
                    .clienteTelefone(telefone)
                    .servico(session.getServicoEscolhido())
                    .profissional(session.getProfissionalEscolhido())
                    .profissionalId(session.getProfissionalId())
                    .duracaoMinutos(duracao)
                    .dataHora(dataHora).status(fila ? "PENDENTE" : "CONFIRMADO").build();
            agendamentoRepository.save(ag);
            String servicoOk = session.getServicoEscolhido();
            botSessionRepository.delete(session);
            log.info("[{}] Agendamento salvo ({}) — {}", tenant.getId(), ag.getStatus(), telefone);

            if (fila) {
                enviar(tenant, telefone, escolher(
                        "📝 Recebi seu pedido de *" + servicoOk + "*! Vou confirmar com o estabelecimento e já te aviso por aqui 👍",
                        "📝 Anotado! Seu pedido de *" + servicoOk + "* foi enviado pra confirmação — te aviso assim que aprovarem 😊"));
                avisarDonoNovoPedido(tenant, ag);
            } else {
                String ok = aiService.redigir("O agendamento de *" + servicoOk
                        + "* foi confirmado com sucesso. Agradeca rapidamente e diga que espera o cliente.");
                if (ok == null) ok = escolher(
                        "✅ Prontinho, agendado! Te espero 😊",
                        "✅ Fechou! Tá marcado. Até lá! 😊",
                        "✅ Agendamento confirmado! A gente se vê 😉");
                enviar(tenant, telefone, ok + "\n\n" + escolher(
                        "🔔 Ah, e não se preocupa em esquecer: eu te lembro no dia! 😉",
                        "🔔 Pode deixar que eu te mando um lembrete antes do horário 😉"));
            }

        } else if ("nao".equals(norm) || "n".equals(norm)) {
            botSessionRepository.delete(session);
            enviar(tenant, telefone, textoDespedida());
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
            if (s.getHoraEscolhida() != null && !dispDaSessao(tenant, ex.data, s).contains(s.getHoraEscolhida()))
                s.setHoraEscolhida(null);
        }
        if (ex.hora != null && s.getDataEscolhida() != null && !ex.hora.equals(s.getHoraEscolhida())) {
            if (dispDaSessao(tenant, s.getDataEscolhida(), s).contains(ex.hora)) {
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
        var agOpt = agendamentoRepository.findTopByTenantIdAndClienteTelefoneAndStatusAndDataHoraBetweenOrderByDataHora(
                tenant.getId(), telefone, "CONFIRMADO", agora, agora.plusHours(26));

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
            avisarDono(tenant, "❌ *" + ag.getClienteNome() + "* cancelou (respondendo o lembrete): *"
                    + ag.getServico() + "* — " + formatarDataHora(ag.getDataHora()) + ".");
        }
    }

    // ── Cancelamento de agendamento ────────────────────────────────────────────

    /** Acha o próximo agendamento confirmado do número e pede confirmação de cancelamento. */
    private void iniciarCancelamento(String telefone, Tenant tenant) {
        var agOpt = agendamentoRepository.findTopByTenantIdAndClienteTelefoneAndStatusInAndDataHoraAfterOrderByCriadoEmDesc(
                tenant.getId(), telefone, List.of("CONFIRMADO", "PENDENTE"), LocalDateTime.now());
        BotSession existente = botSessionRepository.findByTelefoneAndTenantId(telefone, tenant.getId()).orElse(null);

        if (agOpt.isEmpty()) {
            if (existente != null) botSessionRepository.delete(existente);
            enviar(tenant, telefone,
                    "Não encontrei nenhum agendamento ativo no seu número 🤔\nSe quiser marcar um, é só mandar *oi*! 😊");
            return;
        }

        Agendamento ag = agOpt.get();
        if (!podeMexer(tenant, ag)) {
            enviar(tenant, telefone, msgAntecedencia(tenant, ag, "cancelamentos são aceitos"));
            return;
        }
        BotSession session = (existente != null) ? existente
                : BotSession.builder().tenantId(tenant.getId()).telefone(telefone).build();
        session.setEtapa("CANCELAMENTO");
        session.setTentativas(0);
        session.setUltimaInteracao(LocalDateTime.now());
        botSessionRepository.save(session);

        String prof = ag.getProfissional() != null ? " com *" + ag.getProfissional() + "*" : "";
        enviar(tenant, telefone,
                "Encontrei seu agendamento:\n\n✂️ *" + ag.getServico() + "*" + prof
                + "\n📅 *" + formatarDataHora(ag.getDataHora()) + "*"
                + "\n\nQuer mesmo cancelar? Responda *sim* pra cancelar ou *não* pra manter. 😊");
    }

    /** Modo atendente: bot silencia 1h pra esse número e avisa o dono no WhatsApp. */
    private void iniciarAtendimentoHumano(String telefone, String clienteNome, Tenant tenant) {
        BotSession session = botSessionRepository.findByTelefoneAndTenantId(telefone, tenant.getId())
                .orElse(BotSession.builder().tenantId(tenant.getId()).telefone(telefone).build());
        session.setEtapa("HUMANO");
        session.setTentativas(0);
        session.setUltimaInteracao(LocalDateTime.now());
        botSessionRepository.save(session);

        enviar(tenant, telefone,
                "🙋 Combinado! Já avisei o pessoal aqui — assim que possível alguém te responde por esta mesma conversa.\n\n"
                + "_(Pra voltar a falar comigo, é só mandar *menu*.)_");

        String donoFone = tenant.getTelefoneWhatsapp();
        if (donoFone != null && !donoFone.isBlank()) {
            try {
                String nome = clienteNome != null && !clienteNome.isBlank() ? clienteNome : telefone;
                enviar(tenant, donoFone,
                        "🙋 *" + nome + "* (" + telefone + ") quer falar com uma pessoa!\n\n"
                        + "Responda ele direto por aqui — o bot vai ficar fora dessa conversa por *1 hora*.");
            } catch (Exception e) {
                log.warn("[{}] Falha ao avisar o dono do pedido de atendente: {}", tenant.getId(), e.getMessage());
            }
        }
    }

    /** "Meus horários": lista os agendamentos ativos (pendentes+confirmados) do cliente neste tenant. */
    private void mostrarMeusHorarios(String telefone, Tenant tenant) {
        List<Agendamento> ativos = agendamentoRepository
                .findByTenantIdAndClienteTelefoneAndStatusInAndDataHoraAfterOrderByDataHora(
                        tenant.getId(), telefone, List.of("CONFIRMADO", "PENDENTE"), LocalDateTime.now());
        if (ativos.isEmpty()) {
            enviar(tenant, telefone, "Você não tem nenhum horário marcado por aqui 🤔\nQuer agendar? É só mandar *oi*! 😊");
            return;
        }
        StringBuilder sb = new StringBuilder("📅 *Seus horários:*\n\n");
        for (Agendamento a : ativos) {
            sb.append("• *").append(a.getServico()).append("*");
            if (a.getProfissional() != null) sb.append(" com ").append(a.getProfissional());
            sb.append(" — ").append(formatarDataHora(a.getDataHora()));
            if ("PENDENTE".equals(a.getStatus())) sb.append(" _(aguardando confirmação)_");
            sb.append("\n");
        }
        sb.append("\nPra mudar, responda *remarcar* ou *cancelar*. 😊");
        enviar(tenant, telefone, sb.toString());
    }

    /** "De novo": repete serviço/profissional do último agendamento do cliente e pergunta só o dia/hora. */
    private void iniciarDeNovo(String telefone, Tenant tenant) {
        var ultimoOpt = agendamentoRepository.findTopByTenantIdAndClienteTelefoneOrderByCriadoEmDesc(
                tenant.getId(), telefone);
        BotSession existente = botSessionRepository.findByTelefoneAndTenantId(telefone, tenant.getId()).orElse(null);

        // Sem histórico, ou o serviço saiu do catálogo → fluxo normal do zero.
        List<Servico> servicos = servicoRepository.findByTenantIdAndAtivoTrue(tenant.getId());
        var servicoAindaExiste = ultimoOpt.isPresent()
                && servicos.stream().anyMatch(sv -> sv.getNome().equals(ultimoOpt.get().getServico()));
        if (ultimoOpt.isEmpty() || !servicoAindaExiste) {
            if (existente != null) botSessionRepository.delete(existente);
            iniciarSessao(telefone, tenant, null);
            return;
        }

        Agendamento ultimo = ultimoOpt.get();
        // Profissional só é reaproveitado se ainda estiver ativo.
        Profissional prof = ultimo.getProfissionalId() == null ? null
                : profissionalRepository.findByTenantIdAndAtivoTrue(tenant.getId()).stream()
                        .filter(p -> p.getId().equals(ultimo.getProfissionalId())).findFirst().orElse(null);

        BotSession session = (existente != null) ? existente
                : BotSession.builder().tenantId(tenant.getId()).telefone(telefone).build();
        session.setServicoEscolhido(ultimo.getServico());
        session.setProfissionalId(prof != null ? prof.getId() : null);
        session.setProfissionalEscolhido(prof != null ? prof.getNome() : null);
        session.setDataEscolhida(null);
        session.setHoraEscolhida(null);
        session.setRemarcandoId(null);   // é um agendamento NOVO, não remarcação
        session.setTentativas(0);
        session.setUltimaInteracao(LocalDateTime.now());
        session.setEtapa(proximoSlot(session, tenant));
        botSessionRepository.save(session);

        String comQuem = prof != null ? " com *" + prof.getNome() + "*" : "";
        enviar(tenant, telefone, "🔁 Fechado, igual da última vez: *" + ultimo.getServico() + "*" + comQuem + "!");
        askSlot(session, proximoSlot(session, tenant), telefone, tenant);
    }

    /** Inicia a remarcação: mantém serviço/profissional do último agendamento e pergunta novo dia/horário. */
    private void iniciarRemarcacao(String telefone, Tenant tenant) {
        var agOpt = agendamentoRepository.findTopByTenantIdAndClienteTelefoneAndStatusInAndDataHoraAfterOrderByCriadoEmDesc(
                tenant.getId(), telefone, List.of("CONFIRMADO", "PENDENTE"), LocalDateTime.now());
        BotSession existente = botSessionRepository.findByTelefoneAndTenantId(telefone, tenant.getId()).orElse(null);

        if (agOpt.isEmpty()) {
            if (existente != null) botSessionRepository.delete(existente);
            enviar(tenant, telefone, "Não encontrei um agendamento ativo pra remarcar 🤔\nSe quiser marcar um novo, manda *oi*! 😊");
            return;
        }

        Agendamento ag = agOpt.get();
        if (!podeMexer(tenant, ag)) {
            enviar(tenant, telefone, msgAntecedencia(tenant, ag, "remarcações são aceitas"));
            return;
        }
        BotSession session = (existente != null) ? existente
                : BotSession.builder().tenantId(tenant.getId()).telefone(telefone).build();
        // mantém serviço/profissional; zera dia/hora; marca que é remarcação
        session.setServicoEscolhido(ag.getServico());
        session.setProfissionalId(ag.getProfissionalId());
        session.setProfissionalEscolhido(ag.getProfissional());
        session.setDataEscolhida(null);
        session.setHoraEscolhida(null);
        session.setRemarcandoId(ag.getId());
        session.setTentativas(0);
        session.setUltimaInteracao(LocalDateTime.now());
        session.setEtapa(proximoSlot(session, tenant));
        botSessionRepository.save(session);

        enviar(tenant, telefone, "🔄 Vamos remarcar seu *" + ag.getServico() + "* (estava em *"
                + formatarDataHora(ag.getDataHora()) + "*).");
        askSlot(session, proximoSlot(session, tenant), telefone, tenant);
    }

    /** Trata o sim/não do cancelamento (re-busca o agendamento na hora de cancelar). */
    private void handleCancelamento(BotSession session, String norm, String telefone, Tenant tenant) {
        // IA interpreta confirmação natural ("pode cancelar", "deixa", "não quero mais")
        if (!"sim".equals(norm) && !"s".equals(norm) && !"nao".equals(norm) && !"n".equals(norm)) {
            int ai = aiService.escolherOpcao(norm,
                    List.of("Sim, cancelar o agendamento", "Nao, manter o agendamento"));
            if (ai == 1) norm = "sim";
            else if (ai == 2) norm = "nao";
        }

        if ("sim".equals(norm) || "s".equals(norm)) {
            var agOpt = agendamentoRepository.findTopByTenantIdAndClienteTelefoneAndStatusInAndDataHoraAfterOrderByCriadoEmDesc(
                    tenant.getId(), telefone, List.of("CONFIRMADO", "PENDENTE"), LocalDateTime.now());
            botSessionRepository.delete(session);
            if (agOpt.isEmpty()) {
                enviar(tenant, telefone,
                        "Hmm, não achei mais esse agendamento — pode já ter sido cancelado. Manda *oi* se quiser marcar de novo 😊");
                return;
            }
            Agendamento ag = agOpt.get();
            ag.setStatus("CANCELADO");
            agendamentoRepository.save(ag);
            log.info("[{}] Agendamento {} cancelado pelo cliente {}", tenant.getId(), ag.getId(), telefone);
            enviar(tenant, telefone,
                    "Pronto, cancelei seu agendamento de *" + ag.getServico() + "* (" + formatarDataHora(ag.getDataHora())
                    + "). 😊\n\nSe quiser remarcar, é só mandar *oi*.");
            avisarDono(tenant, "❌ *" + ag.getClienteNome() + "* cancelou: *" + ag.getServico()
                    + "* — " + formatarDataHora(ag.getDataHora()) + ".\nO horário ficou livre.");

        } else if ("nao".equals(norm) || "n".equals(norm)) {
            botSessionRepository.delete(session);
            enviar(tenant, telefone, "Ufa! Mantive seu agendamento. 👍 Te espero lá!");
        } else {
            erroComTentativa(session, telefone, tenant,
                    "Só pra confirmar: responda *sim* pra cancelar ou *não* pra manter o agendamento. 😊");
        }
    }

    private String formatarDataHora(LocalDateTime dt) {
        return dt.format(DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm"));
    }

    // ── Tentativas ────────────────────────────────────────────────────────────

    private void erroComTentativa(BotSession session, String telefone, Tenant tenant, String msg) {
        session.setTentativas(session.getTentativas() + 1);
        if (session.getTentativas() >= MAX_TENTATIVAS) {
            botSessionRepository.delete(session);
            enviar(tenant, telefone, "Foi mal, acho que me embananei aqui 😅 Manda *oi* que a gente recomeça — "
                    + "ou responda *atendente* pra falar com uma pessoa 🙋");
        } else {
            botSessionRepository.save(session);
            enviar(tenant, telefone, msg);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void enviar(Tenant tenant, String telefone, String texto) {
        evolutionApiService.enviarMensagemNaInstancia(tenant.getId().toString(), telefone, texto);
    }

    /** Aviso genérico ao dono no WhatsApp. Best-effort: nunca quebra o fluxo. */
    private void avisarDono(Tenant tenant, String texto) {
        String donoFone = tenant.getTelefoneWhatsapp();
        if (donoFone == null || donoFone.isBlank()) return;
        try {
            enviar(tenant, donoFone, texto);
        } catch (Exception e) {
            log.warn("[{}] Falha ao avisar o dono: {}", tenant.getId(), e.getMessage());
        }
    }

    /** Regra de antecedência: o cliente ainda pode cancelar/remarcar esse agendamento? */
    private boolean podeMexer(Tenant t, Agendamento ag) {
        int h = t.getAntecedenciaMinHoras();
        return h <= 0 || ag.getDataHora().isAfter(LocalDateTime.now().plusHours(h));
    }

    private String msgAntecedencia(Tenant t, Agendamento ag, String acao) {
        return "😕 Seu horário é *" + formatarDataHora(ag.getDataHora()) + "* e " + acao
                + " por aqui só até *" + t.getAntecedenciaMinHoras() + "h antes*.\n\n"
                + "Se for urgente, responda *atendente* que o pessoal resolve com você 😊";
    }

    /** Avisa o DONO no WhatsApp que entrou pedido na fila de aprovação. Best-effort: nunca quebra o fluxo. */
    private void avisarDonoNovoPedido(Tenant tenant, Agendamento ag) {
        String donoFone = tenant.getTelefoneWhatsapp();
        if (donoFone == null || donoFone.isBlank()) return;
        try {
            String prof = ag.getProfissional() != null ? "\n👤 " + ag.getProfissional() : "";
            enviar(tenant, donoFone,
                    "📥 *Novo pedido de agendamento!*\n\n"
                    + "🙋 " + ag.getClienteNome()
                    + "\n✂️ " + ag.getServico() + prof
                    + "\n📅 " + formatarDataHora(ag.getDataHora())
                    + "\n\nAbra o painel, aba *Solicitações*, pra aceitar ou recusar.");
        } catch (Exception e) {
            log.warn("[{}] Falha ao avisar o dono do novo pedido: {}", tenant.getId(), e.getMessage());
        }
    }

    /** Disponibilidade da sessão: usa a DURAÇÃO do serviço escolhido (fonte única: DisponibilidadeService). */
    private List<String> dispDaSessao(Tenant tenant, LocalDate data, BotSession s) {
        int dur = disponibilidadeService.duracaoServico(tenant, s.getServicoEscolhido());
        return disponibilidadeService.horariosDisponiveis(tenant, data, s.getProfissionalId(), dur);
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

    /** Escolhe uma das variações ao acaso — quebra a repetição dos textos fixos. */
    private String escolher(String... opcoes) {
        return opcoes[RND.nextInt(opcoes.length)];
    }

    /** Despedida (cancelou/desistiu): IA com tom natural, ou fallback variado. */
    private String textoDespedida() {
        String ai = aiService.redigir("O cliente encerrou a conversa sem agendar. "
                + "Despeca-se de forma simpatica e diga que e so mandar *oi* quando quiser marcar.");
        return ai != null ? ai : escolher(
                "Tranquilo! Quando quiser agendar, é só mandar *oi* 😊",
                "Sem problemas! Tô por aqui — manda *oi* quando quiser marcar 👍",
                "Beleza! Qualquer hora manda *oi* que a gente agenda 😉");
    }

    private String formatarServicos(List<Servico> servicos) {
        var sb = new StringBuilder();
        for (int i = 0; i < servicos.size(); i++) {
            Servico sv = servicos.get(i);
            sb.append(i + 1).append(". ").append(sv.getNome());
            if (sv.getPreco() != null) {
                sb.append(" — R$ ").append(String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", sv.getPreco()));
            }
            sb.append(" _(").append(sv.getDuracaoMinutos()).append(" min)_\n");
        }
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

    /** Primeira data a partir de 'inicio' (até 30 dias) com ao menos um horário livre pro serviço da sessão. */
    private LocalDate proximaDataComVaga(Tenant tenant, LocalDate inicio, BotSession s) {
        LocalDate limite = LocalDate.now().plusDays(30);
        for (LocalDate d = inicio; !d.isAfter(limite); d = d.plusDays(1)) {
            if (!dispDaSessao(tenant, d, s).isEmpty()) return d;
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
        if (norm == null || norm.isBlank()) return null;

        // "depois de amanhã" (checar antes de "amanha", que está contido)
        if (norm.contains("depois de amanha") || norm.contains("dps de amanha")
                || (norm.contains("dps") && norm.contains("amanha")))
            return LocalDate.now().plusDays(2);

        // palavras soltas (token a token, evita falso positivo tipo "ter" em "atender")
        Set<String> tokens = new HashSet<>(Arrays.asList(norm.split("[^a-z0-9]+")));
        if (!disjuntos(tokens, "hoje", "hj", "hje", "agora")) return LocalDate.now();
        if (!disjuntos(tokens, "amanha", "amanhaa", "amnh", "amnha", "amn")) return LocalDate.now().plusDays(1);
        for (String w : tokens) {
            Integer dow = DIA_SEMANA.get(w);
            if (dow != null) return proximoDiaDaSemana(dow);
        }

        // dd/mm ou dd/mm/aaaa em qualquer lugar do texto (ex.: "dia 30/06")
        Matcher m = Pattern.compile("(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?").matcher(norm);
        if (m.find()) {
            try {
                int dd = Integer.parseInt(m.group(1));
                int mm = Integer.parseInt(m.group(2));
                int ano = m.group(3) != null ? completarAno(Integer.parseInt(m.group(3))) : LocalDate.now().getYear();
                return LocalDate.of(ano, mm, dd);
            } catch (java.time.DateTimeException | NumberFormatException ignored) {}
        }
        return null;
    }

    /** Algum token coincide com as palavras dadas? (true = nenhum coincide) */
    private boolean disjuntos(Set<String> tokens, String... palavras) {
        for (String p : palavras) if (tokens.contains(p)) return false;
        return true;
    }

    private int completarAno(int ano) { return ano < 100 ? 2000 + ano : ano; }

    /** Próxima ocorrência futura (1–7 dias) do dia da semana ISO informado. */
    private LocalDate proximoDiaDaSemana(int isoAlvo) {
        LocalDate d = LocalDate.now();
        for (int i = 0; i < 7; i++) {
            d = d.plusDays(1);
            if (d.getDayOfWeek().getValue() == isoAlvo) return d;
        }
        return null;
    }

    private boolean isEncerrar(String n) { return "sair".equals(n) || "parar".equals(n); }
    private boolean isCancelarAgendamento(String n) { return n.contains("cancel") || n.contains("desmarc"); }
    private boolean isRemarcar(String n) { return n.contains("remarc") || n.contains("reagend"); }
    private boolean isMeusHorarios(String n) {
        return n.contains("meus horario") || n.contains("meu horario")
                || n.contains("minha agenda") || n.contains("meus agendamento") || n.contains("meu agendamento");
    }
    private boolean isDeNovo(String n) {
        return n.contains("de novo") || n.contains("denovo")
                || n.contains("igual da ultima") || n.contains("mesmo de sempre") || n.contains("o de sempre");
    }
    private boolean isAtendente(String n) {
        return n.contains("atendente") || n.contains("humano") || n.contains("falar com");
    }
    private boolean isReiniciar(String n) {
        return Set.of("oi", "oii", "oie", "ola", "opa", "eai", "e ai", "salve", "bom dia",
                "boa tarde", "boa noite", "boa", "blz", "beleza", "tudo bem", "tudo bom", "menu", "inicio")
                .contains(n);
    }

    private String normalizar(String texto) {
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "").toLowerCase();
    }
}
