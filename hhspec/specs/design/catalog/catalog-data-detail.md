# catalog 数据层详细设计（L2）

> 当前契约覆盖声明：`catalog-contract-status.md` 覆盖本文早期 TagDimension/Tag、
> tag_* 表和 `cover` 字段描述。当前物理表为 collection_group/collection/
> collection_translation/product_collection，集合封面不落库。

> 角色: l2_data_designer ｜ change: portal-api-integration ｜ domain: catalog
> 方法论：Entity Design / Repository 方法(RM-CAT) / DTO 映射(MAP-CAT) / 索引(IDX-CAT，含 FULLTEXT) / 事务边界(TX-CAT) / 数据校验(CV-CAT) / 领域事件与 MQ 消费(EVT-CAT) / 缓存设计(CACHE-CAT) / 完整 DDL。
> 来源权威：catalog-api.openapi.yml + catalog-contract-status.md + data-flow.md（缓存矩阵/MQ 拓扑/FLOW-P01/P02/P03/P17/P19）+ 后端实现。

## 1. Entity Design（基类选型 / 逻辑删除 / 审计字段）

### 1.1 基类与通用约定

- **基类**：全部实体继承 `huihao.mysql.auditable.LongAuditableEntity`（与 identity User 同款）——提供 `id BIGINT AUTO_INCREMENT 主键` + `created_at` / `updated_at DATETIME(3)` 审计列（CommonDBConst.ID/CREATED_AT/UPDATED_AT）。决策 12：Long 自增主键、标准增表无迁移。
- **注解范式**（CP-015）：`@Table(indexes=...)` + `@TableName(value, autoResultMap=true)` + `@Column(name=<EntityDBConst 常量>, definition=...)`；每实体配 `{Entity}DBConst extends CommonDBConst`（置于 `com.dreamy.catalog.domain.{聚合根}/consts/`）。
- **逻辑删除**：**不启用**（huihao 基类无 @TableLogic；identity 样板同口径）。state-machine 中的 `deleted` 终态＝**物理删除**，由删除端点的引用守卫先行保证安全（409502/409503/409506/409507/409509）；商品删除不影响订单（OrderLine 为快照，读路径不跨域 join）。
- **枚举落地**（CP-003）：status/kind/visibility/type/locale 等枚举列用 `VARCHAR + Java enum` 双保险（与契约字符串取值一致，便于 FULLTEXT/种子数据直读；不沿用 identity 的 tinyint 映射，理由：本域枚举值直接透出契约且消费端可读）。
- **时间**：DATETIME(3) UTC ↔ LocalDateTime ↔ ISO8601（CP-014）。
- **包结构**：`com.dreamy.catalog/`（单模块多 domain，与 identity 平级）：`domain/{product,category,attribute,tag}/{entity,repository,service,consts}` + `controller/` + `dto/` + `mq/`（消费者）+ `config/`。

### 1.2 实体清单（10 实体 + 5 translation 附表 + 1 关联表 = 16 张表）

| 实体 | 表名 | 要点 |
|---|---|---|
| Category | category | parent_id 自关联三层树；level 1..3；attribute_set_id（根必填）；attr_overrides JSON delta；sort |
| CategoryTranslation | category_translation | uk(category_id, locale)；locale ∈ {es,fr} |
| AttributeDef | attribute_def | key 唯一；type enum；options JSON（仅 select/multiselect） |
| AttributeDefTranslation | attribute_def_translation | uk(attribute_def_id, locale)；options 译文与主表等长 |
| AttributeSet | attribute_set | label |
| AttributeSetItem | attribute_set_item | uk(attribute_set_id, attribute_id)；visibility enum 三态 |
| CollectionGroup | collection_group | name/description（由旧 TagDimension 重命名） |
| CollectionGroupTranslation | collection_group_translation | uk(collection_group_id, locale) |
| Collection | collection | collection_group_id；name；status enum；无 cover（封面为商品图拼贴） |
| CollectionTranslation | collection_translation | uk(collection_id, locale) |
| Product | product | 全量字段见 DDL；slug 唯一；status enum；**冗余回写列 sales_30d / sales_refreshed_at / rating_avg / rating_count**（决策 29 + FLOW-P14，L1 留 L2 定稿——定稿为 Product 冗余列方案，不建独立聚合表） |
| ProductTranslation | product_translation | uk(product_id, locale)；name/subtitle/description/seo_title/seo_description |
| ProductImage | product_image | kind enum 四类；sort=0 主图；color_name（swatch） |
| Sku | sku | sku_code 全局唯一；color×size 矩阵；stock + version 乐观锁（@Version，BE-DIM-4 扣减防超卖；本域编辑防丢失 409508） |
| SizeChartRow | size_chart_row | us 必填 + uk/au + 四维体征数值 |
| ProductCollection（关联） | product_collection | uk(product_id, collection_id)；nm 关系（AdminProductUpsert.collection_ids 落点）；无审计需求仍用基类（成本可忽略，保持范式统一） |

