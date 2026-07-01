package com.agendamento.backend.controller;

import com.agendamento.backend.dto.api.AgendamentoDto;
import com.agendamento.backend.dto.api.NovoAgendamentoRequest;
import com.agendamento.backend.entity.Agendamento;
import com.agendamento.backend.entity.Profissional;
import com.agendamento.backend.entity.Servico;
import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.repository.AgendamentoRepository;
import com.agendamento.backend.repository.ProfissionalRepository;
import com.agendamento.backend.repository.ServicoRepository;
import com.agendamento.backend.repository.TenantRepository;
import com.agendamento.backend.security.TenantContext;
import com.agendamento.backend.service.DisponibilidadeService;
import com.agendamento.backend.service.EvolutionApiService;
import com.agendamento.backend.service.PlanoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agendamentos")
@RequiredArgsConstructor
public class AgendamentoController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");

    private final AgendamentoRepository repo;
    private final ServicoRepository servicoRepository;
    private final ProfissionalRepository profissionalRepository;
    private final TenantRepository tenantRepository;
    private final DisponibilidadeService disponibilidadeService;
    private final PlanoService planoService;
    private final EvolutionApiService evolutionApiService;

    /**
     * Agendamento manual pelo dono (cliente que ligou/chegou na porta). Vale como ENCAIXE:
     * só barra conflito de horário e passado — não exige que a hora esteja na grade do bot.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgendamentoDto criar(@Valid @RequestBody NovoAgendamentoRequest req) {
        UUID tenantId = TenantContext.get();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        Servico servico = servicoRepository.findById(req.servicoId())
                .filter(s -> s.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Serviço inválido."));

        Profissional prof = null;
        if (req.profissionalId() != null) {
            prof = profissionalRepository.findById(req.profissionalId())
                    .filter(p -> p.getTenantId().equals(tenantId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profissional inválido."));
        }

        LocalDateTime dataHora = LocalDateTime.of(req.data(), LocalTime.parse(normalizarHora(req.hora())));
        if (dataHora.isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esse horário já passou.");
        }

        planoService.validarNovoAgendamento(tenant); // limite do plano vale pro manual também

        int duracao = Math.max(1, servico.getDuracaoMinutos());
        if (disponibilidadeService.conflita(tenant, prof != null ? prof.getId() : null, dataHora, duracao)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esse horário já está ocupado. Escolha outro.");
        }

        Agendamento ag = Agendamento.builder()
                .tenantId(tenantId)
                .clienteNome(req.clienteNome().trim())
                .clienteTelefone(req.clienteTelefone() != null ? req.clienteTelefone().trim() : "")
                .servico(servico.getNome())
                .profissional(prof != null ? prof.getNome() : null)
                .profissionalId(prof != null ? prof.getId() : null)
                .duracaoMinutos(duracao)
                .dataHora(dataHora)
                .status("CONFIRMADO")
                .build();
        return toDto(repo.save(ag));
    }

    /** "9:00" → "09:00" (LocalTime.parse exige dois dígitos). */
    private String normalizarHora(String h) {
        String[] p = h.split(":");
        return String.format("%02d:%02d", Integer.parseInt(p[0]), Integer.parseInt(p[1]));
    }

    /** Fila de solicitações: agendamentos PENDENTE futuros do estabelecimento. */
    @GetMapping("/pendentes")
    public List<AgendamentoDto> pendentes() {
        return repo.findByTenantIdAndStatusAndDataHoraAfterOrderByDataHora(
                        TenantContext.get(), "PENDENTE", LocalDateTime.now())
                .stream().map(this::toDto).toList();
    }

    @PatchMapping("/{id}/cancelar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@PathVariable UUID id) {
        atualizarStatus(id, "CANCELADO");
    }

    @PatchMapping("/{id}/confirmar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmar(@PathVariable UUID id) {
        Agendamento ag = buscar(id);
        boolean eraPendente = "PENDENTE".equals(ag.getStatus());
        ag.setStatus("CONFIRMADO");
        repo.save(ag);
        // Avisa o cliente só quando saiu da fila (PENDENTE → CONFIRMADO).
        if (eraPendente) {
            notificar(ag, "✅ Boa notícia! Seu agendamento de *" + ag.getServico() + "* em *"
                    + ag.getDataHora().format(FMT) + "* foi *confirmado*. Te esperamos! 😊");
        }
    }

    /** Recusar um pedido pendente: cancela e avisa o cliente (libera o horário). */
    @PatchMapping("/{id}/recusar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recusar(@PathVariable UUID id) {
        Agendamento ag = buscar(id);
        ag.setStatus("CANCELADO");
        repo.save(ag);
        notificar(ag, "😕 Poxa, não foi possível confirmar seu horário de *" + ag.getServico() + "* em *"
                + ag.getDataHora().format(FMT) + "*. Quer tentar outro? É só mandar *oi* que a gente acha um horário 👍");
    }

    @PatchMapping("/{id}/nao-compareceu")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void naoCompareceu(@PathVariable UUID id) {
        atualizarStatus(id, "NAO_COMPARECEU");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private Agendamento buscar(UUID id) {
        Agendamento ag = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!ag.getTenantId().equals(TenantContext.get()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return ag;
    }

    private void atualizarStatus(UUID id, String status) {
        Agendamento ag = buscar(id);
        ag.setStatus(status);
        repo.save(ag);
    }

    /** Manda WhatsApp pro cliente pela instância do tenant; nunca derruba a requisição se a IA/Evolution falhar. */
    private void notificar(Agendamento ag, String texto) {
        try {
            evolutionApiService.enviarMensagemNaInstancia(
                    ag.getTenantId().toString(), ag.getClienteTelefone(), texto);
        } catch (Exception e) {
            // best-effort: o status já foi salvo; aviso é secundário.
        }
    }

    private AgendamentoDto toDto(Agendamento a) {
        return new AgendamentoDto(a.getId(), a.getClienteNome(), a.getClienteTelefone(),
                a.getServico(), a.getProfissional(), a.getDataHora(), a.getStatus());
    }
}
