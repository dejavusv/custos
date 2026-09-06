package com.custos.modules.transfer.service;

import com.custos.modules.transfer.dto.StoragePrecheckRequest;
import com.custos.modules.transfer.dto.StoragePrecheckResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
public class StoragePrecheckService {

    public StoragePrecheckResult checkStorage(StoragePrecheckRequest request) {
        if (request == null || request.getPath() == null || request.getPath().trim().isEmpty()) {
            throw new IllegalArgumentException("Target path cannot be empty");
        }

        Path path = Paths.get(request.getPath().trim()).toAbsolutePath().normalize();
        File targetFile = path.toFile();

        // If path doesn't exist yet, find the existing parent directory
        File existingAnchor = targetFile;
        while (existingAnchor != null && !existingAnchor.exists()) {
            existingAnchor = existingAnchor.getParentFile();
        }

        if (existingAnchor == null) {
            existingAnchor = new File(System.getProperty("user.dir"));
        }

        long usableSpace = existingAnchor.getUsableSpace();
        long totalSpace = existingAnchor.getTotalSpace();

        double margin = request.getSafetyMargin() > 0 ? request.getSafetyMargin() : 1.0;
        long requiredWithMargin = (long) (request.getRequiredBytes() * margin);

        boolean hasEnough = usableSpace >= requiredWithMargin;
        double freePercentage = totalSpace > 0 ? ((double) usableSpace / totalSpace) * 100.0 : 0.0;

        String msg;
        if (hasEnough) {
            msg = String.format("Storage check passed: %d MB usable space available (Required with margin: %d MB)",
                    usableSpace / (1024 * 1024), requiredWithMargin / (1024 * 1024));
            log.info(msg);
        } else {
            msg = String.format("Insufficient storage on %s! Usable space: %d MB, Required: %d MB (Safety margin: %.1fx)",
                    existingAnchor.getAbsolutePath(), usableSpace / (1024 * 1024), requiredWithMargin / (1024 * 1024), margin);
            log.warn(msg);
        }

        return StoragePrecheckResult.builder()
                .targetPath(path.toString())
                .usableSpaceBytes(usableSpace)
                .totalSpaceBytes(totalSpace)
                .requiredBytes(request.getRequiredBytes())
                .safetyMargin(margin)
                .hasEnoughSpace(hasEnough)
                .freeSpacePercentage(freePercentage)
                .message(msg)
                .build();
    }

    /**
     * Asserts that sufficient space exists, otherwise throws an exception immediately.
     */
    public void assertSufficientSpace(String path, long requiredBytes, double safetyMargin) {
        StoragePrecheckResult result = checkStorage(StoragePrecheckRequest.builder()
                .path(path)
                .requiredBytes(requiredBytes)
                .safetyMargin(safetyMargin)
                .build());

        if (!result.isHasEnoughSpace()) {
            throw new IllegalStateException(result.getMessage());
        }
    }
}
