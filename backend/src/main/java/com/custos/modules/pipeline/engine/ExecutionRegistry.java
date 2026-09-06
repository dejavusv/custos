package com.custos.modules.pipeline.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

@Component
@Slf4j
public class ExecutionRegistry {

    private final Map<UUID, Future<?>> activeFutures = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Process>> activeProcesses = new ConcurrentHashMap<>();
    private final Map<UUID, PipelineExecutionContext> activeContexts = new ConcurrentHashMap<>();
    private final Set<UUID> abortedExecutions = ConcurrentHashMap.newKeySet();

    public void registerExecution(UUID executionId, Future<?> future, PipelineExecutionContext context) {
        if (future != null) {
            activeFutures.put(executionId, future);
        }
        if (context != null) {
            activeContexts.put(executionId, context);
        }
        activeProcesses.putIfAbsent(executionId, ConcurrentHashMap.newKeySet());
    }

    public void registerProcess(UUID executionId, Process process) {
        Set<Process> processes = activeProcesses.computeIfAbsent(executionId, k -> ConcurrentHashMap.newKeySet());
        processes.add(process);
    }

    public void unregisterProcess(UUID executionId, Process process) {
        Set<Process> processes = activeProcesses.get(executionId);
        if (processes != null) {
            processes.remove(process);
        }
    }

    public boolean abortExecution(UUID executionId) {
        log.warn("Aborting pipeline execution: {}", executionId);
        abortedExecutions.add(executionId);

        // 1. Mark context cancelled
        PipelineExecutionContext context = activeContexts.get(executionId);
        if (context != null) {
            context.cancel();
        }

        // 2. Terminate any registered OS processes
        Set<Process> processes = activeProcesses.remove(executionId);
        if (processes != null) {
            for (Process p : processes) {
                try {
                    if (p.isAlive()) {
                        p.destroyForcibly();
                        log.info("Forcibly destroyed process for execution: {}", executionId);
                    }
                } catch (Exception e) {
                    log.error("Failed to destroy process: {}", e.getMessage());
                }
            }
        }

        // 3. Cancel background future
        Future<?> future = activeFutures.remove(executionId);
        if (future != null) {
            future.cancel(true);
        }

        return true;
    }

    public void unregisterExecution(UUID executionId) {
        activeFutures.remove(executionId);
        activeProcesses.remove(executionId);
        activeContexts.remove(executionId);
        abortedExecutions.remove(executionId);
    }

    public boolean isAborted(UUID executionId) {
        return abortedExecutions.contains(executionId);
    }
}
