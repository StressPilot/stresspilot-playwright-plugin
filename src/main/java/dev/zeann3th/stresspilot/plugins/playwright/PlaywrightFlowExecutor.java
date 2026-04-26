package dev.zeann3th.stresspilot.plugins.playwright;

import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutionContext;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutor;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.Extension;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Extension
@Component
public class PlaywrightFlowExecutor extends FlowExecutor {

    private static final String PLAYWRIGHT = "PLAYWRIGHT";

    @Override
    public String getType() {
        return PLAYWRIGHT;
    }

    @Override
    public boolean supports(String type) {
        return PLAYWRIGHT.equalsIgnoreCase(type);
    }

    @Override
    protected void executeWorker(FlowExecutionContext ctx, Map<String, FlowStepEntity> stepMap) {
        FlowStepEntity startStep = findStartNode(stepMap);
        if (startStep == null) return;

        log.info("Playwright Run {} thread {} started", ctx.getRunId(), ctx.getThreadId());

        while (!ctx.shouldStop()) {
            try {
                executeIteration(startStep, stepMap, ctx);
                ctx.incrementIteration();
            } catch (Exception e) {
                log.error("Playwright thread {} iteration error: {}", ctx.getThreadId(), e.getMessage(), e);
                ctx.recordRequest(false);
            }
        }

        log.info("Playwright Run {} thread {} finished: {} iterations", ctx.getRunId(), ctx.getThreadId(), ctx.getIterationCount());
        ctx.getExecutionContext().clear();
    }
}
