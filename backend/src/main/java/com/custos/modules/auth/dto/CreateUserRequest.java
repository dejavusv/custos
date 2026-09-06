package com.custos.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
public class CreateUserRequest {

    @NotBlank(message = "กรุณาระบุ Username")
    @Size(min = 3, max = 50, message = "Username ต้องมีความยาว 3-50 ตัวอักษร")
    private String username;

    @NotBlank(message = "กรุณาระบุ Email")
    @Email(message = "รูปแบบ Email ไม่ถูกต้อง")
    private String email;

    @NotBlank(message = "กรุณาระบุรหัสผ่าน")
    @Size(min = 8, message = "รหัสผ่านต้องมีความยาวอย่างน้อย 8 ตัวอักษร")
    private String password;

    @NotEmpty(message = "กรุณาเลือกอย่างน้อย 1 สิทธิ์ (Role)")
    private Set<String> roles;
}
