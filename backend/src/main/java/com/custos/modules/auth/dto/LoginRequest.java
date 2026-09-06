package com.custos.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "กรุณาระบุชื่อผู้ใช้หรืออีเมล")
    private String username;

    @NotBlank(message = "กรุณาระบุรหัสผ่าน")
    private String password;
}
