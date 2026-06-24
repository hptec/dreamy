-- site-decoration-fullstack 变更 DDL 脚本
-- 手动执行（项目无 Flyway）
-- 执行前请备份相关表

-- ===== 1. home_sections 表 =====
CREATE TABLE IF NOT EXISTS home_sections (
  id BIGINT NOT NULL AUTO_INCREMENT,
  section_type VARCHAR(32) NOT NULL COMMENT '区块类型：hero/theme_cards/product_rail/editorial_feature/newsletter/custom',
  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '启用状态',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
  data_json JSON NULL COMMENT '按 section_type 区分的配置数据',
  i18n_json JSON NULL COMMENT '多语言文案 {en:{},es:{},fr:{}}',
  label VARCHAR(255) NULL COMMENT 'EN 基准标题（冗余便于查询）',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  PRIMARY KEY (id),
  INDEX idx_home_sections_sort_order (sort_order, id),
  INDEX idx_home_sections_enabled_sort (enabled, sort_order, id),
  INDEX idx_home_sections_type (section_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页区块配置';

-- ===== 2. navigation_items 表 =====
CREATE TABLE IF NOT EXISTS navigation_items (
  id BIGINT NOT NULL AUTO_INCREMENT,
  parent_id BIGINT NULL COMMENT '父导航项 id，null=顶级',
  label VARCHAR(255) NOT NULL COMMENT 'EN 基准标签',
  label_i18n_key VARCHAR(128) NULL COMMENT 'i18n key（可选）',
  url VARCHAR(512) NULL COMMENT '自定义 URL',
  target VARCHAR(16) NOT NULL DEFAULT 'self' COMMENT 'target: self/blank',
  link_type VARCHAR(16) NOT NULL DEFAULT 'custom' COMMENT 'link_type: custom/taxonomy',
  taxonomy_id BIGINT NULL COMMENT 'taxonomy_id（link_type=taxonomy 时非空）',
  mega_menu_json JSON NULL COMMENT 'Mega Menu 列配置',
  i18n_json JSON NULL COMMENT '多语言 {en:{label},es:{},fr:{}}',
  sort_order INT NOT NULL DEFAULT 0,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  version INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  PRIMARY KEY (id),
  INDEX idx_navigation_items_parent_sort (parent_id, sort_order, id),
  INDEX idx_navigation_items_enabled (enabled, sort_order),
  INDEX idx_navigation_items_taxonomy (taxonomy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导航项配置';

-- ===== 3. footer_columns 表 =====
CREATE TABLE IF NOT EXISTS footer_columns (
  id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL COMMENT 'EN 基准标题',
  i18n_json JSON NULL COMMENT '多语言 {en:{title},es:{},fr:{}}',
  sort_order INT NOT NULL DEFAULT 0,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  version INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  PRIMARY KEY (id),
  INDEX idx_footer_columns_sort (sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页脚栏目';

-- ===== 4. footer_links 表 =====
CREATE TABLE IF NOT EXISTS footer_links (
  id BIGINT NOT NULL AUTO_INCREMENT,
  column_id BIGINT NOT NULL COMMENT '所属栏目 id',
  label VARCHAR(255) NOT NULL COMMENT 'EN 基准标签',
  url VARCHAR(512) NOT NULL COMMENT 'HTTP(S) URL',
  target VARCHAR(16) NOT NULL DEFAULT 'self',
  i18n_json JSON NULL,
  sort_order INT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  PRIMARY KEY (id),
  INDEX idx_footer_links_column_sort (column_id, sort_order, id),
  INDEX idx_footer_links_url (url),
  CONSTRAINT fk_footer_links_column FOREIGN KEY (column_id) REFERENCES footer_columns(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页脚链接';

-- ===== 5. announcements 表 =====
CREATE TABLE IF NOT EXISTS announcements (
  id BIGINT NOT NULL AUTO_INCREMENT,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  priority INT NOT NULL DEFAULT 0 COMMENT '优先级 DESC',
  start_at DATETIME NULL COMMENT '时间窗开始',
  end_at DATETIME NULL COMMENT '时间窗结束',
  content TEXT NULL COMMENT 'EN 基准内容',
  content_i18n_json JSON NOT NULL COMMENT '公告内容多语言 {en:{content},es:{},fr:{}}',
  i18n_json JSON NULL COMMENT '其他文案多语言',
  version INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  PRIMARY KEY (id),
  INDEX idx_announcements_priority_id (priority DESC, id),
  INDEX idx_announcements_enabled_time (enabled, start_at, end_at),
  INDEX idx_announcements_priority_time (priority, start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告';

-- ===== 6. site_builder_config 表（单例） =====
CREATE TABLE IF NOT EXISTS site_builder_config (
  id BIGINT NOT NULL DEFAULT 1,
  navigation_version INT NOT NULL DEFAULT 0 COMMENT '导航整体版本（乐观锁）',
  footer_version INT NOT NULL DEFAULT 0 COMMENT '页脚整体版本（乐观锁）',
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT chk_single_row CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站点装修配置单例';

INSERT INTO site_builder_config (id, navigation_version, footer_version, updated_at)
VALUES (1, 0, 0, NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- ===== 7. permission 表新增 site_builder 域权限点（KD-15） =====
INSERT INTO permission (key, name, domain_code, description) VALUES
('/site/home', '首页区块管理', 'site_builder', 'HomeBuilder 页面管理权限'),
('/site/navigation', '导航与页脚管理', 'site_builder', 'NavigationConfig 页面管理权限'),
('/site/announcement', '公告管理', 'site_builder', 'Announcement 管理权限')
ON DUPLICATE KEY UPDATE name=VALUES(name), description=VALUES(description);

-- ===== 8. BannerTranslation 扩展字段（KD-14：cta_*_secondary） =====
ALTER TABLE banner_translations
  ADD COLUMN cta_text_secondary VARCHAR(255) NULL COMMENT '次要 CTA 文案（KD-14）',
  ADD COLUMN cta_link_secondary VARCHAR(512) NULL COMMENT '次要 CTA 链接（KD-14）';

-- ===== 9. NewsletterSource 枚举扩展（KD-13：HOME_BLOCK=4） =====
-- 注：NewsletterSource 是 Java enum，无需 DDL，但需要在 Java 代码中新增 HOME_BLOCK(4) 枚举值
-- 见 backend/src/main/java/com/dreamy/enums/NewsletterSource.java（如已存在则扩展）

-- ===== 完成提示 =====
SELECT 'site-decoration-fullstack DDL executed successfully' AS status;
