package com.example.automation.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class UrlSafetyValidatorTest {
    private final UrlSafetyValidator validator = new UrlSafetyValidator();

    @Test
    void allowsPublicHttpsWebsite() {
        assertDoesNotThrow(() -> validator.validate("https://phptravels.com/demo"));
    }

    @Test
    void rejectsLocalhost() {
        assertThrows(AutomationException.class, () -> validator.validate("http://localhost/"));
    }

    @Test
    void rejectsPrivateIpLiteral() {
        assertThrows(AutomationException.class, () -> validator.validate("http://192.168.1.10/"));
    }

    @Test
    void rejectsIpv6Loopback() {
        assertThrows(AutomationException.class, () -> validator.validate("http://[::1]/"));
    }

    @Test
    void rejectsUnsupportedScheme() {
        assertThrows(AutomationException.class, () -> validator.validate("file:///etc/passwd"));
    }

    @Test
    void rejectsNonStandardPort() {
        assertThrows(AutomationException.class, () -> validator.validate("http://example.com:8080/"));
    }
}
