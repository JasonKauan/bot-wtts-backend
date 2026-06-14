package com.agendamento.backend.controller;

import com.agendamento.backend.dto.api.AgendamentoDto;
import com.agendamento.backend.entity.Agendamento;
import com.agendamento.backend.repository.AgendamentoRepository;
import com.agendamento.backend.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agenda")
@RequiredArgsConstructor
public class AgendaController {

    private final AgendamentoRepository agendamentoRepository;

    @GetMapping
    public List<AgendamentoDto> agenda(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(required = false) UUID profissionalId) {

        LocalDate dia        = data != null ? data : LocalDate.now();
        LocalDateTime inicio = dia.atStartOfDay();
        LocalDateTime fim    = inicio.plusDays(1);
        UUID tenantId        = TenantContext.get();

        List<Agendamento> resultado = profissionalId != null
                ? agendamentoRepository.findByTenantIdAndProfissionalIdAndDataHoraBetweenOrderByDataHora(
                        tenantId, profissionalId, inicio, fim)
                : agendamentoRepository.findByTenantIdAndDataHoraBetweenOrderByDataHora(
                        tenantId, inicio, fim);

        return resultado.stream().map(this::toDto).toList();
    }

    private AgendamentoDto toDto(Agendamento a) {
        return new AgendamentoDto(a.getId(), a.getClienteNome(), a.getClienteTelefone(),
                a.getServico(), a.getProfissional(), a.getDataHora(), a.getStatus());
    }
}
