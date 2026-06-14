package com.agendamento.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank
    private String nomeEstabelecimento;

    @NotBlank
    private String telefoneWhatsapp;

    @Email @NotBlank
    private String email;

    @NotBlank
    private String senha;
}
