package com.custos.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {

    @NotBlank(message = "กรุณาระบุ Refresh Token")
    private String refreshToken;
}
