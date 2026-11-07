# Short Note: Approach and Limitations

## Approach

The API accepts a URL and a plain-English test case. Spring validates the request, and the URL safety validator blocks local/private destinations. Selenium opens the supplied site and the element finder builds a compact summary of visible page controls and text from the live DOM.

OpenRouter receives the test case and current page summary and returns a constrained JSON plan. The application accepts only the supported browser actions (`navigate`, `click`, `fill`, `select`, `wait`, `verify_visible`, `verify_text`, and `verify_url`). Selenium checks each target against the current DOM before interacting, refreshes the summary after page/form changes, and verifies the requested result. The browser is closed when the request finishes.

No website domain, page structure, selector, or XPath is built into the automation. The request URL selects the site. Ambiguous or missing elements cause a clear failure instead of a random interaction.

## Limitations

- “URL-independent” means no per-site code changes are intended for ordinary public websites; it cannot guarantee every website or every flow.
- Only public HTTP/HTTPS destinations on standard ports are allowed. Local/private addresses are blocked for SSRF protection.
- CAPTCHA, OTP/MFA, email verification, SSO, bot protection, and login flows requiring external approval are not handled automatically.
- The finder targets visible controls in the main DOM. Controls inside iframes or shadow DOM, canvas-based interfaces, and complex custom widgets may not be found.
- Credentials or other input values must be included in the test case. The model does not invent them. The test case and visible page summary are sent to OpenRouter, so use disposable test credentials.
- This is UI automation. It cannot confirm database state, API/business logic, or server-side authorization beyond what the page displays.
- Runs are limited to 20 actions and 8 planning rounds. AI plans may still be incomplete or unsuitable for a particular page; a `PASS` reports only the UI checks that actually ran successfully.