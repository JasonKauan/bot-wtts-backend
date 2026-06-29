package com.agendamento.backend.dto.api;

/** Quantas vezes um serviço apareceu no período (relatórios). */
public record ServicoContagem(String servico, long total) {}
