package com.agendamento.backend.controller;

import com.agendamento.backend.dto.api.RelatorioDto;
import com.agendamento.backend.dto.api.ServicoContagem;
import com.agendamento.backend.entity.Agendamento;
import com.agendamento.backend.repository.AgendamentoRepository;
import com.agendamento.backend.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Relatórios simples do estabelecimento (janela -30d a +7d, calculado em memória). */
@RestController
@RequestMapping("/api/relatorios")
@RequiredArgsConstructor
public class RelatorioController {

    private final AgendamentoRepository repo;

    @GetMapping
    public RelatorioDto relatorio() {
        UUID tenantId       = TenantContext.get();
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime ini30 = agora.minusDays(30);
        LocalDateTime fim7  = agora.plusDays(7);

        List<Agendamento> janela = repo.findByTenantIdAndDataHoraBetweenOrderByDataHora(tenantId, ini30, fim7);

        long proximos7 = janela.stream()
                .filter(a -> a.getDataHora().isAfter(agora) && !a.getDataHora().isAfter(fim7))
                .filter(a -> "CONFIRMADO".equals(a.getStatus()) || "PENDENTE".equals(a.getStatus()))
                .count();

        List<Agendamento> passados = janela.stream()
                .filter(a -> !a.getDataHora().isAfter(agora)) // dataHora <= agora
                .toList();

        long realizados = passados.stream().filter(a -> "CONFIRMADO".equals(a.getStatus())).count();
        long faltas     = passados.stream().filter(a -> "NAO_COMPARECEU".equals(a.getStatus())).count();
        long cancelados = passados.stream().filter(a -> "CANCELADO".equals(a.getStatus())).count();

        long base = realizados + faltas;
        int taxaFalta = base == 0 ? 0 : (int) Math.round(faltas * 100.0 / base);

        List<ServicoContagem> servicosTop = passados.stream()
                .filter(a -> !"CANCELADO".equals(a.getStatus()))
                .collect(Collectors.groupingBy(Agendamento::getServico, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> new ServicoContagem(e.getKey(), e.getValue()))
                .toList();

        return new RelatorioDto(proximos7, realizados, faltas, cancelados, taxaFalta, servicosTop);
    }
}
