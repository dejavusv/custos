package com.custos.modules.externalnotify.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExternalNotifyTaskDto {
    private String taskId;
    private String title;
    private String chatbotId;
    private String chatbotName;
    private List<String> recipients;
}
