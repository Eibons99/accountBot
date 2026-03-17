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

-- ============================================
-- 修复日切时间数据的 SQL（如果需要）
-- ============================================
-- 如果你已经设置了日切时间（如 04:00），但之前的交易记录还是按 00:00 记录的
-- 可以执行以下 SQL 来更新旧数据的 transaction_date

-- 方法 1：使用子查询更新（推荐）
UPDATE transactions t
SET transaction_date = (
    SELECT DATE_SUB(t.transaction_date, INTERVAL 1 DAY)
    FROM dual
    WHERE TIME(t.created_at) >= '00:00:00' 
    AND TIME(t.created_at) < '04:00:00'
    AND t.chat_id = 你的群聊 ID  -- 替换为实际的群聊 ID
)
WHERE TIME(created_at) >= '00:00:00' 
AND TIME(created_at) < '04:00:00'
AND chat_id = 你的群聊 ID;  -- 替换为实际的群聊 ID

-- 方法 2：使用 JOIN 更新（MySQL 8.0+）
-- UPDATE transactions t
-- INNER JOIN (
--     SELECT id, DATE_SUB(transaction_date, INTERVAL 1 DAY) as new_date
--     FROM transactions
--     WHERE TIME(created_at) >= '00:00:00' 
--     AND TIME(created_at) < '04:00:00'
--     AND chat_id = 你的群聊 ID  -- 替换为实际的群聊 ID
-- ) sub ON t.id = sub.id
-- SET t.transaction_date = sub.new_date;

-- 注意事项：
-- 1. 执行前请先备份数据库
-- 2. 将 "你的群聊 ID" 替换为实际的数字 ID（如：-1001234567890）
-- 3. 将 '04:00:00' 替换为你实际设置的日切时间
-- 4. 可以先用 SELECT 查看会影响多少条记录：
--    SELECT COUNT(*) FROM transactions 
--    WHERE TIME(created_at) >= '00:00:00' 
--    AND TIME(created_at) < '04:00:00'
--    AND chat_id = 你的群聊 ID;
