# Plain-English Browser Automation API

Java 21, Spring Boot, Selenium 4, Maven, and Groq Chat Completions. The API accepts a test case, asks Groq for a constrained JSON action plan, resolves elements from the live page DOM, runs the plan in headless Chrome, and returns a PASS/FAIL JSON result.

## Requirements

- JDK 21
- Maven 3.9+ (or use the included Maven wrapper)
- Google Chrome or Chromium
- A Groq API key in `GROQ_API_KEY` (preferred); `OPENAI_API_KEY` is also accepted as a compatibility alias

The default model is `openai/gpt-oss-20b`, which supports Groq strict JSON Schema output. Override it with `GROQ_MODEL` if desired. API keys are never stored in source files.

## Build and run

```powershell
.\mvnw.cmd clean compile
.\mvnw.cmd test
$env:GROQ_API_KEY = "your-groq-api-key"
# Or, if the key is already stored under this name:
$env:OPENAI_API_KEY = "your-groq-api-key"
.\mvnw.cmd spring-boot:run
```

Set the environment variable before starting the application. Without it, requests return a clear FAIL result that asks for `GROQ_API_KEY` or `OPENAI_API_KEY`.

## Postman

- Method: `POST`
- URL: `http://localhost:8080/api/automation/run`
- Header: `Content-Type: application/json`
- Body → raw → JSON:

```json
{
  "url": "https://phptravels.com/demo",
  "testCase": "Open the website and verify that the homepage is displayed."
}
```

Expected response shape:

```json
{
  "success": true,
  "status": "PASS",
  "testCase": "Open the website and verify that the homepage is displayed.",
  "message": "All planned browser actions and verifications succeeded.",
  "executedSteps": ["Opened website", "Verified visible: homepage heading"],
  "error": null
}
```

The supported AI actions are `navigate`, `click`, `fill`, `select`, `wait`, `verify_visible`, `verify_text`, and `verify_url`. A run fails if Groq produces no verification, if an element match is ambiguous, or if any verification does not pass. Fill/select values must appear literally in the supplied test case; the model cannot invent input data.

## Dynamic element matching and URL safety

The finder inspects visible DOM elements and combines accessible labels, `aria-label`, `aria-labelledby`, labels, placeholders, names, IDs, input types, roles, titles, visible text, and link/button semantics. It scores candidates and fails on a weak or ambiguous match. No PHPTravels selectors or page-specific workflow are in the code.

Only absolute HTTP/HTTPS URLs on standard ports are accepted. Localhost, private, reserved, and non-public IP destinations are rejected after DNS resolution. Selenium's Chrome DevTools Fetch request interception also checks and blocks browser requests that fail those checks, including redirects and subresources. Browser resources are always closed after a run.

## PHPTravels checks

Try these as separate Postman requests:

1. `Open the website and verify the homepage.`
2. `Find the login option and navigate to the login page. Verify that the login page is displayed.`
3. `Find the login option, open the login page, and verify that the email and password fields are visible.`

The live checks require a working Groq key, network access, Chrome, and a ChromeDriver available through Selenium Manager. `mvn test` runs local URL-safety unit tests and does not call Groq or claim that the live PHPTravels cases passed.