冗余列写权限约束：sales_30d/sales_refreshed_at/rating_avg/rating_count 仅 EVT-CAT-001/002 消费者与 EVT-CAT-003 定时任务可写；管理端整单覆盖（TX-CAT-002）不得触碰。

## 2. Repository 方法（RM-CAT）

### CategoryRepository
- RM-CAT-001 `listAll() -> List<Category>` —— ORDER BY sort, id（树组装；E-CAT-06/15）
- RM-CAT-002 `findById(id) -> Category?`
- RM-CAT-003 `insert(Category)` / RM-CAT-004 `update(Category)` / RM-CAT-005 `deleteById(id)`
- RM-CAT-006 `countByParentId(parentId) -> int` —— 删除 guard②（has_children）
- RM-CAT-007 `countByAttributeSetId(setId) -> int` —— 409503 guard（AttributeSet 删除）
- RM-CAT-008 `maxSortOfSiblings(parentId?) -> int` —— 新增分类缺省排序

### CategoryTranslationRepository
- RM-CAT-010 `listByCategoryIds(ids) -> List` —— 批查防 N+1
- RM-CAT-011 `replaceAll(categoryId, rows[])` —— DELETE+批量 INSERT（整单覆盖）
- RM-CAT-012 `deleteByCategoryId(categoryId)`

### AttributeDefRepository
- RM-CAT-020 `listAll()` / RM-CAT-021 `findById(id)` / RM-CAT-022 `existsByKey(key) -> bool`（uk_attribute_def_key 兜底）
- RM-CAT-023 `insert` / RM-CAT-024 `update` / RM-CAT-025 `deleteById`
- RM-CAT-026 `listByIds(ids)` —— attr_overrides key 与属性集行校验

### AttributeDefTranslationRepository
- RM-CAT-030 `listByDefIds(ids)` / RM-CAT-031 `replaceAll(defId, rows[])` / RM-CAT-032 `deleteByDefId(defId)`

### AttributeSetRepository / AttributeSetItemRepository
- RM-CAT-040 `listAll()` / RM-CAT-041 `findById(id)` / RM-CAT-042 `insert` / RM-CAT-043 `updateLabel(id, label)` / RM-CAT-044 `deleteById`
- RM-CAT-045 `listItemsBySetIds(setIds) -> List<AttributeSetItem>` —— 矩阵批查
- RM-CAT-046 `replaceItems(setId, items[])` —— 事务内 DELETE+批量 INSERT（TX-CAT-010 全量重写）
- RM-CAT-047 `countItemsByAttributeId(attributeId) -> int` —— 409507 guard

### CollectionGroupRepository / CollectionGroupTranslationRepository
- RM-CAT-050 `listAll()` / RM-CAT-051 `findById(id)` / RM-CAT-052 `insert` / RM-CAT-053 `update` / RM-CAT-054 `deleteById`
- RM-CAT-055 `listTranslationsByGroupIds(ids)` / RM-CAT-056 `replaceTranslations(groupId, rows[])`

### CollectionRepository / CollectionTranslationRepository
- RM-CAT-060 `listByGroupId(groupId?) -> List<Collection>` —— idx_collection_group
- RM-CAT-061 `findById(id)` / RM-CAT-062 `listByIds(ids)`（collection_ids 存在性校验）
- RM-CAT-063 `listEnabled(groupId?)` —— 消费端（status=enabled）
- RM-CAT-064 `searchEnabledByName(qLike, locale) -> List<collectionId>` —— E-CAT-02 STEP-CAT-04（主表 name LIKE + 附表 label LIKE UNION）
- RM-CAT-065 `countByGroupId(groupId) -> int` —— 409506 guard
- RM-CAT-066 `insert` / RM-CAT-067 `update` / RM-CAT-068 `deleteById`
- RM-CAT-069 `listTranslationsByCollectionIds(ids)` / RM-CAT-070 `replaceTranslations(collectionId, rows[])`

