# L2 数据层详设 - site_builder 域

> 来源：er-diagram.yml（权威）+ site-builder-api.openapi.yml + api-detail.md
> 覆盖：5 个核心实体 + Repository + DTO + 索引 + 事务 + DataInitializer 扩展 + 跨域缓存失效联动

## Entity 设计

### 1. HomePageSection（首页区块）

**表名**: `home_sections`
**基类**: `LongAuditableEntity`（com.dreamy.common.entity，参考 baseline）
**主键**: `id` BIGINT AUTO_INCREMENT

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| section_type | VARCHAR(32) | NOT NULL | 枚举 [hero, theme_cards, product_rail, editorial_feature, newsletter, custom] |
| enabled | TINYINT(1) | NOT NULL DEFAULT 1 | 启用状态（二态，KD-4 保存即发布） |
| sort_order | INT | NOT NULL DEFAULT 0 | 排序，>= 0 |
| data_json | JSON | NULL | 按 section_type 区分的配置数据 |
| i18n_json | JSON | NULL | 多语言文案 `{en:{}, es:{}, fr:{}}`（KD-16） |
| label | VARCHAR(255) | NULL | EN 基准标题（冗余字段便于查询，从 i18n_json.en.label 同步） |
| version | INT | NOT NULL DEFAULT 0 | 乐观锁 |
| created_at | DATETIME | NOT NULL | LongAuditableEntity |
| updated_at | DATETIME | NOT NULL | LongAuditableEntity |
| created_by | BIGINT | NULL | LongAuditableEntity |
| updated_by | BIGINT | NULL | LongAuditableEntity |

**索引**:
- IDX-001: `idx_home_sections_sort_order` (sort_order, id) — 列表排序
- IDX-002: `idx_home_sections_enabled_sort` (enabled, sort_order, id) — 消费端查询
- IDX-003: `idx_home_sections_type` (section_type) — 按类型筛选

**不变量**:
- section_type 必须在枚举内
- `section_type=hero` 是首页配置单例：所有首页区块写操作由应用层 Redisson 分布式锁串行化，锁覆盖完整事务提交/回滚；持锁后检查全表并拒绝第二条 Hero（enabled=false 也仍计数）。数据库不使用生成列或唯一索引约束该不变量。该单例约束针对 section，不限制 HERO Banner 数量。
- sort_order >= 0
- data_json + i18n_json 组合满足 js_guard（见 api-detail.md V-001~V-005）

### 2. NavigationItem（导航项）

**表名**: `navigation_items`
**基类**: `LongAuditableEntity`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| parent_id | BIGINT | NULL, FK→navigation_items.id | 父导航项，null 表示顶级 |
| label | VARCHAR(255) | NOT NULL | EN 基准标签（冗余） |
| label_i18n_key | VARCHAR(128) | NULL | i18n key（可选，与 i18n_json 二选一） |
| url | VARCHAR(512) | NULL | 自定义 URL（link_type=custom 时） |
| target | VARCHAR(16) | NOT NULL DEFAULT 'self' | 枚举 [self, blank] |
| link_type | VARCHAR(16) | NOT NULL DEFAULT 'custom' | 枚举 [custom, taxonomy] |
| taxonomy_id | BIGINT | NULL, FK→taxonomy.id（跨域引用） | link_type=taxonomy 时非空 |
| mega_menu_json | JSON | NULL | Mega Menu 列配置 `{columns:[{title, links:[{label, url, target}]}]}`（KD-6） |
| i18n_json | JSON | NULL | 多语言 `{en:{label}, es:{}, fr:{}}` |
| sort_order | INT | NOT NULL DEFAULT 0 | 排序 |
| enabled | TINYINT(1) | NOT NULL DEFAULT 1 | 启用 |
| version | INT | NOT NULL DEFAULT 0 | 乐观锁 |
| created_at/updated_at/created_by/updated_by | 同上 | | |

**索引**:
- IDX-004: `idx_navigation_items_parent_sort` (parent_id, sort_order, id) — 树组装
- IDX-005: `idx_navigation_items_enabled` (enabled, sort_order) — 消费端查询
- IDX-006: `idx_navigation_items_taxonomy` (taxonomy_id) — 跨域引用查询

**不变量**:
- parent_id 不能形成环（CV-001 DFS 检测）
- link_type=taxonomy 时 taxonomy_id 非空且存在
- link_type=custom 时 url 非空

