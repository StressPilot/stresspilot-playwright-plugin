package dev.zeann3th.stresspilot.plugins.playwright;

import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.services.executors.context.BaseExecutionContext;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutionContext;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutor;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.Extension;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

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
    protected void executeWorker(int threadId, RunEntity run,
                                 Map<String, FlowStepEntity> stepMap,
                                 Map<String, Object> environment,
                                 long totalMs, AtomicBoolean stopSignal) {
        long deadline = System.currentTimeMillis() + totalMs;

        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.setThreadId(threadId);
        ctx.setRunId(run.getId());
        ctx.setRun(run);
        ctx.setVariables(new ConcurrentHashMap<>(environment));
        ctx.setExecutionContext(new BaseExecutionContext());
        ctx.setStopSignal(stopSignal);
        ctx.setDeadline(deadline);

        FlowStepEntity startStep = findStartNode(stepMap);
        if (startStep == null) return;

        log.info("Playwright Run {} thread {} started", run.getId(), threadId);

        while (!stopSignal.get() && System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted()) {
            try {
                executeIteration(startStep, stepMap, ctx);
                ctx.incrementIteration();
            } catch (Exception e) {
                log.error("Playwright thread {} iteration error: {}", threadId, e.getMessage(), e);
            }
        }

        log.info("Playwright Run {} thread {} finished: {} iterations", run.getId(), threadId, ctx.getIterationCount());
        ctx.getExecutionContext().clear();
    }
}