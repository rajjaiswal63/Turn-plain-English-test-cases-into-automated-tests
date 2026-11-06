package com.example.automation.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AutomationRequest(
        @NotBlank @Size(max = 2048) String url,
        @NotBlank @Size(max = 4000) String testCase) {
}
