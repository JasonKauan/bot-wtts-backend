package com.agendamento.backend.controller;

import com.agendamento.backend.dto.api.AgendamentoDto;
import com.agendamento.backend.dto.api.DashboardDto;
import com.agendamento.backend.entity.Agendamento;
import com.agendamento.backend.entity.Servico;
import com.agendamento.backend.repository.AgendamentoRepository;
import com.agendamento.backend.repository.ServicoRepository;
import com.agendamento.backend.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final AgendamentoRepository agendamentoRepository;
    private final ServicoRepository servicoRepository;

    @GetMapping
    public DashboardDto dashboard() {
        UUID tenantId = TenantContext.get();
        LocalDateTime inicio = LocalDate.now().atStartOfDay();
        LocalDateTime fim    = inicio.plusDays(1);

        List<Agendamento> hoje = agendamentoRepository
                .findByTenantIdAndDataHoraBetweenOrderByDataHora(tenantId, inicio, fim);

        long pendentes = hoje.stream()
                .filter(a -> "CONFIRMADO".equals(a.getStatus())
                          && a.getDataHora().isAfter(LocalDateTime.now()))
                .count();

        // ROI do bot (V27): o que o bot agendou nos últimos 30 dias e quanto isso vale
        // (preço atual dos serviços; cancelados ficam de fora).
        List<Agendamento> doBot = agendamentoRepository
                .findByTenantIdAndOrigemAndCriadoEmGreaterThanEqual(
                        tenantId, "BOT", LocalDateTime.now().minusDays(30))
                .stream().filter(a -> !"CANCELADO".equals(a.getStatus())).toList();
        Map<String, BigDecimal> precos = servicoRepository.findByTenantIdAndAtivoTrue(tenantId).stream()
                .filter(s -> s.getPreco() != null)
                .collect(Collectors.toMap(Servico::getNome, Servico::getPreco, (a, b) -> a));
        BigDecimal botReceita = doBot.stream()
                .map(a -> precos.getOrDefault(a.getServico(), BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<AgendamentoDto> dtos = hoje.stream().map(this::toDto).toList();
        return new DashboardDto(dtos, pendentes, doBot.size(), botReceita);
    }

    private AgendamentoDto toDto(Agendamento a) {
        return new AgendamentoDto(a.getId(), a.getClienteNome(), a.getClienteTelefone(),
                a.getServico(), a.getProfissional(), a.getDataHora(), a.getStatus());
    }
}