### ProductRepository
- RM-CAT-080 `findBySlugPublished(slug) -> Product?` —— uk_product_slug 点查（FLOW-P01 热路径）
- RM-CAT-081 `findById(id)` / RM-CAT-082 `existsBySlugExcept(slug, exceptId?) -> bool`（409501）
- RM-CAT-083 `pageStoreList(filter{categoryIds[], collectionId?, color?, size?, priceMin?, priceMax?, sort}, page) -> Page<Product>` —— status=published；EXISTS 子查询挂 product_collection/sku（IDX-CAT-003/006/007）
- RM-CAT-084 `fulltextSearchMain(q, page) -> List<id+score>` —— `MATCH(name, subtitle) AGAINST(? IN NATURAL LANGUAGE MODE)` AND status=published（IDX-CAT-004）
- RM-CAT-085 `pageAdminList(filter{status?, categoryIds[], search?}, page) -> Page<Product>` —— name/style_no LIKE
- RM-CAT-086 `insert` / RM-CAT-087 `update`（冗余列不在 SET 列表）/ RM-CAT-088 `deleteById`
- RM-CAT-089 `updateStatus(id, status)`（TX-CAT-004）/ RM-CAT-090 `patchFlags(id, partial{isNew?,isBest?,recommend?,sort?})`
- RM-CAT-091 `listRecoNewArrivals(limit)` / RM-CAT-092 `listRecoBestSellers(limit)`（sales_30d DESC，全 0 回退 recommend）/ RM-CAT-093 `listRecoByCollection(collectionId, limit)` / RM-CAT-094 `listRecoSimilar(categoryId, priceLow, priceHigh, exceptId, limit)` / RM-CAT-095 `listRecoCrossCategory(rootCategoryId, exceptLeafCategoryId, limit)` —— 决策 29 五规则
- RM-CAT-096 `countByCategoryIdsPublished(ids) -> Map<categoryId,int>` / RM-CAT-097 `countByCategoryIdsAll(ids) -> Map`（product_count 两口径）
- RM-CAT-098 `updateSales30d(productId, sales, refreshedAt)`（EVT-CAT-001/003 专用）/ RM-CAT-099 `updateRating(productId, avg, count)`（EVT-CAT-002 专用）

### ProductTranslationRepository
- RM-CAT-100 `listByProductIds(ids, locale?)` —— 批查（FLOW-P01 STEP 翻译合并）
- RM-CAT-101 `replaceAll(productId, rows[])` / RM-CAT-102 `deleteByProductId(productId)`
- RM-CAT-103 `fulltextSearch(q, locale, page) -> List<productId+score>` —— `MATCH(name, subtitle) AGAINST(?)` AND locale=?（IDX-CAT-010）

### ProductImageRepository / SkuRepository / SizeChartRowRepository / ProductCollectionRepository
- RM-CAT-110 `listByProductId(productId)`（image，ORDER BY sort）/ RM-CAT-111 `listByProductIds(ids)`（卡片主图/swatch 批查）/ RM-CAT-112 `replaceAll(productId, rows[])` / RM-CAT-113 `deleteByProductId`
- RM-CAT-120 `listByProductId(productId)`（sku）/ RM-CAT-121 `existsBySkuCodes(codes[], exceptProductId?) -> List<String>`（409504）
- RM-CAT-122 `casUpdate(sku) -> int` —— `UPDATE ... SET ..., version=version+1 WHERE id=? AND version=?`（CP-016：`setSql("version = version + 1")` + eq(version)，affected=0 → 409508）
- RM-CAT-123 `insertBatch(skus[])`（version=0）/ RM-CAT-124 `deleteByIds(ids)` / RM-CAT-125 `sumStockByProductIds(ids) -> Map`（stock_total 派生）
- RM-CAT-130 `listByProductId(productId)`（size_chart，ORDER BY bust ASC——E-CAT-05 区间匹配前置序）/ RM-CAT-131 `replaceAll(productId, rows[])` / RM-CAT-132 `deleteByProductId`
- RM-CAT-140 `listCollectionIdsByProductId(productId)` / RM-CAT-141 `listByProductIds(ids)` / RM-CAT-142 `replaceAll(productId, collectionIds[])` / RM-CAT-143 `deleteByProductId` / RM-CAT-144 `deleteByCollectionId(collectionId)` / RM-CAT-145 `countByCollections(publishedOnly)` / RM-CAT-146 `listProductIdsByCollectionId(collectionId, limit)`

