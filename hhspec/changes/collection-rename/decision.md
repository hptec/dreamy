# Change: 自定义标签 → 集合（Collection）完整重构

> 单次大 PR 一次性完成；命名 + DB + API 路径 + 前端 UI + Spec 文档全切换。
> **Hard cutover（无兼容期）**——项目初期无外部消费者，前后端同部署，附完整回滚预案。

## 1. 背景与动机

后台「分类管理 › 自定义标签」当前承载的是运营营销聚合关系（Style/Season/Shop by Color 色板等），其 UI 文案自己也写明"仅用于前台导航和营销聚合，不影响商品属性表单"。但命名上仍叫「标签」，与商品固有的属性标签（材质、颜色等 EAV 体系）语义打架，新人理解成本高。

同时，商品编辑页把这个"营销聚合关系"塞在「基础属性」区内，混淆了"商品固有属性"与"运营后挂关系"两个性质不同的概念。

## 2. 目标

1. **概念重命名**：Tag → Collection（集合）；TagDimension → CollectionGroup（集合分组）；ProductTag → ProductCollection。
2. **UI 结构调整**：ProductEdit 把"加入集合"从基础属性区抽出，独立成 section，位于基础属性后、SKU 前，仍是按分组的多选 chip。
3. **代码干净**：包名、类名、表名、列名、索引名、缓存 key、MQ event type、API 路径、DTO 字段、错误码枚举名、**以及所有硬编码的 "tag"/"tag_changed" 字符串字面量**全部统一改名，无残留。
4. **数据保留**：仅 rename 表/列/索引，不重置业务数据；历史 `operation_log` / `cache_invalidation_log` 行的旧字符串值保留不变（仅日志，新写入用新值）。

## 3. 关键决策

| 决策点 | 选择 | 理由 |
|---|---|---|
| 命名方案 | `Collection` / `CollectionGroup` / `ProductCollection` | 电商通用术语，准确表达"营销聚合"语义 |
| 商品编辑交互 | 独立 section + 多选 chip | 高效；保留现有交互模式，仅位置和文案变化 |
| PR 拆分 | 单大 PR 一次过 | 项目初期无外部消费者，前后端同部署，避免中间断线状态 |
| 兼容策略 | **Hard cutover，无 alias / 双消费** | 项目初期、无外部消费者；alias 会引入技术债 |
| 错误码数字 | 保持 404505 / 409506 不变 | 仅改 Java 枚举名，前端错误码映射无需调整 |
| `style_tags` JSON 列 | 不动 | 商品自由文本风格标签，与 collection 实体无关，spec 已明确区分 |
| 缓存 key | `catalog:tags:*` → `catalog:collections:*` | 部署时旧 key 自然过期（TTL 600s） |
| MQ event type | `tag_changed` → `collection_changed` | 前后端同 PR 部署；部署前清空 MQ 队列 |
| 历史日志行 | 保留旧字符串值不动 | `operation_log.action` / `cache_invalidation_log.event_type` 历史行仅是日志，无业务依赖 |

## 4. 影响范围

### 4.1 后端（~32 文件）

- `com.dreamy.domain.tag` 整包迁移到 `com.dreamy.domain.collection`（15 文件：4 entity + 4 const + 4 repository/mapper + 2 service + 1 translation mapper）
- `ProductTag` → `ProductCollection`（仍在 `domain.product` 包，3 文件：entity + repository + mapper）
- `ProductRepository.java`：EXISTS 子查询 `product_tag`/`tag_id` → `product_collection`/`collection_id`（第 83、250、255 行）
- DTO：`AdminCatalogDtos` / `TranslationDtos` / `StoreTagDimensionGroup` / `AdminProductDetail` / `AdminProductUpsert` / `StoreProductDetail` / `PresignDtos`
- Controller：`AdminTagController` → `AdminCollectionController`；`StoreCategoryController` 中 `/api/store/tags` → `/api/store/collections`；`StoreProductController` 的 `tag_id` 参数 → `collection_id`
- Enum：`TagStatus` → `CollectionStatus`；`RecommendationBlock.requiresTagId()` → `requiresCollectionId()`；`UploadScope.TAG("tag")` → `UploadScope.COLLECTION("collection")`
- `CatalogErrorCode.TAG_NOT_FOUND` / `TAG_DIMENSION_IN_USE` → `COLLECTION_NOT_FOUND` / `COLLECTION_GROUP_IN_USE`（数字 404505/409506 不变）
- `CatalogCacheService.Family.TAGS`（prefix `catalog:tags:`）→ `Family.COLLECTIONS`（prefix `catalog:collections:`）
- `ContentInvalidatedPublisher`：常量 `TYPE_TAG_CHANGED` → `TYPE_COLLECTION_CHANGED`；`startsWith("tag_")` 分支 → `startsWith("collection_")`；`return "tag"` → `return "collection"`；`"tag".equals(resourceType)` → `"collection".equals(resourceType)`
- `mq/InvalidatePathMapper.java:68`：`case "category_changed", "tag_changed"` → `case "category_changed", "collection_changed"`
- `domain/cache/service/AdminCacheService.java:110`：`"tag".equals(resourceType)` → `"collection".equals(resourceType)`
- `CatalogSeedInitializer.java:146`：seed 清表名列表 `"tag_translation", "product_tag", "tag", "tag_dimension_translation", "tag_dimension"` → `"collection_translation", "product_collection", "collection", "collection_group_translation", "collection_group"`
- `RecommendationService.java`：`tagId` 参数 → `collectionId`
- `AdminProductService` / `StoreProductService`：依赖同步
- DB migration `V20260617_tag_to_collection.sql`

