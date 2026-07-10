package com.agendamento.backend.service;

import com.agendamento.backend.entity.BotMensagem;
import com.agendamento.backend.repository.BotMensagemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/** Log das conversas do bot (V28). Gravação best-effort: NUNCA derruba o fluxo do bot. */
@Service
@RequiredArgsConstructor
@Slf4j
public class BotMensagemService {

    private static final int RETENCAO_DIAS = 90;

    private final BotMensagemRepository repo;

    public void registrar(UUID tenantId, String telefone, String clienteNome, boolean deCliente, String texto) {
        try {
            repo.save(BotMensagem.builder()
                    .tenantId(tenantId)
                    .telefone(telefone)
                    .clienteNome(clienteNome)
                    .deCliente(deCliente)
                    .texto(texto)
                    .build());
        } catch (Exception e) {
            log.warn("[BotMensagem] Falha ao gravar mensagem do tenant {}: {}", tenantId, e.getMessage());
        }
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void limparAntigas() {
        repo.deleteByCriadoEmBefore(LocalDateTime.now().minusDays(RETENCAO_DIAS));
    }
}
