package com.agendamento.backend.controller;

import com.agendamento.backend.dto.api.ClienteCrmDto;
import com.agendamento.backend.entity.Agendamento;
import com.agendamento.backend.entity.Plano;
import com.agendamento.backend.repository.AgendamentoRepository;
import com.agendamento.backend.security.TenantContext;
import com.agendamento.backend.service.PlanoService;
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
    private final PlanoService planoService;
    private final com.agendamento.backend.repository.AniversarioRepository aniversarioRepository;

    @GetMapping
    public List<ClienteCrmDto> listar() {
        planoService.exigir(TenantContext.get(), Plano.Recurso.CRM);
        LocalDateTime agora = LocalDateTime.now();
        List<Agendamento> todos = repo.findByTenantId(TenantContext.get());
        Map<String, String> aniversarios = aniversarioRepository.findByTenantId(TenantContext.get())
                .stream().collect(Collectors.toMap(
                        com.agendamento.backend.entity.Aniversario::getTelefone,
                        a -> String.format("%02d/%02d", a.getDia(), a.getMes()), (a, b) -> a));

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
                    return new ClienteCrmDto(nome, e.getKey(), visitas, faltas, ultima, proximo,
                            aniversarios.get(e.getKey()));
                })
                // com horário marcado primeiro; depois os mais recentes
                .sorted(Comparator
                        .comparing((ClienteCrmDto c) -> c.proximoAgendamento() == null)
                        .thenComparing(c -> c.ultimaVisita() == null ? LocalDateTime.MIN : c.ultimaVisita(),
                                Comparator.reverseOrder()))
                .toList();
    }

    public record AniversarioRequest(
            @jakarta.validation.constraints.NotBlank String telefone,
            @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(31) int dia,
            @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(12) int mes,
            String nome) {}

    /** Define (ou remove, com dia=0/mes=0) o aniversário de um cliente — recurso Diamond (V33). */
    @org.springframework.web.bind.annotation.PutMapping("/aniversario")
    public Map<String, String> salvarAniversario(
            @org.springframework.web.bind.annotation.RequestBody @jakarta.validation.Valid AniversarioRequest req) {
        java.util.UUID tenantId = TenantContext.get();
        planoService.exigir(tenantId, Plano.Recurso.ANIVERSARIO);

        var existente = aniversarioRepository.findByTenantIdAndTelefone(tenantId, req.telefone());
        if (req.dia() == 0 || req.mes() == 0) {   // remover
            existente.ifPresent(aniversarioRepository::delete);
            return Map.of("aniversario", "");
        }
        try {   // valida a combinação (ex.: 31/02 não existe)
            java.time.MonthDay.of(req.mes(), req.dia());
        } catch (java.time.DateTimeException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Data inválida — confira dia e mês.");
        }
        var a = existente.orElseGet(() -> com.agendamento.backend.entity.Aniversario.builder()
                .tenantId(tenantId).telefone(req.telefone()).build());
        a.setDia(req.dia());
        a.setMes(req.mes());
        a.setNome(req.nome());
        aniversarioRepository.save(a);
        return Map.of("aniversario", String.format("%02d/%02d", req.dia(), req.mes()));
    }
}
