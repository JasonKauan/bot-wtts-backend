package com.agendamento.backend.controller;

import com.agendamento.backend.dto.api.AgendamentoDto;
import com.agendamento.backend.dto.api.DashboardDto;
import com.agendamento.backend.entity.Agendamento;
import com.agendamento.backend.repository.AgendamentoRepository;
import com.agendamento.backend.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final AgendamentoRepository agendamentoRepository;

    @GetMapping
    public DashboardDto dashboard() {
        LocalDateTime inicio = LocalDate.now().atStartOfDay();
        LocalDateTime fim    = inicio.plusDays(1);

        List<Agendamento> hoje = agendamentoRepository
                .findByTenantIdAndDataHoraBetweenOrderByDataHora(TenantContext.get(), inicio, fim);

        long pendentes = hoje.stream()
                .filter(a -> "CONFIRMADO".equals(a.getStatus())
                          && a.getDataHora().isAfter(LocalDateTime.now()))
                .count();

        List<AgendamentoDto> dtos = hoje.stream().map(this::toDto).toList();
        return new DashboardDto(dtos, pendentes);
    }

    private AgendamentoDto toDto(Agendamento a) {
        return new AgendamentoDto(a.getId(), a.getClienteNome(), a.getClienteTelefone(),
                a.getServico(), a.getProfissional(), a.getDataHora(), a.getStatus());
    }
}
