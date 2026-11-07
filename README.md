# Task 2: Plain-English Browser Automation

A small REST API built with Java 21, Spring Boot, Selenium WebDriver, Maven, and OpenRouter. It turns a plain-English browser test into a restricted sequence of browser actions, runs those actions in Chrome, and returns `PASS` or `FAIL`.

The project has no site-specific selectors or domain allow-list. Each request supplies the site URL and test instruction. It is intended for public websites with standard HTTP/HTTPS pages; see [Short Note.md](Short%20Note.md) for the approach and limitations.

## Requirements

- JDK 21
- Google Chrome installed
- Internet access for Maven dependencies, Selenium Manager/driver setup, and OpenRouter requests
- An OpenRouter API key with access to the configured model

The project uses Maven Wrapper, so a separate Maven installation is not required.

## Setup and run on Windows

Open PowerShell in the project folder:

```powershell
cd "C:\Users\ASUS-PC\Desktop\CloudQA\task 2"
$env:OPENROUTER_API_KEY = "your-openrouter-api-key"
```

Optionally choose a different OpenRouter model or server port:

```powershell
$env:OPENROUTER_MODEL = "openai/gpt-4.1-mini"
$env:PORT = "8080"
```

Start the API:

```powershell
.\mvnw.cmd spring-boot:run
```

The server listens on `http://localhost:8080` by default. Keep this terminal open while sending requests. Press `Ctrl+C` to stop it. The key is read from the environment and is not stored in the project files.

To run the packaged application instead:

```powershell
.\mvnw.cmd -DskipTests package
java -jar .\target\browser-automation-api-1.0.0.jar
```

## Run a test with Postman

1. Start the application using the steps above.
2. Create a `POST` request to `http://localhost:8080/api/automation/run`.
3. Set `Content-Type` to `application/json`.
4. Select **Body → raw → JSON** and enter a request such as:

```json
{
  "url": "https://example.com",
  "testCase": "Open the page and verify that the Example Domain heading is visible."
}
```

Use the exact public page URL you want to test. For a login test, start at the relevant login page and include the test account values in the English instruction. The automation will not invent credentials.

A successful response has this general shape:

```json
{
  "success": true,
  "status": "PASS",
  "testCase": "Open the page and verify that the Example Domain heading is visible.",
  "message": "All planned browser actions and verifications succeeded.",
  "executedSteps": [
    "Opened website",
    "Verified text: Example Domain"
  ],
  "error": null
}
```

`FAIL` responses include the completed steps and an error message. `PASS` means the planned UI actions and checks succeeded; it does not prove backend or database behavior.

## Project structure

```text
src/main/java/com/example/automation/
├── AutomationApplication.java
├── controller/AutomationController.java
├── model/AutomationModels.java
└── service/
    ├── DynamicElementFinder.java
    ├── OpenRouterService.java
    ├── SeleniumAutomationService.java
    └── UrlSafetyValidator.java
```

- `AutomationController` exposes and validates the REST request.
- `OpenRouterService` sends the test case and visible page summary to OpenRouter and parses its structured action plan.
- `DynamicElementFinder` inspects visible DOM controls and matches targets using labels, accessible names, placeholders, text, IDs/names, input type, and autocomplete metadata.
- `SeleniumAutomationService` runs the plan one step at a time, refreshes the page summary after interactions, verifies results, and closes Chrome.
- `UrlSafetyValidator` blocks local, private, and reserved destinations to reduce SSRF risk.

Supported actions are `navigate`, `click`, `fill`, `select`, `wait`, `verify_visible`, `verify_text`, and `verify_url`. A request is limited to 20 steps and 8 page-planning rounds.

## Configuration and safety

`src/main/resources/application.properties` reads:

```properties
openrouter.api-key=${OPENROUTER_API_KEY:}
openrouter.model=${OPENROUTER_MODEL:openai/gpt-4.1-mini}
server.port=${PORT:8080}
```

The test instruction and visible page summary are sent to OpenRouter. Use disposable test accounts and do not put real customer information in test cases. URL validation permits public HTTP/HTTPS sites on standard ports and blocks local/private addresses.