package com.agendamento.backend.service;

import com.agendamento.backend.dto.assinatura.AssinaturaStatusResponse;
import com.agendamento.backend.dto.assinatura.PagamentoStatusResponse;
import com.agendamento.backend.dto.assinatura.PixResponse;
import com.agendamento.backend.entity.Pagamento;
import com.agendamento.backend.entity.Plano;
import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.repository.PagamentoRepository;
import com.agendamento.backend.repository.TenantRepository;
import com.agendamento.backend.repository.UsuarioRepository;
import com.agendamento.backend.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Fluxo de assinatura/cobrança — Iteração 6. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssinaturaService {

    private final TenantRepository tenantRepository;
    private final PagamentoRepository pagamentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MercadoPagoService mercadoPagoService;

    public AssinaturaStatusResponse status() {
        Tenant t = tenantAtual();
        LocalDateTime expiraEm = t.getPlano() == Plano.TRIAL ? t.getTrialExpiraEm() : t.getAssinaturaExpiraEm();
        long diasRestantes = expiraEm == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(LocalDateTime.now(), expiraEm));
        boolean vencida = t.isAssinaturaVencida();
        // bot-zap It.6: aviso a partir do 25º dia do trial ("vence em 5 dias")
        boolean avisoTrial = t.getPlano() == Plano.TRIAL && !vencida && diasRestantes <= 5;
        return new AssinaturaStatusResponse(t.getPlano().name(), t.getPlano().getValorMensal(),
                expiraEm, diasRestantes, vencida, avisoTrial);
    }

    @Transactional
    public PixResponse gerarPix(Plano plano) {
        if (plano == null || plano == Plano.TRIAL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha um plano pago (BASICO, PRO ou PLUS).");
        }
        Tenant t = tenantAtual();

        Pagamento pagamento = Pagamento.builder()
                .tenantId(t.getId())
                .valor(plano.getValorMensal())
                .plano(plano)
                .mesReferencia(YearMonth.now().toString())
                .build();
        pagamentoRepository.save(pagamento);

        String emailPagador = usuarioRepository.findFirstByTenantId(t.getId())
                .map(u -> u.getEmail())
                .orElse("pagador@agendabot.local");

        MercadoPagoService.PixCriado pix;
        try {
            pix = mercadoPagoService.criarPix(pagamento, emailPagador,
                    "AgendaBot — Plano " + plano + " (" + pagamento.getMesReferencia() + ")");
        } catch (RestClientException e) {
            log.error("Falha ao criar PIX no Mercado Pago para tenant {}: {}", t.getId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha ao gerar o PIX. Tente novamente.");
        }

        pagamento.setMercadoPagoId(pix.mercadoPagoId());
        pagamentoRepository.save(pagamento);

        return new PixResponse(pagamento.getId(), pagamento.getValor(),
                pix.qrCode(), pix.qrCodeBase64(), pix.ticketUrl());
    }

    public PagamentoStatusResponse statusPagamento(UUID pagamentoId) {
        Pagamento p = pagamentoRepository.findByIdAndTenantId(pagamentoId, TenantContext.get())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pagamento não encontrado."));
        return new PagamentoStatusResponse(p.getId(), p.getStatus(), p.getPlano().name(), p.getValor());
    }

    /**
     * Processa a notificação do Mercado Pago. Sempre consulta o status real na API
     * (não confia no payload do webhook). Idempotente.
     */
    @Transactional
    public void processarWebhook(String mercadoPagoId) {
        Pagamento pagamento = pagamentoRepository.findByMercadoPagoId(mercadoPagoId).orElse(null);
        if (pagamento == null) {
            log.warn("Webhook MP ignorado: pagamento {} não encontrado", mercadoPagoId);
            return;
        }
        if ("APROVADO".equals(pagamento.getStatus())) return;

        String statusMp = mercadoPagoService.consultarStatus(mercadoPagoId);
        if ("approved".equalsIgnoreCase(statusMp)) {
            pagamento.setStatus("APROVADO");
            pagamentoRepository.save(pagamento);

            Tenant t = tenantRepository.findById(pagamento.getTenantId()).orElseThrow();
            t.setPlano(pagamento.getPlano());
            t.setAssinaturaExpiraEm(LocalDateTime.now().plusDays(30)); // bot-zap: hoje + 30 dias
            tenantRepository.save(t);
            log.info("Assinatura ativada: tenant {} → plano {} até {}",
                    t.getId(), t.getPlano(), t.getAssinaturaExpiraEm());
        } else if ("rejected".equalsIgnoreCase(statusMp) || "cancelled".equalsIgnoreCase(statusMp)) {
            pagamento.setStatus("REJEITADO");
            pagamentoRepository.save(pagamento);
            log.info("Pagamento {} rejeitado/cancelado no MP", mercadoPagoId);
        } else {
            log.info("Webhook MP: pagamento {} ainda com status '{}'", mercadoPagoId, statusMp);
        }
    }

    private Tenant tenantAtual() {
        return tenantRepository.findById(TenantContext.get())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
