package com.custos.modules.backup.filesystem;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.FileSystem;
import java.nio.file.PathMatcher;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Glob-based exclusion rules shared by the local and Docker file archivers. */
@Slf4j
public final class ExclusionMatcher {

    private final List<PathMatcher> matchers;

    private ExclusionMatcher(List<PathMatcher> matchers) {
        this.matchers = matchers;
    }

    public static ExclusionMatcher of(List<String> patterns, FileSystem fileSystem) {
        if (patterns == null || patterns.isEmpty()) {
            return new ExclusionMatcher(Collections.emptyList());
        }
        List<PathMatcher> matchers = new ArrayList<>();
        for (String pattern : patterns) {
            if (pattern != null && !pattern.trim().isEmpty()) {
                String clean = pattern.trim().replace('\\', '/');
                try {
                    matchers.add(fileSystem.getPathMatcher("glob:" + clean));
                } catch (Exception e) {
                    log.warn("Invalid glob pattern '{}': {}", clean, e.getMessage());
                }
            }
        }
        return new ExclusionMatcher(matchers);
    }

    /** True if {@code path} (below {@code root}) matches an exclusion by relative path or by file name. */
    public boolean isExcluded(Path path, Path root) {
        if (matchers.isEmpty()) {
            return false;
        }
        String relativeStr = root.relativize(path).toString().replace('\\', '/');
        String fileName = path.getFileName() != null ? path.getFileName().toString() : "";
        return matches(relativeStr, fileName);
    }

    /**
     * Same rule for a '/'-separated path relative to the archive root (used for Docker tar entries).
     * A directory match prunes its whole subtree, like the local walker does, so every ancestor is checked too.
     */
    public boolean isRelativePathExcluded(String relativePath) {
        if (matchers.isEmpty() || relativePath == null || relativePath.isEmpty()) {
            return false;
        }
        String[] parts = relativePath.split("/");
        StringBuilder prefix = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (prefix.length() > 0) prefix.append('/');
            prefix.append(part);
            if (matches(prefix.toString(), part)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(String relativeStr, String fileName) {
        Path normalizedRelative = Paths.get(relativeStr);
        for (PathMatcher matcher : matchers) {
            if (matcher.matches(normalizedRelative) || matcher.matches(Paths.get(fileName))) {
                return true;
            }
        }
        return false;
    }
}
