package com.example.automation.controller;

import com.example.automation.model.AutomationResponse;
import com.example.automation.service.AutomationException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AutomationResponse> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream().map(FieldError::getDefaultMessage)
                .distinct().collect(Collectors.joining(" "));
        return ResponseEntity.badRequest().body(AutomationResponse.failed("", message, List.of()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AutomationResponse> malformedJson() {
        return ResponseEntity.badRequest().body(AutomationResponse.failed("", "Request body must be valid JSON with url and testCase fields.", List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AutomationResponse> unexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AutomationResponse.failed("", "Request could not be processed.", List.of()));
    }
}
