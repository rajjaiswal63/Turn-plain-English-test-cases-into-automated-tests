package com.example.automation.model;

public record TestStep(String action, String target, String value, String expected, int timeoutSeconds) {
}