### 4.2 持久化数据/字符串处理清单（BLK-001 修复）

| 位置 | 类型 | 处理 |
|---|---|---|
| `operation_log.action` 历史行 | 持久化字符串 | **保留不动**（"创建标签"等历史值仅日志，无业务依赖） |
| `operation_log.action` 新写入 | 持久化字符串 | 改为 "创建集合"/"编辑集合"/"删除集合"/"创建集合分组"/... |
| `cache_invalidation_log.event_type` 历史行 | 持久化字符串 | **保留不动**（历史 `tag_changed` 行仅日志） |
| `cache_invalidation_log.event_type` 新写入 | 持久化字符串 | 改为 `collection_changed` |
| `cache_invalidation_log.resource_type` 历史行 | 持久化字符串 | **保留不动**（历史 `tag` 行） |
| `cache_invalidation_log.resource_type` 新写入 | 持久化字符串 | 改为 `collection` |
| `ContentInvalidatedPublisher` 常量与分支 | 硬编码字符串 | 全部改名（见 4.1） |
| `InvalidatePathMapper.java:68` switch case | 硬编码字符串 | `tag_changed` → `collection_changed` |
| `AdminCacheService.java:110` | 硬编码字符串 | `"tag"` → `"collection"` |
| `UploadScope.TAG("tag")` | 枚举字面量 | → `COLLECTION("collection")`，影响 presign 上传 scope 字符串 |
| `CatalogSeedInitializer.java:146` 表名列表 | 硬编码字符串 | 同步改 5 个表名 |
| `V20260616_i18n_ai_gateway.sql:42` `biz_type` 注释 | SQL 注释 | 注释更新 `product/category/tag等` → `product/category/collection等`（仅注释，无数据影响） |
| 前端 localStorage | 无 | 经查无 tag 相关持久化（cart/order/cookie-consent/showroom/i18n/auth 均不涉及） |

**无持久化的 saved filter / user preference / recommendation block config 包含 tag_id**——`RecommendationBlock` 是纯枚举无 DB 配置；`shop_by_color` 的 `tag_id` 是 API 查询参数，运行时透传，无落库。

### 4.3 前端（~6 文件）

- `stores/tags.ts` (`useTagsStore`) → `stores/collections.ts` (`useCollectionsStore`)
- `api/catalog.ts`：`listTagDimensions` / `createTagDimension` / `listTags` / `createTag` 等 8 个函数 → `listCollectionGroups` / `createCollectionGroup` / `listCollections` / `createCollection` 等
- `api/types.ts`：`Tag` / `TagDimension` / `TagUpsert` / `TagDimensionUpsert` / `TagTranslation` / `TagDimensionTranslation` / `TagStatus` / `PresignScope` 全部改名
- `views/Categories.vue`：Tab key `'tags'` → `'collections'`，文案「自定义标签」→「集合」，Tab 标题「自定义标签」→「集合」，空态文案「暂无标签维度」→「暂无集合分组」
- `views/ProductEdit.vue`：`form.tagIds` → `form.collectionIds`，"自定义标签" section **从基础属性区抽出独立成「加入集合」section**（位于基础属性后、SKU 前），交互仍是按分组多选 chip
- `views/Products.vue`：`moreFilters.tagIds` → `collectionIds`，筛选标题改"集合"
- `tests/ui-verification/admin-prototype-alignment.verify.mjs`：文案断言「自定义标签」→「集合」

