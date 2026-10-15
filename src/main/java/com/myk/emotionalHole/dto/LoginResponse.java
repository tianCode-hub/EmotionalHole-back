package com.myk.emotionalHole.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应数据
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {
    private String anonymousId;
    private int userStatus;
    private String token;
    private String refreshToken;
    private String avatar;


}