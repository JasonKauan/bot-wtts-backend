package com.agendamento.backend.controller;

import com.agendamento.backend.dto.api.ClienteCrmDto;
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
import java.util.stream.Collectors;

/** CRM leve: clientes do estabelecimento, agregados do histórico de agendamentos. */
@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final AgendamentoRepository repo;

    @GetMapping
    public List<ClienteCrmDto> listar() {
        LocalDateTime agora = LocalDateTime.now();
        List<Agendamento> todos = repo.findByTenantId(TenantContext.get());

        Map<String, List<Agendamento>> porTelefone = todos.stream()
                .filter(a -> a.getClienteTelefone() != null && !a.getClienteTelefone().isBlank())
                .collect(Collectors.groupingBy(Agendamento::getClienteTelefone));

        return porTelefone.entrySet().stream()
                .map(e -> {
                    List<Agendamento> ags = e.getValue();
                    String nome = ags.stream()
                            .max(Comparator.comparing(Agendamento::getCriadoEm))
                            .map(Agendamento::getClienteNome).orElse(e.getKey());
                    long visitas = ags.stream()
                            .filter(a -> "CONFIRMADO".equals(a.getStatus()) && !a.getDataHora().isAfter(agora))
                            .count();
                    long faltas = ags.stream()
                            .filter(a -> "NAO_COMPARECEU".equals(a.getStatus()))
                            .count();
                    LocalDateTime ultima = ags.stream()
                            .filter(a -> "CONFIRMADO".equals(a.getStatus()) && !a.getDataHora().isAfter(agora))
                            .map(Agendamento::getDataHora).max(Comparator.naturalOrder()).orElse(null);
                    LocalDateTime proximo = ags.stream()
                            .filter(a -> ("CONFIRMADO".equals(a.getStatus()) || "PENDENTE".equals(a.getStatus()))
                                    && a.getDataHora().isAfter(agora))
                            .map(Agendamento::getDataHora).min(Comparator.naturalOrder()).orElse(null);
                    return new ClienteCrmDto(nome, e.getKey(), visitas, faltas, ultima, proximo);
                })
                // com horário marcado primeiro; depois os mais recentes
                .sorted(Comparator
                        .comparing((ClienteCrmDto c) -> c.proximoAgendamento() == null)
                        .thenComparing(c -> c.ultimaVisita() == null ? LocalDateTime.MIN : c.ultimaVisita(),
                                Comparator.reverseOrder()))
                .toList();
    }
}
