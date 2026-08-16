package com.example.agent.model.bo;

import com.example.agent.constant.enums.ChatMessageRole;

import java.time.LocalDateTime;

/** 对话历史消息业务对象。 */
public record ChatMessageBo(
        Long id,
        ChatMessageRole role,
        String content,
        LocalDateTime createdAt
) {
}
