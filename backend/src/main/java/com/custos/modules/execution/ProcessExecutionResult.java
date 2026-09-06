package com.custos.modules.execution;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ProcessExecutionResult {

    private final int exitCode;
    private final boolean success;
    private final boolean timedOut;
    private final long durationMs;
    private final List<String> outputLines;
    private final List<String> errorOutputLines;
    private final String errorMessage;

    public String getOutputAsText() {
        return outputLines != null ? String.join("\n", outputLines) : "";
    }

    public String getErrorOutputAsText() {
        return errorOutputLines != null ? String.join("\n", errorOutputLines) : "";
    }
}
