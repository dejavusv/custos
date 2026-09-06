package com.custos.modules.console;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogMessageDto {
    private UUID executionId;
    private String stepName;
    private String level;
    private String message;

    @Builder.Default
    private Instant timestamp = Instant.now();
}
