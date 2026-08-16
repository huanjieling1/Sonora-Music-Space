package com.example.agent.model.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 注册接口请求参数。 */
public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^(?!\\d+$)[\\p{L}\\d_]{3,32}$", message = "用户名应为 3-32 位中文、字母、数字或下划线，且不能为纯数字")
        String username,
        @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") @Size(max = 254) String email,
        @NotBlank(message = "手机号不能为空") @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的中国大陆手机号") String phone,
        @NotBlank(message = "密码不能为空")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$", message = "密码应为 8-64 位并同时包含字母和数字")
        String password,
        @NotBlank(message = "请再次输入密码") String confirmPassword,
        @NotBlank(message = "请输入图形验证码") @Size(min = 5, max = 5, message = "图形验证码应为 5 位") String imageCaptcha
) {
}
