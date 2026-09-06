package com.custos.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {

    @NotBlank(message = "กรุณาระบุรหัสผ่านใหม่")
    @Size(min = 8, message = "รหัสผ่านต้องมีความยาวอย่างน้อย 8 ตัวอักษร")
    private String newPassword;
}
