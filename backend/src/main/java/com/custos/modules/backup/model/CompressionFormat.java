package com.custos.modules.backup.model;

public enum CompressionFormat {
    GZIP(".gz"),
    TAR_GZ(".tar.gz"),
    ZIP(".zip"),
    NONE(".sql");

    private final String defaultExtension;

    CompressionFormat(String defaultExtension) {
        this.defaultExtension = defaultExtension;
    }

    public String getDefaultExtension() {
        return defaultExtension;
    }
}
