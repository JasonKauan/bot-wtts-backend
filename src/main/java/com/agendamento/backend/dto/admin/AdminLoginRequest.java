package com.agendamento.backend.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Login do back-office (SUPERADMIN) — tela /admin/login. */
@Data
public class AdminLoginRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String senha;
}