### 4.4 Spec 文档（~15 文件）

- `hhspec/specs/architecture/{er-diagram,api-contracts/catalog-api.openapi,state-machine,business-flow}.yml`
- `hhspec/specs/design/data/catalog-ddl.sql`
- `hhspec/specs/design/catalog/catalog-{data,api,frontend,test}-detail.md` + `task-allocation.yml` + `catalog-ui-test-spec.yml`
- `hhspec/specs/design/admin-prototype-alignment/categories-frontend-detail.md`
- `hhspec/specs/design/{Tag,TagDimension,CustomTag}/` 目录 → `Collection/` + `CollectionGroup/`（CustomTag 目录删除，合并声明已在 catalog-api-detail）

## 5. API 路径变更（Hard cutover，无 alias）

| 旧 | 新 |
|---|---|
| `/api/admin/tag-dimensions` | `/api/admin/collection-groups` |
| `/api/admin/tags` | `/api/admin/collections` |
| `/api/store/tags` | `/api/store/collections` |
| `?tag_id=` | `?collection_id=` |
| `AdminProductUpsert.tag_ids` | `collection_ids` |
| `StoreProductDetail.tags[]` | `collections[]` |
| `PresignScope = 'tag'` | `'collection'` |
| MQ `type = "tag_changed"` | `"collection_changed"` |
| MQ `resource_type = "tag"`（派生） | `"collection"` |
| 审计 action "创建标签" 等 | "创建集合" 等 |

## 6. DB 迁移 `V20260617_tag_to_collection.sql`

```sql
-- 1. tag_dimension → collection_group
RENAME TABLE tag_dimension TO collection_group;
RENAME TABLE tag_dimension_translation TO collection_group_translation;
ALTER TABLE collection_group_translation
  CHANGE COLUMN tag_dimension_id collection_group_id BIGINT NOT NULL;
ALTER TABLE collection_group_translation
  DROP INDEX uk_tdt,
  ADD UNIQUE KEY uk_cgt (collection_group_id, locale);

-- 2. tag → collection
RENAME TABLE tag TO collection;
ALTER TABLE collection
  CHANGE COLUMN dimension_id collection_group_id BIGINT NOT NULL COMMENT '逻辑外键 collection_group.id',
  DROP INDEX idx_tag_dimension,
  DROP INDEX idx_tag_status,
  ADD KEY idx_collection_group (collection_group_id),
  ADD KEY idx_collection_status (status);

RENAME TABLE tag_translation TO collection_translation;
ALTER TABLE collection_translation
  CHANGE COLUMN tag_id collection_id BIGINT NOT NULL;
ALTER TABLE collection_translation
  DROP INDEX uk_tt,
  ADD UNIQUE KEY uk_ct (collection_id, locale);

-- 3. product_tag → product_collection
RENAME TABLE product_tag TO product_collection;
ALTER TABLE product_collection
  CHANGE COLUMN tag_id collection_id BIGINT NOT NULL;
ALTER TABLE product_collection
  DROP INDEX uk_ptag,
  DROP INDEX idx_ptag_tag,
  ADD UNIQUE KEY uk_pcol (product_id, collection_id),
  ADD KEY idx_pcol_collection (collection_id);
```

`huihao-mysql` 的 `@Table` 注解会基于实体类生成 DDL 校验，迁移脚本与实体注解需严格对应。Seed 数据无需调整（重命名不改 ID 与数据）。

## 7. 部署与回滚（BLK-002 修复）

### 7.1 部署顺序（Hard cutover）

1. **T-0 前**：MySQL 物理备份（mysqldump 全库或仅 catalog 5 张表 + operation_log + cache_invalidation_log）
2. **T-0**：停 backend（`bootRun` / 服务进程），前端构建产物暂不部署
3. **T-1**：执行 `V20260617_tag_to_collection.sql`（rename 表/列/索引）
4. **T-2**：清空 MQ `content.invalidated` 队列（避免旧 `tag_changed` 消息被新消费者漏掉；项目用 RabbitMQ，管理界面 purge 队列）
5. **T-3**：部署新 backend（含新实体/Service/Controller/MQ 字符串）
6. **T-4**：部署新前端构建产物
7. **T-5**：smoke test：`GET /api/admin/collection-groups`、`GET /api/admin/collections`、`GET /api/store/collections`、商品编辑「加入集合」section、商品筛选「集合」chip
8. **T-6**：旧 JetCache key `catalog:tags:*` 自然过期（TTL 600s），无需主动清

