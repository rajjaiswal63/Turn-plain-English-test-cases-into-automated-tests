package com.example.automation.enums;

import java.util.Arrays;

public enum AutomationAction {
    NAVIGATE("navigate"), CLICK("click"), FILL("fill"), SELECT("select"), WAIT("wait"),
    VERIFY_VISIBLE("verify_visible"), VERIFY_TEXT("verify_text"), VERIFY_URL("verify_url");

    private final String wireName;
    AutomationAction(String wireName) { this.wireName = wireName; }
    public String wireName() { return wireName; }

    public static AutomationAction from(String value) {
        if (value == null) throw new IllegalArgumentException("AI plan contains an empty action.");
        return Arrays.stream(values()).filter(a -> a.wireName.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported browser action: " + value));
    }
}
