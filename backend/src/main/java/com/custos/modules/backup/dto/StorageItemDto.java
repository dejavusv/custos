package com.custos.modules.backup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageItemDto {
    private String name;
    private String path;
    private String absolutePath;
    private boolean isDirectory;
    private long sizeBytes;
    private Instant lastModified;
    private String extension;
}
