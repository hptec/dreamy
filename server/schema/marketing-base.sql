-- ============================================================
-- marketing 底座域 DDL(bootstrap 1.0):newsletter_subscriber + contact_message
-- 语义对齐 Java huihao-mysql DDL + V20260718_newsletter_unsubscribe.sql 增量终态。
-- 纪律:仅 CREATE IF NOT EXISTS,绝不 DROP/ALTER;列变更走新增自举段+人工 ALTER。
-- 库:dreamy_server(identity 域 16 表之后追加;ID 段独立,与 identity 域表无 ID 冲突)
-- ============================================================

CREATE TABLE IF NOT EXISTS `newsletter_subscriber` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `email`           VARCHAR(255) NOT NULL COMMENT '小写归一,唯一(幂等判重首写胜出)',
  `source`          TINYINT NOT NULL COMMENT '来源:1=页脚 2=弹窗 3=退出挽留 4=首页区块',
  `locale`          VARCHAR(8) NOT NULL COMMENT 'en|es|fr',
  `status`          TINYINT NOT NULL DEFAULT 1 COMMENT '1=已订阅 2=已退订',
  `subscribed_at`   DATETIME(3) NOT NULL COMMENT '订阅时间(退订 token 代际锚点)',
  `unsubscribed_at` DATETIME(3) NULL COMMENT '退订时间(重复退订保留首次时间)',
  `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_newsletter_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Newsletter 订阅(仅落表,不发码不发邮件)';

CREATE TABLE IF NOT EXISTS `contact_message` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name`         VARCHAR(100) NOT NULL,
  `email`        VARCHAR(255) NOT NULL,
  `subject`      VARCHAR(200) NULL,
  `message`      VARCHAR(5000) NOT NULL,
  `submitted_at` DATETIME(3) NOT NULL COMMENT '提交时间',
  `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_contact_submitted` (`submitted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='联系表单消息(管理端本期不做查看页)';
