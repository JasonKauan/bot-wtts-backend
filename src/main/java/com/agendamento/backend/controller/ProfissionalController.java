package com.agendamento.backend.controller;

import com.agendamento.backend.dto.api.ProfissionalRequest;
import com.agendamento.backend.entity.Profissional;
import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.repository.AgendamentoRepository;
import com.agendamento.backend.repository.BloqueioRepository;
import com.agendamento.backend.repository.ProfissionalRepository;
import com.agendamento.backend.repository.RecorrenciaRepository;
import com.agendamento.backend.repository.TenantRepository;
import com.agendamento.backend.security.TenantContext;
import com.agendamento.backend.service.PlanoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/profissionais")
@RequiredArgsConstructor
public class ProfissionalController {

    private final ProfissionalRepository repo;
    private final TenantRepository tenantRepository;
    private final PlanoService planoService;
    private final AgendamentoRepository agendamentoRepository;
    private final RecorrenciaRepository recorrenciaRepository;
    private final BloqueioRepository bloqueioRepository;

    @GetMapping
    public List<Profissional> listar() {
        return repo.findByTenantIdOrderByNome(TenantContext.get());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Profissional criar(@Valid @RequestBody ProfissionalRequest req) {
        validarLimitePlano(); // Iteração 6
        validarGrade(req);
        Profissional p = Profissional.builder()
                .tenantId(TenantContext.get())
                .nome(req.nome())
                .ativo(true)
                .build();
        aplicarGrade(p, req);
        return repo.save(p);
    }

    @PutMapping("/{id}")
    public Profissional atualizar(@PathVariable UUID id, @Valid @RequestBody ProfissionalRequest req) {
        validarGrade(req);
        Profissional p = buscarDoTenant(id);
        p.setNome(req.nome());
        aplicarGrade(p, req);
        return repo.save(p);
    }

    /**
     * Grade própria é tudo-ou-nada: com qualquer campo presente, abertura/fechamento/dias
     * são obrigatórios (evita grade "meio herdada" ambígua). Sem nenhum campo = herda do tenant.
     */
    private void validarGrade(ProfissionalRequest req) {
        if (!req.temGrade()) return;
        planoService.exigir(TenantContext.get(), com.agendamento.backend.entity.Plano.Recurso.GRADE_PROFISSIONAL);
        if (req.horarioAbertura() == null || req.horarioFechamento() == null
                || req.diasTrabalho() == null || req.diasTrabalho().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Grade própria precisa de abertura, fechamento e dias de trabalho.");
        }
        if (req.horarioFechamento() <= req.horarioAbertura()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fechamento deve ser depois da abertura.");
        }
        boolean temAlmoco = req.almocoInicio() != null && req.almocoFim() != null;
        if ((req.almocoInicio() != null) != (req.almocoFim() != null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe início e fim do almoço.");
        }
        if (temAlmoco && req.almocoFim() <= req.almocoInicio()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fim do almoço deve ser depois do início.");
        }
    }

    private void aplicarGrade(Profissional p, ProfissionalRequest req) {
        boolean tem = req.temGrade();
        p.setHorarioAbertura(tem ? req.horarioAbertura() : null);
        p.setHorarioFechamento(tem ? req.horarioFechamento() : null);
        p.setAlmocoInicio(tem ? req.almocoInicio() : null);
        p.setAlmocoFim(tem ? req.almocoFim() : null);
        p.setDiasTrabalho(tem ? req.diasTrabalho() : null);
    }

    /**
     * Exclusão de verdade. O histórico antigo NÃO se perde (o agendamento guarda o nome).
     * Barra se houver horário futuro ativo ou cliente fixo dependendo dele — mensagem
     * diz exatamente o que resolver. As folgas do profissional são removidas junto.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void remover(@PathVariable UUID id) {
        Profissional p = buscarDoTenant(id);
        UUID tenantId = TenantContext.get();
        if (agendamentoRepository.existsByTenantIdAndProfissionalIdAndStatusInAndDataHoraAfter(
                tenantId, id, List.of("CONFIRMADO", "PENDENTE"), LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esse profissional ainda tem horários futuros marcados. Cancele ou remarque esses horários — ou apenas Desative o profissional.");
        }
        if (recorrenciaRepository.existsByTenantIdAndProfissionalIdAndAtivoTrue(tenantId, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esse profissional está em um cliente fixo. Remova ou pause o cliente fixo (aba Clientes fixos) antes de excluir.");
        }
        bloqueioRepository.deleteByTenantIdAndProfissionalId(tenantId, id);
        repo.delete(p);
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