## 3. DTO ↔ Entity 映射（MAP-CAT）

- MAP-CAT-001 Product→StoreProductCard：id/slug/name/subtitle/price/compare_at/multi_currency_prices/installment/is_new/is_best + 派生 image_url（image kind=gallery sort=0）/swatches（kind=swatch → {color_name,url}）/rating_avg/rating_count（冗余列）；**不暴露** sort/recommend/sales_30d/状态字段
- MAP-CAT-002 Product+子资源→StoreProductDetail：全量版型/模特/护理字段 + images[]/skus[]（含 stock/version——version 暴露供下单 CAS 与购物车展示，无敏感性）/size_chart[]/collections[]（id/collection_group_id/name）+ category_name 派生 + rating 冗余列；locale 解析后输出扁平文案（决策 13 回退在 Service 层完成）
- MAP-CAT-003 Product→AdminProductListItem：+style_no/category_name/status/recommend/sort/stock_total（SUM(sku.stock) 派生）/image_url
- MAP-CAT-004 Product+子资源→AdminProductDetail：AdminProductUpsert 全字段回显 + id/created_at/updated_at + translations 三语 tab 原样（不回退合并）
- MAP-CAT-005 Category→StoreCategoryNode：id/name(locale 解析)/parent_id/level/sort/product_count(published 口径)/children 递归；**不暴露** attribute_set_id/attr_overrides
- MAP-CAT-006 Category→AdminCategoryNode：全字段 + product_count(全量口径) + translations + children
- MAP-CAT-007 CollectionGroup+Collection→StoreCollectionGroup：分组 id/name(locale)/description + collections[]{id,name(locale),product_count(published 口径)}；仅 status=enabled，消费端不返回 cover
- MAP-CAT-008 AttributeSet+Items→AttributeSet DTO：label + items[]{attribute_id,visibility} + category_count 派生
- MAP-CAT-009 AttributeDef→AttributeDef DTO：key/label/type/options + translations
- MAP-CAT-010 Collection→Collection DTO（admin）：collection_group_id/name/status/product_count/fallback_cover_urls（前 4 张商品主图）+ translations
- MAP-CAT-011 SizeChartRow→recommended_row：原样行；SizeRecommendationResponse.dimension_notes 由 Service 区间匹配产出（非实体映射）
- MAP-CAT-012 枚举：Java enum ↔ VARCHAR 契约字符串（draft/published、gallery/lifestyle/video/swatch、visible/optional/hidden、select/multiselect/text/toggle、enabled/disabled、es/fr）
- MAP-CAT-013 JSON 列（attr_overrides/options/embellishments/occasions/style_tags/multi_currency_prices）：MyBatis-Plus JacksonTypeHandler（autoResultMap=true）↔ DTO 原生对象/数组
- MAP-CAT-014 时间字段 LocalDateTime(UTC) ↔ ISO8601 字符串；出参 snake_case（前端 client 转 camelCase，CP-001）

## 4. 索引设计（IDX-CAT）

