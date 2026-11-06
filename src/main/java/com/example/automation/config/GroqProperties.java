package com.example.automation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "groq")
public class GroqProperties {
    private String apiKey = "";
    private String legacyApiKey = "";
    private String model = "openai/gpt-oss-20b";

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getLegacyApiKey() { return legacyApiKey; }
    public void setLegacyApiKey(String legacyApiKey) { this.legacyApiKey = legacyApiKey; }
    public String getEffectiveApiKey() {
        return apiKey != null && !apiKey.isBlank() ? apiKey : legacyApiKey;
    }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}