### 7.2 回滚预案

| 阶段 | 回滚动作 |
|---|---|
| T-1 后/T-3 前（migration 跑完，新 backend 未部署） | 反向 SQL：rename 回旧名（脚本预生成 `V20260617_rollback_tag_to_collection.sql`） |
| T-3 后/T-5 前（新 backend 部署，前端旧） | 回滚 backend 镜像到上一版；DB 已 rename，旧 backend 无法启动，**必须同时 restore DB backup** |
| T-5 后（前后端都已切换） | 一般不回滚；如必须回滚：停服务 → restore DB backup → 部署旧前后端镜像 |

**关键**：DDL rename 不可逆（除非用反向 SQL），所以 T-3 后回滚必须 restore DB backup。建议备份保留 7 天。

### 7.3 MQ 兼容性

- `content.invalidated` 队列：部署前 purge，避免旧 `tag_changed` 消息漏处理
- `cache_invalidation_log` 历史行 `event_type='tag_changed'` 保留，新写入 `collection_changed`；查询界面（发布中心监控页）需同时支持两种值（已有逻辑是 LIKE/等值查询，新值不影响旧数据展示）

## 8. 数据保留验收（BLK-003 修复）

迁移前后执行以下校验，全部通过方可继续部署：

```sql
-- 1. 5 张表 row count 前后比对（业务数据零丢失）
-- pre-migration:
SELECT 'tag_dimension' AS t, COUNT(*) AS cnt FROM tag_dimension WHERE deleted_at IS NULL
UNION ALL SELECT 'tag', COUNT(*) FROM tag WHERE deleted_at IS NULL
UNION ALL SELECT 'tag_translation', COUNT(*) FROM tag_translation
UNION ALL SELECT 'tag_dimension_translation', COUNT(*) FROM tag_dimension_translation
UNION ALL SELECT 'product_tag', COUNT(*) FROM product_tag;

-- post-migration（应与 pre 完全一致）:
SELECT 'collection_group' AS t, COUNT(*) AS cnt FROM collection_group WHERE deleted_at IS NULL
UNION ALL SELECT 'collection', COUNT(*) FROM collection WHERE deleted_at IS NULL
UNION ALL SELECT 'collection_translation', COUNT(*) FROM collection_translation
UNION ALL SELECT 'collection_group_translation', COUNT(*) FROM collection_group_translation
UNION ALL SELECT 'product_collection', COUNT(*) FROM product_collection;

-- 2. product-collection JOIN 完整性（取 10 个 product 验证 collectionIds 与原 tagIds 一致）
-- pre:  SELECT product_id, GROUP_CONCAT(tag_id ORDER BY tag_id) FROM product_tag GROUP BY product_id LIMIT 10;
-- post: SELECT product_id, GROUP_CONCAT(collection_id ORDER BY collection_id) FROM product_collection GROUP BY product_id LIMIT 10;
-- 两个结果集应字节级一致

-- 3. 索引唯一性验证
-- uk_ct(collection_id, locale)、uk_cgt(collection_group_id, locale)、uk_pcol(product_id, collection_id) 应存在且唯一
SHOW INDEX FROM collection_translation WHERE Key_name = 'uk_ct';
SHOW INDEX FROM collection_group_translation WHERE Key_name = 'uk_cgt';
SHOW INDEX FROM product_collection WHERE Key_name = 'uk_pcol';

-- 4. translation 附表行数比对（同 row count 逻辑，单独确认翻译行不丢）
```

### 8.1 自动化验收脚本

新增 `backend/src/test/java/com/dreamy/migration/TagToCollectionMigrationTest.java`（集成测试，@SpringBootTest + @ActiveProfiles("test")）：
- @BeforeAll 跑反向 SQL 准备旧表 + insert 样本数据
- @Test 跑 `V20260617_tag_to_collection.sql`
- @Test 断言 5 张表 row count 一致
- @Test 断言 product→collectionIds 映射一致
- @Test 断言 3 个唯一索引存在
- @AfterAll 清理

### 8.2 残留搜索（SUG-001 修复）

用 symbol-aware 搜索 + allowlist，避免 `style_tags` / `stag` / 文档历史误报：

