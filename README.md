# StressPilot Playwright Plugin

Playwright plugin for browser automation and UI stress testing. It uses GraalVM JS to execute Playwright scripts in a sandboxed environment.

## Usage (CURL Examples)

Replace `projectId=1` with your actual project ID. Scripts are passed in the `body` field.

### 1. Test Wikipedia Search

This script navigates to Wikipedia, searches for "Software Testing", and waits for the results.

**PowerShell:**
```powershell
curl.exe -X POST "http://localhost:52000/api/v1/endpoints/execute-adhoc?projectId=1" -H "Content-Type: application/json" -d '{"type":"PLAYWRIGHT","url":"https://wikipedia.org","body":"page.goto(\"https://www.wikipedia.org/\"); page.fill(\"input[name=\\\"search\\\"]\", \"Software Testing\"); page.press(\"input[name=\\\"search\\\"]\", \"Enter\"); page.waitForSelector(\"#firstHeading\");"}'
```

**CMD:**
```cmd
curl -X POST "http://localhost:52000/api/v1/endpoints/execute-adhoc?projectId=1" -H "Content-Type: application/json" -d "{\"type\":\"PLAYWRIGHT\",\"url\":\"https://wikipedia.org\",\"body\":\"page.goto(\\\"https://www.wikipedia.org/\\\"); page.fill(\\\"input[name=\\\\\\\"search\\\\\\\"]\\\", \\\"Software Testing\\\"); page.press(\\\"input[name=\\\\\\\"search\\\\\\\"]\\\", \\\"Enter\\\"); page.waitForSelector(\\\"#firstHeading\\\");\"}"
```

### 2. Simple Navigation and Title Check

**PowerShell:**
```powershell
curl.exe -X POST "http://localhost:52000/api/v1/endpoints/execute-adhoc?projectId=1" -H "Content-Type: application/json" -d '{"type":"PLAYWRIGHT","url":"https://github.com","body":"page.goto(\"https://github.com\"); console.log(\"Title: \" + page.title());"}'
```

**CMD:**
```cmd
curl -X POST "http://localhost:52000/api/v1/endpoints/execute-adhoc?projectId=1" -H "Content-Type: application/json" -d "{\"type\":\"PLAYWRIGHT\",\"url\":\"https://github.com\",\"body\":\"page.goto(\\\"https://github.com\\\");\"}"
```

## Supported Page Actions

The plugin exposes a `page` object with the following methods:

- `goto(url)` / `navigate(url)`
- `url()`, `title()`
- `textContent(selector)`, `innerText(selector)`, `content()`
- `getAttribute(selector, attr)`
- `isVisible(selector)`, `exists(selector)`
- `click(selector)`, `dblclick(selector)`, `hover(selector)`
- `fill(selector, value)`
- `selectOption(selector, value)`
- `check(selector)`, `uncheck(selector)`
- `press(selector, key)`
- `waitForSelector(selector)`
- `waitForTimeout(ms)`
- `screenshot()`

Action methods are synchronous and fluent, so old scripts still work and chains are also valid:

```javascript
page.goto(env.BASE_URL)
    .waitForSelector("#login")
    .fill("#username", "seeduser002")
    .click("button[type=submit]");

page.locator("form")
    .getByPlaceholder("Password")
    .fill("password123")
    .press("Enter");
```

## Assertions and screenshots

The sandbox provides lightweight synchronous Playwright-style assertions:

```javascript
expect(page.locator("h1")).toContainText("Dashboard");
expect(page.getByTestId("ready")).toBeVisible();
expect(page.locator(".row")).toHaveCount(3);
expect(page.title()).toContain("StressPilot");

const screenshotPath = page.screenshot({ fullPage: true });
page.screenshot({ type: "jpeg", quality: 80, name: "home" });
```

Screenshots are written directly to the current operating-system user's
`Pictures/StressPilot` folder. Filenames include the supplied name, a timestamp,
and a collision-safe suffix. The method returns the absolute file path; image data
is never returned as Base64. Failed scripts also save a timestamped `error` PNG in
the same folder and expose its path as `env.errorScreenshot`.

Supported locator assertions include `toBeVisible`, `toBeHidden`, `toBeEnabled`,
`toBeChecked`, `toHaveText`, `toContainText`, `toHaveCount`, and `toHaveValue`.
Generic assertions include `toBe`, `toEqual`, `toContain`, `toBeTruthy`, and `toBeFalsy`.

## Environment Variables

You can access environment variables via the `env` object:
```javascript
page.goto(env.BASE_URL + "/login");
page.fill("#username", env.USER);
```
