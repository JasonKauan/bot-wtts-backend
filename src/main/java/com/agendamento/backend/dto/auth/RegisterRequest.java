package com.agendamento.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @Size(min = 8, message = "A senha deve ter pelo menos 8 caracteres.")
    private String senha;
}
