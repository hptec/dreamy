-- business-legacy.sql -- Java era business schema snapshot (from data/backup-identity-pre-drop-20260918-002337.sql,
-- generated 2026-10-01 by pd monitor migration audit). Schema only, no data. All IF NOT EXISTS, idempotent.
-- Excluded (owned by identity.sql / marketing-base.sql): address, admin_session, admin_user, auth_config, browse_history, contact_message, email_template, login_history, newsletter_subscriber, operation_log, otp_code, permission, role, role_permission, user, user_identity, user_session, wishlist_item

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `announcements` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `priority` int NOT NULL DEFAULT '0' COMMENT '优先级 DESC',
  `start_at` datetime DEFAULT NULL COMMENT '时间窗开始',
  `end_at` datetime DEFAULT NULL COMMENT '时间窗结束',
  `content` text COMMENT 'EN 基准内容',
  `content_i18n_json` json NOT NULL COMMENT '公告内容多语言 {en:{content},es:{},fr:{}}',
  `i18n_json` json DEFAULT NULL COMMENT '其他文案多语言',
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_announcements_priority_id` (`priority`,`id`),
  KEY `idx_announcements_enabled_time` (`enabled`,`start_at`,`end_at`),
  KEY `idx_announcements_priority_time` (`priority`,`start_at`,`end_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `attribute_def` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `key` varchar(64) NOT NULL COMMENT '属性键（小写下划线，不可改）',
  `label` varchar(64) NOT NULL COMMENT '显示名(EN 基准)',
  `type` tinyint NOT NULL COMMENT '类型：1=单选 2=多选 3=文本 4=开关',
  `options` json DEFAULT NULL COMMENT '可选值列表（仅 select/multiselect）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_attribute_def_key` (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `attribute_def_translation` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `attribute_def_id` bigint NOT NULL COMMENT '逻辑外键 attribute_def.id',
  `locale` varchar(8) NOT NULL COMMENT 'es|fr',
  `label` varchar(64) DEFAULT NULL COMMENT '显示名译文',
  `options` json DEFAULT NULL COMMENT '与主表 options 等长的译文数组',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_adt` (`attribute_def_id`,`locale`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `attribute_set` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `label` varchar(64) NOT NULL COMMENT '属性集名称',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `attribute_set_item` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `attribute_set_id` bigint NOT NULL COMMENT '逻辑外键 attribute_set.id',
  `attribute_id` bigint NOT NULL COMMENT '逻辑外键 attribute_def.id',
  `visibility` tinyint NOT NULL COMMENT '可见性：1=显示 2=可选 3=隐藏',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序顺序（数字越小越靠前）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_asi` (`attribute_set_id`,`attribute_id`),
  KEY `idx_asi_attribute` (`attribute_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `banner` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(128) NOT NULL COMMENT '内部名称',
  `image_url` varchar(512) NOT NULL COMMENT '预签名上传 public_url（scope=banner）',
  `position` tinyint NOT NULL COMMENT '位置：1=首屏大图 2=精选位 3=顶栏',
  `start_time` datetime(3) DEFAULT NULL COMMENT '投放开始（空=立即）',
  `end_time` datetime(3) DEFAULT NULL COMMENT '投放结束（空=长期）；读路径窗口过滤（DEC-MKT-2）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=草稿 2=已发布 3=已归档',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `title` varchar(255) DEFAULT NULL COMMENT '文案标题(EN 基准，DEC-MKT-1)',
  `subtitle` varchar(255) DEFAULT NULL COMMENT '文案副题(EN 基准)',
  `cta_text` varchar(64) DEFAULT NULL COMMENT 'CTA 文案(EN 基准)',
  `cta_link` varchar(512) DEFAULT NULL COMMENT 'CTA 链接(EN 基准)',
  `cta_text_secondary` varchar(64) DEFAULT NULL COMMENT '次要 CTA 文案(EN 基准)',
  `cta_link_secondary` varchar(512) DEFAULT NULL COMMENT '次要 CTA 链接(EN 基准)',
  PRIMARY KEY (`id`),
  KEY `idx_banner_status_position` (`status`,`position`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `banner_translation` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `banner_id` bigint NOT NULL COMMENT '逻辑外键 banner.id',
  `locale` varchar(8) NOT NULL COMMENT 'es|fr（EN 存主表）',
  `image_url` varchar(512) DEFAULT NULL COMMENT '语言专属 Banner 图',
  `title` varchar(255) DEFAULT NULL,
  `subtitle` varchar(255) DEFAULT NULL,
  `cta_text` varchar(64) DEFAULT NULL,
  `cta_text_secondary` varchar(255) DEFAULT NULL COMMENT '次要 CTA 文案（KD-14）',
  `cta_link_secondary` varchar(512) DEFAULT NULL COMMENT '次要 CTA 链接（KD-14）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bt` (`banner_id`,`locale`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `blog_post` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `title` varchar(200) NOT NULL COMMENT '标题(EN 基准)',
  `cover` varchar(512) DEFAULT NULL,
  `category` varchar(64) DEFAULT NULL COMMENT '文章栏目',
  `author` varchar(64) DEFAULT NULL,
  `content` mediumtext COMMENT '正文(EN 基准, Markdown 格式)',
  `excerpt` varchar(500) DEFAULT NULL COMMENT 'EN 摘要(主表提升)',
  `seo_title` varchar(128) DEFAULT NULL COMMENT 'EN SEO 标题',
  `seo_description` varchar(255) DEFAULT NULL COMMENT 'EN SEO 描述',
  `word_count` int NOT NULL DEFAULT '0' COMMENT '字数(CJK按字+Latin按词)',
  `reading_minutes` decimal(5,1) NOT NULL DEFAULT '0.0' COMMENT '阅读时长(cjk/300+latin/200)',
  `slug` varchar(128) DEFAULT NULL COMMENT '静态文章页路径 ^[a-z0-9-]+$；published 必填（CV-MKT-012）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=草稿 2=已发布 3=已归档',
  `published_at` datetime(3) DEFAULT NULL COMMENT '首次发布时间（republish 不刷新）',
  `views` int NOT NULL DEFAULT '0' COMMENT '阅读数近似计数（SCHED-MKT-02 flush，DEC-MKT-6）',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '乐观锁(仅 update 全量保存使用;patchStatus 走状态前置条件不增 version)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_blog_slug` (`slug`),
  KEY `idx_blog_status_published` (`status`,`published_at`),
  KEY `idx_blog_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `blog_post_translation` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `blog_post_id` bigint NOT NULL COMMENT '逻辑外键 blog_post.id',
  `locale` varchar(8) NOT NULL COMMENT 'es|fr',
  `title` varchar(200) DEFAULT NULL,
  `excerpt` varchar(500) DEFAULT NULL,
  `body` text,
  `seo_title` varchar(128) DEFAULT NULL,
  `seo_description` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bpt` (`blog_post_id`,`locale`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `cache_invalidation_step` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` bigint NOT NULL COMMENT '缓存失效任务 ID',
  `step_type` varchar(32) NOT NULL COMMENT 'LOCAL_CACHE/VERIFY',
  `target` varchar(100) NOT NULL COMMENT '缓存目标',
  `status` tinyint NOT NULL COMMENT '步骤状态',
  `attempt` int NOT NULL COMMENT '所属执行轮次',
  `started_at` datetime(3) NOT NULL COMMENT '开始时间 UTC',
  `completed_at` datetime(3) DEFAULT NULL COMMENT '结束时间 UTC',
  `result_detail` text COMMENT '代际号等执行结果',
  `error_message` text COMMENT '失败原因',
  PRIMARY KEY (`id`),
  KEY `idx_cache_step_task` (`task_id`,`id`),
  KEY `idx_cache_step_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `cache_invalidation_task` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `correlation_id` varchar(36) NOT NULL COMMENT '跨步骤关联 ID',
  `trigger_mode` varchar(20) NOT NULL COMMENT 'BUSINESS_WRITE/MANUAL/SCHEDULED/SYSTEM_EVENT',
  `trigger_point` varchar(100) NOT NULL COMMENT '业务触发点',
  `resource_type` varchar(40) NOT NULL COMMENT '资源类型',
  `resource_id` varchar(64) DEFAULT NULL COMMENT '资源 ID 或批次标识',
  `resource_label` varchar(255) DEFAULT NULL COMMENT '资源展示名',
  `targets` json NOT NULL COMMENT '缓存目标列表',
  `details` json DEFAULT NULL COMMENT '触发上下文与计划边界详情',
  `triggered_by` varchar(100) NOT NULL COMMENT '触发者',
  `triggered_at` datetime(3) NOT NULL COMMENT '任务创建时间 UTC',
  `scheduled_at` datetime(3) NOT NULL COMMENT '计划执行时间 UTC',
  `started_at` datetime(3) DEFAULT NULL COMMENT '首次开始时间 UTC',
  `completed_at` datetime(3) DEFAULT NULL COMMENT '最终完成时间 UTC',
  `next_retry_at` datetime(3) DEFAULT NULL COMMENT '下次重试时间 UTC',
  `status` tinyint NOT NULL COMMENT '任务状态',
  `attempt_count` int NOT NULL DEFAULT '0' COMMENT '已执行次数',
  `max_attempts` int NOT NULL DEFAULT '3' COMMENT '最大执行次数',
  `error_message` text COMMENT '最终或最近错误',
  PRIMARY KEY (`id`),
  KEY `idx_cache_task_status_due` (`status`,`next_retry_at`,`scheduled_at`),
  KEY `idx_cache_task_resource` (`resource_type`,`resource_id`),
  KEY `idx_cache_task_triggered_at` (`triggered_at`),
  KEY `idx_cache_task_correlation` (`correlation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `carrier` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(64) NOT NULL COMMENT '承运方名称（Order.carrier 快照取值源）',
  `code` varchar(32) DEFAULT NULL COMMENT '承运商编码（FEDEX/UPS/DHL/USPS；shipment.carrier_code 引用）',
  `tracking_url_template` varchar(255) DEFAULT NULL COMMENT '跟踪链接模板，{tracking_no} 占位',
  `zones` varchar(255) DEFAULT NULL COMMENT '覆盖区域描述（纯展示）',
  `lead_time` varchar(64) DEFAULT NULL COMMENT '时效描述（进入报价项 lead_time）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=启用 2=禁用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_carrier_code` (`code`),
  KEY `idx_carrier_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `cart_item` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `customer_id` bigint NOT NULL COMMENT '逻辑外键 user.id（BE-DIM-6 隔离）',
  `product_id` bigint NOT NULL COMMENT '逻辑外键 product.id',
  `sku_id` bigint DEFAULT NULL COMMENT '现货必填；定制款 NULL（决策6）',
  `qty` int NOT NULL COMMENT '数量 >=1',
  `custom_size_data` json DEFAULT NULL COMMENT '定制尺寸 {bust,waist,hips,hollow_to_floor,height?}',
  `custom_size_hash` char(64) DEFAULT NULL COMMENT '定制数据合并判定哈希（规范化 JSON SHA-256，应用计算）',
  PRIMARY KEY (`id`),
  KEY `idx_cart_customer` (`customer_id`),
  KEY `idx_cart_customer_sku` (`customer_id`,`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `cart_merge_record` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `customer_id` bigint NOT NULL,
  `anon_token` varchar(64) NOT NULL COMMENT '前端匿名购物车标识（合并幂等键）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merge_customer_token` (`customer_id`,`anon_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `category` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(64) NOT NULL COMMENT '品类名称(EN 基准)',
  `parent_id` bigint DEFAULT NULL COMMENT '父分类，NULL=根（CP-010 逻辑外键）',
  `level` tinyint NOT NULL DEFAULT '1' COMMENT '层级 1..3（应用层校验 V-CAT-045）',
  `attribute_set_id` bigint DEFAULT NULL COMMENT '绑定属性集（根必填，子可空=沿父链继承）',
  `attr_overrides` json DEFAULT NULL COMMENT '子分类属性可见性 delta {attrKey: visibility}',
  `sort` int NOT NULL DEFAULT '0' COMMENT '同层排序（拖拽落库）',
  PRIMARY KEY (`id`),
  KEY `idx_category_parent` (`parent_id`),
  KEY `idx_category_attrset` (`attribute_set_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `category_translation` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `category_id` bigint NOT NULL COMMENT '逻辑外键 category.id',
  `locale` varchar(8) NOT NULL COMMENT 'es|fr（EN 存主表）',
  `name` varchar(64) DEFAULT NULL COMMENT '品类名译文',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ct` (`category_id`,`locale`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `checkout_config` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `gift_wrap_fee_usd` decimal(12,2) NOT NULL DEFAULT '15.00' COMMENT '礼品包装固定费 USD 基准（决策28）',
  `custom_refund_grace_hours` int NOT NULL DEFAULT '24' COMMENT '定制款退款宽限期小时 1..168（决策24）',
  `auto_complete_days` int NOT NULL DEFAULT '7' COMMENT '签收后自动完成天数 1..60',
  `auto_deliver_days` int NOT NULL DEFAULT '30' COMMENT '发货后无签收自动视为送达天数 1..120',
  `pending_timeout_minutes` int NOT NULL DEFAULT '30' COMMENT '待支付超时分钟 5..1440',
  `exchange_rate_spread_scaled` int NOT NULL DEFAULT '0' COMMENT '汇率加价率 ×10000（100=1%）0..2000',
  `production_days_default` int NOT NULL DEFAULT '21' COMMENT '默认制作周期天数（商品未配置时）1..180',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `collection` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `collection_group_id` bigint NOT NULL COMMENT '逻辑外键 collection_group.id',
  `name` varchar(64) NOT NULL COMMENT '集合名(EN 基准)',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=启用 2=禁用',
  PRIMARY KEY (`id`),
  KEY `idx_collection_group` (`collection_group_id`),
  KEY `idx_collection_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `collection_group` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(64) NOT NULL COMMENT '分组名(EN 基准)',
  `description` varchar(255) DEFAULT NULL COMMENT '分组说明',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `collection_group_translation` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `collection_group_id` bigint NOT NULL COMMENT '逻辑外键 collection_group.id',
  `locale` varchar(8) NOT NULL COMMENT 'es|fr',
  `name` varchar(64) DEFAULT NULL COMMENT '分组名译文',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cgt` (`collection_group_id`,`locale`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `collection_translation` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `collection_id` bigint NOT NULL COMMENT '逻辑外键 collection.id',
  `locale` varchar(8) NOT NULL COMMENT 'es|fr',
  `label` varchar(64) DEFAULT NULL COMMENT '集合名译文',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ct` (`collection_id`,`locale`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `coupon` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(32) NOT NULL COMMENT '券码 ^[A-Z0-9]+$ 唯一（大写归一）',
  `name` varchar(64) NOT NULL COMMENT '券名(EN 基准)',
  `type` tinyint NOT NULL COMMENT '类型：1=折扣 2=固定金额 3=免运费',
  `value` varchar(32) NOT NULL COMMENT '展示串，按 type pattern 可解析（DEC-MKT-4）',
  `min_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '门槛金额 USD 基准',
  `total_limit` int NOT NULL DEFAULT '100000' COMMENT '限量；>9999 视为不限（DEC-MKT-5）',
  `used_count` int NOT NULL DEFAULT '0' COMMENT '核销计数，仅 RM-MKT-107/108 可写',
  `start_at` datetime(3) DEFAULT NULL,
  `end_at` datetime(3) DEFAULT NULL COMMENT 'js_guard end_at>start_at',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=草稿 2=已排期 3=生效中 4=即将过期 5=已过期（SCHED 翻转）',
  `description` varchar(255) DEFAULT NULL COMMENT '券说明(EN 基准，DEC-MKT-1)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_coupon_code` (`code`),
  KEY `idx_coupon_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `coupon_translation` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `coupon_id` bigint NOT NULL COMMENT '逻辑外键 coupon.id',
  `locale` varchar(8) NOT NULL COMMENT 'es|fr',
  `name` varchar(64) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cpt` (`coupon_id`,`locale`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `event_outbox` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `event_type` varchar(64) NOT NULL COMMENT '事件类型（= routing key 语义）',
  `routing_key` varchar(64) NOT NULL COMMENT 'topic routing key，如 order.paid',
  `payload` json NOT NULL COMMENT '事件载荷 JSON（snake_case）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1=PENDING 2=SENT 3=DEAD',
  `attempts` int NOT NULL DEFAULT '0' COMMENT '已尝试投递次数',
  `next_attempt_at` datetime(3) NOT NULL COMMENT '下次可投递时刻（relay 扫描列）',
  `last_error` varchar(255) DEFAULT NULL,
  `sent_at` datetime(3) DEFAULT NULL,
  `event_id` varchar(36) DEFAULT NULL COMMENT '稳定事件 id（重投复用，消费端幂等键；存量行 NULL 时投递自动生成）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outbox_event_id` (`event_id`),
  KEY `idx_outbox_status_next` (`status`,`next_attempt_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `exchange_rate` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `currency` char(3) NOT NULL,
  `rate` decimal(12,6) NOT NULL COMMENT '相对 USD；USD 恒 1',
  `updated_by` bigint DEFAULT NULL COMMENT '逻辑外键 admin_user.id',
  `source` tinyint NOT NULL DEFAULT '1' COMMENT '1=手工 2=供应商',
  `synced_at` datetime(3) DEFAULT NULL COMMENT '供应商最近同步时间',
  `manual_override` tinyint(1) NOT NULL DEFAULT '0' COMMENT '1=手工锁定，供应商刷新跳过',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rate_currency` (`currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `exchange_rate_history` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `base_currency` char(3) NOT NULL DEFAULT 'USD',
  `currency` char(3) NOT NULL,
  `rate` decimal(12,6) NOT NULL,
  `source` tinyint NOT NULL COMMENT '1=MANUAL 2=PROVIDER',
  `quote_date` date NOT NULL COMMENT '供应商报价日 / 手工=当日',
  `recorded_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rate_history` (`currency`,`source`,`quote_date`),
  KEY `idx_rate_history_currency_recorded` (`currency`,`recorded_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `external_gateway_config` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `gateway_type` tinyint NOT NULL COMMENT '网关类型：AI(1)/LOGISTICS(2)/PAYMENT(3)',
  `name` varchar(64) NOT NULL COMMENT '配置名称',
  `protocol` tinyint NOT NULL DEFAULT '1' COMMENT '协议：openai(1)',
  `base_url` varchar(255) NOT NULL COMMENT '网关地址',
  `api_key_encrypted` varchar(4096) NOT NULL COMMENT 'API Key密文(AES-256-GCM, IV+密文 base64)',
  `default_model` varchar(128) DEFAULT NULL COMMENT '全局默认模型',
  `model_list` json DEFAULT NULL COMMENT '可用模型列表缓存',
  `models_synced_at` datetime DEFAULT NULL COMMENT '上次模型同步时间',
  `model_refresh_strategy` tinyint DEFAULT '1' COMMENT '刷新策略：manual(1)/scheduled(2)',
  `model_refresh_interval_min` int DEFAULT NULL COMMENT '定时刷新间隔(分钟)',
  `consecutive_failures` int NOT NULL DEFAULT '0' COMMENT '模型同步连续失败次数(L2细化字段, 决策5降级计数)',
  `enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否启用',
  `extra_config` json DEFAULT NULL COMMENT '协议扩展配置',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_name` (`gateway_type`,`name`),
  KEY `idx_type_enabled` (`gateway_type`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `flash_sale` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(64) NOT NULL COMMENT '活动名(EN 基准)',
  `discount` varchar(32) NOT NULL COMMENT '如 最高 40% OFF',
  `start_at` datetime(3) NOT NULL,
  `end_at` datetime(3) NOT NULL COMMENT 'js_guard end_at>start_at；到期 SCHED 自动 ended（s-761）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=草稿 2=已排期 3=进行中 4=已结束',
  PRIMARY KEY (`id`),
  KEY `idx_flash_status_end` (`status`,`end_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `flash_sale_product` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `flash_sale_id` bigint NOT NULL,
  `product_id` bigint NOT NULL COMMENT '逻辑外键 catalog.product.id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fsp` (`flash_sale_id`,`product_id`),
  KEY `idx_fsp_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `flash_sale_translation` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `flash_sale_id` bigint NOT NULL COMMENT '逻辑外键 flash_sale.id',
  `locale` varchar(8) NOT NULL COMMENT 'es|fr',
  `name` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fst` (`flash_sale_id`,`locale`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `footer_columns` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `title` varchar(255) NOT NULL COMMENT 'EN 基准标题',
  `i18n_json` json DEFAULT NULL COMMENT '多语言 {en:{title},es:{},fr:{}}',
  `sort_order` int NOT NULL DEFAULT '0',
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_footer_columns_sort` (`sort_order`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `footer_links` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `column_id` bigint NOT NULL COMMENT '所属栏目 id',
  `label` varchar(255) NOT NULL COMMENT 'EN 基准标签',
  `url` varchar(512) NOT NULL COMMENT 'HTTP(S) URL',
  `target` varchar(16) NOT NULL DEFAULT 'self',
  `i18n_json` json DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_footer_links_column_sort` (`column_id`,`sort_order`,`id`),
  KEY `idx_footer_links_url` (`url`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `guide` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `phase` varchar(32) NOT NULL COMMENT '备婚阶段，如 Phase 1',
  `timeframe` varchar(64) DEFAULT NULL COMMENT '如 12+ months out',
  `title` varchar(128) NOT NULL COMMENT '指南标题(EN 基准)',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=草稿 2=已发布',
  `body` text COMMENT '指南正文(EN 基准，DEC-MKT-1)',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序（升序）',
  PRIMARY KEY (`id`),
  KEY `idx_guide_status_phase` (`status`,`phase`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `guide_task` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `guide_id` bigint NOT NULL,
  `label` varchar(256) NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_guide_task_guide_sort` (`guide_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `guide_task_translation` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` bigint NOT NULL,
  `locale` varchar(8) NOT NULL COMMENT 'es|fr',
  `label` varchar(256) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gtt` (`task_id`,`locale`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `guide_translation` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `guide_id` bigint NOT NULL COMMENT '逻辑外键 guide.id',
  `locale` varchar(8) NOT NULL COMMENT 'es|fr',
  `title` varchar(128) DEFAULT NULL,
  `body` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gt` (`guide_id`,`locale`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `home_sections` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `section_type` varchar(32) NOT NULL COMMENT '区块类型：hero/featured_banner/theme_cards/product_rail/editorial_feature/newsletter/custom',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '启用状态',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `data_json` json DEFAULT NULL COMMENT '按 section_type 区分的配置数据',
  `i18n_json` json DEFAULT NULL COMMENT '多语言文案 {en:{},es:{},fr:{}}',
  `label` varchar(255) DEFAULT NULL COMMENT 'EN 基准标题（冗余便于查询）',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_home_sections_sort_order` (`sort_order`,`id`),
  KEY `idx_home_sections_enabled_sort` (`enabled`,`sort_order`,`id`),
  KEY `idx_home_sections_type` (`section_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `lookbook` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `title` varchar(128) NOT NULL COMMENT '画册标题(EN 基准)',
  `theme` varchar(32) DEFAULT NULL COMMENT 'Vineyard/Beach/Forest',
  `cover` varchar(512) DEFAULT NULL COMMENT '独立封面；为空时回退关联商品主图',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=草稿 2=已发布',
  `description` varchar(500) DEFAULT NULL COMMENT '画册描述(EN 基准，DEC-MKT-1)',
  PRIMARY KEY (`id`),
  KEY `idx_lookbook_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `lookbook_product` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `lookbook_id` bigint NOT NULL,
  `product_id` bigint NOT NULL COMMENT '逻辑外键 catalog.product.id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lbp` (`lookbook_id`,`product_id`),
  KEY `idx_lbp_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `lookbook_translation` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `lookbook_id` bigint NOT NULL COMMENT '逻辑外键 lookbook.id',
  `locale` varchar(8) NOT NULL COMMENT 'es|fr',
  `title` varchar(128) DEFAULT NULL,
  `description` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lbt` (`lookbook_id`,`locale`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `mail_record` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `type` tinyint NOT NULL COMMENT '邮件类型：1=订单确认 2=订单发货 3=退款处理结果 4=样品间邀请 5=样品间指派 6=样品间提醒（决策 20.5 扩展枚举）',
  `recipient` varchar(254) NOT NULL COMMENT '收件邮箱（订单类经 CustomerEmailPort 解析；showroom 类取 payload.email）',
  `locale` varchar(8) NOT NULL DEFAULT 'en' COMMENT '渲染语言 en/es/fr（取事件 payload.locale，缺省 en）',
  `payload` json DEFAULT NULL COMMENT '事件载荷 JSON 快照（dead 人工补发渲染依据）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=待发送 2=已发送 3=发送失败 4=死信（MailStatus 状态机）',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '失败重试计数（超 dreamy.mq.max-retries=3 → dead）',
  `event_id` varchar(64) NOT NULL COMMENT '领域事件 event_id（UUID，消费幂等键，uk_mail_record_event）',
  `sent_at` datetime(3) DEFAULT NULL COMMENT '发送成功时间（status=sent 同写）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mail_record_event` (`event_id`),
  KEY `idx_mail_record_status_created` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `navigation_items` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `parent_id` bigint DEFAULT NULL COMMENT '父导航项 id，null=顶级',
  `label` varchar(255) NOT NULL COMMENT 'EN 基准标签',
  `label_i18n_key` varchar(128) DEFAULT NULL COMMENT 'i18n key（可选）',
  `url` varchar(512) DEFAULT NULL COMMENT '自定义 URL（link_type=custom 时非空）',
  `target` varchar(16) NOT NULL DEFAULT 'self' COMMENT 'target: self/blank',
  `link_type` tinyint NOT NULL DEFAULT '1' COMMENT 'LinkType: 1=custom 2=page 3=category 4=collection 5=product 6=blog_post 7=real_wedding 8=lookbook 9=guide',
  `ref_id` bigint DEFAULT NULL COMMENT '引用资源 id（link_type=category/collection/product/blog_post/real_wedding/lookbook/guide 时非空）',
  `page_key` varchar(64) DEFAULT NULL COMMENT '系统页 key（link_type=page 时非空，NavPageKey.key）',
  `mega_menu_json` json DEFAULT NULL COMMENT 'Mega Menu 列配置',
  `i18n_json` json DEFAULT NULL COMMENT '多语言 {en:{label},es:{},fr:{}}',
  `sort_order` int NOT NULL DEFAULT '0',
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_navigation_items_parent_sort` (`parent_id`,`sort_order`,`id`),
  KEY `idx_navigation_items_enabled` (`enabled`,`sort_order`),
  KEY `idx_navigation_items_ref` (`link_type`,`ref_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `order_event` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` bigint NOT NULL,
  `type` tinyint NOT NULL COMMENT '1=STATUS_CHANGED 2=NOTE 3=SHIPMENT 4=PAYMENT 5=REFUND 6=EMAIL 7=PRODUCTION',
  `actor_type` tinyint NOT NULL COMMENT '1=SYSTEM 2=CUSTOMER 3=ADMIN',
  `actor_id` bigint DEFAULT NULL COMMENT 'customer_id / admin_user.id；SYSTEM 为 NULL',
  `title` varchar(128) NOT NULL,
  `detail` varchar(512) DEFAULT NULL,
  `payload` json DEFAULT NULL COMMENT '结构化附加数据（from/to 状态、金额、单号等）',
  `customer_visible` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  KEY `idx_order_event_order_created` (`order_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `order_line` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `sku_id` bigint DEFAULT NULL COMMENT '定制款 NULL',
  `product_name` varchar(128) NOT NULL COMMENT '快照',
  `sku_code` varchar(64) DEFAULT NULL,
  `color` varchar(32) DEFAULT NULL,
  `size` varchar(16) DEFAULT NULL,
  `qty` int NOT NULL,
  `unit_price` decimal(12,2) NOT NULL COMMENT '订单币种单价快照',
  `img` varchar(512) DEFAULT NULL COMMENT '快照图',
  `custom_size_data` json DEFAULT NULL COMMENT '定制行判定依据（决策24）',
  PRIMARY KEY (`id`),
  KEY `idx_line_order` (`order_id`),
  KEY `idx_line_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `orders` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(20) NOT NULL COMMENT 'DRM-YYYYMMDD-NNNN（预生成）',
  `customer_id` bigint NOT NULL,
  `status` tinyint NOT NULL DEFAULT '1' COMMENT 'order_lifecycle：1=待支付 2=已支付 3=已发货 4=已完成 5=已取消 6=退款中 7=已退款 8=已送达',
  `currency` char(3) NOT NULL COMMENT 'USD/EUR/CAD/AUD/GBP（决策14）',
  `locale_snapshot` varchar(8) DEFAULT NULL COMMENT '下单时语言环境快照(决策13/FUNC-020,邮件三语用)',
  `exchange_rate` decimal(12,6) NOT NULL COMMENT '下单锁定 USD→订单币种汇率（决策14）',
  `wedding_date` date DEFAULT NULL COMMENT '婚期（交期复核，决策20.6）',
  `subtotal` decimal(12,2) NOT NULL COMMENT '订单币种行小计求和',
  `shipping_fee` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '所选承运商报价快照（F-036）',
  `gift_wrap` tinyint(1) NOT NULL DEFAULT '0' COMMENT '礼品包装（决策28）',
  `gift_wrap_fee` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '礼品包装费快照（决策28）',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '券减免（订单币种）',
  `total_amount` decimal(12,2) NOT NULL COMMENT '= subtotal+shipping_fee+gift_wrap_fee+tax_amount-discount_amount',
  `coupon_id` bigint DEFAULT NULL COMMENT '逻辑外键 coupon.id（marketing）',
  `payment_method` varchar(32) DEFAULT NULL COMMENT 'Stripe/Apple Pay/Google Pay/Klarna/Afterpay（决策25）',
  `address_snapshot` json NOT NULL COMMENT '下单地址快照（删地址不波及）',
  `carrier` varchar(64) DEFAULT NULL COMMENT '承运商快照枚举三值（F-036）',
  `tracking_no` varchar(64) DEFAULT NULL COMMENT '物流单号（手填，BE-DIM-5）',
  `idempotency_key` varchar(64) NOT NULL COMMENT '客户端 UUID 防重（BE-DIM-4）',
  `expires_at` datetime(3) NOT NULL COMMENT 'created_at+30min 超时取消（BE-DIM-4）',
  `paid_at` datetime(3) DEFAULT NULL,
  `shipped_at` datetime(3) DEFAULT NULL,
  `completed_at` datetime(3) DEFAULT NULL,
  `delivered_at` datetime(3) DEFAULT NULL COMMENT '全部包裹签收/用户确认/自动签收时间',
  `tax_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '订单币种税费（DDP 计入 total；DDU 为 0）',
  `tax_breakdown` json DEFAULT NULL COMMENT '税费明细快照',
  `incoterm` tinyint DEFAULT NULL COMMENT '1=DDP 2=DDU（目的国政策快照）',
  `refunded_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '已退款累计（订单币种）；>= total_amount 才进入 REFUNDED',
  `production_stage` tinyint DEFAULT NULL COMMENT '制作阶段（仅 PAID 态非空）：1=待审核 2=制作中 3=质检中 4=待发货',
  `shipping_service_level` tinyint DEFAULT NULL COMMENT '1=STANDARD 2=EXPRESS',
  `estimated_delivery_from` date DEFAULT NULL COMMENT '下单时预计送达区间起',
  `estimated_delivery_to` date DEFAULT NULL COMMENT '下单时预计送达区间止',
  `amount_version` tinyint NOT NULL DEFAULT '1' COMMENT '金额算法版本 1=存量无税 2=含税',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_idem` (`idempotency_key`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_order_customer_created` (`customer_id`,`created_at`),
  KEY `idx_order_status_expires` (`status`,`expires_at`),
  KEY `idx_order_status_created` (`status`,`created_at`),
  KEY `idx_order_status_paid` (`status`,`paid_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `payment` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` bigint NOT NULL,
  `provider` varchar(16) NOT NULL DEFAULT 'stripe',
  `payment_intent_id` varchar(64) DEFAULT NULL COMMENT '非敏感引用；client_secret 永不落库',
  `amount` decimal(12,2) NOT NULL COMMENT '订单币种',
  `currency` char(3) NOT NULL,
  `status` tinyint NOT NULL DEFAULT '1' COMMENT 'payment_lifecycle：1=已创建 2=处理中 3=支付成功 4=支付失败 5=已退款 6=部分退款',
  `card_summary` varchar(64) DEFAULT NULL COMMENT '如 Stripe · Visa ···4242',
  `paid_at` datetime(3) DEFAULT NULL,
  `refunded_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '已退款累计（每次批准仅退 delta）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_intent` (`payment_intent_id`),
  KEY `idx_payment_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `processed_event` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `event_id` varchar(64) NOT NULL COMMENT 'Stripe Event id（evt_...）',
  `event_type` varchar(64) NOT NULL COMMENT 'payment_intent.succeeded 等',
  `received_at` datetime(3) NOT NULL COMMENT '接收时刻（90 天清理扫描列）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_id` (`event_id`),
  KEY `idx_event_received` (`received_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `product` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(128) NOT NULL COMMENT '商品名(EN 基准)',
  `slug` varchar(128) NOT NULL COMMENT 'URL slug ^[a-z0-9-]+$',
  `category_id` bigint NOT NULL COMMENT '逻辑外键 category.id（CV-CAT-005）',
  `description` text COMMENT '富文本介绍',
  `designer_note` text COMMENT '品牌故事',
  `selling_points` json DEFAULT NULL COMMENT '商品卖点（EN 基准，数组，最多5个）',
  `price` decimal(12,2) NOT NULL COMMENT '现价 USD 基准',
  `compare_at` decimal(12,2) DEFAULT NULL COMMENT '划线价 >= price（应用层校验 V-CAT-027）',
  `installment` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Klarna/Afterpay 分期',
  `multi_currency_prices` json DEFAULT NULL COMMENT '每币种覆盖价 {CAD: 99.0,...}',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=草稿 2=已上架',
  `is_new` tinyint(1) NOT NULL DEFAULT '0',
  `is_best` tinyint(1) NOT NULL DEFAULT '0',
  `recommend` tinyint(1) NOT NULL DEFAULT '0' COMMENT '手动推荐标记（best_sellers 冷启动回退）',
  `sort` int NOT NULL DEFAULT '0',
  `lead_time_days` int NOT NULL DEFAULT '1' COMMENT '标准发货周期(天) >=1',
  `rush_available` tinyint(1) NOT NULL DEFAULT '0',
  `custom_size_available` tinyint(1) NOT NULL DEFAULT '0' COMMENT '定制尺寸开关（A-007）',
  `style_no` varchar(32) DEFAULT NULL COMMENT '款式编号',
  `seo_title` varchar(128) DEFAULT NULL,
  `seo_desc` varchar(255) DEFAULT NULL,
  `sales_30d` int NOT NULL DEFAULT '0' COMMENT '近30天已支付销量（EVT-CAT-001/003 回写，决策29）',
  `sales_refreshed_at` datetime(3) DEFAULT NULL COMMENT '销量窗口刷新时间',
  `rating_avg` decimal(3,2) NOT NULL DEFAULT '0.00' COMMENT '已通过评价均分（EVT-CAT-002 回写）',
  `rating_count` int NOT NULL DEFAULT '0' COMMENT '已通过评价数（EVT-CAT-002 回写）',
  `fabric_care_note` text COMMENT '面料护理说明（自由文本补充）',
  `fabric_compositions` json DEFAULT NULL COMMENT '面料成分 [{layer,material,percentage}]',
  `care` json DEFAULT NULL COMMENT '护理标签 [{symbol,label}]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_slug` (`slug`),
  KEY `idx_product_status_category` (`status`,`category_id`),
  KEY `idx_product_status_created` (`status`,`created_at`),
  KEY `idx_product_sales` (`status`,`sales_30d`),
  FULLTEXT KEY `ft_product_search` (`name`) /*!50100 WITH PARSER `ngram` */ 
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `product_attribute_value` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_id` bigint NOT NULL COMMENT '逻辑外键 product.id',
  `attribute_id` bigint NOT NULL COMMENT '逻辑外键 attribute_def.id',
  `value` varchar(255) NOT NULL COMMENT '属性值（EN 规范值）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pav` (`product_id`,`attribute_id`,`value`),
  KEY `idx_pav_filter` (`attribute_id`,`value`,`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `product_collection` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_id` bigint NOT NULL COMMENT '逻辑外键 product.id',
  `collection_id` bigint NOT NULL COMMENT '逻辑外键 collection.id',
  `sort` int NOT NULL DEFAULT '0' COMMENT '集合内商品显示顺序（小在前）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pcol` (`product_id`,`collection_id`),
  KEY `idx_pcol_collection` (`collection_id`),
  KEY `idx_pcol_collection_sort` (`collection_id`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `product_image` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_id` bigint NOT NULL COMMENT '逻辑外键 product.id',
  `url` varchar(512) NOT NULL COMMENT '预签名上传 public_url',
  `kind` tinyint NOT NULL COMMENT '类型：1=商品图 2=场景图 3=视频 4=色板',
  `color_name` varchar(32) DEFAULT NULL COMMENT 'kind=swatch 时颜色名',
  `sort` int NOT NULL DEFAULT '0' COMMENT 'gallery sort=0 为主图',
  PRIMARY KEY (`id`),
  KEY `idx_image_product_sort` (`product_id`,`sort`),
  KEY `idx_image_product_kind_sort` (`product_id`,`kind`,`sort`,`url`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `product_question` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_id` bigint NOT NULL COMMENT '逻辑外键 product.id',
  `user_id` bigint NOT NULL COMMENT '逻辑外键 user.id（设计派生列：BE-DIM-6 提交者强关联，不出契约 DTO）',
  `asker` varchar(64) DEFAULT NULL COMMENT '提问者姓名快照（store 输出脱敏，CV-REV-010 不可变）',
  `question` varchar(1000) NOT NULL COMMENT '提问内容 trim 1..1000',
  `asked_at` datetime(3) NOT NULL COMMENT '提问时间（业务时间，列表排序键）',
  `answer` varchar(2000) DEFAULT NULL COMMENT '官方回答；NULL=unanswered（question_answer_flow）',
  `answer_time` datetime(3) DEFAULT NULL COMMENT '回答时间（服务端生成）',
  `visible` tinyint NOT NULL DEFAULT '2' COMMENT '可见性：1=显示 2=隐藏；首次回答自动置显示（E-REV-14）',
  PRIMARY KEY (`id`),
  KEY `idx_pq_product_visible` (`product_id`,`visible`,`asked_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `product_translation` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_id` bigint NOT NULL COMMENT '逻辑外键 product.id',
  `locale` varchar(8) NOT NULL COMMENT 'es|fr',
  `name` varchar(128) DEFAULT NULL,
  `description` text,
  `designer_note` text COMMENT '设计师备注(三语独立,决策12/FUNC-017)',
  `selling_points` json DEFAULT NULL COMMENT '翻译卖点（数组，与主表对应）',
  `seo_title` varchar(128) DEFAULT NULL,
  `seo_description` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pt` (`product_id`,`locale`),
  FULLTEXT KEY `ft_pt_search` (`name`) /*!50100 WITH PARSER `ngram` */ 
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `real_wedding` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `couple` varchar(64) NOT NULL COMMENT '如 Emma & James',
  `location` varchar(128) DEFAULT NULL,
  `theme` varchar(32) DEFAULT NULL,
  `wedding_date` varchar(16) DEFAULT NULL COMMENT '如 2025-06',
  `cover` varchar(512) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=草稿 2=已发布',
  `title` varchar(200) DEFAULT NULL COMMENT '案例标题(EN 基准，DEC-MKT-1)',
  `story` text COMMENT '婚礼故事(EN 基准)',
  PRIMARY KEY (`id`),
  KEY `idx_wedding_status` (`status`,`wedding_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `real_wedding_product` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `real_wedding_id` bigint NOT NULL,
  `product_id` bigint NOT NULL COMMENT '逻辑外键 catalog.product.id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rwp` (`real_wedding_id`,`product_id`),
  KEY `idx_rwp_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `real_wedding_translation` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `real_wedding_id` bigint NOT NULL COMMENT '逻辑外键 real_wedding.id',
  `locale` varchar(8) NOT NULL COMMENT 'es|fr',
  `title` varchar(200) DEFAULT NULL,
  `story` text,
  `theme` varchar(32) DEFAULT NULL COMMENT '主题翻译（EN 主表 real_wedding.theme 的译文）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rwt` (`real_wedding_id`,`locale`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `refund` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `refund_no` varchar(20) NOT NULL COMMENT 'RFD-YYYYMMDD-NNNN',
  `order_id` bigint NOT NULL,
  `customer_id` bigint NOT NULL,
  `amount` decimal(12,2) NOT NULL COMMENT '<= orders.total_amount 含礼品包装费（决策28）',
  `currency` char(3) NOT NULL COMMENT '原币种原金额退款（决策14）',
  `reason` varchar(255) DEFAULT NULL COMMENT '申请原因',
  `reject_reason` varchar(255) DEFAULT NULL COMMENT '拒绝原因（回执邮件与消费端）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=待处理 2=已同意 3=已拒绝',
  `stripe_refund_id` varchar(64) DEFAULT NULL COMMENT '审核通过后写入',
  `return_tracking_no` varchar(64) DEFAULT NULL COMMENT '退货物流单号登记（决策31，无 RMA 节点）',
  `applied_at` datetime(3) NOT NULL,
  `from_status` tinyint DEFAULT NULL COMMENT '申请时订单状态快照（还原目标）',
  `from_stage` tinyint DEFAULT NULL COMMENT '申请时制作阶段快照',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_no` (`refund_no`),
  KEY `idx_refund_order_status` (`order_id`,`status`),
  KEY `idx_refund_status_applied` (`status`,`applied_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `review` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_id` bigint NOT NULL COMMENT '逻辑外键 product.id（写前经 ReviewCatalogSnapshotPort 校验，CV-REV-005）',
  `user_id` bigint NOT NULL COMMENT '逻辑外键 user.id（JWT subject，BE-DIM-6 强隔离）',
  `customer_name` varchar(64) DEFAULT NULL COMMENT '提交时用户姓名快照（store 输出脱敏 MAP-REV-001，CV-REV-010 不可变）',
  `rating` tinyint NOT NULL COMMENT '评分 1..5（应用层校验 CV-REV-001）',
  `content` text COMMENT '评价内容 <=5000，trim 后空存 NULL；不做多语翻译',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=待审核 2=已通过 3=已拒绝（review_moderation）',
  `featured` tinyint(1) NOT NULL DEFAULT '0' COMMENT '精选；不变量 status!=approved => 0（CV-REV-007）',
  `submitted_at` datetime(3) NOT NULL COMMENT '提交时间（业务时间，列表排序键）',
  `reply_author` varchar(64) DEFAULT NULL COMMENT '官方回复署名（缺省 "Dreamy Team"，配置化 dreamy.review.reply-author）',
  `reply_content` varchar(2000) DEFAULT NULL COMMENT '官方回复内容，trim 非空（CV-REV-006）',
  `reply_time` datetime(3) DEFAULT NULL COMMENT '官方回复时间（服务端生成）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_user_product` (`user_id`,`product_id`),
  KEY `idx_review_product_status` (`product_id`,`status`,`featured`,`submitted_at`),
  KEY `idx_review_status_submitted` (`status`,`submitted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `review_image` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `review_id` bigint NOT NULL COMMENT '逻辑外键 review.id（仅经 TX-REV-001 聚合根写入）',
  `url` varchar(512) NOT NULL COMMENT '预签名上传 public_url（review/ 前缀，CV-REV-008）',
  `rejected` tinyint(1) NOT NULL DEFAULT '0' COMMENT '驳回标记；1=前台不展示（review_image_visibility shown/rejected）',
  PRIMARY KEY (`id`),
  KEY `idx_ri_review` (`review_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `shipment` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` bigint NOT NULL,
  `shipment_no` varchar(24) NOT NULL COMMENT 'SHP-yyyyMMdd-NNNN',
  `carrier_code` varchar(32) NOT NULL COMMENT 'carrier.code',
  `carrier_name` varchar(64) DEFAULT NULL COMMENT 'carrier.name 快照',
  `tracking_no` varchar(64) NOT NULL,
  `tracking_url` varchar(255) DEFAULT NULL COMMENT '按 carrier.tracking_url_template 生成',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1=PENDING 2=IN_TRANSIT 3=OUT_FOR_DELIVERY 4=DELIVERED 5=EXCEPTION 6=CANCELLED',
  `shipped_at` datetime(3) DEFAULT NULL,
  `delivered_at` datetime(3) DEFAULT NULL,
  `last_event_at` datetime(3) DEFAULT NULL,
  `last_event_desc` varchar(255) DEFAULT NULL,
  `provider_ref` varchar(64) DEFAULT NULL COMMENT '轨迹供应商登记引用',
  `idempotency_key` varchar(64) DEFAULT NULL COMMENT '创建请求 Idempotency-Key',
  `synced_at` datetime(3) DEFAULT NULL COMMENT '供应商最近同步时间',
  `sync_failures` int NOT NULL DEFAULT '0' COMMENT '供应商连续失败次数（≥3 告警）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shipment_no` (`shipment_no`),
  UNIQUE KEY `uk_shipment_tracking` (`order_id`,`carrier_code`,`tracking_no`),
  UNIQUE KEY `uk_shipment_idem` (`idempotency_key`),
  KEY `idx_shipment_order` (`order_id`),
  KEY `idx_shipment_status_event` (`status`,`last_event_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `shipment_event` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shipment_id` bigint NOT NULL,
  `occurred_at` datetime(3) NOT NULL,
  `status` tinyint NOT NULL COMMENT 'ShipmentStatus',
  `location` varchar(128) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `source` tinyint NOT NULL DEFAULT '1' COMMENT '1=MANUAL 2=PROVIDER 3=SYSTEM',
  `provider_event_id` varchar(64) DEFAULT NULL COMMENT '供应商事件 id（MANUAL 为 NULL）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shipment_event_provider` (`shipment_id`,`provider_event_id`),
  KEY `idx_shipment_event_occurred` (`shipment_id`,`occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `shipment_line` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shipment_id` bigint NOT NULL,
  `order_line_id` bigint NOT NULL,
  `qty` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shipment_line` (`shipment_id`,`order_line_id`),
  KEY `idx_shipment_line_order_line` (`order_line_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `shipping_option` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `zone` varchar(32) NOT NULL COMMENT 'NORTH_AMERICA/EUROPE/UK/OCEANIA/ASIA/LATAM/MEA/REST',
  `carrier_code` varchar(32) NOT NULL COMMENT 'carrier.code；ANY=任意启用承运商',
  `service_level` tinyint NOT NULL DEFAULT '1' COMMENT '1=STANDARD 2=EXPRESS',
  `fee_under` decimal(12,2) DEFAULT NULL COMMENT '基础邮费 USD（subtotal<threshold；NULL 计 0）',
  `fee_over` decimal(12,2) DEFAULT NULL COMMENT '满额邮费 USD（subtotal>=threshold；NULL 计 0）',
  `threshold` decimal(12,2) DEFAULT NULL COMMENT '满额门槛 USD（NULL=恒收 fee_under）',
  `transit_days_min` int NOT NULL DEFAULT '5' COMMENT '运输天数下限',
  `transit_days_max` int NOT NULL DEFAULT '10' COMMENT '运输天数上限',
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shipping_option` (`zone`,`carrier_code`,`service_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `shipping_rate` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `zone` varchar(128) NOT NULL COMMENT '规则行标识（唯一）：<地理区域> 或 <地理区域> / <承运商名>（F-036）',
  `fee_under` decimal(10,2) DEFAULT NULL COMMENT '基础邮费 USD（subtotal<threshold；NULL 计费按 0）',
  `fee_over` decimal(10,2) DEFAULT NULL COMMENT '满额邮费 USD（subtotal>=threshold；0 即包邮；NULL 计费按 0）',
  `threshold` decimal(10,2) DEFAULT NULL COMMENT '满额门槛 USD（NULL=无满额档恒收 fee_under，DEC-SHP-3）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shipping_rate_zone` (`zone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `showroom` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `owner_id` bigint NOT NULL COMMENT '逻辑外键 user.id（创建者新娘，JWT subject，BE-DIM-6 强隔离）',
  `name` varchar(64) NOT NULL COMMENT '名称 trim 1..64（CV-SHR-001）',
  `wedding_date` date DEFAULT NULL COMMENT '婚期（F-077 结算自动带入，可空）',
  `invite_token` varchar(64) NOT NULL COMMENT '不可猜 UUID 邀请 token（决策 20.2）',
  `invite_token_prev` varchar(64) DEFAULT NULL COMMENT '设计派生列：上一代 token（重置识别 410101，单代保留）',
  `invite_version` int NOT NULL DEFAULT '1' COMMENT '设计派生列：邀请版本号（guest JWT inv_ver 等值校验，重置自增，CV-SHR-008）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_showroom_invite` (`invite_token`),
  KEY `idx_showroom_owner` (`owner_id`,`created_at`),
  KEY `idx_showroom_invite_prev` (`invite_token_prev`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `showroom_comment` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `showroom_item_id` bigint NOT NULL COMMENT '逻辑外键 showroom_item.id',
  `member_id` bigint NOT NULL COMMENT '逻辑外键 showroom_member.id（nickname 联表派生展示）',
  `content` varchar(500) NOT NULL COMMENT '留言 trim 1..500',
  PRIMARY KEY (`id`),
  KEY `idx_sc_item` (`showroom_item_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `showroom_item` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `showroom_id` bigint NOT NULL COMMENT '逻辑外键 showroom.id',
  `product_id` bigint NOT NULL COMMENT '逻辑外键 product.id（写前经 ShowroomCatalogSnapshotPort 校验）',
  `color` varchar(64) NOT NULL DEFAULT '' COMMENT '加入时选择的颜色；未选归一化空串（uk 三元唯一前提，CV-SHR-003）',
  `last_ordered_at` datetime(3) DEFAULT NULL COMMENT '设计派生列：同房该款式最近已付订单时间（order.paid 消费回写，dye lot 24h 窗口源，决策 20.4）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_si_room_product_color` (`showroom_id`,`product_id`,`color`),
  KEY `idx_si_product_ordered` (`product_id`,`last_ordered_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `showroom_member` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `showroom_id` bigint NOT NULL COMMENT '逻辑外键 showroom.id',
  `nickname` varchar(32) NOT NULL COMMENT '昵称 trim 1..32，同房唯一（去重身份 409101）',
  `email` varchar(254) DEFAULT NULL COMMENT '提醒邮件收件地址（决策 20.5 新娘指派时填写；仅 owner 视图输出）',
  `assigned_item_id` bigint DEFAULT NULL COMMENT '逻辑外键 showroom_item.id（被指派款式；item 删除时清理 RM-SHR-039/040）',
  `assign_status` tinyint NOT NULL DEFAULT '1' COMMENT '指派状态：1=未指派 2=已指派 3=已提醒 4=已下单（showroom_member_assignment 状态机，仅 CAS 推进）',
  `linked_customer_id` bigint DEFAULT NULL COMMENT '逻辑外键 user.id（访客登录后绑定回填，决策 20.2；仅 owner 视图输出）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sm_room_nickname` (`showroom_id`,`nickname`),
  KEY `idx_sm_linked` (`linked_customer_id`),
  KEY `idx_sm_assigned_item` (`assigned_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `showroom_vote` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `showroom_item_id` bigint NOT NULL COMMENT '逻辑外键 showroom_item.id',
  `member_id` bigint NOT NULL COMMENT '逻辑外键 showroom_member.id（鉴权主体解析，不接收请求体）',
  `vote` tinyint NOT NULL COMMENT '投票：1=赞 2=踩（重复投票覆盖原票）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sv_member_item` (`member_id`,`showroom_item_id`),
  KEY `idx_sv_item` (`showroom_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `size_chart_row` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_id` bigint NOT NULL COMMENT '逻辑外键 product.id',
  `us` varchar(8) NOT NULL,
  `uk` varchar(8) DEFAULT NULL,
  `au` varchar(8) DEFAULT NULL,
  `bust` decimal(6,2) DEFAULT NULL COMMENT '胸围(in)',
  `waist` decimal(6,2) DEFAULT NULL COMMENT '腰围(in)',
  `hips` decimal(6,2) DEFAULT NULL COMMENT '臀围(in)',
  `hollow_to_floor` decimal(6,2) DEFAULT NULL COMMENT '中空到地(in)',
  PRIMARY KEY (`id`),
  KEY `idx_scr_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `sku` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_id` bigint NOT NULL COMMENT '逻辑外键 product.id',
  `sku_code` varchar(64) NOT NULL COMMENT '^[A-Z0-9-]+$ 全局唯一',
  `color` varchar(32) NOT NULL,
  `size` varchar(16) NOT NULL,
  `stock` int NOT NULL DEFAULT '0' COMMENT '现货库存；定制款不扣减（决策6）',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '乐观锁（扣减防超卖 BE-DIM-4 / 编辑防丢失 409508）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_code` (`sku_code`),
  KEY `idx_sku_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `tax_destination_policy` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `country_code` char(2) NOT NULL,
  `incoterm` tinyint NOT NULL DEFAULT '2' COMMENT '1=DDP 2=DDU',
  `duties_notice` tinyint(1) NOT NULL DEFAULT '1' COMMENT '结算页是否显示关税提示',
  `notice_text` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tax_policy_country` (`country_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `tax_rule` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `country_code` char(2) NOT NULL COMMENT 'ISO-3166-1 alpha-2',
  `region` varchar(8) NOT NULL DEFAULT '' COMMENT '州/省码；空串=国家级',
  `tax_type` tinyint NOT NULL COMMENT '1=VAT 2=GST 3=SALES_TAX 4=DUTY',
  `rate_scaled` int NOT NULL COMMENT '税率×10000（10000=100%）',
  `applies_to_shipping` tinyint(1) NOT NULL DEFAULT '0' COMMENT '运费是否计税',
  `threshold_usd` decimal(12,2) DEFAULT NULL COMMENT '起征额 USD（base < threshold 不计税）',
  `effective_from` date DEFAULT NULL,
  `effective_to` date DEFAULT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `label` varchar(64) DEFAULT NULL COMMENT '展示标签，如 VAT 20%',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tax_rule_key` (`country_code`,`region`,`tax_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `user_guide_task` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL,
  `guide_id` bigint NOT NULL,
  `task_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
