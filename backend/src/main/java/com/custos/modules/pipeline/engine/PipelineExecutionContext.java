package com.custos.modules.pipeline.engine;

import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PipelineExecutionContext {

    private final Map<String, Object> variables = new ConcurrentHashMap<>();
    private final Map<String, Object> metrics = new ConcurrentHashMap<>();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    public void setVariable(String key, Object value) {
        if (value != null) {
            variables.put(key, value);
        } else {
            variables.remove(key);
        }
    }

    public Object getVariable(String key) {
        return variables.get(key);
    }

    public String getStringVariable(String key, String defaultValue) {
        Object val = variables.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    public void setLastOutputPath(String path) {
        setVariable("last_output_path", path);
    }

    public String getLastOutputPath() {
        return getStringVariable("last_output_path", null);
    }

    public void setLastChecksum(String checksum) {
        setVariable("last_checksum", checksum);
    }

    public String getLastChecksum() {
        return getStringVariable("last_checksum", null);
    }

    public void setLastFileSize(long sizeBytes) {
        setVariable("last_file_size_bytes", sizeBytes);
    }

    public Long getLastFileSize() {
        Object size = variables.get("last_file_size_bytes");
        if (size instanceof Number) {
            return ((Number) size).longValue();
        }
        return null;
    }

    public void setStepOutput(String nodeKey, String outputPath, String checksum, Long fileSize) {
        if (nodeKey != null) {
            if (outputPath != null) setVariable(nodeKey + ".output_path", outputPath);
            if (checksum != null) setVariable(nodeKey + ".checksum", checksum);
            if (fileSize != null) setVariable(nodeKey + ".file_size_bytes", fileSize);
        }
        if (outputPath != null) setLastOutputPath(outputPath);
        if (checksum != null) setLastChecksum(checksum);
        if (fileSize != null) setLastFileSize(fileSize != null ? fileSize : 0L);
    }

    public void addMetric(String key, Object value) {
        if (value != null) {
            metrics.put(key, value);
        }
    }

    public Map<String, Object> getAllVariables() {
        return new ConcurrentHashMap<>(variables);
    }

    public Map<String, Object> getAllMetrics() {
        return new ConcurrentHashMap<>(metrics);
    }

    /**
     * Resolves expressions like "${last_output_path}" or "${dumpNode.output_path}"
     * against variables stored in this context.
     */
    public String resolvePlaceholders(String text) {
        if (text == null || !text.contains("${")) {
            return text;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            Object value = variables.get(key);
            String replacement = value != null ? Matcher.quoteReplacement(value.toString()) : matcher.group(0);
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void cancel() {
        cancelled.set(true);
    }
}
