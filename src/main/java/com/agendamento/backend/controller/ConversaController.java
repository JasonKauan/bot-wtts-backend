package com.agendamento.backend.controller;

import com.agendamento.backend.entity.BotMensagem;
import com.agendamento.backend.entity.Plano;
import com.agendamento.backend.repository.BotMensagemRepository;
import com.agendamento.backend.security.TenantContext;
import com.agendamento.backend.service.PlanoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/** Histórico das conversas do bot (V28) — o dono audita o que o robô falou com cada cliente. */
@RestController
@RequestMapping("/api/conversas")
@RequiredArgsConstructor
public class ConversaController {

    private final BotMensagemRepository repo;
    private final PlanoService planoService;

    public record ConversaResumo(String telefone, String clienteNome, String ultimaMensagem,
                                 boolean deCliente, LocalDateTime em, long mensagens) {}

    public record MensagemDto(boolean deCliente, String texto, LocalDateTime em) {}

    /** Conversas recentes agrupadas por cliente (mais recentes primeiro). */
    @GetMapping
    public List<ConversaResumo> listar() {
        planoService.exigir(TenantContext.get(), Plano.Recurso.CONVERSAS);
        var porFone = repo.findTop500ByTenantIdOrderByCriadoEmDesc(TenantContext.get()).stream()
                .collect(Collectors.groupingBy(BotMensagem::getTelefone,
                        LinkedHashMap::new, Collectors.toList()));
        return porFone.entrySet().stream().map(e -> {
            BotMensagem ultima = e.getValue().get(0);
            String nome = e.getValue().stream().map(BotMensagem::getClienteNome)
                    .filter(n -> n != null && !n.isBlank()).findFirst().orElse(null);
            return new ConversaResumo(e.getKey(), nome, ultima.getTexto(),
                    ultima.isDeCliente(), ultima.getCriadoEm(), e.getValue().size());
        }).toList();
    }

    /** A conversa de um cliente (até 100 mensagens, em ordem cronológica). */
    @GetMapping("/{telefone}")
    public List<MensagemDto> mensagens(@PathVariable String telefone) {
        planoService.exigir(TenantContext.get(), Plano.Recurso.CONVERSAS);
        List<MensagemDto> out = repo
                .findTop100ByTenantIdAndTelefoneOrderByCriadoEmDesc(TenantContext.get(), telefone)
                .stream().map(m -> new MensagemDto(m.isDeCliente(), m.getTexto(), m.getCriadoEm()))
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.reverse(out);
        return out;
    }
}
