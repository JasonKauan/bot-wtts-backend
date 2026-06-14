package com.agendamento.backend.controller;

import com.agendamento.backend.dto.api.ProfissionalRequest;
import com.agendamento.backend.entity.Profissional;
import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.repository.ProfissionalRepository;
import com.agendamento.backend.repository.TenantRepository;
import com.agendamento.backend.security.TenantContext;
import com.agendamento.backend.service.PlanoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/profissionais")
@RequiredArgsConstructor
public class ProfissionalController {

    private final ProfissionalRepository repo;
    private final TenantRepository tenantRepository;
    private final PlanoService planoService;

    @GetMapping
    public List<Profissional> listar() {
        return repo.findByTenantIdOrderByNome(TenantContext.get());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Profissional criar(@Valid @RequestBody ProfissionalRequest req) {
        validarLimitePlano(); // Iteração 6
        Profissional p = Profissional.builder()
                .tenantId(TenantContext.get())
                .nome(req.nome())
                .ativo(true)
                .build();
        return repo.save(p);
    }

    @PutMapping("/{id}")
    public Profissional atualizar(@PathVariable UUID id, @Valid @RequestBody ProfissionalRequest req) {
        Profissional p = buscarDoTenant(id);
        p.setNome(req.nome());
        return repo.save(p);
    }

    @PatchMapping("/{id}/ativo")
    public Profissional toggleAtivo(@PathVariable UUID id) {
        Profissional p = buscarDoTenant(id);
        if (!p.isAtivo()) validarLimitePlano(); // reativar também conta para o limite do plano
        p.setAtivo(!p.isAtivo());
        return repo.save(p);
    }

    /** Iteração 6: BASICO permite 1 profissional ativo, PRO permite 5. */
    private void validarLimitePlano() {
        Tenant t = tenantRepository.findById(TenantContext.get())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        planoService.validarNovoProfissional(t.getId(), t.getPlano());
    }

    private Profissional buscarDoTenant(UUID id) {
        Profissional p = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!p.getTenantId().equals(TenantContext.get()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return p;
    }
}
