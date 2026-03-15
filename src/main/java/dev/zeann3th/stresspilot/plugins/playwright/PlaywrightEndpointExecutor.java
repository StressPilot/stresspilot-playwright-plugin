package dev.zeann3th.stresspilot.plugins.playwright;

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
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.pf4j.Extension;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Extension
@Component
public class PlaywrightEndpointExecutor implements EndpointExecutor {

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

        } catch (TimeoutException e) {
            log.error("Playwright script execution timed out for endpoint {}", endpoint.getId());
            return ExecuteEndpointResponse.builder()
                    .success(false)
                    .statusCode(408)
                    .message("Execution timed out")
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (Exception e) {
            log.error("Playwright Execution failed for endpoint {}", endpoint.getId(), e);
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

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
             BrowserContext browserContext = browser.newContext();
             Page page = browserContext.newPage()) {

            SafePageProxy safeProxy = new SafePageProxy(page);

            try (Context jsContext = Context.newBuilder("js")
                    .allowHostAccess(secureAccess)
                    .allowCreateProcess(false)
                    .allowCreateThread(false)
                    .allowIO(IOAccess.NONE)
                    .allowNativeAccess(false)
                    .allowHostClassLookup(className -> false)
                    .build()) {

                jsContext.getBindings("js").putMember("page", safeProxy);
                jsContext.getBindings("js").putMember("env", ProxyObject.fromMap(envVars));

                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<?> future = executor.submit(() -> jsContext.eval("js", jsScript));

                try {
                    future.get(60, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    jsContext.close(true);
                    throw e;
                } finally {
                    executor.shutdownNow();
                }
            }
        } catch (PolyglotException e) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0001,
                    Map.of(
                            Constants.REASON,
                            "GraalVM JS Evaluation Error: " + e.getMessage()
                    )
            );
        }
    }
}