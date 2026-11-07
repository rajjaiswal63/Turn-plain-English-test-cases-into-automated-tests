package com.example.automation.controller;

import com.example.automation.model.AutomationModels.Request;
import com.example.automation.model.AutomationModels.Response;
import com.example.automation.service.SeleniumAutomationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/automation")
public class AutomationController {
    private final SeleniumAutomationService automation;

    public AutomationController(SeleniumAutomationService automation) { this.automation = automation; }

    @PostMapping("/run")
    public ResponseEntity<Response> run(@Valid @RequestBody Request request) {
        return ResponseEntity.ok(automation.run(request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Response> invalidRequest(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream().map(FieldError::getDefaultMessage)
                .distinct().collect(Collectors.joining(" "));
        return ResponseEntity.badRequest().body(Response.failed("", message, List.of()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Response> invalidJson() {
        return ResponseEntity.badRequest().body(Response.failed("", "Request body must be valid JSON with url and testCase fields.", List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response> unexpected() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Response.failed("", "Request could not be processed.", List.of()));
    }
}
