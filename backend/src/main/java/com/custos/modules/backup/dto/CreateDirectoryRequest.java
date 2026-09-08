package com.custos.modules.backup.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDirectoryRequest {

    @NotBlank(message = "Parent directory path is required")
    private String parentPath;

    @NotBlank(message = "Folder name is required")
    private String folderName;
}
