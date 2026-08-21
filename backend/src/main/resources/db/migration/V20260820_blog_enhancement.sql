-- 手动执行（项目无 Flyway）
-- Blog 增强：EN 主表补 excerpt/seo_title/seo_description + 字数/阅读时长 + 乐观锁 version +
-- slug 变更历史表 + blog_category 栏目表化。
-- 存量数据：version 默认 0，无需回填；新增 SEO 三列全部 NULL，运行时 fallback（excerpt→content 截 160，seo_title→title）。
-- 不向后兼容点：无（新增列 + 新表，旧代码读旧字段行为不变）。

-- STEP 1: blog_post 主表新增列
ALTER TABLE blog_post
    ADD COLUMN excerpt           VARCHAR(500) NULL COMMENT 'EN 摘要(主表提升)' AFTER content,
    ADD COLUMN seo_title         VARCHAR(128) NULL COMMENT 'EN SEO 标题' AFTER excerpt,
    ADD COLUMN seo_description   VARCHAR(255) NULL COMMENT 'EN SEO 描述' AFTER seo_title,
    ADD COLUMN word_count        INT NOT NULL DEFAULT 0 COMMENT '字数(CJK按字+Latin按词)' AFTER seo_description,
    ADD COLUMN reading_minutes   DECIMAL(5,1) NOT NULL DEFAULT 0 COMMENT '阅读时长(cjk/300+latin/200)' AFTER word_count,
    ADD COLUMN version           BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁(仅 update 全量保存使用;patchStatus 走状态前置条件不增 version)' AFTER views,
    MODIFY COLUMN content        MEDIUMTEXT NULL COMMENT '正文(EN 基准, Markdown 格式)';

-- STEP 2: slug 变更历史表(P1 用于 301 跳转 / 410 Gone;P0 仅锁定不写入)
CREATE TABLE blog_post_slug_history (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    post_id     BIGINT NOT NULL COMMENT '逻辑外键 blog_post.id',
    slug        VARCHAR(128) NOT NULL COMMENT '历史 slug(旧值)',
    changed_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '变更时间',
    changed_by  BIGINT NULL COMMENT '操作人 admin.id',
    PRIMARY KEY (id),
    KEY idx_post_created (post_id, changed_at),
    KEY idx_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Blog slug 变更历史(发布后锁定;不存 from/to 对,不去重保留完整审计)';

-- STEP 3: blog_category 栏目表化(P1,独立发布单元)
CREATE TABLE blog_category (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    slug        VARCHAR(64) NOT NULL COMMENT '栏目标识 ^[a-z0-9-]+$',
    name_en     VARCHAR(64) NOT NULL COMMENT 'EN 栏目名',
    name_es     VARCHAR(64) NULL COMMENT 'ES 栏目名',
    name_fr     VARCHAR(64) NULL COMMENT 'FR 栏目名',
    sort_order  INT NOT NULL DEFAULT 0 COMMENT '排序(升序)',
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_blog_category_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Blog 栏目表化(替代自由文本 category 列)';

-- STEP 4: 历史数据初始化(防御;新列 DEFAULT 0 已生效,此语句保险)
UPDATE blog_post SET version = 0 WHERE version IS NULL;