| ID | 表 | 索引 | 支撑路径 |
|---|---|---|---|
| IDX-CAT-001 | product | `UNIQUE uk_product_slug(slug)` | E-CAT-04 PDP 点查 / 409501 |
| IDX-CAT-002 | product | `idx_product_status_category(status, category_id)` | 列表筛选（E-CAT-01/08）|
| IDX-CAT-003 | product | `idx_product_status_created(status, created_at)` | new_arrivals / newest 排序 |
| IDX-CAT-004 | product | `FULLTEXT ft_product_search(name, subtitle) WITH PARSER ngram` | **决策 17 全文搜索（必含）** |
| IDX-CAT-005 | product | `idx_product_sales(status, sales_30d)` | best_sellers |
| IDX-CAT-006 | sku | `UNIQUE uk_sku_code(sku_code)` | 409504 / 下单行点查 |
| IDX-CAT-007 | sku | `idx_sku_product(product_id)` | 子资源批查 / color-size EXISTS |
| IDX-CAT-008 | product_image | `idx_image_product_sort(product_id, sort)` | 图廊/主图 |
| IDX-CAT-009 | size_chart_row | `idx_scr_product(product_id)` | 尺码表/推荐 |
| IDX-CAT-010 | product_translation | `UNIQUE uk_pt(product_id, locale)` + `FULLTEXT ft_pt_search(name, subtitle) WITH PARSER ngram` | 翻译合并 + ES/FR 搜索（**FULLTEXT 必含**）|
| IDX-CAT-011 | category | `idx_category_parent(parent_id)` + `idx_category_attrset(attribute_set_id)` | 树组装 / 409503 计数 |
| IDX-CAT-012 | category_translation | `UNIQUE uk_ct(category_id, locale)` | 翻译合并 |
| IDX-CAT-013 | attribute_def | `UNIQUE uk_attribute_def_key(\`key\`)` | key 唯一 |
| IDX-CAT-014 | attribute_def_translation | `UNIQUE uk_adt(attribute_def_id, locale)` | 翻译合并 |
| IDX-CAT-015 | attribute_set_item | `UNIQUE uk_asi(attribute_set_id, attribute_id)` + `idx_asi_attribute(attribute_id)` | 矩阵幂等 / 409507 计数 |
| IDX-CAT-016 | collection | `idx_collection_group(collection_group_id)` + `idx_collection_status(status)` | 分组筛选 / enabled 过滤 / 409506 计数 |
| IDX-CAT-017 | collection_translation | `UNIQUE uk_ct(collection_id, locale)` | 翻译合并 |
| IDX-CAT-018 | collection_group_translation | `UNIQUE uk_cgt(collection_group_id, locale)` | 翻译合并 |
| IDX-CAT-019 | product_collection | `UNIQUE uk_pcol(product_id, collection_id)` + `idx_pcol_collection(collection_id)` | nm 幂等 / 集合反查 |

查询优化补充：
- NP-CAT-001 防 N+1：列表卡片装配的 image/swatch/translation/rating 一律 productIds IN 批查（RM-CAT-111/100）
- NP-CAT-002 分类树/集合分组 product_count 用单条 GROUP BY 聚合（RM-CAT-096/097/145），禁止逐节点 COUNT
- QP-CAT-001 store 列表 LIMIT/OFFSET 在千级 SKU 下可接受；total 走 COUNT 同条件（MyBatis-Plus Page）
- QP-CAT-002 搜索合并去重在 Service 内存完成（三路 id+score，千级量安全），不做 SQL 大 UNION 排序

## 5. 事务边界（TX-CAT）

| ID | 端点/流程 | 边界与回滚语义 |
|---|---|---|
| TX-CAT-001 | E-CAT-09 创建商品 | 单事务：product + image/sku/size_chart/product_collection/translation 批插 + operation_log；唯一索引冲突（slug/sku_code）映射 409501/409504 回滚；**缓存失效与 MQ publish 在事务提交后**（CP-031；MQ 失败不回滚，TTL 兜底） |
| TX-CAT-002 | E-CAT-11 编辑商品 | 单事务整单覆盖；SKU CAS（RM-CAT-122）任一 affected=0 → VersionConflict 全量回滚 → 409508；冗余列不参与 SET |
| TX-CAT-003 | E-CAT-12 删除商品 | 单事务：guard(409509) → 六表级联物理删除 + operation_log |
| TX-CAT-004 | E-CAT-13 上下架 | 单事务：status UPDATE + operation_log；幂等短路不开事务 |
| TX-CAT-005 | E-CAT-14 flags | 单事务：partial UPDATE + operation_log |
| TX-CAT-006/007/008 | 分类创建/编辑/删除 | 单事务：主表 + translation 整单覆盖 + operation_log；删除前 guard 409502（商品数/子分类）在事务内复查（防并发挂商品） |
| TX-CAT-009/010/011 | 属性集创建/编辑/删除 | 单事务；TX-CAT-010 矩阵 DELETE+INSERT 全量重写原子（同 identity TX-004 范式）；删除 guard 409503 事务内复查 |
| TX-CAT-012/013/014 | 属性定义创建/编辑/删除 | 单事务；翻译 options 等长校验（CV-CAT-007）在写前；删除 guard 409507 事务内复查 |
| TX-CAT-015/016/017 | 集合分组创建/编辑/删除 | 单事务；删除 guard 409506 事务内复查 |
| TX-CAT-018/019/020 | 集合创建/编辑/删除 | 单事务；TX-CAT-020 先清 product_collection 与译文后物理删除 |
| TX-CAT-021 | EVT-CAT-001 销量回写 | 消费者内单事务：按 product_id 集合逐一 RM-CAT-098；天然可重入（覆盖写幂等） |
| TX-CAT-022 | EVT-CAT-002 评分回写 | 同上，RM-CAT-099 覆盖写 |
| EC-CAT-001 | 乐观锁冲突策略 | 本域**编辑场景不重试**（与 trading 扣减重试 ×3 不同）：编辑冲突即用户层冲突，直接 409508 让用户刷新（error-strategy 前端约定"提示刷新重载表单"） |
| EC-CAT-002 | 缓存失效失败 | 不回滚 DB；记告警，JetCache TTL（60~600s）+ CDN s-maxage 自然过期收敛（identity EC-002 同口径） |

