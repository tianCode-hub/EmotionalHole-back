package com.myk.emotionalHole.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理员登录请求参数
 */
@Data
public class AdminLoginRequest {

    @NotBlank(message = "账号不能为空")
    private String adminAccount;

    @NotBlank(message = "密码不能为空")
    private String adminPassword;
}
