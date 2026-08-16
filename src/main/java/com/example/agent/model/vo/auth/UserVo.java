package com.example.agent.model.vo.auth;

import com.example.agent.model.bo.UserProfileBo;

import java.time.LocalDateTime;

/** 返回给前端的用户视图对象。 */
public record UserVo(
        Long id,
        String username,
        String email,
        String phone,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static UserVo from(UserProfileBo user) {
        return new UserVo(user.id(), user.username(), user.email(), user.phone(),
                user.createdAt(), user.updatedAt());
    }
}
