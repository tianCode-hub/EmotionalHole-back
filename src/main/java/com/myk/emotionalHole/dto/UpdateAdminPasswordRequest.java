package com.myk.emotionalHole.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改管理员密码请求参数（管理员列表页使用）
 */
@Data
public class UpdateAdminPasswordRequest {

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度必须在6-32个字符之间")
    private String password;
}