## 6. 数据校验与引用完整性（CV-CAT）

- CV-CAT-001 枚举值落库前校验 ∈ 取值集（Java enum 反序列化失败 → 422501；DB 层 VARCHAR 不加 CHECK，应用层单保险 + 契约测试兜底）
- CV-CAT-002 长度上限与 er-diagram/契约一致（见 DDL 列定义；超长 → 422501，DB 截断禁用 STRICT_TRANS_TABLES 保障）
- CV-CAT-003 数值域：price/compare_at ≥0、stock ≥0、sort ≥0、lead_time_days ≥1、level 1..3、size_chart 数值 ≥0、rating_avg 0..5
- CV-CAT-004 compare_at ≥ price 应用层校验（V-CAT-027）；DB 不建跨列 CHECK（保持 huihao 注解建表能力内）
- CV-CAT-005 逻辑外键（CP-010 无物理 FK）：写入前校验引用存在——product.category_id（V-CAT-025）、product_collection.collection_id（V-CAT-034）、category.parent_id/attribute_set_id（V-CAT-044/045）、attribute_set_item.attribute_id（V-CAT-050）、collection.collection_group_id（V-CAT-063）
- CV-CAT-006 删除引用守卫：category←product/子分类（409502）、attribute_set←category（409503）、attribute_def←attribute_set_item/product_attribute_value（409507）、collection_group←collection（409506）；collection 删除无守卫但级联清 product_collection
- CV-CAT-007 attribute_def_translation.options 与主表 options 等长（V-CAT-058）；主表 options 变更（增删项）时 Service 同步对齐译文数组（缺位补 null 占位，等长不破坏）
- CV-CAT-008 attr_overrides 仅含生效属性集内的 key 且 value ∈ 三态（V-CAT-047）
- CV-CAT-009 translation locale 仅 {es,fr}（EN 存主表，决策 13）；uk(entity_id, locale) 防重
- CV-CAT-010 product_image 主图不变量：kind=gallery 且 sort=0 至多一张（V-CAT-031 应用层；整单覆盖天然维护）
- CV-CAT-011 sku (product_id, color, size) 组合提交集内唯一（V-CAT-032）；DB 以 uk_sku_code 全局唯一兜底

## 7. 缓存设计（CACHE-CAT，JetCache 两级 Caffeine+Redis，对齐 data-flow 缓存矩阵）

| ID | key 模板 | TTL | 装载点 | 失效触发者（@CacheInvalidate + MQ content.invalidated） |
|---|---|---|---|---|
| CACHE-CAT-001 | `catalog:products:{filtersHash}:{locale}` | 300s | E-CAT-01 | 商品创建/编辑/删除/上下架/flags（TX-CAT-001~005）、集合写（间接：collection 筛选）、分类写 |
| CACHE-CAT-002 | `catalog:product:{slug}:{locale}` | 300s（null 值 60s） | E-CAT-04 | 商品编辑/删除/上下架（含 SKU/尺码表/翻译变更；旧 slug 一并失效） |
| CACHE-CAT-003 | `catalog:search:{qNorm}:{locale}:{page}` | 60s | E-CAT-02 | **TTL 自然过期**（决策 17 短 TTL 兜底，无主动失效） |
| CACHE-CAT-004 | `catalog:reco:{block}:{pid\|cid\|-}:{locale}` | 300s | E-CAT-03 | 商品写/flags/上下架 + **order.paid 销量回写**（EVT-CAT-001 末步失效）+ 集合写 |
| CACHE-CAT-005 | `catalog:categories:{locale}` | 600s | E-CAT-06 | 分类创建/编辑/删除；商品增删/上下架（product_count 变化） |
| CACHE-CAT-006 | `catalog:collections:{group\|all}:{locale}` | 600s | E-CAT-07 | 集合/分组写；商品 collection_ids 变更、上下架（product_count 变化） |

