package com.agendamento.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Lançada quando o tenant estoura um limite do plano (Iteração 6). */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class LimitePlanoException extends RuntimeException {
    public LimitePlanoException(String message) { super(message); }
}
