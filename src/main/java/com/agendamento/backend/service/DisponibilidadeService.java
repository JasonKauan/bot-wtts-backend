package com.agendamento.backend.service;

import com.agendamento.backend.entity.Agendamento;
import com.agendamento.backend.entity.Servico;
import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.repository.AgendamentoRepository;
import com.agendamento.backend.repository.BloqueioRepository;
import com.agendamento.backend.repository.ServicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Disponibilidade de horários — fonte única usada pelo bot e pelo painel.
 * Ciente de DURAÇÃO: um serviço de 60min ocupa todos os slots que cobre, e um
 * candidato só é oferecido se couber inteiro (não estoura fechamento nem almoço).
 * Agendamentos PENDENTES e CONFIRMADOS seguram o horário.
 */
@Service
@RequiredArgsConstructor
public class DisponibilidadeService {

    private static final List<String> ATIVOS = List.of("CONFIRMADO", "PENDENTE");

    private final AgendamentoRepository agendamentoRepository;
    private final ServicoRepository servicoRepository;
    private final BloqueioRepository bloqueioRepository;

    /** Duração (min) do serviço pelo nome; fallback = intervalo da grade do tenant. */
    public int duracaoServico(Tenant t, String nomeServico) {
        if (nomeServico != null) {
            for (Servico sv : servicoRepository.findByTenantIdAndAtivoTrue(t.getId())) {
                if (sv.getNome().equals(nomeServico)) return Math.max(1, sv.getDuracaoMinutos());
            }
        }
        return Math.max(1, t.getIntervaloMinutos());
    }

    /** Horários de início livres do dia para um serviço de {@code duracaoMin} minutos. */
    public List<String> horariosDisponiveis(Tenant t, LocalDate data, UUID profissionalId, int duracaoMin) {
        if (!diaFunciona(t, data)) return List.of();   // fechado nesse dia da semana
        if (bloqueioRepository.existsByTenantIdAndDataInicioLessThanEqualAndDataFimGreaterThanEqual(
                t.getId(), data, data)) return List.of();  // folga/feriado

        List<int[]> ocupados = ocupacoesDoDia(t, data, profissionalId);
        LocalDateTime agora = LocalDateTime.now();
        int fimExpediente = t.getHorarioFechamento() * 60;
        Integer almIni = t.getAlmocoInicio() != null ? t.getAlmocoInicio() * 60 : null;
        Integer almFim = t.getAlmocoFim() != null ? t.getAlmocoFim() * 60 : null;

        List<String> livres = new ArrayList<>();
        for (String h : gerarGrade(t)) {
            int ini = emMinutos(h);
            int fim = ini + duracaoMin;
            if (fim > fimExpediente) continue;                                         // estoura o fechamento
            if (almIni != null && almFim != null && ini < almFim && almIni < fim) continue; // cruza o almoço
            LocalDateTime slot = LocalDateTime.of(data, LocalTime.of(ini / 60, ini % 60));
            if (!slot.isAfter(agora)) continue;                                        // já passou (hoje)
            if (temConflito(ocupados, ini, fim)) continue;
            livres.add(h);
        }
        return livres;
    }

    /** true se [inicio, inicio+duracaoMin) colide com algum agendamento ativo do dia. */
    public boolean conflita(Tenant t, UUID profissionalId, LocalDateTime inicio, int duracaoMin) {
        int ini = inicio.getHour() * 60 + inicio.getMinute();
        return temConflito(ocupacoesDoDia(t, inicio.toLocalDate(), profissionalId), ini, ini + duracaoMin);
    }

    /** O estabelecimento funciona nesse dia da semana? (dias ISO 1=seg..7=dom) */
    public boolean diaFunciona(Tenant t, LocalDate data) {
        String dias = t.getDiasFuncionamento();
        if (dias == null || dias.isBlank()) return true;
        String alvo = String.valueOf(data.getDayOfWeek().getValue());
        for (String p : dias.split(",")) {
            if (p.trim().equals(alvo)) return true;
        }
        return false;
    }

    /** Grade do dia: de abertura a fechamento, de intervalo em intervalo, pulando o almoço. */
    public List<String> gerarGrade(Tenant t) {
        int intervalo = t.getIntervaloMinutos() > 0 ? t.getIntervaloMinutos() : 60;
        int inicioMin = t.getHorarioAbertura() * 60;
        int fimMin    = t.getHorarioFechamento() * 60;
        Integer almIni = t.getAlmocoInicio() != null ? t.getAlmocoInicio() * 60 : null;
        Integer almFim = t.getAlmocoFim()    != null ? t.getAlmocoFim()    * 60 : null;

        List<String> slots = new ArrayList<>();
        for (int m = inicioMin; m < fimMin; m += intervalo) {
            if (almIni != null && almFim != null && m >= almIni && m < almFim) continue;
            slots.add(String.format("%02d:%02d", m / 60, m % 60));
        }
        return slots;
    }

    // ── Internos ──────────────────────────────────────────────────────────────

    /**
     * Intervalos ocupados do dia, em minutos [inicio, fim). Com profissional definido, só
     * conflita com o MESMO profissional; agendamento SEM profissional bloqueia todos
     * (conservador — evita encaixe em cima de um manual sem profissional).
     */
    private List<int[]> ocupacoesDoDia(Tenant t, LocalDate data, UUID profissionalId) {
        List<Agendamento> doDia = agendamentoRepository.findByTenantIdAndDataHoraBetweenOrderByDataHora(
                t.getId(), data.atStartOfDay(), data.plusDays(1).atStartOfDay());
        List<int[]> out = new ArrayList<>();
        for (Agendamento a : doDia) {
            if (!ATIVOS.contains(a.getStatus())) continue;
            if (profissionalId != null && a.getProfissionalId() != null
                    && !profissionalId.equals(a.getProfissionalId())) continue;
            int ini = a.getDataHora().getHour() * 60 + a.getDataHora().getMinute();
            int dur = a.getDuracaoMinutos() != null ? a.getDuracaoMinutos() : Math.max(1, t.getIntervaloMinutos());
            out.add(new int[]{ini, ini + dur});
        }
        return out;
    }

    private boolean temConflito(List<int[]> ocupados, int ini, int fim) {
        for (int[] o : ocupados) {
            if (ini < o[1] && o[0] < fim) return true;
        }
        return false;
    }

    private int emMinutos(String hhmm) {
        String[] p = hhmm.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }
}