- 穿透保护（BE-DIM-8）：`cacheNullValue=true`，null 短 TTL 60s（E-CAT-04 不存在 slug 不反复打穿源库）
- key 一律含 locale 维度（决策 13）；**不含 currency**（决策 14 L1 定夺：仅 USD 基准价，换算放客户端）
- 模式失效：JetCache `@CacheInvalidate` 按前缀批量失效（remote Redis SCAN+DEL 封装 / 本地 Caffeine invalidateAll by prefix）；admin 端点一律不缓存
- CDN 层：E-CAT-01/03/04/06/07 响应 `Cache-Control: s-maxage={同 TTL}`；E-CAT-02 不缓存；秒级失效由 MQ 消费者 revalidatePath + Cloudflare purge 完成（FLOW-P03，消费者归 marketing/基建侧 q.invalidate，本域只负责 publish 事件）

## 8. 领域事件与 MQ 消费（EVT-CAT，RabbitMQ topic exchange `dreamy.events`）

### 8.1 本域发布

| 事件 | routing key | 触发 | payload |
|---|---|---|---|
| 内容失效 | `content.invalidated` | TX-CAT-001/002/003/004/005/006/007/008/016/017/018/019/020 提交后 | `{event_id(UUID), type: product_created\|product_updated\|product_status_changed\|product_flags_changed\|category_changed\|collection_changed, slug?, old_slug?, locales:[en,es,fr], occurred_at}` |

消费者 `q.invalidate`（FLOW-P03，基建侧）：Next.js `POST /api/revalidate`（按 type 映射路径：PDP `/product/{slug}`、列表 `/wedding-dresses` 等聚合页、首页）×3 locale 路径 + Cloudflare purge API；nack ×3 指数退避 → `dreamy.dlq`。

### 8.2 本域消费（反向依赖回写，读路径不跨域 join）

| ID | 队列 | 绑定 | 逻辑 |
|---|---|---|---|
| EVT-CAT-001 | `q.catalog.sales` | `order.paid` | ① event_id 幂等（`processed_event` 表 INSERT 唯一索引冲突即跳过，与 trading 共表，error-strategy L2 要求 3）② 取 payload.lines[].product_id 去重 ③ 经 **trading 领域服务接口**（进程内直调，决策 3）`tradingQueryPort.sumPaidQty(productId, now-30d)` 重算 30 天滚动销量 ④ RM-CAT-098 覆盖写 sales_30d/sales_refreshed_at（TX-CAT-021）⑤ @CacheInvalidate `catalog:reco:*` ⑥ 失败 nack → 重试 ×3（指数退避 1s/4s/16s，延迟队列 TTL 实现）→ `dreamy.dlq` 告警人工重放 |
| EVT-CAT-002 | `q.catalog.rating` | `review.moderated` | ① event_id 幂等（同上）② 经 **review 领域服务接口** `reviewQueryPort.approvedRatingSummary(product_id)` 取 {avg, count} ③ RM-CAT-099 覆盖写 rating_avg/rating_count（TX-CAT-022）④ @CacheInvalidate `catalog:product:{slug}:*` + `catalog:products:*` + `catalog:reco:*` ⑤ 重试/死信同上 |
| EVT-CAT-003 | @Scheduled 每日 04:00 | —（非 MQ） | 30 天滚动窗口自然衰减补偿：`SELECT DISTINCT product_id FROM`（trading 服务接口取近 31 天有售商品 ∪ sales_30d>0 商品）逐一重算回写；防止无新订单时窗口不滑动 |

