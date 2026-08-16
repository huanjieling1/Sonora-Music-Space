package com.example.agent.model.entity;

import com.example.agent.constant.enums.ChatMessageRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_chat_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 消息所属会话 UUID。 */
    @Column(name = "conversation_id", nullable = false, length = 36)
    private String conversationId;

    /** 消息角色，只允许用户或 Agent。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ChatMessageRole role;

    /** 消息正文。 */
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    /** 消息创建日期。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder(access = AccessLevel.PRIVATE)
    private AgentChatMessage(String conversationId, ChatMessageRole role, String content, LocalDateTime createdAt) {
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }

    public static AgentChatMessage user(String conversationId, String content, LocalDateTime createdAt) {
        return create(conversationId, ChatMessageRole.USER, content, createdAt);
    }

    public static AgentChatMessage assistant(String conversationId, String content, LocalDateTime createdAt) {
        return create(conversationId, ChatMessageRole.ASSISTANT, content, createdAt);
    }

    private static AgentChatMessage create(String conversationId, ChatMessageRole role,
                                           String content, LocalDateTime createdAt) {
        return AgentChatMessage.builder()
                .conversationId(conversationId)
                .role(role)
                .content(content)
                .createdAt(createdAt)
                .build();
    }
}
