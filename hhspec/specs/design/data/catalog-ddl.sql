-- =============================================================================
-- catalog 限界上下文 物理库 DDL（MySQL 8.0+）
-- change: portal-api-integration  domain: catalog
-- 引擎: InnoDB  字符集: utf8mb4  排序: utf8mb4_0900_ai_ci
-- 约定:
--   - 主键: BIGINT AUTO_INCREMENT（决策 12，huihao LongAuditableEntity）
--   - 枚举: VARCHAR + Java enum 双保险（MAP-CAT-012，契约字符串取值）
--   - 时间: DATETIME(3) 存 UTC（CP-014）
--   - 外键: 全表无物理 FOREIGN KEY（CP-010），引用完整性由应用层 + 事务维护（CV-CAT-005）
--   - 逻辑删除: 不启用；deleted 终态=物理删除 + 引用守卫（409502/409503/409506/409507/409509）
--   - FULLTEXT: ngram parser（决策 17）；huihao @Index 不支持 FULLTEXT，
--     运行期由 CatalogFulltextIndexInitializer 幂等补建（与本 DDL 等价）
-- 运行期建表: huihao-mysql DDL-auto（@EnableMysql scanPackages=com.dreamy.domain）
--             以实体注解为准自动建表/加列；本文件为权威等价 SQL（审计/还原用）。
-- 演示数据: 仅 DEMO_SEED_ENABLED=true 时由 CatalogSeedInitializer 初始化；任一 Catalog
--           聚合根已有数据即整体跳过，不覆盖运营数据。
-- 来源: catalog-data-detail.md §9（16 表）/ catalog-contract-status.md（Collection 现行命名）
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- 1. category 三层分类树（IDX-CAT-011）
-- -----------------------------------------------------------------------------
CREATE TABLE category (
  id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  name             VARCHAR(64)  NOT NULL COMMENT '品类名称(EN 基准)',
  parent_id        BIGINT       NULL COMMENT '父分类，NULL=根',
  level            TINYINT      NOT NULL DEFAULT 1 COMMENT '层级 1..3（应用层校验）',
  attribute_set_id BIGINT       NULL COMMENT '绑定属性集（根必填，子可空=沿父链继承）',
  attr_overrides   JSON         NULL COMMENT '子分类属性可见性 delta {attrKey: visibility}',
  sort             INT          NOT NULL DEFAULT 0 COMMENT '同层排序（拖拽落库）',
  created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_category_parent (parent_id),
  KEY idx_category_attrset (attribute_set_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品标准品类（三层树）';

-- -----------------------------------------------------------------------------
-- 2. category_translation（IDX-CAT-012）
-- -----------------------------------------------------------------------------
CREATE TABLE category_translation (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  category_id BIGINT      NOT NULL COMMENT '逻辑外键 category.id',
  locale      VARCHAR(8)  NOT NULL COMMENT 'es|fr（EN 存主表）',
  name        VARCHAR(64) NULL COMMENT '品类名译文',
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ct (category_id, locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分类多语言附表';

-- -----------------------------------------------------------------------------
-- 3. attribute_def 属性字典（IDX-CAT-013）
-- -----------------------------------------------------------------------------
CREATE TABLE attribute_def (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  `key`      VARCHAR(64)  NOT NULL COMMENT '属性键（小写下划线，不可改）',
  label      VARCHAR(64)  NOT NULL COMMENT '显示名(EN 基准)',
  type       VARCHAR(16)  NOT NULL COMMENT 'select|multiselect|text|toggle',
  options    JSON         NULL COMMENT '可选值列表（仅 select/multiselect）',
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_attribute_def_key (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品属性字典';

-- -----------------------------------------------------------------------------
-- 4. attribute_def_translation（IDX-CAT-014）
-- -----------------------------------------------------------------------------
CREATE TABLE attribute_def_translation (
  id               BIGINT      NOT NULL AUTO_INCREMENT,
  attribute_def_id BIGINT      NOT NULL,
  locale           VARCHAR(8)  NOT NULL COMMENT 'es|fr',
  label            VARCHAR(64) NULL,
  options          JSON        NULL COMMENT '与主表 options 等长的译文数组',
  created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_adt (attribute_def_id, locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='属性字典多语言附表';

-- -----------------------------------------------------------------------------
-- 5. attribute_set 属性集
-- -----------------------------------------------------------------------------
CREATE TABLE attribute_set (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  label      VARCHAR(64) NOT NULL COMMENT '属性集名称',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='属性集';

-- -----------------------------------------------------------------------------
-- 6. attribute_set_item 可见性矩阵（IDX-CAT-015）
-- -----------------------------------------------------------------------------
CREATE TABLE attribute_set_item (
  id               BIGINT      NOT NULL AUTO_INCREMENT,
  attribute_set_id BIGINT      NOT NULL,
  attribute_id     BIGINT      NOT NULL COMMENT '逻辑外键 attribute_def.id',
  visibility       VARCHAR(16) NOT NULL COMMENT 'visible|optional|hidden（必填/可选/隐藏）',
  created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_asi (attribute_set_id, attribute_id),
  KEY idx_asi_attribute (attribute_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='属性集明细行';

-- -----------------------------------------------------------------------------
-- 7. collection_group 集合分组
-- -----------------------------------------------------------------------------
CREATE TABLE collection_group (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  name        VARCHAR(64)  NOT NULL COMMENT '维度名(EN 基准)',
  description VARCHAR(255) NULL COMMENT '维度说明',
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='集合分组';

-- -----------------------------------------------------------------------------
-- 8. collection_group_translation（IDX-CAT-018）
-- -----------------------------------------------------------------------------
CREATE TABLE collection_group_translation (
  id                  BIGINT      NOT NULL AUTO_INCREMENT,
  collection_group_id BIGINT      NOT NULL,
  locale              VARCHAR(8)  NOT NULL COMMENT 'es|fr',
  name                VARCHAR(64) NULL,
  created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_cgt (collection_group_id, locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='集合分组多语言附表';

-- -----------------------------------------------------------------------------
-- 9. collection 营销集合（IDX-CAT-016；封面由集合商品主图动态派生）
-- -----------------------------------------------------------------------------
CREATE TABLE collection (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  collection_group_id BIGINT       NOT NULL COMMENT '逻辑外键 collection_group.id',
  name                VARCHAR(64)  NOT NULL COMMENT '集合名(EN 基准)',
  status              VARCHAR(16)  NOT NULL DEFAULT 'enabled' COMMENT 'enabled|disabled',
  created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_collection_group (collection_group_id),
  KEY idx_collection_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='营销集合';

-- -----------------------------------------------------------------------------
-- 10. collection_translation（IDX-CAT-017）
-- -----------------------------------------------------------------------------
CREATE TABLE collection_translation (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  collection_id BIGINT      NOT NULL,
  locale        VARCHAR(8)  NOT NULL COMMENT 'es|fr',
  label         VARCHAR(64) NULL,
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ct (collection_id, locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='集合多语言附表';

-- -----------------------------------------------------------------------------
-- 11. product 商品主档（IDX-CAT-001~005；冗余回写列仅 EVT-CAT-001/002/003 可写）
-- -----------------------------------------------------------------------------
CREATE TABLE product (
  id                    BIGINT        NOT NULL AUTO_INCREMENT,
  name                  VARCHAR(128)  NOT NULL COMMENT '商品名(EN 基准)',
  slug                  VARCHAR(128)  NOT NULL COMMENT 'URL slug ^[a-z0-9-]+$',
  subtitle              VARCHAR(255)  NULL COMMENT '副标题/卖点',
  category_id           BIGINT        NOT NULL COMMENT '逻辑外键 category.id',
  product_type          VARCHAR(64)   NULL,
  description           TEXT          NULL COMMENT '富文本介绍',
  designer_note         TEXT          NULL COMMENT '品牌故事',
  price                 DECIMAL(12,2) NOT NULL COMMENT '现价 USD 基准',
  compare_at            DECIMAL(12,2) NULL COMMENT '划线价 >= price（应用层校验）',
  installment           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT 'Klarna/Afterpay 分期',
  multi_currency_prices JSON          NULL COMMENT '每币种覆盖价 {CAD: 99.0,...}',
  status                VARCHAR(16)   NOT NULL DEFAULT 'draft' COMMENT 'draft|published',
  is_new                TINYINT(1)    NOT NULL DEFAULT 0,
  is_best               TINYINT(1)    NOT NULL DEFAULT 0,
  recommend             TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '手动推荐标记（best_sellers 冷启动回退）',
  sort                  INT           NOT NULL DEFAULT 0,
  lead_time_days        INT           NOT NULL DEFAULT 1 COMMENT '标准发货周期(天) >=1',
  rush_available        TINYINT(1)    NOT NULL DEFAULT 0,
  custom_size_available TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '定制尺寸开关（A-007）',
  silhouette            VARCHAR(64)   NULL,
  neckline              VARCHAR(64)   NULL,
  sleeve                VARCHAR(64)   NULL,
  back_style            VARCHAR(64)   NULL,
  waistline             VARCHAR(64)   NULL,
  train                 VARCHAR(64)   NULL,
  length                VARCHAR(64)   NULL,
  fabric                VARCHAR(64)   NULL,
  fabric_composition    VARCHAR(128)  NULL,
  support               VARCHAR(64)   NULL,
  season                VARCHAR(64)   NULL,
  embellishments        JSON          NULL COMMENT '装饰细节多选',
  occasions             JSON          NULL COMMENT '适合场合多选',
  style_tags            JSON          NULL COMMENT '风格标签多选（自由文本，区别于 collection 实体）',
  model_height          VARCHAR(32)   NULL,
  model_size            VARCHAR(16)   NULL,
  model_body_type       VARCHAR(32)   NULL,
  care_instructions     TEXT          NULL,
  country_of_origin     VARCHAR(64)   NULL,
  style_no              VARCHAR(32)   NULL COMMENT '款式编号',
  seo_title             VARCHAR(128)  NULL,
  seo_desc              VARCHAR(255)  NULL,
  sales_30d             INT           NOT NULL DEFAULT 0 COMMENT '近30天已支付销量（EVT-CAT-001/003 回写，决策29）',
  sales_refreshed_at    DATETIME(3)   NULL COMMENT '销量窗口刷新时间',
  rating_avg            DECIMAL(3,2)  NOT NULL DEFAULT 0 COMMENT '已通过评价均分（EVT-CAT-002 回写）',
  rating_count          INT           NOT NULL DEFAULT 0 COMMENT '已通过评价数（EVT-CAT-002 回写）',
  created_at            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_product_slug (slug),
  KEY idx_product_status_category (status, category_id),
  KEY idx_product_status_created (status, created_at),
  KEY idx_product_sales (status, sales_30d),
  FULLTEXT KEY ft_product_search (name, subtitle) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品主档（现货+定制双模式）';

-- -----------------------------------------------------------------------------
-- 12. product_translation（IDX-CAT-010 含 FULLTEXT）
-- -----------------------------------------------------------------------------
CREATE TABLE product_translation (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  product_id      BIGINT       NOT NULL,
  locale          VARCHAR(8)   NOT NULL COMMENT 'es|fr',
  name            VARCHAR(128) NULL,
  subtitle        VARCHAR(255) NULL,
  description     TEXT         NULL,
  seo_title       VARCHAR(128) NULL,
  seo_description VARCHAR(255) NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_pt (product_id, locale),
  FULLTEXT KEY ft_pt_search (name, subtitle) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品多语言附表（决策13/17）';

-- -----------------------------------------------------------------------------
-- 13. product_image（IDX-CAT-008）
-- -----------------------------------------------------------------------------
CREATE TABLE product_image (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  product_id BIGINT       NOT NULL,
  url        VARCHAR(512) NOT NULL COMMENT '预签名上传 public_url',
  kind       VARCHAR(16)  NOT NULL COMMENT 'gallery|lifestyle|video|swatch',
  color_name VARCHAR(32)  NULL COMMENT 'kind=swatch 时颜色名',
  sort       INT          NOT NULL DEFAULT 0 COMMENT 'gallery sort=0 为主图',
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_image_product_sort (product_id, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品媒体素材';

-- -----------------------------------------------------------------------------
-- 14. sku（IDX-CAT-006/007；version 乐观锁双用途）
-- -----------------------------------------------------------------------------
CREATE TABLE sku (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  product_id BIGINT      NOT NULL,
  sku_code   VARCHAR(64) NOT NULL COMMENT '^[A-Z0-9-]+$ 全局唯一',
  color      VARCHAR(32) NOT NULL,
  size       VARCHAR(16) NOT NULL,
  stock      INT         NOT NULL DEFAULT 0 COMMENT '现货库存；定制款不扣减（决策6）',
  version    BIGINT      NOT NULL DEFAULT 0 COMMENT '乐观锁（扣减防超卖 BE-DIM-4 / 编辑防丢失 409508）',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_sku_code (sku_code),
  KEY idx_sku_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SKU 颜色x尺码矩阵';

-- -----------------------------------------------------------------------------
-- 15. size_chart_row（IDX-CAT-009，决策 20.3 尺码推荐数据源）
-- -----------------------------------------------------------------------------
CREATE TABLE size_chart_row (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  product_id      BIGINT        NOT NULL,
  us              VARCHAR(8)    NOT NULL,
  uk              VARCHAR(8)    NULL,
  au              VARCHAR(8)    NULL,
  bust            DECIMAL(6,2)  NULL COMMENT '胸围(in)',
  waist           DECIMAL(6,2)  NULL COMMENT '腰围(in)',
  hips            DECIMAL(6,2)  NULL COMMENT '臀围(in)',
  hollow_to_floor DECIMAL(6,2)  NULL COMMENT '中空到地(in)',
  created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_scr_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品尺码对照表行（决策20.3 推荐数据源）';

-- -----------------------------------------------------------------------------
-- 16. product_collection（IDX-CAT-019；Product-Collection nm 关系，支持集合内人工排序）
-- -----------------------------------------------------------------------------
CREATE TABLE product_collection (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  product_id    BIGINT      NOT NULL,
  collection_id BIGINT      NOT NULL,
  sort          INT         NOT NULL DEFAULT 0,
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_pcol (product_id, collection_id),
  KEY idx_pcol_collection (collection_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品-集合挂载';

SET FOREIGN_KEY_CHECKS = 1;

-- 备注:
--   ① FULLTEXT ngram 依赖 MySQL 内置 ngram parser（ngram_token_size=2 默认）；
--   ② 消费幂等不落 processed_event 表（基建 EventIdempotencyGuard Redis SETNX 承载，
--      data-flow 消费幂等规范允许「processed_event 表 / Redis SETNX」二择）；
--      webhook 专用 processed_event 表归 trading 域 DDL；
--   ③ 演示种子仅在 DEMO_SEED_ENABLED=true 时由 CatalogSeedInitializer 灌入，不在本文件硬编码 id。
