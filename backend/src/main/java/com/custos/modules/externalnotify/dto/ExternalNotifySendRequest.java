package com.custos.modules.externalnotify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExternalNotifySendRequest {

    @NotNull(message = "กรุณาเลือก Credential")
    private UUID credentialId;

    @NotBlank(message = "กรุณาเลือก Task")
    private String taskId;

    @NotBlank(message = "กรุณาระบุข้อความ")
    @Size(max = 5000, message = "ข้อความต้องไม่เกิน 5000 ตัวอักษร")
    private String message;
}
