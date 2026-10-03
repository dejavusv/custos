package com.custos.modules.backup.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
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
    // Lombok would expose this as "directory"; the frontend reads "isDirectory"
    @Getter(onMethod_ = @JsonProperty("isDirectory"))
    private boolean isDirectory;
    private long sizeBytes;
    private Instant lastModified;
    private String extension;
}
