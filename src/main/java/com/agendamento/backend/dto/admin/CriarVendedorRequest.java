package com.agendamento.backend.dto.admin;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/** CEO cria um vendedor. */
@Data
public class CriarVendedorRequest {

    @NotBlank
    private String nome;

    @Email @NotBlank
    private String email;

    @NotBlank
    @Size(min = 8, message = "A senha deve ter pelo menos 8 caracteres.")
    private String senha;

    @NotNull
    @DecimalMin(value = "0", message = "Comissão não pode ser negativa.")
    @DecimalMax(value = "100", message = "Comissão máxima é 100%.")
    private BigDecimal comissaoPct;
}
