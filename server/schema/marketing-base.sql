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

-- ─────────────── trading 底座(批次 1:address/wishlist_item/browse_history)───────────────
-- 列结构与 identity 库现存表逐列核实(SHOW CREATE TABLE 对齐;huihao DDL 同构)

CREATE TABLE IF NOT EXISTS `address` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `customer_id`  BIGINT NOT NULL,
  `receiver`     VARCHAR(64) NOT NULL COMMENT '收件人',
  `phone`        VARCHAR(32) NULL,
  `line`         VARCHAR(255) NOT NULL COMMENT '街道地址',
  `city`         VARCHAR(64) NOT NULL,
  `state`        VARCHAR(64) NULL,
  `zip`          VARCHAR(16) NOT NULL,
  `country`      VARCHAR(64) NOT NULL COMMENT '运费分区映射输入',
  `country_code` CHAR(2) NULL COMMENT 'ISO-3166-1 alpha-2(税费/分区匹配输入)',
  `region_code`  VARCHAR(8) NULL COMMENT 'ISO-3166-2 后缀(仅 US/CA/AU 规范化)',
  `is_default`   TINYINT(1) NOT NULL DEFAULT 0 COMMENT '恒至多一个默认(TX-TRD-008)',
  `created_at`   DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_addr_customer` (`customer_id`,`is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收货地址簿(订单存快照)';

CREATE TABLE IF NOT EXISTS `wishlist_item` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `customer_id` BIGINT NOT NULL,
  `product_id`  BIGINT NOT NULL,
  `created_at`  DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wishlist_customer_product` (`customer_id`,`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收藏(决策18)';

CREATE TABLE IF NOT EXISTS `browse_history` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `customer_id` BIGINT NOT NULL,
  `product_id`  BIGINT NOT NULL,
  `viewed_at`   DATETIME(3) NOT NULL COMMENT 'upsert 刷新;每用户滚动保留 50 条',
  `created_at`  DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_browse_customer_product` (`customer_id`,`product_id`),
  KEY `idx_browse_customer_viewed` (`customer_id`,`viewed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Recently Viewed(决策23)';
