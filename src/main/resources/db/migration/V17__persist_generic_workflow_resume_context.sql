ALTER TABLE generic_workflow_execution
    ADD COLUMN resume_context_json LONGTEXT NULL
        COMMENT '恢复 WAITING_USER 所需的原始回合、目标图和跟进计划；不包含用户画像载荷'
        AFTER state_json,
    ADD KEY idx_generic_workflow_conversation_waiting
        (principal_id, conversation_id, status, updated_at);