```bash
# 后端 Java：搜 collection 域包外残留 Tag 类引用
rg --type java '\b(Tag|TagDimension|TagStatus|TagTranslation|TagDimensionTranslation|ProductTag)\b' \
  -g '!**/domain/collection/**' -g '!**/ProductCollection.java' \
  -g '!**/ProductCollectionRepository.java' -g '!**/ProductCollectionMapper.java'

# 后端字符串字面量：搜 "tag" / "tag_changed" / "tag_" 残留（排除 style_tags、i18n biz_type 注释、第三方）
rg '"tag(_changed|_dimension|_id|_translation)?"' backend/src/main/java/com/dreamy \
  -g '!**/style_tags*' -g '!**/i18n_ai_gateway*'

# 数据库 SQL：搜 tag 表名残留
rg '\b(tag_dimension|tag_translation|product_tag)\b' backend/src/main/resources \
  -g '!V20260616_i18n_ai_gateway.sql'  # 该文件仅注释，单独审查

# 前端：搜 Tag 类型引用残留
rg '\b(Tag|TagDimension|TagUpsert|TagTranslation|TagStatus|useTagsStore|tagIds|tag_id)\b' \
  frontend/portal-admin/src frontend/portal-store/src \
  -g '!**/style_tags*' -g '!*.min.js'
```

allowlist：
- `product.style_tags` JSON 列（商品自由文本风格标签，与 collection 实体无关）
- `V20260616_i18n_ai_gateway.sql` 的 `biz_type` 列注释（仅注释更新）
- `hhspec/changes/archive/` 下历史变更文档（归档不动）
- `operation_log.action` / `cache_invalidation_log.event_type` 历史行（数据库行，不在代码搜索范围）

## 9. 风险与对策

| 风险 | 对策 |
|---|---|
| `huihao-mysql` `@Table` 注解与 `@TableName` 双注解需同步改 | 实体类两处注解一起改；启动时 DDL 校验会立即暴露遗漏 |
| `ProductTagMapper` 原生 SQL `FROM product_tag pt`、`pt.tag_id` | 同步改表名/列名 |
| 缓存 key 变更导致旧 key 残留 | TTL 600s 自然过期，无需主动清 |
| MQ 消费端订阅 `tag_changed` | `InvalidatePathMapper` switch case + `ContentInvalidatedPublisher` 常量/分支全部同步；部署前 purge MQ 队列 |
| `cache_invalidation_log` / `operation_log` 历史行 | 保留不动（仅日志，新写入用新值） |
| 前端 i18n 文案 | 项目无 i18n locales 目录，文案硬编码在 .vue，直接改 |
| DDL rename 不可逆 | T-0 前必做 DB backup；预生成反向 SQL `V20260617_rollback_tag_to_collection.sql` |
| `UploadScope.TAG("tag")` 改名影响 presign 上传 | 旧 presign URL 仍在 CDN 可访问；新上传走 `scope=collection` |

## 10. 验收清单

### 10.1 功能验收

- [ ] 后端 bootRun 启动 + huihao-mysql DDL 校验通过（无 schema drift）
- [ ] `/api/admin/collection-groups` GET/POST/PUT/DELETE 联调通过
- [ ] `/api/admin/collections` GET/POST/PUT/DELETE 联调通过（含 dimension_id 过滤）
- [ ] `/api/store/collections?locale=es|fr` 翻译回退正常
- [ ] 商品创建/编辑 `collection_ids` 持久化 + 回读正常
- [ ] 商品列表 `?collection_id=` 筛选正常
- [ ] `shop_by_color` 推荐位 `collection_id` 必填校验 + 商品返回正常
- [ ] 商品编辑页「加入集合」section 独立呈现，多选 chip 正常
- [ ] 商品筛选「集合」chip 正常
- [ ] `tests/ui-verification/admin-prototype-alignment.verify.mjs` 文案断言更新后通过

### 10.2 数据验收（BLK-003）

- [ ] 5 张表 row count 前后一致（脚本见 §8）
- [ ] product→collectionIds 映射一致（10 条样本 JOIN 验证）
- [ ] 3 个唯一索引存在（uk_ct / uk_cgt / uk_pcol）
- [ ] `TagToCollectionMigrationTest` 集成测试通过

### 10.3 残留验收（SUG-001）

- [ ] 后端 collection 域包外无 Tag 类引用（symbol-aware 搜索 + allowlist）
- [ ] 后端无 `"tag_changed"` / `"tag_id"` / `"tag_dimension"` 字面量残留（allowlist 内的除外）
- [ ] 前端无 `Tag` / `useTagsStore` / `tagIds` / `tag_id` 残留（allowlist 内的除外）
- [ ] DB SQL 无 `tag_dimension` / `tag_translation` / `product_tag` 表名残留（allowlist 内的除外）

