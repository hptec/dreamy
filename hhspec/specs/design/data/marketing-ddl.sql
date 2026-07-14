-- =============================================================================
-- marketing 限界上下文 物理库 DDL（MySQL 8.0+）
-- change: portal-api-integration  domain: marketing
-- 引擎: InnoDB  字符集: utf8mb4  排序: utf8mb4_0900_ai_ci
-- 约定:
--   - 主键: BIGINT AUTO_INCREMENT（决策 12，huihao LongAuditableEntity）
--   - 营销状态枚举: ContentStatus/CouponStatus/FlashSaleStatus/PublishStatus 均使用 TINYINT IntEnum；
--     query/request/response 传数字 key，列表省略 status 表示全部（MAP-MKT-013）
--   - 时间: DATETIME(3) 存 UTC（CP-014 / MAP-MKT-014）
--   - 外键: 全表无物理 FOREIGN KEY（CP-010），引用完整性由应用层 + 事务维护（CV-MKT-005/006）
--   - 逻辑删除: 不启用；deleted 终态=物理删除 + 状态机 guard（coupon 仅 draft/expired 且
--     used_count=0 可删 409703；flash 仅 draft 可删 409703；banner/blog/wedding/lookbook/guide 全态可删）
--   - EN 文案列: banner.title/subtitle/cta_text、blog_post.content、real_wedding.title/story、
--     lookbook.description、guide.body、coupon.description 存 EN 基准（DEC-MKT-1，决策 13）；
--     translation 附表仅存 es/fr（uk(entity_id, locale)，CV-MKT-007）
--   - 写权限约束: coupon.used_count 仅 RM-MKT-107/108 核销 CAS/回滚可写（=trading RM-TRD-112/113
--     权威定义点）；blog_post.views 仅 SCHED-MKT-02 flush 可写；banner.clicks 本期无写入端点（保留列）
-- 运行期建表: huihao-mysql DDL-auto（@EnableMysql scanPackages 含 com.dreamy.marketing.domain）
--             以实体注解为准自动建表/加列；本文件为权威等价 SQL（审计/还原用）。
-- 种子数据: MarketingSeedInitializer（决策 21，从 portal-admin mock.js coupons/flashSales/banners/
--           blogPosts/lookbooks/guides/realWeddings + portal-store data/content.ts 提炼，含三语
--           translation + RBAC 权限点 /promotions、/banners）；仅显式设置 DEMO_SEED_ENABLED=true
--           时运行，缺省关闭且正式启动不创建/覆盖业务数据。newsletter_subscriber/contact_message
--           始终纯收集表空表起步（决策 26/30）。
-- 备注: processed_event 幂等表归 trading 域 DDL；q.invalidate 消费者经 infra Redis SETNX 幂等闸
--       （EventIdempotencyGuard），不建本域表。
-- 来源: marketing-data-detail.md §11（19 表）/ er-diagram.yml（9 实体 + 7 translation 附表 + 3 nm）
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- 1. banner 广告位（含 EN 文案列，DEC-MKT-1；IDX-MKT-007）
-- -----------------------------------------------------------------------------
CREATE TABLE banner (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  name       VARCHAR(128) NOT NULL COMMENT '内部名称',
  image_url  VARCHAR(512) NOT NULL COMMENT '预签名上传 public_url（scope=banner）',
  position   TINYINT      NOT NULL COMMENT '1=HERO 2=FEATURED 3=TOPBAR',
  start_time DATETIME(3)  NULL COMMENT '投放开始（空=立即）',
  end_time   DATETIME(3)  NULL COMMENT '投放结束（空=长期）；读路径窗口过滤（DEC-MKT-2）',
  status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1=DRAFT 2=PUBLISHED 3=ARCHIVED',
  sort       INT          NOT NULL DEFAULT 0 COMMENT '排序',
  clicks     INT          NOT NULL DEFAULT 0 COMMENT '点击统计只读（本期无写入端点）',
  title      VARCHAR(255) NULL COMMENT '文案标题(EN 基准)',
  subtitle   VARCHAR(255) NULL COMMENT '文案副题(EN 基准)',
  cta_text   VARCHAR(64)  NULL COMMENT 'CTA 文案(EN 基准)',
  cta_link   VARCHAR(512) NULL COMMENT '第一 CTA 链接（语言无关）',
  cta_text_secondary VARCHAR(64) NULL COMMENT '第二 CTA 文案(EN 基准，KD-14)',
  cta_link_secondary VARCHAR(512) NULL COMMENT '第二 CTA 链接（语言无关，KD-14）',
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_banner_status_position (status, position)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站点广告位（首页Hero/推荐位/顶部条）';

-- -----------------------------------------------------------------------------
-- 2. banner_translation（IDX-MKT-011）
-- -----------------------------------------------------------------------------
CREATE TABLE banner_translation (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  banner_id  BIGINT       NOT NULL COMMENT '逻辑外键 banner.id',
  locale     VARCHAR(8)   NOT NULL COMMENT 'es|fr（EN 存主表）',
  title      VARCHAR(255) NULL,
  subtitle   VARCHAR(255) NULL,
  cta_text   VARCHAR(64)  NULL,
  cta_text_secondary VARCHAR(64) NULL COMMENT '第二 CTA 文案译文（API 可编辑）',
  cta_link_secondary VARCHAR(512) NULL COMMENT '历史扩展列；当前管理 API 不编辑，链接取 Banner 主表',
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_bt (banner_id, locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Banner 多语言附表';

-- -----------------------------------------------------------------------------
-- 3. blog_post（IDX-MKT-004/005/006；slug 可空唯一——MySQL 多 NULL 共存，draft 未填不冲突）
-- -----------------------------------------------------------------------------
CREATE TABLE blog_post (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  title        VARCHAR(200) NOT NULL COMMENT '标题(EN 基准)',
  cover        VARCHAR(512) NULL,
  category     VARCHAR(64)  NULL COMMENT '文章栏目',
  author       VARCHAR(64)  NULL,
  content      TEXT         NULL COMMENT '正文(EN 基准)',
  slug         VARCHAR(128) NULL COMMENT '静态文章页路径 ^[a-z0-9-]+$；published 必填（CV-MKT-012）',
  status       TINYINT      NOT NULL DEFAULT 1 COMMENT '1=DRAFT 2=PUBLISHED 3=ARCHIVED',
  published_at DATETIME(3)  NULL COMMENT '首次发布时间（republish 不刷新）',
  views        INT          NOT NULL DEFAULT 0 COMMENT '阅读数近似计数（SCHED-MKT-02 flush，DEC-MKT-6）',
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_blog_slug (slug),
  KEY idx_blog_status_published (status, published_at),
  KEY idx_blog_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Blog 婚礼策划文章';

-- -----------------------------------------------------------------------------
-- 4. blog_post_translation（IDX-MKT-012）
-- -----------------------------------------------------------------------------
CREATE TABLE blog_post_translation (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  blog_post_id    BIGINT       NOT NULL,
  locale          VARCHAR(8)   NOT NULL COMMENT 'es|fr',
  title           VARCHAR(200) NULL,
  excerpt         VARCHAR(500) NULL,
  body            TEXT         NULL,
  seo_title       VARCHAR(128) NULL,
  seo_description VARCHAR(255) NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_bpt (blog_post_id, locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='博客多语言附表';

-- -----------------------------------------------------------------------------
-- 5. real_wedding（含 EN 文案列；IDX-MKT-008）
-- -----------------------------------------------------------------------------
CREATE TABLE real_wedding (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  couple       VARCHAR(64)  NOT NULL COMMENT '如 Emma & James',
  location     VARCHAR(128) NULL,
  theme        VARCHAR(32)  NULL,
  wedding_date VARCHAR(16)  NULL COMMENT '如 2025-06',
  cover        VARCHAR(512) NULL,
  status       TINYINT      NOT NULL DEFAULT 1 COMMENT '1=DRAFT 2=PUBLISHED',
  title        VARCHAR(200) NULL COMMENT '案例标题(EN 基准)',
  story        TEXT         NULL COMMENT '婚礼故事(EN 基准)',
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_wedding_status (status, wedding_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='真实婚礼案例（Shop the Look）';

-- -----------------------------------------------------------------------------
-- 6. real_wedding_translation（IDX-MKT-013）
-- -----------------------------------------------------------------------------
CREATE TABLE real_wedding_translation (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  real_wedding_id BIGINT       NOT NULL,
  locale          VARCHAR(8)   NOT NULL COMMENT 'es|fr',
  title           VARCHAR(200) NULL,
  story           TEXT         NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_rwt (real_wedding_id, locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='案例多语言附表';

-- -----------------------------------------------------------------------------
-- 7. real_wedding_product（Shop the Look nm；IDX-MKT-019）
-- -----------------------------------------------------------------------------
CREATE TABLE real_wedding_product (
  id              BIGINT      NOT NULL AUTO_INCREMENT,
  real_wedding_id BIGINT      NOT NULL,
  product_id      BIGINT      NOT NULL COMMENT '逻辑外键 catalog.product.id',
  created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_rwp (real_wedding_id, product_id),
  KEY idx_rwp_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='案例-商品挂载';

-- -----------------------------------------------------------------------------
-- 8. lookbook（含 EN description；IDX-MKT-009）
-- -----------------------------------------------------------------------------
CREATE TABLE lookbook (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  title       VARCHAR(128) NOT NULL COMMENT '画册标题(EN 基准)',
  theme       VARCHAR(32)  NULL COMMENT 'Vineyard/Beach/Forest',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=DRAFT 2=PUBLISHED',
  description VARCHAR(500) NULL COMMENT '画册描述(EN 基准)',
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_lookbook_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Lookbook 主题画册';

-- -----------------------------------------------------------------------------
-- 9. lookbook_translation（IDX-MKT-014）
-- -----------------------------------------------------------------------------
CREATE TABLE lookbook_translation (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  lookbook_id BIGINT       NOT NULL,
  locale      VARCHAR(8)   NOT NULL COMMENT 'es|fr',
  title       VARCHAR(128) NULL,
  description VARCHAR(500) NULL,
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_lbt (lookbook_id, locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Lookbook 多语言附表';

-- -----------------------------------------------------------------------------
-- 10. lookbook_product（IDX-MKT-020）
-- -----------------------------------------------------------------------------
CREATE TABLE lookbook_product (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  lookbook_id BIGINT      NOT NULL,
  product_id  BIGINT      NOT NULL COMMENT '逻辑外键 catalog.product.id',
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_lbp (lookbook_id, product_id),
  KEY idx_lbp_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Lookbook-商品挂载';

-- -----------------------------------------------------------------------------
-- 11. guide（含 EN body；IDX-MKT-010）
-- -----------------------------------------------------------------------------
CREATE TABLE guide (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  phase       VARCHAR(32)  NOT NULL COMMENT '备婚阶段，如 Phase 1',
  timeframe   VARCHAR(64)  NULL COMMENT '如 12+ months out',
  title       VARCHAR(128) NOT NULL COMMENT '指南标题(EN 基准)',
  tasks_count INT          NOT NULL DEFAULT 0 COMMENT '待办任务数',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=DRAFT 2=PUBLISHED',
  body        TEXT         NULL COMMENT '指南正文(EN 基准)',
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_guide_status_phase (status, phase)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='备婚指南（按阶段）';

-- -----------------------------------------------------------------------------
-- 12. guide_translation（IDX-MKT-015）
-- -----------------------------------------------------------------------------
CREATE TABLE guide_translation (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  guide_id   BIGINT       NOT NULL,
  locale     VARCHAR(8)   NOT NULL COMMENT 'es|fr',
  title      VARCHAR(128) NULL,
  body       TEXT         NULL,
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_gt (guide_id, locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='指南多语言附表';

-- -----------------------------------------------------------------------------
-- 13. coupon（含 EN description；used_count 仅核销 CAS 可写；IDX-MKT-001/002）
-- -----------------------------------------------------------------------------
CREATE TABLE coupon (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  code        VARCHAR(32)   NOT NULL COMMENT '券码 ^[A-Z0-9]+$ 唯一（大写归一）',
  name        VARCHAR(64)   NOT NULL COMMENT '券名(EN 基准)',
  type        VARCHAR(16)   NOT NULL COMMENT 'discount|fixed_amount|free_shipping',
  value       VARCHAR(32)   NOT NULL COMMENT '展示串，按 type pattern 可解析（DEC-MKT-4）',
  min_amount  DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '门槛金额 USD 基准',
  total_limit INT           NOT NULL DEFAULT 100000 COMMENT '限量；>9999 视为不限（DEC-MKT-5）',
  used_count  INT           NOT NULL DEFAULT 0 COMMENT '核销计数，仅 RM-MKT-107/108 可写',
  start_at    DATETIME(3)   NULL,
  end_at      DATETIME(3)   NULL COMMENT 'js_guard end_at>start_at',
  status      TINYINT       NOT NULL DEFAULT 1 COMMENT '1=DRAFT 2=SCHEDULED 3=ACTIVE 4=EXPIRING 5=EXPIRED（SCHED 翻转）',
  description VARCHAR(255)  NULL COMMENT '券说明(EN 基准)',
  created_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_coupon_code (code),
  KEY idx_coupon_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='优惠券（折扣/满减/包邮）';

-- -----------------------------------------------------------------------------
-- 14. coupon_translation（IDX-MKT-016）
-- -----------------------------------------------------------------------------
CREATE TABLE coupon_translation (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  coupon_id   BIGINT       NOT NULL,
  locale      VARCHAR(8)   NOT NULL COMMENT 'es|fr',
  name        VARCHAR(64)  NULL,
  description VARCHAR(255) NULL,
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_cpt (coupon_id, locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='优惠券多语言附表';

-- -----------------------------------------------------------------------------
-- 15. flash_sale（IDX-MKT-003）
-- -----------------------------------------------------------------------------
CREATE TABLE flash_sale (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  name       VARCHAR(64) NOT NULL COMMENT '活动名(EN 基准)',
  discount   VARCHAR(32) NOT NULL COMMENT '如 最高 40% OFF',
  start_at   DATETIME(3) NOT NULL,
  end_at     DATETIME(3) NOT NULL COMMENT 'js_guard end_at>start_at；到期 SCHED 自动 ended（s-761）',
  status     TINYINT     NOT NULL DEFAULT 1 COMMENT '1=DRAFT 2=SCHEDULED 3=ACTIVE 4=ENDED',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_flash_status_end (status, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='闪购活动（限时秒杀）';

-- -----------------------------------------------------------------------------
-- 16. flash_sale_translation（IDX-MKT-017）
-- -----------------------------------------------------------------------------
CREATE TABLE flash_sale_translation (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  flash_sale_id BIGINT      NOT NULL,
  locale        VARCHAR(8)  NOT NULL COMMENT 'es|fr',
  name          VARCHAR(64) NULL,
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_fst (flash_sale_id, locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='闪购多语言附表';

-- -----------------------------------------------------------------------------
-- 17. flash_sale_product（参与商品 nm；IDX-MKT-018）
-- -----------------------------------------------------------------------------
CREATE TABLE flash_sale_product (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  flash_sale_id BIGINT      NOT NULL,
  product_id    BIGINT      NOT NULL COMMENT '逻辑外键 catalog.product.id',
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_fsp (flash_sale_id, product_id),
  KEY idx_fsp_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='闪购-商品挂载';

-- -----------------------------------------------------------------------------
-- 18. newsletter_subscriber（决策 26；IDX-MKT-021）
-- -----------------------------------------------------------------------------
CREATE TABLE newsletter_subscriber (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  email         VARCHAR(255) NOT NULL COMMENT '小写归一，唯一（幂等判重）',
  source        VARCHAR(16)  NOT NULL COMMENT 'footer|modal|exit_intent',
  locale        VARCHAR(8)   NOT NULL COMMENT 'en|es|fr',
  subscribed_at DATETIME(3)  NOT NULL COMMENT '订阅时间',
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_newsletter_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Newsletter 订阅（仅落表，不发码不发邮件）';

-- -----------------------------------------------------------------------------
-- 19. contact_message（决策 30；IDX-MKT-022）
-- -----------------------------------------------------------------------------
CREATE TABLE contact_message (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  name         VARCHAR(100)  NOT NULL,
  email        VARCHAR(255)  NOT NULL,
  subject      VARCHAR(200)  NULL,
  message      VARCHAR(5000) NOT NULL,
  submitted_at DATETIME(3)   NOT NULL COMMENT '提交时间',
  created_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_contact_submitted (submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='联系表单消息（管理端本期不做查看页）';

SET FOREIGN_KEY_CHECKS = 1;
