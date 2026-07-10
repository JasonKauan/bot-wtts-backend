package com.agendamento.backend.controller;

import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.repository.TenantRepository;
import com.agendamento.backend.security.TenantContext;
import com.agendamento.backend.service.BotService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Test-drive do bot no painel: conversa real (mesmo fluxo), sem WhatsApp e sem salvar agendamento. */
@RestController
@RequestMapping("/api/bot")
@RequiredArgsConstructor
public class BotTesteController {

    private final BotService botService;
    private final TenantRepository tenantRepository;

    public record SimularRequest(@NotBlank String mensagem) {}

    @PostMapping("/simular")
    public List<BotService.RespostaSimulada> simular(@Valid @RequestBody SimularRequest req) {
        return botService.simular(buscarTenant(), req.mensagem());
    }

    /** Recomeçar a conversa de teste. */
    @DeleteMapping("/simular")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetar() {
        botService.resetarSimulacao(buscarTenant());
    }

    private Tenant buscarTenant() {
        return tenantRepository.findById(TenantContext.get())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
