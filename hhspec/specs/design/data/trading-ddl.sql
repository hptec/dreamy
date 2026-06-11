-- =============================================================================
-- trading 限界上下文 物理库 DDL（MySQL 8.0+）
-- change: portal-api-integration  domain: trading  （12 表）
-- 引擎: InnoDB  字符集: utf8mb4  排序: utf8mb4_0900_ai_ci
-- 约定:
--   - 主键: BIGINT AUTO_INCREMENT（huihao LongAuditableEntity；checkout_config 单例 id=1）
--   - 枚举: VARCHAR + Java enum 双保险（MAP-TRD-012，契约字符串取值）
--   - 时间: DATETIME(3) 存 UTC（CP-014/MAP-TRD-011）
--   - 金额: DECIMAL(12,2) 订单币种；汇率 DECIMAL(12,6)
--   - 外键: 全表无物理 FOREIGN KEY（CP-010），引用完整性由应用层 + 事务维护（CV-TRD-011）
--   - CHECK: 语义基线（L3 运行期由实体注解 DdlAuto 建表，CHECK 由应用层枚举/校验双保险承载）
--   - cart_item.custom_size_hash: L2 设计为 STORED 生成列，L3 落地为应用计算普通列
--     （TradingParams.customSizeHash 固定键序规范化 JSON SHA-256，合并判定语义一致 RM-TRD-003）
-- 运行期建表: huihao-mysql DDL-auto（@EnableMysql scanPackages=com.dreamy.trading.domain）
--             以实体注解为准自动建表/加列；本文件为权威等价 SQL（审计/还原用）。
-- 种子数据: TradingSeedInitializer（决策 21：汇率五币种 + checkout_config 单例 + RBAC /settings；
--           订单类数据不灌假数据）。
-- L2 TRACE: trading-data-detail.md §9（语义基线）/ IDX-TRD-001~021 / SCHED-TRD-002~003 保留策略。
-- =============================================================================
SET NAMES utf8mb4;

