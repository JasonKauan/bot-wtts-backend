package com.agendamento.backend.service;

import com.agendamento.backend.entity.Agendamento;
import com.agendamento.backend.entity.BotSession;
import com.agendamento.backend.entity.ListaEspera;
import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.repository.BotSessionRepository;
import com.agendamento.backend.repository.ListaEsperaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Lista de espera (V22): quando um horário de um dia lotado é liberado
 * (cancelamento ou remarcação), chama o PRIMEIRO da fila daquele dia e já
 * deixa a conversa pronta na etapa HORA (é só o cliente responder o horário).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ListaEsperaService {

    private static final DateTimeFormatter FMT_DATA = DateTimeFormatter.ofPattern("dd/MM");

    private final ListaEsperaRepository listaEsperaRepository;
    private final BotSessionRepository botSessionRepository;
    private final DisponibilidadeService disponibilidadeService;
    private final EvolutionApiService evolutionApiService;

    /** Entra na fila do dia (sem duplicar). Devolve false se já estava. */
    @Transactional
    public boolean entrar(Tenant tenant, String telefone, String clienteNome,
                          String servico, java.util.UUID profissionalId, String profissional, LocalDate data) {
        if (listaEsperaRepository.existsByTenantIdAndTelefoneAndData(tenant.getId(), telefone, data)) return false;
        listaEsperaRepository.save(ListaEspera.builder()
                .tenantId(tenant.getId())
                .telefone(telefone)
                .clienteNome(clienteNome)
                .servico(servico)
                .profissionalId(profissionalId)
                .profissional(profissional)
                .data(data)
                .build());
        return true;
    }

    /**
     * Um horário do dia foi liberado: avisa o primeiro da fila que tenha vaga de verdade
     * (checa a disponibilidade com as preferências dele). Best-effort — nunca quebra o
     * cancelamento que disparou.
     */
    @Transactional
    public void notificarProximo(Tenant tenant, LocalDate dataLiberada) {
        try {
            if (dataLiberada == null || dataLiberada.isBefore(LocalDate.now())) return;
            if (!tenant.getPlano().permite(com.agendamento.backend.entity.Plano.Recurso.LISTA_ESPERA)) return;
            List<ListaEspera> fila = listaEsperaRepository
                    .findByTenantIdAndDataOrderByCriadoEmAsc(tenant.getId(), dataLiberada);
            for (ListaEspera e : fila) {
                int dur = disponibilidadeService.duracaoServico(tenant, e.getServico());
                List<String> disp = disponibilidadeService.horariosDisponiveis(
                        tenant, dataLiberada, e.getProfissionalId(), dur);
                if (disp.isEmpty()) continue;   // pra ESTE cliente ainda não há vaga (prefs dele)

                // Conversa em modo humano não é atropelada — tenta o próximo da fila.
                BotSession s = botSessionRepository
                        .findByTelefoneAndTenantId(e.getTelefone(), tenant.getId()).orElse(null);
                if (s != null && "HUMANO".equals(s.getEtapa())) continue;

                if (s == null) {
                    s = BotSession.builder().tenantId(tenant.getId()).telefone(e.getTelefone()).build();
                }
                s.setServicoEscolhido(e.getServico());
                s.setProfissionalId(e.getProfissionalId());
                s.setProfissionalEscolhido(e.getProfissional());
                s.setDataEscolhida(dataLiberada);
                s.setHoraEscolhida(null);
                s.setRemarcandoId(null);
                s.setEsperaData(null);
                s.setTentativas(0);
                s.setEtapa("HORA");
                s.setUltimaInteracao(LocalDateTime.now());
                botSessionRepository.save(s);

                String nome = e.getClienteNome() != null && !e.getClienteNome().isBlank()
                        ? " " + e.getClienteNome().trim().split("\\s+")[0] : "";
                String servicoTxt = e.getServico() != null ? " pra *" + e.getServico() + "*" : "";
                evolutionApiService.enviarMensagemNaInstancia(tenant.getId().toString(), e.getTelefone(),
                        "🎉 Boa notícia" + nome + "! Abriu horário" + servicoTxt + " em *"
                        + dataLiberada.format(FMT_DATA) + "*:\n\n" + formatarLista(disp)
                        + "\n\nResponda o número ou a hora que fica bom — mas corre que é por ordem de chegada 😉");

                listaEsperaRepository.delete(e);
                log.info("[ListaEspera] Notificado {} sobre vaga em {} (tenant {})",
                        e.getTelefone(), dataLiberada, tenant.getId());
                return;   // um por vez: o horário liberado é um só
            }
        } catch (Exception ex) {
            log.warn("[ListaEspera] Falha ao notificar fila do tenant {}: {}", tenant.getId(), ex.getMessage());
        }
    }

    /** Limpeza diária: quem esperava um dia que já passou sai da fila em silêncio. */
    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void limparExpirados() {
        listaEsperaRepository.deleteByDataBefore(LocalDate.now());
    }

    private String formatarLista(List<String> items) {
        var sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) sb.append(i + 1).append(". ").append(items.get(i)).append("\n");
        return sb.toString().trim();
    }
}
