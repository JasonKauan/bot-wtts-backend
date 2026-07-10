package com.agendamento.backend.controller;

import com.agendamento.backend.dto.api.RecorrenciaDto;
import com.agendamento.backend.dto.api.RecorrenciaRequest;
import com.agendamento.backend.entity.Plano;
import com.agendamento.backend.entity.Profissional;
import com.agendamento.backend.entity.Recorrencia;
import com.agendamento.backend.entity.Servico;
import com.agendamento.backend.repository.ProfissionalRepository;
import com.agendamento.backend.repository.RecorrenciaRepository;
import com.agendamento.backend.repository.ServicoRepository;
import com.agendamento.backend.security.TenantContext;
import com.agendamento.backend.service.PlanoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Clientes fixos (V26): "toda quinta às 19h". O job do RecorrenciaService gera os agendamentos. */
@RestController
@RequestMapping("/api/recorrencias")
@RequiredArgsConstructor
public class RecorrenciaController {

    private final RecorrenciaRepository repo;
    private final ServicoRepository servicoRepository;
    private final ProfissionalRepository profissionalRepository;
    private final PlanoService planoService;

    @GetMapping
    public List<RecorrenciaDto> listar() {
        return repo.findByTenantIdOrderByClienteNome(TenantContext.get())
                .stream().map(this::toDto).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecorrenciaDto criar(@Valid @RequestBody RecorrenciaRequest req) {
        UUID tenantId = TenantContext.get();
        planoService.exigir(tenantId, Plano.Recurso.RECORRENCIA);
        if (req.primeiraData().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A primeira data não pode estar no passado.");
        }
        Servico servico = servicoRepository.findById(req.servicoId())
                .filter(s -> s.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Serviço inválido."));
        Profissional prof = null;
        if (req.profissionalId() != null) {
            prof = profissionalRepository.findById(req.profissionalId())
                    .filter(p -> p.getTenantId().equals(tenantId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profissional inválido."));
        }
        Recorrencia r = Recorrencia.builder()
                .tenantId(tenantId)
                .clienteNome(req.clienteNome().trim())
                .clienteTelefone(req.clienteTelefone() != null ? req.clienteTelefone().trim() : null)
                .servico(servico.getNome())
                .profissionalId(prof != null ? prof.getId() : null)
                .profissional(prof != null ? prof.getNome() : null)
                .frequenciaDias(req.frequenciaDias())
                .hora(req.hora())
                .proximaData(req.primeiraData())
                .ativo(true)
                .build();
        return toDto(repo.save(r));
    }

    @PatchMapping("/{id}/ativo")
    public RecorrenciaDto toggleAtivo(@PathVariable UUID id) {
        planoService.exigir(TenantContext.get(), Plano.Recurso.RECORRENCIA);
        Recorrencia r = buscarDoTenant(id);
        r.setAtivo(!r.isAtivo());
        return toDto(repo.save(r));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable UUID id) {
        repo.delete(buscarDoTenant(id));
    }

    private Recorrencia buscarDoTenant(UUID id) {
        Recorrencia r = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!r.getTenantId().equals(TenantContext.get()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return r;
    }

    private RecorrenciaDto toDto(Recorrencia r) {
        return new RecorrenciaDto(r.getId(), r.getClienteNome(), r.getClienteTelefone(),
                r.getServico(), r.getProfissional(), r.getFrequenciaDias(), r.getHora(),
                r.getProximaData(), r.isAtivo());
    }
}
