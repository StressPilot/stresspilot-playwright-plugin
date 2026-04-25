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

## Environment Variables

You can access environment variables via the `env` object:
```javascript
page.goto(env.BASE_URL + "/login");
page.fill("#username", env.USER);
```
