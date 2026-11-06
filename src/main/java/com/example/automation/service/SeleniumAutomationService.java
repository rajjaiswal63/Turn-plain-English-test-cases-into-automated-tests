package com.example.automation.service;

import com.example.automation.enums.AutomationAction;
import com.example.automation.model.AutomationRequest;
import com.example.automation.model.AutomationResponse;
import com.example.automation.model.TestPlan;
import com.example.automation.model.TestStep;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v156.fetch.Fetch;
import org.openqa.selenium.devtools.v156.fetch.model.RequestPattern;
import org.openqa.selenium.devtools.v156.fetch.model.RequestStage;
import org.openqa.selenium.devtools.v156.network.model.ErrorReason;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

@Service
public class SeleniumAutomationService {
    private final GroqAIService groqAIService;
    private final DynamicElementFinder elementFinder;
    private final UrlSafetyValidator urlSafetyValidator;

    public SeleniumAutomationService(GroqAIService groqAIService, DynamicElementFinder elementFinder,
                                     UrlSafetyValidator urlSafetyValidator) {
        this.groqAIService = groqAIService;
        this.elementFinder = elementFinder;
        this.urlSafetyValidator = urlSafetyValidator;
    }

    public AutomationResponse run(AutomationRequest request) {
        List<String> executed = new ArrayList<>();
        ChromeDriver driver = null;
        DevTools devTools = null;
        ExecutorService requestGuard = null;
        AtomicReference<String> blockedRequest = new AtomicReference<>();
        try {
            URI startUri = urlSafetyValidator.validate(request.url());
            groqAIService.requireApiKey();
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage",
                    "--window-size=1440,1200", "--disable-background-networking");
            options.setCapability("webSocketUrl", true);
            options.setPageLoadStrategy(PageLoadStrategy.EAGER);
            driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(35));
            driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(10));
            devTools = driver.getDevTools();
            devTools.createSession();
            DevTools requestDevTools = devTools;
            requestGuard = Executors.newFixedThreadPool(4, runnable -> {
                Thread thread = new Thread(runnable, "browser-request-url-guard");
                thread.setDaemon(true);
                return thread;
            });
            ExecutorService guardExecutor = requestGuard;
            requestDevTools.send(Fetch.enable(Optional.of(List.of(new RequestPattern(
                    Optional.of("*"), Optional.empty(), Optional.of(RequestStage.REQUEST)))), Optional.empty()));
            requestDevTools.addListener(Fetch.requestPaused(), event -> guardExecutor.submit(() -> {
                String requestedUrl = event.getRequest().getUrl();
                try {
                    urlSafetyValidator.validate(requestedUrl);
                    requestDevTools.send(Fetch.continueRequest(event.getRequestId(), Optional.empty(), Optional.empty(),
                            Optional.empty(), Optional.empty(), Optional.empty()));
                } catch (Exception blocked) {
                    if ("Document".equalsIgnoreCase(event.getResourceType().toJson())) {
                        blockedRequest.compareAndSet(null, requestedUrl);
                    }
                    try { requestDevTools.send(Fetch.failRequest(event.getRequestId(), ErrorReason.BLOCKEDBYCLIENT)); }
                    catch (Exception ignored) { }
                }
            }));

            driver.get(startUri.toString());
            failIfBlocked(blockedRequest);
            executed.add("Opened website");
            String snapshot = elementFinder.pageSnapshot(driver);
            List<String> completedActions = new ArrayList<>();
            int successfulVerifications = 0;
            int totalSteps = 0;
            int planningRounds = 0;
            while (true) {
                if (++planningRounds > 8) throw new AutomationException("Test required more than eight page-state planning rounds.");
                TestPlan plan = groqAIService.createPlan(request.testCase(), driver.getCurrentUrl(), snapshot, completedActions);
                validatePlan(plan, request.testCase());
                boolean planChangesPage = plan.steps().stream()
                        .anyMatch(step -> step.action().equals("click") || step.action().equals("navigate"));
                boolean replanFromCurrentPage = false;
                for (TestStep step : plan.steps()) {
                    if (planChangesPage && step.action().equals("verify_url") && !explicitUrlCheck(request.testCase())) {
                        continue; // The destination is checked after the browser reaches it and exposes its DOM.
                    }
                    if (++totalSteps > 20) throw new AutomationException("Test plan exceeded the 20-step execution limit.");
                    executeStep(driver, step, request.testCase(), blockedRequest);
                    executed.add(summarize(step));
                    completedActions.add(step.action() + " | target=" + step.target() + " | value=" + step.value()
                            + " | expected=" + step.expected());
                    if (step.action().startsWith("verify_")) successfulVerifications++;
                    failIfBlocked(blockedRequest);
                    if (step.action().equals("click") || step.action().equals("navigate")) {
                        replanFromCurrentPage = true;
                        break;
                    }
                }
                if (!replanFromCurrentPage) break;
                snapshot = elementFinder.pageSnapshot(driver);
            }
            if (successfulVerifications == 0) {
                throw new AutomationException("AI plan contained no verification steps; PASS is not possible.");
            }
            return AutomationResponse.passed(request.testCase(), "All planned browser actions and verifications succeeded.", executed);
        } catch (Exception ex) {
            String unsafeUrl = blockedRequest.get();
            String message = unsafeUrl != null ? "Blocked a browser request to unsafe destination host: " + safeHost(unsafeUrl)
                    : ex instanceof AutomationException ? ex.getMessage() : "Browser automation failed: " + safeMessage(ex);
            return AutomationResponse.failed(request.testCase(), message, executed);
        } finally {
            if (requestGuard != null) requestGuard.shutdownNow();
            if (devTools != null) {
                try { devTools.send(Fetch.disable()); } catch (Exception ignored) { }
            }
            if (driver != null) {
                try { driver.quit(); } catch (Exception ignored) { }
            }
        }
    }

    private void executeStep(WebDriver driver, TestStep step, String testCase, AtomicReference<String> blockedRequest) {
        AutomationAction action;
        try { action = AutomationAction.from(step.action()); }
        catch (IllegalArgumentException ex) { throw new AutomationException(ex.getMessage()); }
        int timeout = Math.max(1, Math.min(step.timeoutSeconds(), 15));
        switch (action) {
            case NAVIGATE -> {
                String destination = step.value().isBlank() ? step.target() : step.value();
                if (destination.isBlank()) throw new AutomationException("navigate step has no destination.");
                if (destination.matches("(?i)^https?://.*")) {
                    String safeDestination = urlSafetyValidator.validate(destination).toString();
                    if (safeDestination.equalsIgnoreCase(driver.getCurrentUrl())) {
                        throw new AutomationException("AI plan redundantly navigated to the page that is already open.");
                    }
                    driver.get(safeDestination);
                    failIfBlocked(blockedRequest);
                } else {
                    DynamicElementFinder.Match link = elementFinder.find(driver, "click", destination);
                    String href = link.attributes().getOrDefault("href", "");
                    if (href.isBlank()) throw new AutomationException("The navigation target is not a link with a destination.");
                    String safeDestination = urlSafetyValidator.validate(href).toString();
                    if (safeDestination.equalsIgnoreCase(driver.getCurrentUrl())) {
                        throw new AutomationException("AI plan redundantly navigated to the current page.");
                    }
                    driver.get(safeDestination);
                    failIfBlocked(blockedRequest);
                }
            }
            case CLICK -> {
                DynamicElementFinder.Match match = elementFinder.find(driver, "click", step.target());
                if (!match.element().isDisplayed() || !match.element().isEnabled()) throw new AutomationException("Matched element is not interactable: " + match.description());
                String href = match.attributes().getOrDefault("href", "");
                if (!href.isBlank()) urlSafetyValidator.validate(href);
                match.element().click();
                new WebDriverWait(driver, Duration.ofSeconds(timeout)).until(d -> ((JavascriptExecutor) d)
                        .executeScript("return document.readyState").toString().matches("interactive|complete"));
                failIfBlocked(blockedRequest);
            }
            case FILL -> {
                ensureValueFromTestCase(step.value(), testCase);
                if (step.value().isBlank()) throw new AutomationException("AI plan did not provide an explicit value from the test case for '" + step.target() + "'.");
                DynamicElementFinder.Match match = elementFinder.find(driver, "fill", step.target());
                WebElement element = match.element();
                if (!element.isEnabled()) throw new AutomationException("Matched field is disabled: " + match.description());
                element.clear();
                element.sendKeys(step.value());
            }
            case SELECT -> {
                ensureValueFromTestCase(step.value(), testCase);
                if (step.value().isBlank()) throw new AutomationException("AI plan did not provide an explicit selection from the test case for '" + step.target() + "'.");
                DynamicElementFinder.Match match = elementFinder.find(driver, "select", step.target());
                if ("select".equals(match.attributes().get("tag"))) {
                    new Select(match.element()).selectByVisibleText(step.value());
                } else {
                    match.element().click();
                    DynamicElementFinder.Match option = elementFinder.find(driver, "click", step.value());
                    option.element().click();
                }
            }
            case WAIT -> {
                if (!step.target().isBlank()) {
                    new WebDriverWait(driver, Duration.ofSeconds(timeout)).until(d -> {
                        try { return elementFinder.find(d, "verify_visible", step.target()).element().isDisplayed(); }
                        catch (AutomationException ignored) { return false; }
                    });
                } else {
                    try { Thread.sleep(Duration.ofSeconds(timeout)); }
                    catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AutomationException("Wait was interrupted.", ex); }
                }
            }
            case VERIFY_VISIBLE -> {
                DynamicElementFinder.Match match = elementFinder.find(driver, "verify_visible", step.target());
                if (!match.element().isDisplayed()) throw new AutomationException("Expected visible target was not visible: " + step.target());
            }
            case VERIFY_TEXT -> {
                String expected = step.expected().isBlank() ? step.target() : step.expected();
                if (expected.isBlank() || !elementFinder.hasVisibleText(driver, expected)) {
                    throw new AutomationException("Expected visible text was not found: '" + expected + "'.");
                }
            }
            case VERIFY_URL -> {
                String expected = step.expected().isBlank() ? step.target() : step.expected();
                if (expected.isBlank()) throw new AutomationException("verify_url step has no expected URL text.");
                String current = driver.getCurrentUrl();
                if (!current.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT))) {
                    throw new AutomationException("Expected URL to contain '" + expected + "' but was '" + current + "'.");
                }
            }
        }
    }

    private void validatePlan(TestPlan plan, String testCase) {
        if (plan == null || plan.steps() == null || plan.steps().isEmpty() || plan.steps().size() > 20) {
            throw new AutomationException("Groq returned an empty or oversized remaining-step plan.");
        }
        for (TestStep step : plan.steps()) {
            if (step == null || step.action() == null || step.target() == null || step.value() == null || step.expected() == null) {
                throw new AutomationException("Groq returned a malformed browser step.");
            }
            try { AutomationAction.from(step.action()); }
            catch (IllegalArgumentException ex) { throw new AutomationException(ex.getMessage()); }
            switch (step.action()) {
                case "navigate", "click", "fill", "select", "verify_visible" -> {
                    if (step.target().isBlank() && !(step.action().equals("navigate") && !step.value().isBlank())) {
                        throw new AutomationException("Groq returned a " + step.action() + " step without a target.");
                    }
                }
                case "verify_text" -> {
                    if (step.target().isBlank() && step.expected().isBlank()) {
                        throw new AutomationException("Groq returned a verify_text step without expected text.");
                    }
                }
                case "verify_url" -> {
                    String expectedUrl = step.expected().isBlank() ? step.target() : step.expected();
                    boolean urlLike = expectedUrl.contains("://") || expectedUrl.startsWith("/") || expectedUrl.contains(".");
                    boolean hasPageTransition = plan.steps().stream()
                            .anyMatch(planned -> planned.action().equals("click") || planned.action().equals("navigate"));
                    if (expectedUrl.isBlank() || (!urlLike && !explicitUrlCheck(testCase) && !hasPageTransition)) {
                        throw new AutomationException("Groq returned a vague URL assertion. Use visible text for page checks, or explicitly request a URL/path check.");
                    }
                }
                default -> { }
            }
            if (step.action().equals("fill") || step.action().equals("select")) ensureValueFromTestCase(step.value(), testCase);
        }
    }

    private boolean explicitUrlCheck(String testCase) {
        return testCase.toLowerCase(Locale.ROOT).matches("(?s).*(\\burl\\b|address bar|route).*?");
    }

    private void ensureValueFromTestCase(String value, String testCase) {
        if (value == null || value.isBlank()) return;
        if (!testCase.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT))) {
            throw new AutomationException("AI plan attempted to use an input value that was not explicitly present in the test case.");
        }
    }

    private void failIfBlocked(AtomicReference<String> blockedRequest) {
        String url = blockedRequest.get();
        if (url != null) throw new AutomationException("Blocked an unsafe browser network request to: " + url);
    }

    private String summarize(TestStep step) {
        return switch (step.action()) {
            case "navigate" -> "Navigated to " + (step.value().isBlank() ? step.target() : step.value());
            case "click" -> "Clicked " + step.target();
            case "fill" -> "Filled " + step.target();
            case "select" -> "Selected an option in " + step.target();
            case "wait" -> "Waited for " + (step.target().isBlank() ? step.timeoutSeconds() + " seconds" : step.target());
            case "verify_visible" -> "Verified visible: " + step.target();
            case "verify_text" -> "Verified text: " + (step.expected().isBlank() ? step.target() : step.expected());
            case "verify_url" -> "Verified URL contains: " + (step.expected().isBlank() ? step.target() : step.expected());
            default -> "Executed " + step.action();
        };
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null ? ex.getClass().getSimpleName() : message.substring(0, Math.min(350, message.length()));
    }

    private String safeHost(String rawUrl) {
        try {
            String host = java.net.URI.create(rawUrl).getHost();
            return host == null || host.isBlank() ? "unknown" : host;
        } catch (Exception ignored) {
            return "unknown";
        }
    }
}
