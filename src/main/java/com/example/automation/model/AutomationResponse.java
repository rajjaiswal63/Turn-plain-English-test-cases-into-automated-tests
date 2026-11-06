package com.example.automation.model;

import java.util.List;

public record AutomationResponse(
        boolean success,
        String status,
        String testCase,
        String message,
        List<String> executedSteps,
        String error) {

    public static AutomationResponse passed(String testCase, String message, List<String> steps) {
        return new AutomationResponse(true, "PASS", testCase, message, List.copyOf(steps), null);
    }

    public static AutomationResponse failed(String testCase, String error, List<String> steps) {
        return new AutomationResponse(false, "FAIL", testCase, error, List.copyOf(steps), error);
    }
}
