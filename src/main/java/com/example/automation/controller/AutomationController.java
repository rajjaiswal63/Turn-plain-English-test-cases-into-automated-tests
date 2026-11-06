package com.example.automation.controller;

import com.example.automation.model.AutomationRequest;
import com.example.automation.model.AutomationResponse;
import com.example.automation.service.SeleniumAutomationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/automation")
public class AutomationController {
    private final SeleniumAutomationService automationService;

    public AutomationController(SeleniumAutomationService automationService) {
        this.automationService = automationService;
    }

    @PostMapping("/run")
    public ResponseEntity<AutomationResponse> run(@Valid @RequestBody AutomationRequest request) {
        AutomationResponse response = automationService.run(request);
        return ResponseEntity.ok(response);
    }
}
