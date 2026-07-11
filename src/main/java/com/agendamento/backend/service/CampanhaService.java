package com.agendamento.backend.service;

import com.agendamento.backend.entity.Agendamento;
import com.agendamento.backend.entity.Aniversario;
import com.agendamento.backend.entity.Plano;
import com.agendamento.backend.entity.Reativacao;
import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.repository.AgendamentoRepository;
import com.agendamento.backend.repository.AniversarioRepository;
import com.agendamento.backend.repository.ReativacaoRepository;
import com.agendamento.backend.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Campanhas Diamond (V32/V33): reativação de sumidos e parabéns de aniversário.
 * Regras anti-spam: reativação no máximo 1x/60 dias por cliente e 10 envios/dia por
 * tenant (evita rajada no dia em que o dono liga a feature — rajada = risco de ban
 * no WhatsApp); aniversário 1x por ano. Janelas de cron largas (Render free dorme).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CampanhaService {

    private static final int DEDUPE_REATIVACAO_DIAS = 60;
    private static final int MAX_REATIVACOES_DIA = 10;

    private static final String MSG_REATIVACAO_PADRAO =
            "Oi{nome}! 😊 Faz um tempinho que a gente não te vê aqui na {estabelecimento}... "
            + "Bateu saudade! Quer marcar um horário? É só responder *oi* 😉";
    private static final String MSG_ANIVERSARIO_PADRAO =
            "🎉 Feliz aniversário{nome}!! 🥳 A equipe da {estabelecimento} te deseja um dia incrível. "
            + "Passa aqui esse mês pra comemorar com a gente — responda *oi* pra agendar 😉";

    private final TenantRepository tenantRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final ReativacaoRepository reativacaoRepository;
    private final AniversarioRepository aniversarioRepository;
    private final EvolutionApiService evolutionApiService;

    /** Reativação: cliente sem visita há N+ dias e sem horário futuro → "sentimos sua falta". */
    @Scheduled(cron = "0 20 10-13 * * *")
    @Transactional
    public int reativarSumidos() {
        LocalDateTime agora = LocalDateTime.now();
        int totalEnviados = 0;

        for (Tenant t : tenantRepository.findByAtivoTrue()) {
            if (t.getReativacaoDias() <= 0 || t.isAssinaturaVencida()
                    || !t.getPlano().permite(Plano.Recurso.REATIVACAO)) continue;

            Map<String, List<Agendamento>> porTelefone = agendamentoRepository.findByTenantId(t.getId())
                    .stream()
                    .filter(a -> a.getClienteTelefone() != null && !a.getClienteTelefone().isBlank()
                            && !"simulador".equals(a.getClienteTelefone()))
                    .collect(Collectors.groupingBy(Agendamento::getClienteTelefone));

            int enviados = 0;
            for (var e : porTelefone.entrySet()) {
                if (enviados >= MAX_REATIVACOES_DIA) break;
                List<Agendamento> ags = e.getValue();

                boolean temFuturo = ags.stream().anyMatch(a ->
                        ("CONFIRMADO".equals(a.getStatus()) || "PENDENTE".equals(a.getStatus()))
                        && a.getDataHora().isAfter(agora));
                if (temFuturo) continue;

                LocalDateTime ultimaVisita = ags.stream()
                        .filter(a -> "CONFIRMADO".equals(a.getStatus()) && !a.getDataHora().isAfter(agora))
                        .map(Agendamento::getDataHora).max(LocalDateTime::compareTo).orElse(null);
                if (ultimaVisita == null) continue;
                if (ultimaVisita.isAfter(agora.minusDays(t.getReativacaoDias()))) continue;

                if (reativacaoRepository.existsByTenantIdAndTelefoneAndEnviadoEmAfter(
                        t.getId(), e.getKey(), agora.minusDays(DEDUPE_REATIVACAO_DIAS))) continue;

                String nome = ags.stream().max(java.util.Comparator.comparing(Agendamento::getCriadoEm))
                        .map(Agendamento::getClienteNome).orElse(null);
                try {
                    evolutionApiService.enviarMensagemNaInstancia(t.getId().toString(), e.getKey(),
                            montar(t.getReativacaoMsg(), MSG_REATIVACAO_PADRAO, nome, t.getNome()));
                    reativacaoRepository.save(Reativacao.builder()
                            .tenantId(t.getId()).telefone(e.getKey()).build());
                    enviados++;
                } catch (Exception ex) {
                    log.warn("[Reativacao] Falha no tenant {} tel {}: {}", t.getId(), e.getKey(), ex.getMessage());
                }
            }
            totalEnviados += enviados;
            if (enviados > 0) log.info("[Reativacao] {} mensagem(ns) no tenant {}", enviados, t.getId());
        }
        return totalEnviados;
    }

    /** Aniversário: parabéns 1x por ano pra quem faz aniversário hoje. */
    @Scheduled(cron = "0 40 9-12 * * *")
    @Transactional
    public int parabenizarAniversariantes() {
        LocalDate hoje = LocalDate.now();
        Map<UUID, Tenant> tenants = tenantRepository.findByAtivoTrue()
                .stream().collect(Collectors.toMap(Tenant::getId, t -> t));

        int enviados = 0;
        for (Aniversario a : aniversarioRepository.findByDiaAndMes(hoje.getDayOfMonth(), hoje.getMonthValue())) {
            Tenant t = tenants.get(a.getTenantId());
            if (t == null || !t.isAniversarioAtivo() || t.isAssinaturaVencida()
                    || !t.getPlano().permite(Plano.Recurso.ANIVERSARIO)) continue;
            if (a.getUltimoEnvio() != null && a.getUltimoEnvio().getYear() == hoje.getYear()) continue;

            try {
                evolutionApiService.enviarMensagemNaInstancia(t.getId().toString(), a.getTelefone(),
                        montar(t.getAniversarioMsg(), MSG_ANIVERSARIO_PADRAO, a.getNome(), t.getNome()));
                a.setUltimoEnvio(hoje);
                aniversarioRepository.save(a);
                enviados++;
            } catch (Exception ex) {
                log.warn("[Aniversario] Falha no tenant {} tel {}: {}", t.getId(), a.getTelefone(), ex.getMessage());
            }
        }
        if (enviados > 0) log.info("[Aniversario] {} parabéns enviados", enviados);
        return enviados;
    }

    /** Template com {nome} (vira ", Fulano" ou some) e {estabelecimento}. */
    private String montar(String custom, String padrao, String clienteNome, String estabelecimento) {
        String base = (custom != null && !custom.isBlank()) ? custom : padrao;
        String nome = primeiroNome(clienteNome);
        return base.replace("{nome}", nome != null ? ", " + nome : "")
                   .replace("{estabelecimento}", estabelecimento);
    }

    private String primeiroNome(String nome) {
        if (nome == null || nome.isBlank()) return null;
        String primeiro = nome.trim().split("\\s+")[0];
        if (primeiro.isBlank() || primeiro.matches(".*\\d.*")) return null;
        return primeiro.substring(0, 1).toUpperCase() + primeiro.substring(1).toLowerCase();
    }
}
