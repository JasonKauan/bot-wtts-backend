package com.agendamento.backend.service;

import com.agendamento.backend.entity.Agendamento;
import com.agendamento.backend.entity.Profissional;
import com.agendamento.backend.entity.Servico;
import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.repository.AgendamentoRepository;
import com.agendamento.backend.repository.BloqueioRepository;
import com.agendamento.backend.repository.ProfissionalRepository;
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
 *
 * Grade POR PROFISSIONAL (V18): cada profissional pode ter dias/horários próprios;
 * campo nulo herda o valor do estabelecimento (tenant). A resolução fica toda em
 * {@link #gradeEfetiva(Tenant, UUID)} — bot e painel não precisam saber de nada.
 */
@Service
@RequiredArgsConstructor
public class DisponibilidadeService {

    private static final List<String> ATIVOS = List.of("CONFIRMADO", "PENDENTE");

    private final AgendamentoRepository agendamentoRepository;
    private final ServicoRepository servicoRepository;
    private final BloqueioRepository bloqueioRepository;
    private final ProfissionalRepository profissionalRepository;

    /** Grade resolvida (tenant + sobreposições do profissional), em horas/minutos. */
    public record Grade(int abertura, int fechamento, int intervalo,
                        Integer almocoInicio, Integer almocoFim, String dias) {}

    /** Duração (min) do serviço pelo nome; fallback = intervalo da grade do tenant. */
    public int duracaoServico(Tenant t, String nomeServico) {
        if (nomeServico != null) {
            for (Servico sv : servicoRepository.findByTenantIdAndAtivoTrue(t.getId())) {
                if (sv.getNome().equals(nomeServico)) return Math.max(1, sv.getDuracaoMinutos());
            }
        }
        return Math.max(1, t.getIntervaloMinutos());
    }

    /** Grade efetiva: valores do tenant, sobrepostos campo a campo pelos do profissional (se houver). */
    public Grade gradeEfetiva(Tenant t, UUID profissionalId) {
        Profissional p = profissionalId == null ? null
                : profissionalRepository.findById(profissionalId)
                        .filter(x -> x.getTenantId().equals(t.getId())).orElse(null);

        int abertura   = (p != null && p.getHorarioAbertura()   != null) ? p.getHorarioAbertura()   : t.getHorarioAbertura();
        int fechamento = (p != null && p.getHorarioFechamento() != null) ? p.getHorarioFechamento() : t.getHorarioFechamento();
        // Almoço: se o profissional definiu o PRÓPRIO almoço, vale o dele por inteiro
        // (inclusive "sem almoço" quando a grade é própria e ele não marcou almoço).
        Integer almIni = t.getAlmocoInicio(), almFim = t.getAlmocoFim();
        if (p != null && p.temGradePropria()) {
            almIni = p.getAlmocoInicio();
            almFim = p.getAlmocoFim();
        }
        String dias = (p != null && p.getDiasTrabalho() != null && !p.getDiasTrabalho().isBlank())
                ? p.getDiasTrabalho() : t.getDiasFuncionamento();
        return new Grade(abertura, fechamento, t.getIntervaloMinutos(), almIni, almFim, dias);
    }

    /** Horários de início livres do dia para um serviço de {@code duracaoMin} minutos. */
    public List<String> horariosDisponiveis(Tenant t, LocalDate data, UUID profissionalId, int duracaoMin) {
        Grade g = gradeEfetiva(t, profissionalId);
        if (!diaFunciona(g.dias(), data)) return List.of();   // fechado / profissional de folga
        if (bloqueioRepository.existsByTenantIdAndDataInicioLessThanEqualAndDataFimGreaterThanEqual(
                t.getId(), data, data)) return List.of();  // folga/feriado

        List<int[]> ocupados = ocupacoesDoDia(t, data, profissionalId);
        LocalDateTime agora = LocalDateTime.now();
        int fimExpediente = g.fechamento() * 60;
        Integer almIni = g.almocoInicio() != null ? g.almocoInicio() * 60 : null;
        Integer almFim = g.almocoFim() != null ? g.almocoFim() * 60 : null;

        List<String> livres = new ArrayList<>();
        for (String h : gerarGrade(g)) {
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
        return diaFunciona(t.getDiasFuncionamento(), data);
    }

    /** O profissional (ou o estabelecimento, se nulo) atende nesse dia da semana? */
    public boolean diaFunciona(Tenant t, UUID profissionalId, LocalDate data) {
        return diaFunciona(gradeEfetiva(t, profissionalId).dias(), data);
    }

    private boolean diaFunciona(String dias, LocalDate data) {
        if (dias == null || dias.isBlank()) return true;
        String alvo = String.valueOf(data.getDayOfWeek().getValue());
        for (String p : dias.split(",")) {
            if (p.trim().equals(alvo)) return true;
        }
        return false;
    }

    /** Grade do dia (do estabelecimento): de abertura a fechamento, pulando o almoço. */
    public List<String> gerarGrade(Tenant t) {
        return gerarGrade(gradeEfetiva(t, null));
    }

    /** Grade do dia: de abertura a fechamento, de intervalo em intervalo, pulando o almoço. */
    private List<String> gerarGrade(Grade g) {
        int intervalo = g.intervalo() > 0 ? g.intervalo() : 60;
        int inicioMin = g.abertura() * 60;
        int fimMin    = g.fechamento() * 60;
        Integer almIni = g.almocoInicio() != null ? g.almocoInicio() * 60 : null;
        Integer almFim = g.almocoFim()    != null ? g.almocoFim()    * 60 : null;

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
