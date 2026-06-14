package com.agendamento.backend.controller;

import com.agendamento.backend.dto.assinatura.AssinaturaStatusResponse;
import com.agendamento.backend.dto.assinatura.PagamentoStatusResponse;
import com.agendamento.backend.dto.assinatura.PixRequest;
import com.agendamento.backend.dto.assinatura.PixResponse;
import com.agendamento.backend.service.AssinaturaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/assinatura")
@RequiredArgsConstructor
public class AssinaturaController {

    private final AssinaturaService assinaturaService;

    @GetMapping
    public AssinaturaStatusResponse status() {
        return assinaturaService.status();
    }

    @PostMapping("/pix")
    public PixResponse gerarPix(@Valid @RequestBody PixRequest req) {
        return assinaturaService.gerarPix(req.plano());
    }

    /** Polling do painel (a cada 10s) até o pagamento ser confirmado. */
    @GetMapping("/pagamentos/{id}")
    public PagamentoStatusResponse pagamento(@PathVariable UUID id) {
        return assinaturaService.statusPagamento(id);
    }
}
