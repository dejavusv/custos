package com.custos.modules.execution;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessExecutorService {

    private static final int DEFAULT_RING_BUFFER_CAPACITY = 1000;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration GRACEFUL_KILL_WAIT = Duration.ofSeconds(3);

    private final ProcessSanitizer sanitizer;

    private final ExecutorService threadPool = Executors.newFixedThreadPool(
            Math.max(4, Runtime.getRuntime().availableProcessors() * 2),
            new ThreadFactory() {
                private int counter = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "custos-process-worker-" + (++counter));
                    t.setDaemon(true);
                    return t;
                }
            }
    );

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down ProcessExecutorService thread pool...");
        threadPool.shutdownNow();
    }

    /**
     * Executes an operating system command under sandboxed conditions.
     *
     * @param commandWithArgs List of arguments where index 0 is the executable binary.
     * @param environmentVariables Environment variables to pass securely (e.g. PGPASSWORD).
     * @param workingDir Working directory for the process (can be null).
     * @param timeout Maximum allowed execution time.
     * @param stdoutConsumer Optional consumer for streaming stdout in real time.
     * @param stderrConsumer Optional consumer for streaming stderr in real time.
     * @return ProcessExecutionResult with output, exit code, and timing.
     */
    public ProcessExecutionResult execute(
            List<String> commandWithArgs,
            Map<String, String> environmentVariables,
            File workingDir,
            Duration timeout,
            Consumer<String> stdoutConsumer,
            Consumer<String> stderrConsumer
    ) {
        if (commandWithArgs == null || commandWithArgs.isEmpty()) {
            throw new IllegalArgumentException("Command arguments cannot be empty");
        }

        // Security check: validate binary whitelist
        sanitizer.validateExecutable(commandWithArgs.get(0));

        // Security check: validate individual arguments
        for (int i = 1; i < commandWithArgs.size(); i++) {
            sanitizer.validateArgument(commandWithArgs.get(i));
        }

        Duration effectiveTimeout = timeout != null ? timeout : DEFAULT_TIMEOUT;
        RingBuffer<String> stdoutRingBuffer = new RingBuffer<>(DEFAULT_RING_BUFFER_CAPACITY);
        RingBuffer<String> stderrRingBuffer = new RingBuffer<>(DEFAULT_RING_BUFFER_CAPACITY);

        ProcessBuilder processBuilder = new ProcessBuilder(commandWithArgs);

        if (workingDir != null && workingDir.exists() && workingDir.isDirectory()) {
            processBuilder.directory(workingDir);
        }

        if (environmentVariables != null && !environmentVariables.isEmpty()) {
            processBuilder.environment().putAll(environmentVariables);
        }

        Instant startTime = Instant.now();
        Process process = null;
        Future<?> stdoutFuture = null;
        Future<?> stderrFuture = null;
        boolean timedOut = false;
        int exitCode = -1;
        String errorMessage = null;

        try {
            log.info("Starting sandboxed process: {} (timeout: {}s)", commandWithArgs.get(0), effectiveTimeout.toSeconds());
            process = processBuilder.start();

            // Stream stdout in background worker
            final Process procRef = process;
            stdoutFuture = threadPool.submit(() -> readStream(procRef.getInputStream(), stdoutRingBuffer, stdoutConsumer));
            stderrFuture = threadPool.submit(() -> readStream(procRef.getErrorStream(), stderrRingBuffer, stderrConsumer));

            // Watchdog: wait with timeout
            boolean completedInTime = process.waitFor(effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);

            if (!completedInTime) {
                timedOut = true;
                log.warn("Process timed out after {} seconds. Initiating termination: {}",
                        effectiveTimeout.toSeconds(), commandWithArgs.get(0));
                terminateProcess(process);
                errorMessage = "Process exceeded maximum timeout limit of " + effectiveTimeout.toSeconds() + " seconds";
            } else {
                exitCode = process.exitValue();
                log.info("Process completed with exit code: {}", exitCode);
            }

            // Wait for stream readers to drain remaining output
            try {
                if (stdoutFuture != null) {
                    stdoutFuture.get(2, TimeUnit.SECONDS);
                }
                if (stderrFuture != null) {
                    stderrFuture.get(2, TimeUnit.SECONDS);
                }
            } catch (Exception ignored) {
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Process execution interrupted: {}", e.getMessage());
            if (process != null) {
                terminateProcess(process);
            }
            errorMessage = "Process execution was interrupted";
        } catch (Exception e) {
            log.error("Failed to start or run process: {}", e.getMessage(), e);
            if (process != null) {
                terminateProcess(process);
            }
            errorMessage = e.getMessage();
        }

        long durationMs = Duration.between(startTime, Instant.now()).toMillis();
        boolean success = !timedOut && exitCode == 0;

        return ProcessExecutionResult.builder()
                .exitCode(exitCode)
                .success(success)
                .timedOut(timedOut)
                .durationMs(durationMs)
                .outputLines(stdoutRingBuffer.toList())
                .errorOutputLines(stderrRingBuffer.toList())
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * Convenient execution with default timeout and no consumer.
     */
    public ProcessExecutionResult execute(List<String> commandWithArgs) {
        return execute(commandWithArgs, null, null, DEFAULT_TIMEOUT, null, null);
    }

    /**
     * Convenient execution with timeout.
     */
    public ProcessExecutionResult execute(List<String> commandWithArgs, Duration timeout) {
        return execute(commandWithArgs, null, null, timeout, null, null);
    }

    private void readStream(InputStream inputStream, RingBuffer<String> ringBuffer, Consumer<String> consumer) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                ringBuffer.add(line);
                if (consumer != null) {
                    try {
                        consumer.accept(line);
                    } catch (Exception e) {
                        log.trace("Error in log consumer: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void terminateProcess(Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }

        try {
            // Attempt graceful shutdown
            process.destroy();
            boolean exited = process.waitFor(GRACEFUL_KILL_WAIT.toMillis(), TimeUnit.MILLISECONDS);
            if (!exited) {
                log.warn("Process did not terminate gracefully within {}s. Forcing kill...", GRACEFUL_KILL_WAIT.toSeconds());
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
