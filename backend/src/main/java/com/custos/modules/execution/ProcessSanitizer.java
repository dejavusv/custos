package com.custos.modules.execution;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ProcessSanitizer {

    private static final Pattern DB_IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{1,64}$");
    private static final Pattern HOSTNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9.\\-_:]{1,255}$");
    private static final Pattern SAFE_ARGUMENT_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-./:=,@%]+$");

    // Characters strictly prohibited anywhere in process arguments
    private static final char[] DANGEROUS_CHARS = new char[]{
            ';', '&', '|', '`', '$', '>', '<', '\n', '\r', '(', ')', '{', '}', '!', '\\'
    };

    // Whitelisted binaries allowed to be invoked directly
    private static final Set<String> ALLOWED_EXECUTABLES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "mysqldump", "mysqldump.exe",
            "mariadb-dump", "mariadb-dump.exe",
            "pg_dump", "pg_dump.exe",
            "tar", "tar.exe",
            "gzip", "gzip.exe",
            "zstd", "zstd.exe",
            "zip", "zip.exe",
            "split", "split.exe",
            "echo", "echo.exe",
            "ping", "ping.exe",
            "hostname", "hostname.exe"
    )));

    /**
     * Validates an executable binary name against the whitelist.
     */
    public void validateExecutable(String executable) {
        if (executable == null || executable.trim().isEmpty()) {
            throw new SecurityException("Executable command cannot be null or empty");
        }

        // Extract base binary name if path is provided
        String baseName = executable.trim();
        int lastSlash = Math.max(baseName.lastIndexOf('/'), baseName.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            baseName = baseName.substring(lastSlash + 1);
        }

        if (!ALLOWED_EXECUTABLES.contains(baseName.toLowerCase())) {
            log.warn("Security Alert: Executable not in whitelist: {}", baseName);
            throw new SecurityException("Executable '" + baseName + "' is not permitted for execution");
        }
    }

    /**
     * Validates database or table name against SQL identifier rules.
     */
    public void validateIdentifier(String identifier, String fieldName) {
        if (identifier == null || !DB_IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            log.warn("Security Alert: Invalid identifier for {}: {}", fieldName, identifier);
            throw new SecurityException("Invalid " + fieldName + ": must contain only alphanumeric characters and underscores");
        }
    }

    /**
     * Validates host or IP address format.
     */
    public void validateHostname(String host) {
        if (host == null || !HOSTNAME_PATTERN.matcher(host).matches()) {
            log.warn("Security Alert: Invalid hostname: {}", host);
            throw new SecurityException("Invalid hostname format: " + host);
        }
    }

    /**
     * Validates individual argument string against dangerous shell characters.
     */
    public void validateArgument(String argument) {
        if (argument == null) {
            return;
        }

        for (char dangerous : DANGEROUS_CHARS) {
            if (argument.indexOf(dangerous) >= 0) {
                log.warn("Security Alert: Dangerous character '{}' detected in argument: {}", dangerous, argument);
                throw new SecurityException("Dangerous shell character detected in argument: " + dangerous);
            }
        }
    }

    /**
     * Validates safe file path string.
     */
    public void validatePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new SecurityException("Path cannot be empty");
        }
        validateArgument(path);

        // Disallow dangerous traversal sequences
        if (path.contains("..\\") || path.contains("../")) {
            log.warn("Security Alert: Path traversal sequence detected: {}", path);
            throw new SecurityException("Path traversal (..) is not permitted");
        }
    }
}
