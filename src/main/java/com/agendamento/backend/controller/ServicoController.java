package com.agendamento.backend.controller;

import com.agendamento.backend.dto.api.ServicoRequest;
import com.agendamento.backend.entity.Servico;
import com.agendamento.backend.repository.AgendamentoRepository;
import com.agendamento.backend.repository.ServicoRepository;
import com.agendamento.backend.security.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/servicos")
@RequiredArgsConstructor
public class ServicoController {

    private final ServicoRepository repo;
    private final AgendamentoRepository agendamentoRepository;
    private final com.agendamento.backend.repository.RecorrenciaRepository recorrenciaRepository;

    @GetMapping
    public List<Servico> listar() {
        return repo.findByTenantIdAndAtivoTrue(TenantContext.get());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Servico criar(@Valid @RequestBody ServicoRequest req) {
        Servico s = Servico.builder()
                .tenantId(TenantContext.get())
                .nome(req.nome())
                .duracaoMinutos(req.duracaoMinutos())
                .preco(req.preco())
                .ativo(true)
                .build();
        return repo.save(s);
    }

    @PutMapping("/{id}")
    public Servico atualizar(@PathVariable UUID id, @Valid @RequestBody ServicoRequest req) {
        Servico s = buscarDoTenant(id);
        s.setNome(req.nome());
        s.setDuracaoMinutos(req.duracaoMinutos());
        s.setPreco(req.preco());
        return repo.save(s);
    }

    @PatchMapping("/{id}/ativo")
    public Servico toggleAtivo(@PathVariable UUID id) {
        Servico s = buscarDoTenant(id);
        s.setAtivo(!s.isAtivo());
        return repo.save(s);
    }

    /**
     * Exclui de vez. Trava (409) se houver horário futuro ativo ou cliente fixo usando o serviço —
     * a mensagem diz o que resolver. O histórico antigo fica intacto (o agendamento guarda o nome).
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable UUID id) {
        Servico s = buscarDoTenant(id);
        UUID tenantId = TenantContext.get();
        if (agendamentoRepository.existsByTenantIdAndServicoAndStatusInAndDataHoraAfter(
                tenantId, s.getNome(), List.of("CONFIRMADO", "PENDENTE"), LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esse serviço tem horários futuros marcados. Cancele ou remarque esses horários — ou apenas Desative o serviço.");
        }
        if (recorrenciaRepository.existsByTenantIdAndServicoAndAtivoTrue(tenantId, s.getNome())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esse serviço está em um cliente fixo. Remova ou pause o cliente fixo (aba Clientes fixos) antes de excluir.");
        }
        repo.delete(s);
    }

    private Servico buscarDoTenant(UUID id) {
        Servico s = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!s.getTenantId().equals(TenantContext.get()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return s;
    }
}
