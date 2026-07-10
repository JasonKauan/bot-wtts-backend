package com.agendamento.backend.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * ResponseStatusException → responde direto (sem passar pelo ERROR dispatch /error),
     * preservando o status (401 credenciais inválidas, 403 conta inativa, 409 e-mail
     * duplicado, 429 rate-limit) e expondo a mensagem em JSON para o frontend.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        String message = ex.getReason() != null ? ex.getReason() : "Erro na requisição.";
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("status", ex.getStatusCode().value(), "message", message));
    }

    /** Limite de plano (Iteração 6) → 422 com a mensagem de orientação. */
    @ExceptionHandler(LimitePlanoException.class)
    public ResponseEntity<Map<String, Object>> handleLimitePlano(LimitePlanoException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(Map.of("status", 422, "message", ex.getMessage()));
    }

    /** Erros de validação (@Valid) → 400 com o primeiro campo inválido. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream().findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("Dados inválidos.");
        return ResponseEntity.badRequest()
                .body(Map.of("status", 400, "message", message));
    }

    /**
     * Violação de integridade (FK/unique no banco) → 409 legível em vez de 500 cru.
     * Foi o sintoma do excluir-profissional antes da V29; fica como rede de segurança.
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegridade(
            org.springframework.dao.DataIntegrityViolationException ex) {
        return ResponseEntity.status(409).body(Map.of("status", 409,
                "message", "Não foi possível concluir: existem registros vinculados a esse item."));
    }
}
