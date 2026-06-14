package com.agendamento.backend.controller;

import com.agendamento.backend.dto.api.ConfiguracaoRequest;
import com.agendamento.backend.dto.api.ConfiguracaoResponse;
import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.repository.TenantRepository;
import com.agendamento.backend.security.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/configuracoes")
@RequiredArgsConstructor
public class ConfiguracaoController {

    private final TenantRepository tenantRepository;

    @GetMapping
    public ConfiguracaoResponse get() {
        return toDto(buscarTenant());
    }

    @PutMapping
    public ConfiguracaoResponse atualizar(@Valid @RequestBody ConfiguracaoRequest req) {
        Tenant t = buscarTenant();
        t.setNome(req.nome());
        t.setHorarioAbertura(req.horarioAbertura());
        t.setHorarioFechamento(req.horarioFechamento());
        return toDto(tenantRepository.save(t));
    }

    private ConfiguracaoResponse toDto(Tenant t) {
        return new ConfiguracaoResponse(t.getId(), t.getNome(), t.getTelefoneWhatsapp(),
                t.getHorarioAbertura(), t.getHorarioFechamento());
    }

    private Tenant buscarTenant() {
        return tenantRepository.findById(TenantContext.get())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
