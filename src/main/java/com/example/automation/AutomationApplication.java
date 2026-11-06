package com.example.automation;

import com.example.automation.config.GroqProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(GroqProperties.class)
public class AutomationApplication {
    public static void main(String[] args) {
        SpringApplication.run(AutomationApplication.class, args);
    }
}
