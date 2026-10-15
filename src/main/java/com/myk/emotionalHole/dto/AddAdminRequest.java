package com.myk.emotionalHole.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 添加管理员请求参数
 */
@Data
public class AddAdminRequest {

    @NotBlank(message = "管理员账号不能为空")
    @Size(min = 3, max = 32, message = "管理员账号长度必须在3-32个字符之间")
    private String username;

    @NotBlank(message = "管理员密码不能为空")
    @Size(min = 6, max = 32, message = "管理员密码长度必须在6-32个字符之间")
    private String password;
}
