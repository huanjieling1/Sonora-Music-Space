package com.example.agent.model.bo;

import java.time.LocalDateTime;

/** 认证业务向接口层输出的用户信息。 */
public record UserProfileBo(
        Long id,
        String username,
        String email,
        String phone,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
