package com.example.agent.model.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Agent 对话接口请求参数。 */
public record ChatRequest(
        @NotNull(message = "会话标识不能为空") UUID conversationId,
        @NotBlank(message = "消息不能为空") @Size(max = 10000) String message
) {
}
