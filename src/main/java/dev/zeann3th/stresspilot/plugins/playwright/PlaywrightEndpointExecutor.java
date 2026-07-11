package dev.zeann3th.stresspilot.plugins.playwright;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.pf4j.Extension;
import org.springframework.stereotype.Component;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointResponse;
import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.services.executors.EndpointExecutor;
import dev.zeann3th.stresspilot.core.services.executors.context.ExecutionContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Extension
@Component
public class PlaywrightEndpointExecutor implements EndpointExecutor {

    private static final String JS_PAGE_WRAPPER = """
            const wrapLocator = (loc) => {
                if (!loc) return null;
                const api = {
                    click:           ()          => { loc.click(); return api; },
                    dblclick:        ()          => { loc.dblclick(); return api; },
                    hover:           ()          => { loc.hover(); return api; },
                    fill:            (val)       => { loc.fill(val); return api; },
                    press:           (key)       => { loc.press(key); return api; },
                    check:           ()          => { loc.check(); return api; },
                    uncheck:         ()          => { loc.uncheck(); return api; },
                    selectOption:    (val)       => { loc.selectOption(val); return api; },
                    isVisible:       ()          => loc.isVisible(),
                    isEnabled:       ()          => loc.isEnabled(),
                    isChecked:       ()          => loc.isChecked(),
                    inputValue:      ()          => loc.inputValue(),
                    textContent:     ()          => loc.textContent(),
                    innerText:       ()          => loc.innerText(),
                    getAttribute:    (name)      => loc.getAttribute(name),
                    waitFor:         ()          => { loc.waitFor(); return api; },
                    count:           ()          => loc.count(),
                    first:           ()          => wrapLocator(loc.first()),
                    last:            ()          => wrapLocator(loc.last()),
                    nth:             (idx)       => wrapLocator(loc.nth(idx)),
                    locator:         (sel)       => wrapLocator(loc.locator(sel)),
                    getByText:       (text)      => wrapLocator(loc.getByText(text)),
                    getByLabel:      (text)      => wrapLocator(loc.getByLabel(text)),
                    getByPlaceholder:(text)      => wrapLocator(loc.getByPlaceholder(text)),
                    getByTestId:     (id)        => wrapLocator(loc.getByTestId(id)),
                };
                return api;
            };

            const page = {
                goto:            (url)       => { __page__.gotoUrl(url); return page; },
                navigate:        (url)       => { __page__.gotoUrl(url); return page; },
                url:             ()          => __page__.url(),
                title:           ()          => __page__.title(),
                reload:          ()          => { __page__.reload(); return page; },
                goBack:          ()          => { __page__.goBack(); return page; },
                goForward:       ()          => { __page__.goForward(); return page; },
                
                locator:         (sel)       => wrapLocator(__page__.locator(sel)),
                getByRole:       (r, n)      => wrapLocator(__page__.getByRole(r, n)),
                getByText:       (t)         => wrapLocator(__page__.getByText(t)),
                getByLabel:      (l)         => wrapLocator(__page__.getByLabel(l)),
                getByPlaceholder:(p)         => wrapLocator(__page__.getByPlaceholder(p)),
                getByAltText:    (a)         => wrapLocator(__page__.getByAltText(a)),
                getByTitle:      (t)         => wrapLocator(__page__.getByTitle(t)),
                getByTestId:     (id)        => wrapLocator(__page__.getByTestId(id)),

                textContent:     (sel)       => __page__.textContent(sel),
                innerText:       (sel)       => __page__.innerText(sel),
                content:         ()          => __page__.content(),
                getAttribute:    (sel, attr) => __page__.getAttribute(sel, attr),
                isVisible:       (sel)       => __page__.isVisible(sel),
                exists:          (sel)       => __page__.exists(sel),
                click:           (sel)       => { __page__.click(sel); return page; },
                dblclick:        (sel)       => { __page__.dblclick(sel); return page; },
                hover:           (sel)       => { __page__.hover(sel); return page; },
                fill:            (sel, val)  => { __page__.fill(sel, val); return page; },
                press:           (sel, key)  => { __page__.press(sel, key); return page; },
                check:           (sel)       => { __page__.check(sel); return page; },
                uncheck:         (sel)       => { __page__.uncheck(sel); return page; },
                selectOption:    (sel, val)  => { __page__.selectOption(sel, val); return page; },
                
                waitForSelector: (sel)       => { __page__.waitForSelector(sel); return page; },
                waitForTimeout:  (ms)        => { __page__.waitForTimeout(ms); return page; },
                waitForLoadState:(state='load') => { __page__.waitForLoadState(state); return page; },
                screenshot:      (options={}) => {
                    return __page__.screenshot(
                        !!options.fullPage,
                        options.type || 'png',
                        options.quality ?? 80,
                        options.name || 'screenshot'
                    );
                },
            };

            const __assert = (ok, message) => { if (!ok) throw new Error('Assertion failed: ' + message); };
            const expect = (actual) => ({
                toBe:             (wanted) => __assert(actual === wanted, `${actual} !== ${wanted}`),
                toEqual:          (wanted) => __assert(JSON.stringify(actual) === JSON.stringify(wanted), 'values differ'),
                toContain:        (wanted) => __assert(actual != null && actual.includes(wanted), `${actual} does not contain ${wanted}`),
                toBeTruthy:       ()       => __assert(!!actual, `${actual} is not truthy`),
                toBeFalsy:        ()       => __assert(!actual, `${actual} is not falsy`),
                toBeVisible:      ()       => __assert(actual.isVisible(), 'locator is not visible'),
                toBeHidden:       ()       => __assert(!actual.isVisible(), 'locator is visible'),
                toBeEnabled:      ()       => __assert(actual.isEnabled(), 'locator is disabled'),
                toBeChecked:      ()       => __assert(actual.isChecked(), 'locator is not checked'),
                toHaveText:       (wanted) => __assert(actual.innerText() === wanted, `text is '${actual.innerText()}', expected '${wanted}'`),
                toContainText:    (wanted) => __assert(actual.innerText().includes(wanted), `text does not contain '${wanted}'`),
                toHaveCount:      (wanted) => __assert(actual.count() === wanted, `count is ${actual.count()}, expected ${wanted}`),
                toHaveValue:      (wanted) => __assert(actual.inputValue() === wanted, `value is '${actual.inputValue()}', expected '${wanted}'`),
            });
            """;

