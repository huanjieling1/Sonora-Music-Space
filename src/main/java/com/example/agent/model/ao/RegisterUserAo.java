package com.example.agent.model.ao;

/** Web 层提交给注册业务的应用对象。 */
public record RegisterUserAo(
        String username,
        String email,
        String phone,
        String password,
        String confirmPassword
) {
}