## 11. 工作量估算（SUG-002 修订）

| 阶段 | 文件数 | 估时 |
|---|---|---|
| 后端实体/Service/Controller/DTO/Enum/ErrorCode/Cache/MQ 改名 | ~25 文件 | 5-7 小时 |
| DB migration + 反向 SQL + 集成测试 | 3 文件 | 2-3 小时 |
| 前端 store/api/types/3 个 view + UI 重构 | ~6 文件 | 2-3 小时 |
| Spec 文档同步 | ~15 文件 | 3-4 小时 |
| 联调 + 部署演练 + 残留搜索 | - | 2-3 小时 |
| **合计** | **~50 文件** | **14-20 小时（1.5-2.5 工作日）** |

## 12. 实施进度

### 12.1 已完成（代码层）

- ✅ 后端 `domain.tag` 整包迁移到 `domain.collection`（15 文件 git mv + 内容重写）
- ✅ `ProductTag` → `ProductCollection`（3 文件 + ProductRepository EXISTS SQL）
- ✅ DTO/Enum/ErrorCode 改名（AdminCatalogDtos / TranslationDtos / StoreCollectionGroup / StoreProductDetail / AdminProductDetail / AdminProductUpsert / PresignDtos / CollectionStatus / RecommendationBlock / UploadScope / CatalogErrorCode）
- ✅ Controller 改名 + 路径切换（AdminCollectionController / StoreCategoryController /api/store/collections / StoreProductController collection_id）
- ✅ Cache Family.TAGS → COLLECTIONS + MQ 字符串（ContentInvalidatedPublisher / InvalidatePathMapper / AdminCacheService）+ CatalogSeedInitializer 表名列表
- ✅ DB migration V20260617_tag_to_collection.sql + 反向回滚脚本（存 change 目录）
- ✅ 前端 store/api/types 改名（stores/collections.ts / api/catalog.ts / api/types.ts）
- ✅ Categories.vue Tab 重构（tags → collections，文案「自定义标签」→「集合」）
- ✅ ProductEdit.vue UI 重构（form.tagIds → collectionIds，独立「加入集合」section，位于版型属性后、媒体素材前）
- ✅ Products.vue 筛选重构（moreFilters.tagIds → collectionIds，标题「主题标签」→「主题集合」）
- ✅ ui-verification 文案断言更新
- ✅ portal-store 前端同步（catalog-server.ts / store-types.ts / recommendation-rail.tsx / page.tsx / collection-page.tsx）
- ✅ 后端测试同步（CollectionAdminServiceTest / AdminProductServiceTest / RecommendationServiceTest）
- ✅ i18n 消息文案同步（catalog-messages_{en,zh,es,fr}.properties + application.yml 白名单 /api/store/collections）
- ✅ 残留搜索验证通过（后端 com.dreamy.domain.tag 零残留；前端 useTagsStore/TagDimension/TagStatus/tagIds 零残留）

### 12.2 Deferred（归档后独立 PR）

- ⏳ Spec 文档同步（hhspec/specs/ 下 ~30 文件，8300+ 行）
  - architecture/{er-diagram,api-contracts/catalog-api.openapi,state-machine,business-flow}.yml
  - design/data/catalog-ddl.sql
  - design/catalog/catalog-{data,api,frontend,test}-detail.md + task-allocation.yml + catalog-ui-test-spec.yml
  - design/admin-prototype-alignment/categories-frontend-detail.md
  - design/{Tag,TagDimension,CustomTag}/ 目录归并
  - **原因**：spec 文档量大，且 "Tag" 一词在 spec 中可能指代多个概念（style_tags 护理标签、Analytics 流量来源标签等），需逐处人工审查，不适合机械批量替换。代码层已干净，spec 同步作为独立 PR 处理更安全。



| 阶段 | 文件数 | 估时 |
|---|---|---|
| 后端实体/Service/Controller/DTO/Enum/ErrorCode/Cache/MQ 改名 | ~25 文件 | 5-7 小时 |
| DB migration + 反向 SQL + 集成测试 | 3 文件 | 2-3 小时 |
| 前端 store/api/types/3 个 view + UI 重构 | ~6 文件 | 2-3 小时 |
| Spec 文档同步 | ~15 文件 | 3-4 小时 |
| 联调 + 部署演练 + 残留搜索 | - | 2-3 小时 |
| **合计** | **~50 文件** | **14-20 小时（1.5-2.5 工作日）** |
