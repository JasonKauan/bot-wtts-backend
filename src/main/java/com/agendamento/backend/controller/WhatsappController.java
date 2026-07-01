package com.agendamento.backend.controller;

import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.repository.TenantRepository;
import com.agendamento.backend.security.TenantContext;
import com.agendamento.backend.service.EvolutionApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Conexão do WhatsApp pelo painel: o dono escaneia o QR da própria instância
 * (nome da instância = UUID do tenant), sem precisar do manager da Evolution.
 */
@RestController
@RequestMapping("/api/whatsapp")
@RequiredArgsConstructor
@Slf4j
public class WhatsappController {

    private final EvolutionApiService evolutionApiService;
    private final TenantRepository tenantRepository;

    @GetMapping("/status")
    public Map<String, Object> status() {
        String instancia = TenantContext.get().toString();
        String estado = evolutionApiService.estadoConexao(instancia);
        return Map.of("estado", estado, "conectado", "open".equalsIgnoreCase(estado));
    }

    /**
     * Reset da conexão: faz LOGOUT (purga credenciais velhas — cura a sessão zumbi
     * pós device_removed em que o QR "gera mas nunca conecta") e gera um QR novo.
     */
    @PostMapping("/reconectar")
    public Map<String, Object> reconectar() {
        String instancia = TenantContext.get().toString();
        evolutionApiService.logout(instancia);
        String qr = evolutionApiService.obterQrCode(instancia);
        Map<String, Object> out = new HashMap<>();
        out.put("conectado", false);
        out.put("qr", qr != null ? qr : "");
        return out;
    }

    @GetMapping("/qr")
    public Map<String, Object> qr() {
        UUID tenantId = TenantContext.get();
        String instancia = tenantId.toString();

        // Se a instância não existe (ex.: cadastro falhou por timeout na Evolution),
        // recria já com o webhook antes de gerar o QR.
        if (!evolutionApiService.instanciaExiste(instancia)) {
            Tenant t = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant não encontrado."));
            try {
                evolutionApiService.criarInstancia(instancia);
                evolutionApiService.configurarWebhook(instancia, t.getWebhookSecret());
            } catch (Exception e) {
                log.warn("Falha ao recriar instância {}: {}", instancia, e.getMessage());
            }
        }

        Map<String, Object> out = new HashMap<>();
        String estado = evolutionApiService.estadoConexao(instancia);
        if ("open".equalsIgnoreCase(estado)) {
            out.put("conectado", true);
            out.put("qr", "");
        } else {
            out.put("conectado", false);
            String qr = evolutionApiService.obterQrCode(instancia);
            out.put("qr", qr != null ? qr : "");
        }
        return out;
    }
}
