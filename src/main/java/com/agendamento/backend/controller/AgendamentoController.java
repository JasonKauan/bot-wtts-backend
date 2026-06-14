package com.agendamento.backend.controller;

import com.agendamento.backend.repository.AgendamentoRepository;
import com.agendamento.backend.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/agendamentos")
@RequiredArgsConstructor
public class AgendamentoController {

    private final AgendamentoRepository repo;

    @PatchMapping("/{id}/cancelar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@PathVariable UUID id) {
        atualizarStatus(id, "CANCELADO");
    }

    @PatchMapping("/{id}/confirmar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmar(@PathVariable UUID id) {
        atualizarStatus(id, "CONFIRMADO");
    }

    @PatchMapping("/{id}/nao-compareceu")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void naoCompareceu(@PathVariable UUID id) {
        atualizarStatus(id, "NAO_COMPARECEU");
    }

    private void atualizarStatus(UUID id, String status) {
        var ag = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!ag.getTenantId().equals(TenantContext.get()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        ag.setStatus(status);
        repo.save(ag);
    }
}
