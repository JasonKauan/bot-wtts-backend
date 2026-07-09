package com.agendamento.backend.controller;

import com.agendamento.backend.dto.api.BloqueioDto;
import com.agendamento.backend.dto.api.BloqueioRequest;
import com.agendamento.backend.entity.Bloqueio;
import com.agendamento.backend.entity.Profissional;
import com.agendamento.backend.repository.BloqueioRepository;
import com.agendamento.backend.repository.ProfissionalRepository;
import com.agendamento.backend.security.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Folgas/feriados do estabelecimento — ou de um profissional específico (V21). */
@RestController
@RequestMapping("/api/bloqueios")
@RequiredArgsConstructor
public class BloqueioController {

    private final BloqueioRepository repo;
    private final ProfissionalRepository profissionalRepository;

    @GetMapping
    public List<BloqueioDto> listar() {
        UUID tenantId = TenantContext.get();
        Map<UUID, String> nomes = profissionalRepository.findByTenantIdOrderByNome(tenantId)
                .stream().collect(Collectors.toMap(Profissional::getId, Profissional::getNome));
        return repo.findByTenantIdOrderByDataInicioAsc(tenantId)
                .stream().map(b -> toDto(b, nomes)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BloqueioDto criar(@Valid @RequestBody BloqueioRequest req) {
        UUID tenantId = TenantContext.get();
        LocalDate fim = req.dataFim() != null ? req.dataFim() : req.dataInicio();
        if (fim.isBefore(req.dataInicio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A data final não pode ser antes da inicial.");
        }
        // Compromisso avulso (V24): as duas horas juntas, e fim depois do início.
        boolean temHora = req.horaInicio() != null && req.horaFim() != null;
        if ((req.horaInicio() != null) != (req.horaFim() != null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o início e o fim do horário.");
        }
        if (temHora && req.horaFim().compareTo(req.horaInicio()) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O fim deve ser depois do início.");
        }
        Profissional prof = null;
        if (req.profissionalId() != null) {
            prof = profissionalRepository.findById(req.profissionalId())
                    .filter(p -> p.getTenantId().equals(tenantId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profissional inválido."));
        }
        Bloqueio b = Bloqueio.builder()
                .tenantId(tenantId)
                .profissionalId(prof != null ? prof.getId() : null)
                .dataInicio(req.dataInicio())
                .dataFim(fim)
                .horaInicio(temHora ? req.horaInicio() : null)
                .horaFim(temHora ? req.horaFim() : null)
                .descricao(req.descricao())
                .build();
        b = repo.save(b);
        return new BloqueioDto(b.getId(), b.getDataInicio(), b.getDataFim(), b.getDescricao(),
                b.getProfissionalId(), prof != null ? prof.getNome() : null,
                b.getHoraInicio(), b.getHoraFim());
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

    private BloqueioDto toDto(Bloqueio b, Map<UUID, String> nomes) {
        return new BloqueioDto(b.getId(), b.getDataInicio(), b.getDataFim(), b.getDescricao(),
                b.getProfissionalId(),
                b.getProfissionalId() != null ? nomes.get(b.getProfissionalId()) : null,
                b.getHoraInicio(), b.getHoraFim());
    }
}
