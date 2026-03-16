-- 初始化数据库表结构
-- 仅在 ddl-auto=create 时执行

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    telegram_id BIGINT NOT NULL UNIQUE,
    username VARCHAR(255),
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    is_tagged TINYINT(1) DEFAULT 0,
    is_operator TINYINT(1) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT(1) DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 交易记录表
CREATE TABLE IF NOT EXISTS transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    type VARCHAR(20) NOT NULL,
    category VARCHAR(100),
    description VARCHAR(500),
    transaction_date DATE NOT NULL,
    chat_id BIGINT,
    tagged_user_id BIGINT COMMENT '标记员ID',
    operator_user_id BIGINT COMMENT '操作员ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) DEFAULT 'PENDING',
    deleted TINYINT(1) DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 标记记录表
CREATE TABLE IF NOT EXISTS tagged_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tagged_user_id BIGINT NOT NULL,
    operator_user_id BIGINT NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    tag_content VARCHAR(255) NOT NULL,
    chat_id BIGINT,
    message_id INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT(1) DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 聊天记录日志表
CREATE TABLE IF NOT EXISTS chat_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id INT,
    user_id BIGINT,
    username VARCHAR(255),
    nickname VARCHAR(255),
    chat_id BIGINT,
    chat_title VARCHAR(255),
    message_text TEXT,
    log_type VARCHAR(20) NOT NULL COMMENT 'RECEIVE=收到, SEND=发送',
    reply_to_message_id INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT(1) DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 系统配置表（存储日切时间等配置）
CREATE TABLE IF NOT EXISTS system_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value VARCHAR(500),
    description VARCHAR(255),
    chat_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT(1) DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 聊天同步状态表（记录每个聊天最后处理的消息ID）
CREATE TABLE IF NOT EXISTS chat_sync_state (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chat_id BIGINT NOT NULL UNIQUE,
    last_message_id INT,
    last_sync_time TIMESTAMP,
    need_full_sync TINYINT(1) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建索引
CREATE INDEX idx_transactions_user_id ON transactions(user_id);
CREATE INDEX idx_transactions_chat_id ON transactions(chat_id);
CREATE INDEX idx_transactions_date ON transactions(transaction_date);
CREATE INDEX idx_tagged_records_chat_id ON tagged_records(chat_id);
CREATE INDEX idx_tagged_records_tagged_user_id ON tagged_records(tagged_user_id);
CREATE INDEX idx_tagged_records_operator_user_id ON tagged_records(operator_user_id);
CREATE INDEX idx_chat_logs_chat_id ON chat_logs(chat_id);
CREATE INDEX idx_chat_logs_user_id ON chat_logs(user_id);
CREATE INDEX idx_chat_logs_created_at ON chat_logs(created_at);
CREATE INDEX idx_system_config_key ON system_config(config_key);
CREATE INDEX idx_system_config_chat_id ON system_config(chat_id);
