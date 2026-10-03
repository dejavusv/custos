package com.custos.modules.externalnotify.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExternalNotifySendResult {
    private int sentCount;
    @Builder.Default
    private List<String> failedRecipients = new ArrayList<>();
    private String message;
}
