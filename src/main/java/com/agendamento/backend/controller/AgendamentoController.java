package com.agendamento.backend.controller;

import com.agendamento.backend.dto.api.AgendamentoDto;
import com.agendamento.backend.entity.Agendamento;
import com.agendamento.backend.repository.AgendamentoRepository;
import com.agendamento.backend.security.TenantContext;
import com.agendamento.backend.service.EvolutionApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agendamentos")
@RequiredArgsConstructor
public class AgendamentoController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");

    private final AgendamentoRepository repo;
    private final EvolutionApiService evolutionApiService;

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
