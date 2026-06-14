package com.agendamento.backend.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    /** Nome da instância na Evolution API — owner usa para conectar o WhatsApp via QR code. */
    private String instanceName;
}
