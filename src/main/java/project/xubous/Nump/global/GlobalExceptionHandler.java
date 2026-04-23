package project.xubous.Nump.global;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler
{
    // ── Erros de validação (@Valid) ───────────────────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex)
    {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error ->
        {
            String field   = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            errors.put(field, message);
        });

        return buildResponse(HttpStatus.BAD_REQUEST, "Erro de validação", errors);
    }

    // ── Regras de negócio (email duplicado, não encontrado…) ─────────────────
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex)
    {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    // ── Credenciais inválidas no login ────────────────────────────────────────
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex)
    {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Email ou senha incorretos", null);
    }

    // ── Erro genérico ─────────────────────────────────────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex)
    {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno: " + ex.getMessage(), null);
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status,
                                                               String message,
                                                               Object details)
    {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status",    status.value());
        body.put("message",   message);
        if (details != null) body.put("errors", details);

        return ResponseEntity.status(status).body(body);
    }
}