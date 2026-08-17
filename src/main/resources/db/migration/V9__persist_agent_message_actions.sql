ALTER TABLE agent_chat_message
    ADD COLUMN actions_json MEDIUMTEXT NULL
        COMMENT '助手消息关联的结构化音乐展示动作 JSON，用于刷新后恢复结果卡片'
        AFTER content;