### 3. FooterColumn（页脚栏目）

**表名**: `footer_columns`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| title | VARCHAR(255) | NOT NULL | EN 基准标题 |
| i18n_json | JSON | NULL | 多语言 `{en:{title}, es:{}, fr:{}}` |
| sort_order | INT | NOT NULL DEFAULT 0 | |
| enabled | TINYINT(1) | NOT NULL DEFAULT 1 | |
| version | INT | NOT NULL DEFAULT 0 | |
| created_at/updated_at/created_by/updated_by | 同上 | | |

**索引**: IDX-007: `idx_footer_columns_sort` (sort_order, id)

### 4. FooterLink（页脚链接）

**表名**: `footer_links`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| column_id | BIGINT | NOT NULL, FK→footer_columns.id | 所属栏目 |
| label | VARCHAR(255) | NOT NULL | EN 基准标签 |
| url | VARCHAR(512) | NOT NULL | HTTP(S) URL |
| target | VARCHAR(16) | NOT NULL DEFAULT 'self' | [self, blank] |
| i18n_json | JSON | NULL | `{en:{label}, es:{}, fr:{}}` |
| sort_order | INT | NOT NULL DEFAULT 0 | |
| version | INT | NOT NULL DEFAULT 0 | |
| created_at/updated_at/created_by/updated_by | 同上 | | |

**索引**:
- IDX-008: `idx_footer_links_column_sort` (column_id, sort_order, id)
- IDX-009: `idx_footer_links_url` (url) — URL 查询

### 5. Announcement（公告）

**表名**: `announcements`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| enabled | TINYINT(1) | NOT NULL DEFAULT 1 | |
| priority | INT | NOT NULL DEFAULT 0 | 优先级，DESC 排序 |
| start_at | DATETIME | NULL | 时间窗开始，null 表示立即生效 |
| end_at | DATETIME | NULL | 时间窗结束，null 表示永久 |
| content | TEXT | NULL | EN 基准内容（冗余） |
| content_i18n_json | JSON | NOT NULL | 公告内容多语言 `{en:{content}, es:{}, fr:{}}` |
| i18n_json | JSON | NULL | 其他文案 `{en:{dismiss_text}, ...}` |
| version | INT | NOT NULL DEFAULT 0 | |
| created_at/updated_at/created_by/updated_by | 同上 | | |

**索引**:
- IDX-010: `idx_announcements_priority_id` (priority DESC, id) — 列表排序
- IDX-011: `idx_announcements_enabled_time` (enabled, start_at, end_at) — 消费端时间窗查询
- IDX-012: `idx_announcements_priority_time` (priority, start_at, end_at) — 唯一性校验

**唯一约束**:
- UQ-001: 无显式 UNIQUE（priority + 时间窗重叠校验在应用层，CV-002）

### 6. SiteBuilderConfig（站点装修配置单例，可选）

**表名**: `site_builder_config`（单例，id 恒为 1）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, DEFAULT 1 | 单例 |
| navigation_version | INT | NOT NULL DEFAULT 0 | 导航整体版本（乐观锁，409805 用） |
| footer_version | INT | NOT NULL DEFAULT 0 | 页脚整体版本 |
| updated_at | DATETIME | NOT NULL | |

---

## Repository 方法（RM-NNN）

### HomePageSectionRepository

| ID | 方法签名 | 用途 |
|----|---------|------|
| RM-001 | `Optional<HomePageSection> findById(Long id)` | 详情查询 |
| RM-002 | `List<HomePageSection> findAllOrderBySort()` | admin 列表 |
| RM-003 | `List<HomePageSection> findEnabledOrderBySort()` | 消费端查询 |
| RM-004 | `int insert(HomePageSection entity)` | 创建 |
| RM-005 | `int updateByIdAndVersion(HomePageSection entity)` | 乐观锁更新 |
| RM-006 | `int deleteById(Long id)` | 删除 |
| RM-007 | `int batchUpdateSort(List<Pair<Long, Integer>> idSortPairs)` | 批量排序 |
| RM-008 | `int updateEnabled(Long id, Boolean enabled, Integer expectedVersion)` | 启停 |

### NavigationItemRepository

