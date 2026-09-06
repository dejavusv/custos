package com.custos.modules.auth.dto;

import com.custos.modules.auth.entity.UserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStatusRequest {

    @NotNull(message = "กรุณาระบุสถานะผู้ใช้ (ACTIVE, SUSPENDED, LOCKED)")
    private UserStatus status;
}
