package com.example.agent.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agent_conversation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentConversation {
    public static final String DEFAULT_TITLE = "新对话";

    /** 会话 UUID，与用户主键共同构成 Agent 记忆隔离边界。 */
    @Id
    @Column(nullable = false, length = 36)
    private String id;

    /** 会话所属用户主键，所有查询必须同时校验该字段。 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 会话标题，首次成功对话后从用户消息生成。 */
    @Column(nullable = false, length = 120)
    private String title;

    /** 会话创建日期。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 会话最后活跃日期，用于侧栏时间分组和倒序排列。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 逻辑删除状态，false 表示正常，true 表示已删除。 */
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Builder(access = AccessLevel.PRIVATE)
    private AgentConversation(UUID id, Long userId) {
        this.id = id.toString();
        this.userId = userId;
        this.title = DEFAULT_TITLE;
    }

    public static AgentConversation create(Long userId) {
        return AgentConversation.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .build();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    public void recordExchange(String firstUserMessage, LocalDateTime now) {
        if (DEFAULT_TITLE.equals(title)) {
            title = titleFrom(firstUserMessage);
        }
        updatedAt = now;
    }

    /** 将会话标记为已删除；消息记录保留用于审计，但不再参与列表、历史与记忆查询。 */
    public void markDeleted(LocalDateTime now) {
        deleted = true;
        updatedAt = now;
    }

    private static String titleFrom(String message) {
        String normalized = message == null ? "" : message.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return DEFAULT_TITLE;
        }
        int end = normalized.offsetByCodePoints(0, Math.min(32, normalized.codePointCount(0, normalized.length())));
        return normalized.substring(0, end);
    }

    public UUID getConversationId() { return UUID.fromString(id); }
}
