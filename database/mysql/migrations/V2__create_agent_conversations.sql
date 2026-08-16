CREATE TABLE agent_conversation (
    id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '会话 UUID，作为对话记忆的逻辑标识',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '会话所属用户主键，用于用户级数据隔离',
    title VARCHAR(120) NOT NULL COMMENT '会话标题，首次成功对话时由用户消息生成',
    created_at DATETIME(6) NOT NULL COMMENT '会话创建日期',
    updated_at DATETIME(6) NOT NULL COMMENT '会话最后活跃日期，用于时间分组和排序',
    is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除状态：0-未删除，1-已删除',
    PRIMARY KEY (id),
    KEY idx_conversation_user_updated (user_id, is_deleted, updated_at),
    CONSTRAINT fk_conversation_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent 用户会话表';

CREATE TABLE agent_chat_message (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '聊天消息主键，同时用于保证消息顺序',
    conversation_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '消息所属会话 UUID',
    role VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '消息角色：USER-用户，ASSISTANT-Agent',
    content MEDIUMTEXT NOT NULL COMMENT '消息正文，不保存密码、验证码或密钥',
    created_at DATETIME(6) NOT NULL COMMENT '消息创建日期',
    PRIMARY KEY (id),
    KEY idx_chat_message_conversation (conversation_id, id),
    CONSTRAINT fk_chat_message_conversation FOREIGN KEY (conversation_id) REFERENCES agent_conversation (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent 会话消息历史表';

