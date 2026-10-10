package com.aiagent.controller;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream().findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage()).orElse("请求参数不合法");
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> invalid(IllegalArgumentException ex) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, Object>> conflict(IllegalStateException ex) {
        String message = ex.getMessage() == null ? "服务暂不可用" : ex.getMessage();
        HttpStatus status = message.contains("正在处理") || message.contains("重复") ? HttpStatus.CONFLICT : HttpStatus.SERVICE_UNAVAILABLE;
        return response(status, status == HttpStatus.CONFLICT ? "CONFLICT" : "SERVICE_UNAVAILABLE", message);
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message, "timestamp", Instant.now().toString()));
    }
}
