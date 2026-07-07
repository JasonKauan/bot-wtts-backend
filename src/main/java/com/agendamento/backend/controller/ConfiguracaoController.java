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
        if (req.horarioFechamento() <= req.horarioAbertura()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fechamento deve ser depois da abertura.");
        }
        boolean temAlmoco = req.almocoInicio() != null && req.almocoFim() != null;
        if (temAlmoco && req.almocoFim() <= req.almocoInicio()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fim do almoço deve ser depois do início.");
        }

        Tenant t = buscarTenant();
        t.setNome(req.nome());
        t.setHorarioAbertura(req.horarioAbertura());
        t.setHorarioFechamento(req.horarioFechamento());
        t.setIntervaloMinutos(req.intervaloMinutos());
        // Almoço só vale com os dois campos; caso contrário, sem almoço.
        t.setAlmocoInicio(temAlmoco ? req.almocoInicio() : null);
        t.setAlmocoFim(temAlmoco ? req.almocoFim() : null);
        t.setDiasFuncionamento(
                (req.diasFuncionamento() == null || req.diasFuncionamento().isBlank())
                        ? "1,2,3,4,5,6,7" : req.diasFuncionamento());
        t.setAprovacaoManual(req.aprovacaoManual());
        t.setAntecedenciaMinHoras(req.antecedenciaMinHoras());
        t.setResumoDiario(req.resumoDiario());
        t.setFaltasParaAprovacao(req.faltasParaAprovacao());
        return toDto(tenantRepository.save(t));
    }

    private ConfiguracaoResponse toDto(Tenant t) {
        return new ConfiguracaoResponse(t.getId(), t.getNome(), t.getTelefoneWhatsapp(),
                t.getHorarioAbertura(), t.getHorarioFechamento(),
                t.getIntervaloMinutos(), t.getAlmocoInicio(), t.getAlmocoFim(), t.getDiasFuncionamento(),
                t.isAprovacaoManual(), t.getAntecedenciaMinHoras(), t.isResumoDiario(),
                t.getFaltasParaAprovacao());
    }

    private Tenant buscarTenant() {
        return tenantRepository.findById(TenantContext.get())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
