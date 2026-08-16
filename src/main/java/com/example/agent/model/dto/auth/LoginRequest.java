package com.example.agent.model.dto.auth;

import jakarta.validation.constraints.NotBlank;

/** 登录接口请求参数。 */
public record LoginRequest(
        @NotBlank(message = "请输入用户名、邮箱或手机号") String account,
        @NotBlank(message = "请输入密码") String password,
        boolean rememberMe
) {
}
