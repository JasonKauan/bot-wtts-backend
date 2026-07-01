package com.agendamento.backend.controller;

import com.agendamento.backend.dto.api.ServicoRequest;
import com.agendamento.backend.entity.Servico;
import com.agendamento.backend.repository.ServicoRepository;
import com.agendamento.backend.security.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/servicos")
@RequiredArgsConstructor
public class ServicoController {

    private final ServicoRepository repo;

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

    private Servico buscarDoTenant(UUID id) {
        Servico s = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!s.getTenantId().equals(TenantContext.get()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return s;
    }
}
