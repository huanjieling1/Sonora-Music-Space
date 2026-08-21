CREATE TABLE generic_workflow_execution (
    workflow_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '通用 DAG 工作流 UUID',
    principal_id VARCHAR(128) NOT NULL COMMENT '工作流所属登录主体，读取和恢复必须同时校验',
    conversation_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '' COMMENT '关联会话 UUID，可为空字符串',
    plan_json LONGTEXT NOT NULL COMMENT '不可变 CompiledPlan JSON 快照',
    state_json LONGTEXT NOT NULL COMMENT '任务状态、尝试次数、类型化结果、等待槽位和幂等键 JSON',
    status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'RUNNING/WAITING_USER/COMPLETED/FAILED/CANCELLED',
    created_at DATETIME(6) NOT NULL COMMENT '工作流创建时间',
    updated_at DATETIME(6) NOT NULL COMMENT '最近一次状态更新时间',
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA 乐观锁版本',
    PRIMARY KEY (workflow_id),
    KEY idx_generic_workflow_owner_updated (principal_id, updated_at),
    KEY idx_generic_workflow_status_updated (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通用多意图 DAG 计划和执行状态快照';
