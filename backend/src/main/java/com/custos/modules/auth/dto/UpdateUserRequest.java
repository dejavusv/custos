package com.custos.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Set;

@Data
public class UpdateUserRequest {

    @NotBlank(message = "กรุณาระบุ Email")
    @Email(message = "รูปแบบ Email ไม่ถูกต้อง")
    private String email;

    @NotEmpty(message = "กรุณาเลือกอย่างน้อย 1 สิทธิ์ (Role)")
    private Set<String> roles;
}
