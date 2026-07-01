package com.agendamento.backend.dto.admin;

import lombok.Data;

/** Reset de senha do dono pelo admin. Senha vazia/nula = sistema gera e devolve. */
@Data
public class ResetSenhaRequest {
    @jakarta.validation.constraints.Size(min = 8, message = "A senha deve ter pelo menos 8 caracteres.")
    private String senha;
}
