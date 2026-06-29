package com.agendamento.backend.controller;

import com.agendamento.backend.dto.api.BloqueioDto;
import com.agendamento.backend.dto.api.BloqueioRequest;
import com.agendamento.backend.entity.Bloqueio;
import com.agendamento.backend.repository.BloqueioRepository;
import com.agendamento.backend.security.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Folgas/feriados do estabelecimento. */
@RestController
@RequestMapping("/api/bloqueios")
@RequiredArgsConstructor
public class BloqueioController {

    private final BloqueioRepository repo;

    @GetMapping
    public List<BloqueioDto> listar() {
        return repo.findByTenantIdOrderByDataInicioAsc(TenantContext.get())
                .stream().map(this::toDto).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BloqueioDto criar(@Valid @RequestBody BloqueioRequest req) {
        LocalDate fim = req.dataFim() != null ? req.dataFim() : req.dataInicio();
        if (fim.isBefore(req.dataInicio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A data final não pode ser antes da inicial.");
        }
        Bloqueio b = Bloqueio.builder()
                .tenantId(TenantContext.get())
                .dataInicio(req.dataInicio())
                .dataFim(fim)
                .descricao(req.descricao())
                .build();
        return toDto(repo.save(b));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable UUID id) {
        Bloqueio b = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!b.getTenantId().equals(TenantContext.get()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        repo.delete(b);
    }

    private BloqueioDto toDto(Bloqueio b) {
        return new BloqueioDto(b.getId(), b.getDataInicio(), b.getDataFim(), b.getDescricao());
    }
}
