package com.agendamento.backend.dto.admin;

import lombok.Data;

/** Reset de senha do dono pelo admin. Senha vazia/nula = sistema gera e devolve. */
@Data
public class ResetSenhaRequest {
    private String senha;
}
