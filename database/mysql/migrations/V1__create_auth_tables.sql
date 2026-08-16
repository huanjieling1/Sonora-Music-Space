CREATE TABLE app_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户主键',
    username VARCHAR(32) NOT NULL COMMENT '用户名，允许中文、字母、数字和下划线，全局唯一',
    email VARCHAR(254) NOT NULL COMMENT '用户邮箱，统一以小写保存，全局唯一',
    phone VARCHAR(20) NOT NULL COMMENT '中国大陆手机号，全局唯一',
    password VARCHAR(255) NOT NULL COMMENT '带随机盐并迭代计算的 SHA-256 密码摘要，不保存明文密码',
    created_at DATETIME(6) NOT NULL COMMENT '用户记录创建日期',
    updated_at DATETIME(6) NOT NULL COMMENT '用户记录最后修改日期',
    is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除状态：0-未删除，1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_username (username),
    UNIQUE KEY uk_app_user_email (email),
    UNIQUE KEY uk_app_user_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent 系统用户信息表';

CREATE TABLE email_verification_code (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '邮箱验证码记录主键',
    email VARCHAR(254) NOT NULL COMMENT '接收验证码的标准化邮箱地址',
    code_hash VARCHAR(255) NOT NULL COMMENT '带随机盐的验证码摘要，不保存明文验证码',
    expires_at DATETIME(6) NOT NULL COMMENT '验证码失效时间',
    consumed_at DATETIME(6) NULL COMMENT '验证码成功使用时间，未使用时为空',
    failed_attempts INT NOT NULL DEFAULT 0 COMMENT '验证码累计校验失败次数',
    created_at DATETIME(6) NOT NULL COMMENT '验证码发送记录创建日期',
    PRIMARY KEY (id),
    KEY idx_email_code_lookup (email, created_at),
    KEY idx_email_code_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='注册邮箱验证码记录表';