| ID | 方法签名 | 用途 |
|----|---------|------|
| RM-009 | `List<NavigationItem> findAllOrderBySort()` | 全量查询 |
| RM-010 | `List<NavigationItem> findEnabledOrderBySort()` | 消费端 |
| RM-011 | `int upsert(NavigationItem entity)` | 新增/更新 |
| RM-012 | `int deleteByIdsNotIn(List<Long> ids)` | 整体替换时删除多余的 |
| RM-013 | `List<NavigationItem> findByTaxonomyId(Long taxonomyId)` | 跨域引用查询 |

### FooterColumnRepository / FooterLinkRepository

| ID | 方法签名 | 用途 |
|----|---------|------|
| RM-014 | `List<FooterColumn> findAllOrderBySort()` | 全量 |
| RM-015 | `List<FooterLink> findAllOrderBySort()` | 全量 |
| RM-016 | `int deleteAll()` | 整体替换前清空 |
| RM-017 | `int batchInsert(List<FooterColumn> columns)` | 批量插入 |
| RM-018 | `int batchInsert(List<FooterLink> links)` | 批量插入 |

### AnnouncementRepository

| ID | 方法签名 | 用途 |
|----|---------|------|
| RM-019 | `Optional<Announcement> findById(Long id)` | 详情 |
| RM-020 | `Page<Announcement> findAllOrderByPriorityId(int page, int size)` | admin 分页列表 |
| RM-021 | `List<Announcement> findActiveByTimeWindow(LocalDateTime now)` | 消费端有效公告 |
| RM-022 | `List<Announcement> findByPriorityAndTimeOverlap(int priority, LocalDateTime start, LocalDateTime end)` | 唯一性校验 |
| RM-023 | `int insert(Announcement entity)` | 创建 |
| RM-024 | `int updateByIdAndVersion(Announcement entity)` | 乐观锁更新 |
| RM-025 | `int deleteById(Long id)` | 删除 |
| RM-026 | `int updateEnabled(Long id, Boolean enabled, Integer expectedVersion)` | 启停 |

---

## DTO 映射（MAP-NNN）

| ID | 源 | 目标 | 说明 |
|----|----|------|------|
| MAP-001 | HomePageSection Entity | HomePageSectionDto | admin 端，透传 i18n_json 整块 |
| MAP-002 | NavigationItem Entity | NavigationItemDto | admin 端，含 mega_menu_json |
| MAP-003 | FooterColumn + FooterLink | FooterColumnDto | admin 端，含 links 列表 |
| MAP-004 | Announcement Entity | AnnouncementDto | admin 端，含 content_i18n_json |
| MAP-005 | HomePageSection + 跨域派生 | StoreHomePageSectionDto | 消费端，按 locale 扁平化 |
| MAP-006 | NavigationItem | StoreNavigationItemDto | 消费端，按 locale 扁平化 label + mega_menu |
| MAP-007 | FooterColumn + FooterLink | StoreFooterColumnDto | 消费端，按 locale 扁平化 |
| MAP-008 | Announcement | StoreAnnouncementDto | 消费端，按 locale 扁平化 content |

**i18n_json locale 扁平化算法**（消费端用）：
```
flatten(i18n_json, locale, mainField):
  if i18n_json and i18n_json[locale] and i18n_json[locale].nonEmpty:
    return i18n_json[locale]
  if i18n_json and i18n_json.en and i18n_json.en.nonEmpty:
    return i18n_json.en
  return mainField or ""
```

---

## 事务边界（TX-NNN / EC-NNN）

| ID | 事务 | 范围 | 隔离级别 | 失败处理 |
|----|------|------|----------|----------|
| TX-001 | home_sections 写操作 | INSERT/UPDATE/DELETE + OperationLog + cache.invalidateFamily（同事务内） | READ_COMMITTED | 回滚，不触发 publisher.publish |
| TX-002 | navigation 整体替换 | DELETE + UPSERT navigation_items + SiteBuilderConfig.version+1 + OperationLog | READ_COMMITTED | 回滚 |
| TX-003 | footer 整体替换 | DELETE footer_columns + footer_links + INSERT 全量 + OperationLog | READ_COMMITTED | 回滚 |
| TX-004 | announcement CRUD | INSERT/UPDATE/DELETE + OperationLog + cache.invalidateFamily | READ_COMMITTED | 回滚 |

**EC-NNN 错误补偿**:
- EC-001: TX-001 失败 → 事务回滚，cache 不失效（一致性保证）
- EC-002: cache.invalidateFamily 失败 → 事务仍提交，记录 ERROR，publisher.publish 重试一次
- EC-003: publisher.publish 失败 → 事务仍提交，记录 ERROR，不重试（最终一致）

