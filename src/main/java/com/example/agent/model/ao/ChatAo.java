package com.example.agent.model.ao;

import java.util.UUID;

/** 一次 Agent 对话调用所需的应用层参数。 */
public record ChatAo(Long userId, UUID conversationId, String message) {
}
