-- Accounting Bot 数据库升级脚本 v1.1.0
-- 功能：支持日入款消息定位（Telegram 消息链接）

-- 为 transactions 表添加 telegram_message_id 字段
ALTER TABLE transactions 
ADD COLUMN telegram_message_id INT NULL COMMENT 'Telegram 消息 ID，用于消息定位';

-- 可选：为现有数据生成索引（如果数据量大）
-- CREATE INDEX idx_telegram_message_id ON transactions(telegram_message_id);

-- 说明：
-- 1. 此字段用于在 /today 命令中生成可点击的 Telegram 消息链接
-- 2. 用户可以点击金额直接跳转到原始消息
-- 3. 仅对新记录的交易生效，历史数据该字段为 NULL
