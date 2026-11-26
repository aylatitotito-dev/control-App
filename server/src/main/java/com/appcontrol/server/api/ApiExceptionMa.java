package com.appcontrol.server.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Error de la API con el codigo HTTP que corresponde (401, 403, 404, 409, 429...). */
public class ApiExceptionMa extends RuntimeException {

    private final HttpStatus statusJt;

    public ApiExceptionMa(HttpStatus statusJt, String messageJt) {
        super(messageJt);
        this.statusJt = statusJt;
    }

    public static ApiExceptionMa badRequest(String messageJt) {
        return new ApiExceptionMa(HttpStatus.BAD_REQUEST, messageJt);
    }

    public static ApiExceptionMa forbidden(String messageJt) {
        return new ApiExceptionMa(HttpStatus.FORBIDDEN, messageJt);
    }

    public static ApiExceptionMa notFound(String messageJt) {
        return new ApiExceptionMa(HttpStatus.NOT_FOUND, messageJt);
    }

    /** Convierte la excepcion en una respuesta JSON {"error": "..."}. */
    @RestControllerAdvice
    static class HandlerJt {

        @ExceptionHandler(ApiExceptionMa.class)
        ResponseEntity<Map<String, String>> handleMa(ApiExceptionMa exMa) {
            return ResponseEntity.status(exMa.statusJt).body(Map.of("error", exMa.getMessage()));
        }
    }
}