队列参数（error-strategy L2 要求 3 本域落点）：`q.catalog.sales` / `q.catalog.rating` —— durable、prefetch=8、重试经 `dreamy.retry.{queue}`（x-message-ttl 阶梯 + DLX 回投）、超限路由 `dreamy.dlq`（x-dead-letter-exchange）；消费幂等双保险=event_id 去重 + 覆盖写可重入。

## 9. 完整 DDL（MySQL 8.0，utf8mb4_0900_ai_ci，InnoDB；与 huihao-mysql 注解建表等价的权威 SQL）

```sql
-- 1. category 三层分类树
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

-- 2. category_translation
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

-- 3. attribute_def 属性字典
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

-- 4. attribute_def_translation
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

-- 5. attribute_set 属性集
CREATE TABLE attribute_set (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  label      VARCHAR(64) NOT NULL COMMENT '属性集名称',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='属性集';

-- 6. attribute_set_item 可见性矩阵
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

-- 7. collection_group（旧 tag_dimension 重命名）
CREATE TABLE collection_group (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  name        VARCHAR(64)  NOT NULL COMMENT '维度名(EN 基准)',
  description VARCHAR(255) NULL COMMENT '维度说明',
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='集合分组';

-- 8. collection_group_translation
CREATE TABLE collection_group_translation (
  id               BIGINT      NOT NULL AUTO_INCREMENT,
  collection_group_id BIGINT   NOT NULL,
  locale           VARCHAR(8)  NOT NULL COMMENT 'es|fr',
  name             VARCHAR(64) NULL,
  created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_cgt (collection_group_id, locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='集合分组多语言附表';

-- 9. collection（旧 tag 重命名；不再持久化 cover）
CREATE TABLE collection (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  collection_group_id BIGINT NOT NULL COMMENT '逻辑外键 collection_group.id',
  name         VARCHAR(64)  NOT NULL COMMENT '集合名(EN 基准)',
  status       VARCHAR(16)  NOT NULL DEFAULT 'enabled' COMMENT 'enabled|disabled',
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_collection_group (collection_group_id),
  KEY idx_collection_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='营销集合';

-- 10. collection_translation
CREATE TABLE collection_translation (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  collection_id BIGINT  NOT NULL,
  locale     VARCHAR(8)  NOT NULL COMMENT 'es|fr',
  label      VARCHAR(64) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ct (collection_id, locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='集合多语言附表';

-- 11. product 商品主档
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
  style_tags            JSON          NULL COMMENT '风格标签自由文本',
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

-- 12. product_translation
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

-- 13. product_image
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

-- 14. sku
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

-- 15. size_chart_row
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

-- 16. product_collection（Product-Collection nm 关系，AdminProductUpsert.collection_ids 落点）
CREATE TABLE product_collection (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  product_id    BIGINT      NOT NULL,
  collection_id BIGINT      NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_pcol (product_id, collection_id),
  KEY idx_pcol_collection (collection_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品-集合挂载';
```

> 备注：①FULLTEXT ngram 依赖 MySQL 内置 ngram parser；②`processed_event` 幂等表归 trading 域 DDL；③种子数据使用商品/分类/属性集/集合样例 + 三语 translation 行。

## 10. 自检

- [x] CollectionGroup/Collection 及 translation 附表、product_collection 关系表均与当前实现对齐；collection 无 cover 列
- [x] 基类选型（LongAuditableEntity）/ 逻辑删除（不启用，物理删除 + 守卫）/ 审计字段（created_at/updated_at）显式声明
- [x] RM-CAT-001~146（分段编号，无重号）；MAP-CAT-001~014；IDX-CAT-001~019（**含 FULLTEXT ×2：主表 + translation 附表**）；TX-CAT-001~022 + EC-CAT-001/002；CV-CAT-001~011；EVT-CAT-001~003；CACHE-CAT-001~006
- [x] 冗余回写落点定稿（L1 悬置项）：Product 冗余列 sales_30d/rating_avg/rating_count，仅 MQ 消费者/定时任务可写
- [x] 缓存 key/TTL/失效触发者与 data-flow 缓存矩阵逐行一致；key 含 locale 不含 currency；cacheNullValue 穿透保护
- [x] MQ 队列参数（重试 ×3 指数退避 / DLX dreamy.dlq / event_id 幂等）落实 error-strategy L2 要求 3 本域部分
- [x] 16 张表完整 DDL，列定义与 er-diagram maxlen/枚举/必填一致；事务边界与 api-detail TX 引用一一对应
