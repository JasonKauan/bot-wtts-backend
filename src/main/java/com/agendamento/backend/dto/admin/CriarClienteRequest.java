package com.agendamento.backend.dto.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Onboard de um cliente pelo admin (Fase 2). */
@Data
public class CriarClienteRequest {

    @NotBlank
    private String nome;

    @NotBlank
    @Email
    private String email;

    /** Opcional. */
    private String telefone;

    /** Opcional: se vazio/nulo, o sistema gera uma senha provisória e devolve pro vendedor repassar. */
    @jakarta.validation.constraints.Size(min = 8, message = "A senha deve ter pelo menos 8 caracteres.")
    private String senha;

    /** Opcional: duração do trial em dias (nulo = padrão 14). */
    private Integer trialDias;

    /** Opcional: já ativar um plano pago no onboard (fechar a venda em 1 passo). */
    @Valid
    private PlanoRequest plano;
}
