package com.agendamento.backend.dto.assinatura;

import java.math.BigDecimal;
import java.util.UUID;

/** Dados do PIX gerado para o frontend exibir QR code + copia-e-cola. */
public record PixResponse(
        UUID pagamentoId,
        BigDecimal valor,
        String qrCode,
        String qrCodeBase64,
        String ticketUrl
) {}
