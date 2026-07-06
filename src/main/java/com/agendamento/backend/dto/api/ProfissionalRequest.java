package com.agendamento.backend.dto.api;

import jakarta.validation.constraints.NotBlank;

/**
 * Grade própria (V18) é opcional: todos os campos nulos = o profissional segue a grade
 * do estabelecimento. Quando houver grade própria, abertura/fechamento/dias vêm juntos
 * (o controller valida); almoço é opcional dentro dela.
 */
public record ProfissionalRequest(
        @NotBlank String nome,
        Integer horarioAbertura,
        Integer horarioFechamento,
        Integer almocoInicio,
        Integer almocoFim,
        String diasTrabalho) {

    public boolean temGrade() {
        return horarioAbertura != null || horarioFechamento != null
                || almocoInicio != null || almocoFim != null
                || (diasTrabalho != null && !diasTrabalho.isBlank());
    }
}