**重要约束**（GRD-W01）：
- cache.invalidateFamily 在事务**内**调用（同事务），保证 DB 与缓存原子性
- publisher.publish 在事务**外**调用（事务提交后），异步广播
- 不允许 HTTP 自调（如调用 /api/admin/cache/invalidate 端点）

---

## 数据校验（CV-NNN）

| ID | 校验 | 位置 | 错误码 |
|----|------|------|--------|
| CV-001 | NavigationItem parent_id 循环依赖检测 | NavigationService.save 前，DFS 遍历 | 409802 |
| CV-002 | Announcement priority + 时间窗重叠校验 | AnnouncementService.create/update 前 | 409804 |
| CV-003 | FooterLink.column_id 引用完整性 | FooterService.save 前 | 422803 |
| CV-004 | HomePageSection js_guard 校验 | HomePageSectionService.create/update 前 | 422808 |
| CV-005 | i18n_json locale 键合法性 | 各 Service 入参校验 | 422807 |
| CV-006 | NavigationItem.taxonomy_id 跨域存在性 | NavigationService.save 前，调用 TaxonomyService.findById | 404805 |
| CV-007 | HomePageSection.sort_order 非负 | 入参校验 | 422808 |
| CV-008 | Announcement.start_at < end_at | 入参校验 | 422805 |

---

## DataInitializer 扩展（L1 SHOULD_FIX-2 消化）

在 baseline `DataInitializer` 中新增 site_builder 域的权限点种子数据：

```java
// site_builder 域权限点（KD-15 新建限界上下文）
// permission 表新增 3 条记录
private void seedSiteBuilderPermissions() {
    savePermissionIfNotExists("/site/home", "首页区块管理", "site_builder");
    savePermissionIfNotExists("/site/navigation", "导航与页脚管理", "site_builder");
    savePermissionIfNotExists("/site/announcement", "公告管理", "site_builder");
}
```

**permission 表结构**（baseline 既有）：
- key: VARCHAR(128) PK — 权限点 key
- name: VARCHAR(64) — 中文名
- domain_code: VARCHAR(32) — 所属域
- description: VARCHAR(255) — 描述

**调用时机**: DataInitializer.run() 中，在 baseline 权限点种子后调用 seedSiteBuilderPermissions()

**RolePermission 关联**: 不自动关联到任何 Role（管理员在后台手动分配，或由超级管理员 Role.is_locked=true 默认拥有所有权限）

**运行时 seed 安全约定**: 首页区块、Banner 及其他业务样例只在显式设置
`DEMO_SEED_ENABLED=true` / `dreamy.seed.demo-enabled=true` 时灌入；缺省 false，正式环境启动不创建、不重建、
不覆盖 Hero 等运营数据。基线权限字典/auth_config/超管角色仍可幂等初始化，但首个超管账户
必须显式提供 `DREAMY_BOOTSTRAP_ADMIN_EMAIL` 与 `DREAMY_BOOTSTRAP_ADMIN_PASSWORD`（至少 12 字符）；仅 demo seed 开启时
允许使用本地演示凭据，不得将固定公开密码带入正式环境。

---

## 跨域缓存失效联动（L1 SHOULD_FIX-3 消化）

**问题**: marketing 域 Banner 写操作是否触发 site_builder home family 失效？

**决策**: **是的，需要联动失效**

**实现方式**:
1. marketing 域 BannerService 在写操作后，除了失效 marketing 自己的 cache family（如 `banner`），还需 `publisher.publish(TYPE_HOME_SECTION_CHANGED)` 触发 site_builder home family 失效
2. site_builder 节点订阅 TYPE_HOME_SECTION_CHANGED 事件 → 失效本地 L1 home family

**修改点**:
- `BannerService`（baseline 既有）的 create/update/delete/toggle 方法末尾追加：
  ```java
  publisher.publish(CacheInvalidationEvent.builder()
      .type(CacheInvalidationType.TYPE_HOME_SECTION_CHANGED)
      .family("home")
      .build());
  ```
- `CacheInvalidationListener`（site_builder 域新增）订阅 TYPE_HOME_SECTION_CHANGED 事件，失效 home family L1+L2

