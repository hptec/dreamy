-- 手动执行（项目无 Flyway）
-- newsletter 退订（方案 A）：主表加 status + unsubscribed_at。
-- 纯增量、向后兼容：旧代码忽略新列；必须先执行本 DDL 再发布依赖新列的代码。
-- 回退无需删列（保留无害）。
ALTER TABLE newsletter_subscriber
    ADD COLUMN status tinyint NOT NULL DEFAULT 1 COMMENT '1=已订阅 2=已退订' AFTER locale,
    ADD COLUMN unsubscribed_at datetime(3) NULL COMMENT '退订时间（重复退订保留首次时间）' AFTER subscribed_at;
