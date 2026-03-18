package dev.zeann3th.stresspilot.plugins.playwright;

import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;
import dev.zeann3th.stresspilot.core.domain.entities.*;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.enums.FlowStepType;
import dev.zeann3th.stresspilot.core.domain.enums.RunStatus;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.ports.store.EnvironmentVariableStore;
import dev.zeann3th.stresspilot.core.ports.store.ProjectStore;
import dev.zeann3th.stresspilot.core.ports.store.RunStore;
import dev.zeann3th.stresspilot.core.services.ActiveRunRegistry;
import dev.zeann3th.stresspilot.core.services.RequestLogService;
import dev.zeann3th.stresspilot.core.services.executors.context.BaseExecutionContext;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutionContext;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutor;
import dev.zeann3th.stresspilot.core.services.flows.nodes.FlowNodeHandlerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.Extension;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Extension
@Component
@RequiredArgsConstructor
public class PlaywrightFlowExecutor implements FlowExecutor {

    private static final String PLAYWRIGHT = "PLAYWRIGHT";

    private final ProjectStore projectStore;
    private final EnvironmentVariableStore envVarStore;
    private final RunStore runStore;
    private final ActiveRunRegistry activeRunRegistry;
    private final RequestLogService requestLogService;
    private final FlowNodeHandlerFactory nodeHandlerFactory;

    @Override
    public String getType() {
        return PLAYWRIGHT;
    }

    @Override
    public boolean supports(String type) {
        return PLAYWRIGHT.equalsIgnoreCase(type);
    }

    @Override
    @SuppressWarnings("java:S3776")
    public void execute(FlowEntity flow, List<FlowStepEntity> steps, RunFlowCommand runFlowCommand) {
        Map<String, FlowStepEntity> stepMap = steps.stream()
                .collect(Collectors.toMap(FlowStepEntity::getId, s -> s));

        int threads = 1;

        RunEntity run = runStore.save(RunEntity.builder()
                .flow(flow)
                .status(RunStatus.RUNNING.name())
                .threads(threads)
                .duration(runFlowCommand.getTotalDuration())
                .rampUpDuration(runFlowCommand.getRampUpDuration())
                .startedAt(LocalDateTime.now())
                .build());

        AtomicBoolean stopSignal = activeRunRegistry.registerRun(run.getId());

        log.info("Playwright Run {} started: flow={}, threads=1, duration={}s",
                run.getId(), flow.getName(), runFlowCommand.getTotalDuration());

        ProjectEntity project = projectStore.findById(flow.getProjectId())
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0002));

        Map<String, Object> baseEnv = envVarStore
                .findAllByEnvironmentIdAndActiveTrue(project.getEnvironmentId())
                .stream()
                .collect(Collectors.toMap(
                        EnvironmentVariableEntity::getKey,
                        EnvironmentVariableEntity::getValue,
                        (v1, v2) -> v2, HashMap::new));

        if (runFlowCommand.getVariables() != null) {
            baseEnv.putAll(runFlowCommand.getVariables());
        }

        try (ExecutorService pool = Executors.newFixedThreadPool(threads,
                r -> new Thread(r, "playwright-worker-" + run.getId()))) {
            long totalMs = (long) runFlowCommand.getTotalDuration() * 1000;

            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final int threadId = i;

                Map<String, Object> threadEnv = new HashMap<>(baseEnv);
                if (runFlowCommand.getCredentials() != null && !runFlowCommand.getCredentials().isEmpty()) {
                    threadEnv.putAll(runFlowCommand.getCredentials().getFirst());
                }

                futures.add(pool.submit(() -> runWorker(threadId, run, stepMap, threadEnv, totalMs, stopSignal)));
            }

            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    log.warn("Playwright Worker thread encountered error: {}", e.getMessage());
                }
            }

            if (!stopSignal.get()) {
                run.setStatus(RunStatus.COMPLETED.name());
            }

        } finally {
            activeRunRegistry.deregisterRun(run.getId());
            run.setCompletedAt(LocalDateTime.now());
            runStore.save(run);
            requestLogService.ensureFlushed();
            log.info("Playwright Run {} finished: status={}", run.getId(), run.getStatus());
        }
    }

    private void runWorker(int threadId, RunEntity run,
                           Map<String, FlowStepEntity> stepMap,
                           Map<String, Object> environment,
                           long totalMs, AtomicBoolean stop) {
        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.setThreadId(threadId);
        ctx.setRunId(run.getId());
        ctx.setRun(run);
        ctx.setVariables(new ConcurrentHashMap<>(environment));
        ctx.setExecutionContext(new BaseExecutionContext());

        FlowStepEntity startStep = findStartNode(stepMap);
        if (startStep == null) return;

        long deadline = System.currentTimeMillis() + totalMs;

        log.info("Playwright Run {} thread {} started", run.getId(), threadId);

        while (!stop.get() && System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted()) {
            try {
                executeIteration(startStep, stepMap, ctx);
                ctx.incrementIteration();
            } catch (Exception e) {
                log.error("Playwright Thread {} iteration error: {}", threadId, e.getMessage(), e);
            }
        }

        log.info("Playwright Run {} thread {} finished: {} iterations", run.getId(), threadId, ctx.getIterationCount());
        ctx.getExecutionContext().clear();
    }

    private void executeIteration(FlowStepEntity startStep,
                                  Map<String, FlowStepEntity> stepMap,
                                  FlowExecutionContext ctx) {
        FlowStepEntity current = startStep;

        int jumpCount = 0;
        final int MAX_JUMPS = 10000;

        while (current != null) {
            if (jumpCount++ > MAX_JUMPS) {
                log.warn("Iteration exceeded max jumps ({}) at step {} — breaking iteration", MAX_JUMPS,
                        current.getId());
                break;
            }

            FlowStepType type;
            try {
                type = FlowStepType.valueOf(current.getType().toUpperCase());
            } catch (IllegalArgumentException _) {
                log.error("Unknown step type: {}", current.getType());
                break;
            }

            String nextId = nodeHandlerFactory.getHandler(type).handle(current, stepMap, ctx);

            current = nextId != null ? stepMap.get(nextId) : null;
        }
    }

    private FlowStepEntity findStartNode(Map<String, FlowStepEntity> stepMap) {
        return stepMap.values().stream()
                .filter(s -> FlowStepType.START.name().equalsIgnoreCase(s.getType()))
                .findFirst()
                .orElse(null);
    }
}