**理由**: 唯一 Hero section 的轮播数组派生自全部当前有效 Banner position=HERO（KD-2，ORDER BY sort,id），Banner 变更必须触发首页缓存/CDN 失效，否则消费端会显示旧 Hero 轮播。营销缓存本身使用 Redis 共享代际失效，不依赖单进程 key registry；重启/多实例也不会命中旧代 Hero key。

**其他跨域失效联动**:
- catalog 域 Taxonomy 写操作 → 触发 site_builder home + navigation family 失效（ThemeCards + NavigationItem.taxonomy_id 派生）
- catalog 域 Product 写操作 → 触发 site_builder home family 失效（ProductRail 派生）
- showroom 域 Wedding 写操作 → 触发 site_builder home family 失效（EditorialFeature 派生）

**实现位置**: 各域 Service 的写方法末尾追加 publisher.publish(TYPE_HOME_SECTION_CHANGED)，site_builder 节点统一订阅失效。

---

## DDL 脚本（手动执行，项目无 Flyway）

```sql
-- home_sections 表
CREATE TABLE home_sections (
  id BIGINT NOT NULL AUTO_INCREMENT,
  section_type VARCHAR(32) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  data_json JSON NULL,
  i18n_json JSON NULL,
  label VARCHAR(255) NULL,
  version INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  PRIMARY KEY (id),
  INDEX idx_home_sections_sort_order (sort_order, id),
  INDEX idx_home_sections_enabled_sort (enabled, sort_order, id),
  INDEX idx_home_sections_type (section_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- navigation_items 表
CREATE TABLE navigation_items (
  id BIGINT NOT NULL AUTO_INCREMENT,
  parent_id BIGINT NULL,
  label VARCHAR(255) NOT NULL,
  label_i18n_key VARCHAR(128) NULL,
  url VARCHAR(512) NULL,
  target VARCHAR(16) NOT NULL DEFAULT 'self',
  link_type VARCHAR(16) NOT NULL DEFAULT 'custom',
  taxonomy_id BIGINT NULL,
  mega_menu_json JSON NULL,
  i18n_json JSON NULL,
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- footer_columns 表
CREATE TABLE footer_columns (
  id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL,
  i18n_json JSON NULL,
  sort_order INT NOT NULL DEFAULT 0,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  version INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  PRIMARY KEY (id),
  INDEX idx_footer_columns_sort (sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- footer_links 表
CREATE TABLE footer_links (
  id BIGINT NOT NULL AUTO_INCREMENT,
  column_id BIGINT NOT NULL,
  label VARCHAR(255) NOT NULL,
  url VARCHAR(512) NOT NULL,
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- announcements 表
CREATE TABLE announcements (
  id BIGINT NOT NULL AUTO_INCREMENT,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  priority INT NOT NULL DEFAULT 0,
  start_at DATETIME NULL,
  end_at DATETIME NULL,
  content TEXT NULL,
  content_i18n_json JSON NOT NULL,
  i18n_json JSON NULL,
  version INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  PRIMARY KEY (id),
  INDEX idx_announcements_priority_id (priority DESC, id),
  INDEX idx_announcements_enabled_time (enabled, start_at, end_at),
  INDEX idx_announcements_priority_time (priority, start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- site_builder_config 表（单例）
CREATE TABLE site_builder_config (
  id BIGINT NOT NULL DEFAULT 1,
  navigation_version INT NOT NULL DEFAULT 0,
  footer_version INT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT chk_single_row CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO site_builder_config (id, navigation_version, footer_version, updated_at) VALUES (1, 0, 0, NOW());

-- permission 表新增 site_builder 域权限点
INSERT INTO permission (key, name, domain_code, description) VALUES
('/site/home', '首页区块管理', 'site_builder', 'HomeBuilder 页面管理权限'),
('/site/navigation', '导航与页脚管理', 'site_builder', 'NavigationConfig 页面管理权限'),
('/site/announcement', '公告管理', 'site_builder', 'Announcement 管理权限')
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- Banner 表扩展字段（KD-14：cta_text_secondary + cta_link_secondary）
ALTER TABLE banner_translations
  ADD COLUMN cta_text_secondary VARCHAR(255) NULL,
  ADD COLUMN cta_link_secondary VARCHAR(512) NULL;

-- BannerPosition.TOPBAR 标记废弃（KD-17）
-- 注：应用层在 BannerPosition enum 上加 @Deprecated，DDL 不变更
```