-- 1. cart_item 购物车条目（决策 8；IDX-TRD-014/015）
CREATE TABLE `cart_item` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `customer_id`      BIGINT       NOT NULL COMMENT '逻辑外键→user.id（BE-DIM-6 隔离）',
  `product_id`       BIGINT       NOT NULL COMMENT '逻辑外键→product.id',
  `sku_id`           BIGINT       NULL     COMMENT '现货必填；定制款 NULL（决策 6）',
  `qty`              INT          NOT NULL COMMENT '数量 >=1',
  `custom_size_data` JSON         NULL     COMMENT '定制尺寸 {bust,waist,hips,hollow_to_floor,height?}',
  `custom_size_hash` CHAR(64)     NULL     COMMENT '定制数据合并判定哈希（规范化 JSON SHA-256，应用计算）',
  `created_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_cart_customer` (`customer_id`),
  KEY `idx_cart_customer_sku` (`customer_id`,`sku_id`),
  CONSTRAINT `ck_cart_qty` CHECK (`qty` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='购物车条目';

-- 2. cart_merge_record 匿名车合并幂等（决策 8；IDX-TRD-016；保留 30 天 SCHED-TRD-003）
CREATE TABLE `cart_merge_record` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `customer_id` BIGINT      NOT NULL,
  `anon_token`  VARCHAR(64) NOT NULL COMMENT '前端匿名购物车标识（合并幂等键）',
  `created_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merge_customer_token` (`customer_id`,`anon_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='匿名购物车合并幂等记录';

-- 3. address 地址簿（IDX-TRD-017；订单存 address_snapshot 快照，删除不波及）
CREATE TABLE `address` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `customer_id` BIGINT       NOT NULL,
  `receiver`    VARCHAR(64)  NOT NULL COMMENT '收件人',
  `phone`       VARCHAR(32)  NULL,
  `line`        VARCHAR(255) NOT NULL COMMENT '街道地址',
  `city`        VARCHAR(64)  NOT NULL,
  `state`       VARCHAR(64)  NULL,
  `zip`         VARCHAR(16)  NOT NULL,
  `country`     VARCHAR(64)  NOT NULL COMMENT '运费分区映射输入',
  `is_default`  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '恒至多一个默认（TX-TRD-008）',
  `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_addr_customer` (`customer_id`,`is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收货地址簿（订单存快照）';

-- 4. orders 订单主单（表名规避保留字 ORDER；IDX-TRD-001~005）
CREATE TABLE `orders` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT,
  `order_no`         VARCHAR(20)   NOT NULL COMMENT 'DRM-YYYYMMDD-NNNN（预生成）',
  `customer_id`      BIGINT        NOT NULL,
  `status`           VARCHAR(16)   NOT NULL DEFAULT 'pending' COMMENT 'order_lifecycle 七态',
  `currency`         CHAR(3)       NOT NULL COMMENT 'USD/EUR/CAD/AUD/GBP（决策 14）',
  `exchange_rate`    DECIMAL(12,6) NOT NULL COMMENT '下单锁定 USD→订单币种汇率（决策 14）',
  `wedding_date`     DATE          NULL     COMMENT '婚期（交期复核，决策 20.6）',
  `subtotal`         DECIMAL(12,2) NOT NULL COMMENT '订单币种行小计求和',
  `shipping_fee`     DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '所选承运商报价快照（F-036）',
  `gift_wrap`        TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '礼品包装（决策 28）',
  `gift_wrap_fee`    DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '礼品包装费快照（决策 28）',
  `discount_amount`  DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '券减免（订单币种）',
  `total_amount`     DECIMAL(12,2) NOT NULL COMMENT '= subtotal+shipping_fee+gift_wrap_fee-discount_amount',
  `coupon_id`        BIGINT        NULL     COMMENT '逻辑外键→coupon.id（marketing）',
  `payment_method`   VARCHAR(32)   NULL     COMMENT 'Stripe/Apple Pay/Google Pay/Klarna/Afterpay（决策 25）',
  `address_snapshot` JSON          NOT NULL COMMENT '下单地址快照（删地址不波及）',
  `carrier`          VARCHAR(64)   NULL     COMMENT '承运商快照枚举三值（F-036）',
  `tracking_no`      VARCHAR(64)   NULL     COMMENT '物流单号（手填，BE-DIM-5）',
  `idempotency_key`  VARCHAR(64)   NOT NULL COMMENT '客户端 UUID 防重（BE-DIM-4）',
  `expires_at`       DATETIME(3)   NOT NULL COMMENT 'created_at+30min 超时取消（BE-DIM-4）',
  `paid_at`          DATETIME(3)   NULL,
  `shipped_at`       DATETIME(3)   NULL,
  `completed_at`     DATETIME(3)   NULL,
  `created_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_idem` (`idempotency_key`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_order_customer_created` (`customer_id`,`created_at`),
  KEY `idx_order_status_expires` (`status`,`expires_at`),
  KEY `idx_order_status_created` (`status`,`created_at`),
  CONSTRAINT `ck_order_status`   CHECK (`status` IN ('pending','paid','shipped','completed','cancelled','refunding','refunded')),
  CONSTRAINT `ck_order_currency` CHECK (`currency` IN ('USD','EUR','CAD','AUD','GBP')),
  CONSTRAINT `ck_order_carrier`  CHECK (`carrier` IS NULL OR `carrier` IN ('FedEx International Priority','UPS Worldwide Express','DHL Express')),
  CONSTRAINT `ck_order_amounts`  CHECK (`subtotal`>=0 AND `shipping_fee`>=0 AND `gift_wrap_fee`>=0 AND `discount_amount`>=0 AND `total_amount`>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单主单';

-- 5. order_line 订单行（快照；IDX-TRD-013）
CREATE TABLE `order_line` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT,
  `order_id`         BIGINT        NOT NULL,
  `product_id`       BIGINT        NOT NULL,
  `sku_id`           BIGINT        NULL COMMENT '定制款 NULL',
  `product_name`     VARCHAR(128)  NOT NULL COMMENT '快照',
  `sku_code`         VARCHAR(64)   NULL,
  `color`            VARCHAR(32)   NULL,
  `size`             VARCHAR(16)   NULL,
  `qty`              INT           NOT NULL,
  `unit_price`       DECIMAL(12,2) NOT NULL COMMENT '订单币种单价快照',
  `img`              VARCHAR(512)  NULL COMMENT '快照图',
  `custom_size_data` JSON          NULL COMMENT '定制行判定依据（决策 24）',
  `created_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_line_order` (`order_id`),
  CONSTRAINT `ck_line_qty` CHECK (`qty` >= 1),
  CONSTRAINT `ck_line_price` CHECK (`unit_price` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单行快照';

-- 6. payment 支付单（Stripe；IDX-TRD-008/009）
CREATE TABLE `payment` (
  `id`                BIGINT        NOT NULL AUTO_INCREMENT,
  `order_id`          BIGINT        NOT NULL,
  `provider`          VARCHAR(16)   NOT NULL DEFAULT 'stripe',
  `payment_intent_id` VARCHAR(64)   NULL COMMENT '非敏感引用可落库；client_secret 永不落库',
  `amount`            DECIMAL(12,2) NOT NULL COMMENT '订单币种',
  `currency`          CHAR(3)       NOT NULL,
  `status`            VARCHAR(16)   NOT NULL DEFAULT 'created' COMMENT 'payment_lifecycle 五态',
  `card_summary`      VARCHAR(64)   NULL COMMENT '如 Stripe · Visa ···4242',
  `paid_at`           DATETIME(3)   NULL,
  `created_at`        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_intent` (`payment_intent_id`),
  KEY `idx_payment_order` (`order_id`),
  CONSTRAINT `ck_payment_provider` CHECK (`provider` IN ('stripe')),
  CONSTRAINT `ck_payment_status`   CHECK (`status` IN ('created','processing','succeeded','failed','refunded')),
  CONSTRAINT `ck_payment_currency` CHECK (`currency` IN ('USD','EUR','CAD','AUD','GBP'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付单';

-- 7. refund 退款工单（决策 24/31；IDX-TRD-010~012）
CREATE TABLE `refund` (
  `id`                 BIGINT        NOT NULL AUTO_INCREMENT,
  `refund_no`          VARCHAR(20)   NOT NULL COMMENT 'RFD-YYYYMMDD-NNNN',
  `order_id`           BIGINT        NOT NULL,
  `customer_id`        BIGINT        NOT NULL,
  `amount`             DECIMAL(12,2) NOT NULL COMMENT '<= orders.total_amount 含礼品包装费（决策 28）',
  `currency`           CHAR(3)       NOT NULL COMMENT '原币种原金额退款（决策 14）',
  `reason`             VARCHAR(255)  NULL COMMENT '申请原因',
  `reject_reason`      VARCHAR(255)  NULL COMMENT '拒绝原因（回执邮件与消费端）',
  `status`             VARCHAR(16)   NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected',
  `stripe_refund_id`   VARCHAR(64)   NULL COMMENT '审核通过后写入',
  `return_tracking_no` VARCHAR(64)   NULL COMMENT '退货物流单号登记（决策 31，无 RMA 节点）',
  `applied_at`         DATETIME(3)   NOT NULL,
  `created_at`         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_no` (`refund_no`),
  KEY `idx_refund_order_status` (`order_id`,`status`),
  KEY `idx_refund_status_applied` (`status`,`applied_at`),
  CONSTRAINT `ck_refund_status`   CHECK (`status` IN ('pending','approved','rejected')),
  CONSTRAINT `ck_refund_currency` CHECK (`currency` IN ('USD','EUR','CAD','AUD','GBP')),
  CONSTRAINT `ck_refund_amount`   CHECK (`amount` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='退款工单';

-- 8. wishlist_item 收藏（决策 18；IDX-TRD-018）
CREATE TABLE `wishlist_item` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `customer_id` BIGINT      NOT NULL,
  `product_id`  BIGINT      NOT NULL,
  `created_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wishlist_customer_product` (`customer_id`,`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收藏清单';

-- 9. browse_history 浏览历史（决策 23；IDX-TRD-019/020；每用户 50 条滚动）
CREATE TABLE `browse_history` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `customer_id` BIGINT      NOT NULL,
  `product_id`  BIGINT      NOT NULL,
  `viewed_at`   DATETIME(3) NOT NULL COMMENT 'upsert 更新；每用户保留最近 50 条',
  `created_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_browse_customer_product` (`customer_id`,`product_id`),
  KEY `idx_browse_customer_viewed` (`customer_id`,`viewed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Recently Viewed 浏览历史';

-- 10. exchange_rate 汇率（决策 14；种子五行，USD 恒 1；IDX-TRD-021）
CREATE TABLE `exchange_rate` (
  `id`         BIGINT        NOT NULL AUTO_INCREMENT,
  `currency`   CHAR(3)       NOT NULL,
  `rate`       DECIMAL(12,6) NOT NULL COMMENT '相对 USD；USD 恒 1',
  `updated_by` BIGINT        NULL COMMENT '逻辑外键→admin_user.id',
  `created_at` DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rate_currency` (`currency`),
  CONSTRAINT `ck_rate_currency` CHECK (`currency` IN ('USD','EUR','CAD','AUD','GBP')),
  CONSTRAINT `ck_rate_positive` CHECK (`rate` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='汇率表';

-- 11. checkout_config 结算配置单例（决策 24/28；id=1 单例，L3 实体以 BIGINT 主键落地、应用层固定 id=1）
CREATE TABLE `checkout_config` (
  `id`                        BIGINT        NOT NULL COMMENT '单例 =1',
  `gift_wrap_fee_usd`         DECIMAL(12,2) NOT NULL DEFAULT 15.00 COMMENT '礼品包装固定费 USD 基准（决策 28）',
  `custom_refund_grace_hours` INT           NOT NULL DEFAULT 24 COMMENT '定制款退款宽限期小时 1..168（决策 24）',
  `created_at`                DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`                DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  CONSTRAINT `ck_cfg_singleton` CHECK (`id` = 1),
  CONSTRAINT `ck_cfg_fee`       CHECK (`gift_wrap_fee_usd` >= 0),
  CONSTRAINT `ck_cfg_grace`     CHECK (`custom_refund_grace_hours` BETWEEN 1 AND 168)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结算配置单例';

-- 12. processed_event Stripe webhook 幂等存储（BE-DIM-4；保留 90 天，SCHED-TRD-002 每日清理）
CREATE TABLE `processed_event` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `event_id`    VARCHAR(64)  NOT NULL COMMENT 'Stripe Event id（evt_...）',
  `event_type`  VARCHAR(64)  NOT NULL COMMENT 'payment_intent.succeeded 等',
  `received_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_id` (`event_id`),
  KEY `idx_event_received` (`received_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='webhook 幂等消费记录；保留 90 天，SCHED-TRD-002 每日清理';

-- =============================================================================
-- 种子数据（决策 21；运行期由 TradingSeedInitializer 幂等灌入，本段为等价 SQL）
-- EUR/CAD/AUD/GBP 初值取原型前端硬编码汇率口径（决策 14 上线后管理端维护接管）；
-- gift_wrap_fee_usd=15.00 与原型 checkout「Add gift wrapping (+$15)」一致（决策 28）；grace=24h（决策 24 默认）。
-- =============================================================================
INSERT INTO `exchange_rate` (`currency`,`rate`) VALUES
  ('USD',1.000000),('EUR',0.920000),('CAD',1.360000),('AUD',1.520000),('GBP',0.790000);
INSERT INTO `checkout_config` (`id`,`gift_wrap_fee_usd`,`custom_refund_grace_hours`) VALUES (1,15.00,24);
