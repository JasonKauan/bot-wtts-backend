package com.agendamento.backend.controller;

import com.agendamento.backend.dto.api.FaturamentoLinha;
import com.agendamento.backend.dto.api.RelatorioDto;
import com.agendamento.backend.dto.api.ServicoContagem;
import com.agendamento.backend.entity.Agendamento;
import com.agendamento.backend.entity.Plano;
import com.agendamento.backend.entity.Servico;
import com.agendamento.backend.repository.AgendamentoRepository;
import com.agendamento.backend.repository.ServicoRepository;
import com.agendamento.backend.repository.TenantRepository;
import com.agendamento.backend.security.TenantContext;
import com.agendamento.backend.service.PlanoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Relatórios simples do estabelecimento (janela -30d a +7d, calculado em memória). */
@RestController
@RequestMapping("/api/relatorios")
@RequiredArgsConstructor
public class RelatorioController {

    private final AgendamentoRepository repo;
    private final ServicoRepository servicoRepository;
    private final TenantRepository tenantRepository;
    private final PlanoService planoService;

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

        List<Agendamento> atendidos = passados.stream()
                .filter(a -> "CONFIRMADO".equals(a.getStatus())).toList();
        long realizados = atendidos.size();
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

        // ── Financeiro (recurso Diamond): receita estimada dos atendidos ──
        boolean financeiroLiberado = tenantRepository.findById(tenantId)
                .map(t -> t.getPlano().permite(Plano.Recurso.FINANCEIRO)).orElse(false);
        BigDecimal receita30 = BigDecimal.ZERO;
        List<FaturamentoLinha> porServico = List.of();
        List<FaturamentoLinha> porProfissional = List.of();
        if (financeiroLiberado) {
            Map<String, BigDecimal> precos = precosPorNome(tenantId);
            receita30 = atendidos.stream()
                    .map(a -> precoDe(a, precos)).reduce(BigDecimal.ZERO, BigDecimal::add);
            porServico = agrupar(atendidos, Agendamento::getServico, precos);
            porProfissional = agrupar(atendidos,
                    a -> a.getProfissional() != null ? a.getProfissional() : "Sem profissional", precos);
        }

        return new RelatorioDto(proximos7, realizados, faltas, cancelados, taxaFalta, servicosTop,
                receita30, porServico, porProfissional, financeiroLiberado);
    }

    /** Export CSV: atendimentos realizados dos últimos 30 dias, com preço (abre no Excel BR). */
    @GetMapping("/financeiro.csv")
    public ResponseEntity<byte[]> financeiroCsv() {
        UUID tenantId = TenantContext.get();
        planoService.exigir(tenantId, Plano.Recurso.FINANCEIRO);
        LocalDateTime agora = LocalDateTime.now();
        Map<String, BigDecimal> precos = precosPorNome(tenantId);
        DateTimeFormatter fData = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter fHora = DateTimeFormatter.ofPattern("HH:mm");

        StringBuilder sb = new StringBuilder("Data;Hora;Servico;Profissional;Cliente;Preco estimado\n");
        repo.findByTenantIdAndDataHoraBetweenOrderByDataHora(tenantId, agora.minusDays(30), agora)
                .stream().filter(a -> "CONFIRMADO".equals(a.getStatus()))
                .forEach(a -> sb.append(a.getDataHora().format(fData)).append(';')
                        .append(a.getDataHora().format(fHora)).append(';')
                        .append(csv(a.getServico())).append(';')
                        .append(csv(a.getProfissional())).append(';')
                        .append(csv(a.getClienteNome())).append(';')
                        .append(precoDe(a, precos).toPlainString().replace('.', ',')).append('\n'));

        byte[] corpo = ("﻿" + sb).getBytes(StandardCharsets.UTF_8);   // BOM pro Excel
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=financeiro.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(corpo);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Preço atual por nome de serviço (agendamento guarda o nome, não o id). */
    private Map<String, BigDecimal> precosPorNome(UUID tenantId) {
        return servicoRepository.findByTenantIdAndAtivoTrue(tenantId).stream()
                .filter(s -> s.getPreco() != null)
                .collect(Collectors.toMap(Servico::getNome, Servico::getPreco, (a, b) -> a));
    }

    private BigDecimal precoDe(Agendamento a, Map<String, BigDecimal> precos) {
        return precos.getOrDefault(a.getServico(), BigDecimal.ZERO);
    }

    private List<FaturamentoLinha> agrupar(List<Agendamento> atendidos,
                                           Function<Agendamento, String> chave,
                                           Map<String, BigDecimal> precos) {
        return atendidos.stream()
                .collect(Collectors.groupingBy(chave))
                .entrySet().stream()
                .map(e -> new FaturamentoLinha(e.getKey(), e.getValue().size(),
                        e.getValue().stream().map(a -> precoDe(a, precos))
                                .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .sorted(Comparator.comparing(FaturamentoLinha::receita).reversed())
                .toList();
    }

    private String csv(String s) {
        if (s == null) return "";
        return s.replace(';', ',').replace('\n', ' ');
    }
}
