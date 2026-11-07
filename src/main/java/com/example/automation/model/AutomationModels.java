package com.example.automation.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AutomationModels {
    private AutomationModels() {}

    public record Request(@NotBlank @Size(max = 2048) String url,
                          @NotBlank @Size(max = 4000) String testCase) {}

    public record Response(boolean success, String status, String testCase, String message,
                           List<String> executedSteps, String error) {
        public static Response passed(String testCase, String message, List<String> steps) {
            return new Response(true, "PASS", testCase, message, List.copyOf(steps), null);
        }
        public static Response failed(String testCase, String error, List<String> steps) {
            return new Response(false, "FAIL", testCase, error, List.copyOf(steps), error);
        }
    }

    public record Plan(String summary, List<Step> steps) {}
    public record Step(String action, String target, String value, String expected, int timeoutSeconds) {}

    public static class Failure extends RuntimeException {
        public Failure(String message) { super(message); }
        public Failure(String message, Throwable cause) { super(message, cause); }
    }
}

