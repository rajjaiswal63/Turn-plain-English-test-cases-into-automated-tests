package com.example.automation.model;

import java.util.List;

public record TestPlan(String summary, List<TestStep> steps) {
}
