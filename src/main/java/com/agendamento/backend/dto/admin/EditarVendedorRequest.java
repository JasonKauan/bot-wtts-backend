package com.agendamento.backend.dto.admin;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** CEO edita um vendedor (só os campos enviados mudam). */
@Data
public class EditarVendedorRequest {

    private String nome;

    @DecimalMin(value = "0", message = "Comissão não pode ser negativa.")
    @DecimalMax(value = "100", message = "Comissão máxima é 100%.")
    private BigDecimal comissaoPct;

    /** Mensalidades comissionadas por cliente. 0 = vitalício. */
    @Min(value = 0, message = "Não pode ser negativo.")
    @Max(value = 60, message = "Máximo de 60 mensalidades.")
    private Integer comissaoMeses;

    private Boolean ativo;

    @Size(min = 8, message = "A senha deve ter pelo menos 8 caracteres.")
    private String senha;
}