    @Override
    public String getType() {
        return "PLAYWRIGHT";
    }

    @Override
    public ExecuteEndpointResponse execute(EndpointEntity endpoint, Map<String, Object> environment, ExecutionContext context) {
        String jsScript = endpoint.getBody();

        if (jsScript == null || jsScript.isBlank()) {
            log.warn("No JavaScript code found for Playwright endpoint {}", endpoint.getId());
            return ExecuteEndpointResponse.builder()
                    .success(false)
                    .statusCode(400)
                    .message("No JavaScript script provided in endpoint body.")
                    .responseTimeMs(0)
                    .build();
        }

        long startTime = System.currentTimeMillis();

        try {
            executePlaywrightSandbox(jsScript, environment);
            long responseTime = System.currentTimeMillis() - startTime;

            return ExecuteEndpointResponse.builder()
                    .success(true)
                    .statusCode(200)
                    .message("Playwright script executed successfully")
                    .responseTimeMs(responseTime)
                    .build();

        } catch (TimeoutException _) {
            log.error("Playwright script execution timed out for endpoint {}", endpoint.getId());
            return ExecuteEndpointResponse.builder()
                    .success(false)
                    .statusCode(408)
                    .message("Execution timed out")
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (Exception e) {
            log.error("Playwright execution failed for endpoint {}", endpoint.getId(), e);
            return ExecuteEndpointResponse.builder()
                    .success(false)
                    .statusCode(500)
                    .message("Execution failed: " + e.getMessage())
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    private void executePlaywrightSandbox(String jsScript, Map<String, Object> envVars) throws Exception {
        HostAccess secureAccess = HostAccess.newBuilder(HostAccess.EXPLICIT)
                .allowPublicAccess(true)
                .build();

        log.debug("Starting Playwright Browser instance inside Endpoint Executor...");

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {

            Callable<Void> browserTask = () -> {
                try (Playwright playwright = Playwright.create();
                     Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                             .setHeadless(false)); // Headless by default for load testing
                     BrowserContext browserContext = browser.newContext();
                     Page page = browserContext.newPage()) {

                    SafePageProxy safeProxy = new SafePageProxy(page);

                    try (Context jsContext = Context.newBuilder("js")
                            .allowHostAccess(secureAccess)
                            .allowCreateProcess(false)
                            .allowCreateThread(false)
                            .allowIO(IOAccess.NONE)
                            .allowNativeAccess(false)
                            .allowHostClassLookup(_ -> false)
                            .build()) {

                        jsContext.getBindings("js").putMember("__page__", safeProxy);
                        jsContext.getBindings("js").putMember("env", ProxyObject.fromMap(envVars));
                        jsContext.eval("js", JS_PAGE_WRAPPER);

                        try {
                            jsContext.eval("js", jsScript);
                        } catch (PolyglotException e) {
                            captureErrorScreenshot(page, envVars);
                            throw new RuntimeException(e.getMessage());
                        }
                    }
                }
                return null;
            };

            Future<Void> future = executor.submit(browserTask);

            try {
                future.get(60, TimeUnit.SECONDS);
            } catch (TimeoutException _) {
                future.cancel(true);
                throw CommandExceptionBuilder.exception(ErrorCode.ER0001,
                        Map.of(Constants.REASON, "Playwright execution timed out after 60 seconds")
                );
            } catch (ExecutionException e) {
                throw CommandExceptionBuilder.exception(ErrorCode.ER0001,
                        Map.of(Constants.REASON, "GraalVM JS Evaluation Error: " + e.getCause().getMessage())
                );
            }
        }
    }

    private void captureErrorScreenshot(Page page, Map<String, Object> envVars) {
        try {
            log.warn("Playwright script failed. Capturing crash screenshot...");
            String path = SafePageProxy.saveScreenshot(page, true, "png", 80, "error");
            envVars.put("errorScreenshot", path);
        } catch (Exception screenshotEx) {
            log.error("Failed to capture screenshot during crash", screenshotEx);
        }
    }
}
