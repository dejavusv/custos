package com.custos.modules.backup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageBrowseResponse {
    private String currentPath;
    private String absolutePath;
    private String defaultDirectory;
    private String parentPath;
    private boolean canGoUp;
    private List<StorageItemDto> items;
}
