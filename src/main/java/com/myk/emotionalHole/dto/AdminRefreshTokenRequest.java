package com.myk.emotionalHole.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理员刷新令牌请求参数
 */
@Data
public class AdminRefreshTokenRequest {

    @NotBlank(message = "刷新令牌不能为空")
    private String refreshToken;
}
