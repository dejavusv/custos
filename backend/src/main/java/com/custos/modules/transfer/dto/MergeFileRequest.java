package com.custos.modules.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MergeFileRequest {

    @NotEmpty(message = "List of chunk file paths cannot be empty")
    private List<String> chunkFilePaths;

    @NotBlank(message = "Destination merged file path is required")
    private String destinationFilePath;
}
