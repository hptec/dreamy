# catalog API 详细设计（L2）

> 当前契约覆盖声明：请先阅读 `catalog-contract-status.md`。本文早期章节中的
> TagDimension/Tag、`/api/store/tags`、`/api/admin/tags` 和持久化 `cover` 均为迁移前历史
> 术语，不是当前实现契约；当前端点为 CollectionGroup/Collection 与 `/api/store/collections`、
> `/api/admin/collection-groups`、`/api/admin/collections`，集合封面使用 `fallback_cover_urls`。

> 角色: l2_api_designer ｜ change: portal-api-integration ｜ domain: catalog
> 方法论：每端点四部分 — 入参验证(V-CAT-NNN，全域连续唯一) / 业务步骤(STEP-CAT-NN，每端点独立编号段，溯源以「端点编号 E-CAT-NN + STEP-CAT-NN」组合唯一) / 出参构造 / 错误码映射。
> 来源权威：api-contracts/catalog-api.openapi.yml（35 端点）+ data-flow.md（FLOW-P01/P02/P03/P17/P19 + 缓存矩阵 + MQ 拓扑）+ error-strategy.md（catalog 域段 5，17 码）+ er-diagram.yml + state-machine.yml。
> 伪代码级，不绑定 Spring 语法。线上响应统一 huihao R 包络 `{code,message,data}`；分页统一 huihao.page.Paginated（data/total_elements/page_number/page_size/number_of_elements/total_pages）；JSON 字段一律 snake_case。

## 0. 全局横切（所有端点适用）

- **鉴权过滤器**：
  - `/api/store/*` → StoreJwtFilter（STORE_JWT_SECRET）。catalog 消费端 7 端点全部为**匿名公开**，经**配置化公开路径白名单**放行（见 0.1）；带 token 访问公开端点不报错（principal 可选注入，本域不消费）。
  - `/api/admin/*` → AdminJwtFilter（ADMIN_JWT_SECRET）+ RBAC 菜单权限 key 守卫：`/products`（admin-products + uploads）、`/categories`（categories + tag-dimensions + tags）、`/attribute-sets`（attribute-sets + attribute-defs）。缺权限 → 403 `40300`；跨端 token 误用 → 401 `40100`。
- **i18n**：store 读 `locale` query 参数（en/es/fr，缺省 en；与 Accept-Language 并存时 query 优先），文案按决策 13 翻译回退（ES/FR 命中 translation 附表，缺翻译回退 EN 主表）；admin 固定中文。
- **审计（admin 写操作，BE-DIM-7）**：AOP 切面写 operation_log；集合动作统一使用创建/编辑/删除集合分组、创建/编辑/删除集合及编辑/摘除集合商品。
- **缓存（BE-DIM-8）**：消费端只读端点按缓存矩阵走 JetCache 两级 + CDN s-maxage；写操作 `@CacheInvalidate` + MQ `content.invalidated` 失效链（FLOW-P03）；后台端点一律不缓存。key/TTL 详见 catalog-data-detail.md 第 7 节（CACHE-CAT-*）。
- **422 字段级错误结构**（error-strategy L2 要求 1）：`MethodArgumentNotValidException`/手工校验失败 → 422 `422501`，`details` 形如 `{ "fields": { "<field>": "<reason_key>" } }`（线上装入 R.data）；store 端 reason_key 由前端 next-intl 字典渲染，admin 端后端直出中文。

### 0.1 StoreJwtFilter 公开路径白名单（本域登记条目，error-strategy L2 要求 2）

白名单为配置化 pattern 列表（`dreamy.security.store-public-paths`，AntPath 风格，七域共用同一机制，禁止逐端点硬编码在 filter 内）。**catalog 域登记 3 条 pattern**：

| 白名单 pattern | 覆盖端点 |
|---|---|
| `/api/store/products/**` | E-CAT-01 列表、E-CAT-02 搜索、E-CAT-03 推荐位、E-CAT-04 PDP、E-CAT-05 尺码推荐（POST） |
| `/api/store/categories` | E-CAT-06 分类树 |
| `/api/store/collections` | E-CAT-07 集合导航 |

写限流（E-CAT-05 为公开 POST）在 Cloudflare WAF 层（决策 11），后端不实现限流。

---

## 1. STORE 商品端点

### E-CAT-01 listStoreProducts — GET /api/store/products （FLOW-P01, ALIGN-002）

**公开端点**：StoreJwtFilter 白名单 `/api/store/products/**`。

**入参**: query `{ locale?, page?, page_size?, category_id?, collection_id?, color?, size?, price_min?, price_max?, sort? }`
- V-CAT-001 locale ∈ {en,es,fr}，缺省 en（枚举外 → 422 `422501` fields.locale=invalid_enum）
- V-CAT-002 page ≥ 1 缺省 1；page_size 1..100 缺省 20（越界 → 422 `422501`）
- V-CAT-003 price_min/price_max ≥ 0；二者均给定时 price_min ≤ price_max（否则 422 `422501` fields.price_min=range_invalid）
- V-CAT-004 sort ∈ {newest, price_asc, price_desc, recommended}，缺省 recommended
- V-CAT-005 color maxLength 32、size maxLength 16；category_id/collection_id 为正整数 int64（非法 → 422 `422501`）

**业务步骤**:
- STEP-CAT-01 组装缓存 key `catalog:products:{filtersHash}:{locale}`（filtersHash=全部筛选参数规范化序列化），查 JetCache（TTL 300s）命中即返回
- STEP-CAT-02 category_id 给定 → 经分类树解析子树 id 集（含自身，最多 3 层；分类不存在 → 视为空集，返回空页，不 404）
- STEP-CAT-03 查询 `product WHERE status=published`，叠加筛选：category_id IN 子树集 / EXISTS product_collection(collection_id) / EXISTS sku(color/size) / price BETWEEN；排序：newest=created_at DESC（决策 29 New Arrivals 规则）、price_asc/desc=price、recommended=sort ASC, created_at DESC；分页 LIMIT/OFFSET
- STEP-CAT-04 批量装配卡片派生字段（防 N+1，单次 IN 批查）：主图（product_image kind∈{gallery} sort=0）、swatches（kind=swatch）、rating_avg/rating_count（商品冗余列直读，FLOW-P14 回写）
- STEP-CAT-05 locale=es/fr → 批查 product_translation(product_id IN, locale)，命中字段覆盖 name/subtitle，缺翻译回退 EN（决策 13）
- STEP-CAT-06 写 JetCache（结果，TTL 300s；空页同样缓存防穿透）→ 响应头 `Cache-Control: s-maxage=300`

**出参**: 200 Paginated`{ data: StoreProductCard[], total_elements, page_number, page_size, number_of_elements, total_pages }`（价格仅 USD 基准 + multi_currency_prices 原样透出，决策 14）
**错误映射**: 422 `422501` / 500 `50000`

### E-CAT-02 searchStoreProducts — GET /api/store/products/search （FLOW-P02, ALIGN-020, 决策 17）

**公开端点**：白名单 `/api/store/products/**`。

**入参**: query `{ q!, locale?, page?, page_size? }`
- V-CAT-006 q 必填，trim 后长度 1..80（空/超长 → 422 `422501` fields.q）
- V-CAT-007 locale/page/page_size 同 V-CAT-001/002

**业务步骤**:
- STEP-CAT-01 查 JetCache `catalog:search:{qNorm}:{locale}:{page}`（qNorm=trim+lower，TTL 60s）命中即返回
- STEP-CAT-02 EN 主检索：`SELECT id FROM product WHERE status=published AND MATCH(name, subtitle) AGAINST(? IN NATURAL LANGUAGE MODE)`（FULLTEXT ngram，IDX-CAT-004）
- STEP-CAT-03 locale=es/fr → UNION 附表检索：`MATCH(pt.name, pt.subtitle) AGAINST(?)` ON product_translation.locale=? JOIN product(status=published)（IDX-CAT-010）
- STEP-CAT-04 集合命中：collection.name（及 locale 对应 collection_translation.label）LIKE %q% AND status=enabled → 经 product_collection 取 published 商品 id 并入结果集
- STEP-CAT-05 合并去重（保持相关度序：主表 MATCH 得分 DESC → 附表 → 集合命中按 sort）、分页、装配 StoreProductCard（同 E-CAT-01 STEP-CAT-04/05）
- STEP-CAT-06 写 JetCache TTL 60s（短 TTL 自然过期兜底，无主动失效；CDN 不缓存本端点）

**出参**: 200 Paginated（无结果空 data，不报错）
**错误映射**: 422 `422501` / 500 `50000`

### E-CAT-03 listStoreRecommendations — GET /api/store/products/recommendations （决策 29, ALIGN-033）

**公开端点**：白名单 `/api/store/products/**`。

**入参**: query `{ block!, product_id?, collection_id?, limit?, locale? }`
- V-CAT-008 block 必填 ∈ {new_arrivals, best_sellers, shop_by_color, you_may_also_like, complete_the_look}
- V-CAT-009 block=you_may_also_like/complete_the_look 时 product_id 必填（缺 → 422 `422501` fields.product_id=required）
- V-CAT-010 block=shop_by_color 时 collection_id 必填（缺 → 422 `422501` fields.collection_id=required）
- V-CAT-011 limit 1..24 缺省 8

**业务步骤**:
- STEP-CAT-01 查 JetCache `catalog:reco:{block}:{pid|tid|-}:{locale}`（TTL 300s）命中即返回
- STEP-CAT-02 按 block 规则查询（一律仅 status=published，决策 29 规则化）：
  - new_arrivals：created_at DESC LIMIT limit
  - best_sellers：sales_30d DESC（商品冗余列，trading→catalog MQ 回写，EVT-CAT-001）；全部 sales_30d=0（冷启动）→ 回退 `recommend=true ORDER BY sort` 手动标记
  - shop_by_color：EXISTS product_collection(collection_id=?) ORDER BY sort（集合不存在/disabled → 空 items，不 404）
  - you_may_also_like：基准品（product_id；不存在或未发布 → 空 items）同 category_id + price ∈ [基准价×0.7, 基准价×1.3] 且 id≠基准 ORDER BY sort；不足 limit 放宽为仅同 category_id 补足
  - complete_the_look：同根品类下**其他叶子分类**（婚纱推配饰/面纱等关联品类规则凑数，不建关联表）ORDER BY sort
- STEP-CAT-03 装配 StoreProductCard + locale 翻译（同 E-CAT-01）
- STEP-CAT-04 写 JetCache TTL 300s → `Cache-Control: s-maxage=300`（失效触发者：商品写操作 + order.paid 销量回写，CACHE-CAT-004）

**出参**: 200 `{ items: StoreProductCard[] }`
**错误映射**: 422 `422501` / 500 `50000`

### E-CAT-04 getStoreProduct — GET /api/store/products/{slug} （FLOW-P01, ALIGN-002/003/024）

**公开端点**：白名单 `/api/store/products/**`。

**入参**: path `slug`；query `{ locale? }`
- V-CAT-012 slug 匹配 `^[a-z0-9-]+$` 且长度 ≤128（不匹配 → 404 `404501`，与不存在同口径防探测）
- V-CAT-013 locale 同 V-CAT-001

**业务步骤**:
- STEP-CAT-01 查 JetCache `catalog:product:{slug}:{locale}`（TTL 300s；含 null 值缓存）命中：null → 404 `404501`；DTO → 返回
- STEP-CAT-02 `SELECT product WHERE slug=? AND status=published`；不存在/未发布 → 写 null 缓存 60s（穿透保护，BE-DIM-8）→ 404 `404501`
- STEP-CAT-03 批量取子资源（各一次 IN 查询）：product_image（按 sort ASC）、sku（含 stock/version 现货矩阵）、size_chart_row（按 id ASC）、product_collection JOIN collection(status=enabled)
- STEP-CAT-04 取 category.name（分类名派生；es/fr 经 category_translation 解析）
- STEP-CAT-05 locale=es/fr → product_translation 覆盖 name/subtitle/description/seo_title/seo_desc，collection_translation 覆盖集合 name，缺翻译回退 EN
- STEP-CAT-06 装配 StoreProductDetail（含 lead_time_days/rush_available/custom_size_available 决策 6/20.6 字段、rating_avg/rating_count 冗余列）→ 写 JetCache TTL 300s → `Cache-Control: s-maxage=300`（ISR 商品页同源）

**出参**: 200 StoreProductDetail
**错误映射**: 404 `404501` / 500 `50000`

### E-CAT-05 recommendSize — POST /api/store/products/{id}/size-recommendation （FLOW-P19①, ALIGN-024, 决策 20.3）

**公开端点**：白名单 `/api/store/products/**`；公开 POST 写限流在 WAF（决策 11）。纯函数：仅读 size_chart_row，无写副作用，不缓存。

**入参**: path `id`；query `locale?`；body `{ height!, bust!, waist!, hips!, fit_preference? }`
- V-CAT-014 height/bust/waist/hips 必填且 > 0（缺/非数值/≤0 → 422 `422501` fields.<dim>）
- V-CAT-015 体征合理域校验（超出 → 422 `422502` SIZE_INPUT_OUT_OF_RANGE，details.fields 指明维度）：height ∈ [36, 90] in；bust/waist/hips ∈ [15, 80] in
- V-CAT-016 fit_preference ∈ {snug, regular, relaxed} 缺省 regular

**业务步骤**:
- STEP-CAT-01 `SELECT product WHERE id=? AND status=published`；不存在 → 404 `404501`
- STEP-CAT-02 `SELECT size_chart_row WHERE product_id=? ORDER BY bust ASC`；空表 → 200 `{matched:false, explanation: i18n(size_reco.no_chart_contact_support, locale)}`（商品无尺码表视为不可推荐，不报错）
- STEP-CAT-03 逐维度区间匹配（bust/waist/hips）：取**第一行该维度值 ≥ 输入值**的行为该维度落点（行按维度值升序）；输入值 > 全表该维度最大值 → 该维度落点 = 超界
- STEP-CAT-04 身高复核：hollow_to_floor 列非空时按 height×0.60 估算中空到地落入行，产出 dimension=hollow_to_floor 的落点说明（仅提示用，不参与定码）
- STEP-CAT-05 取码规则：三围落点中**最大码**为基准（跨码段取大码，dimension_notes 透出各维度 matched_us）；fit_preference=snug → 基准下移一档（不低于最小行）、relaxed → 上移一档（不高于最大行）、regular → 不偏移
- STEP-CAT-06 任一维度超界（STEP-CAT-03 超界）→ 200 `{matched:false, explanation: i18n(size_reco.out_of_chart_contact_support, locale), dimension_notes}`（不虚构买家占比；与 422502 的区分：422502=输入不合理，matched=false=输入合理但超出本品尺码表覆盖）
- STEP-CAT-07 命中 → 200 `{matched:true, recommended_row, explanation: i18n(size_reco.interval_explain, locale, us=row.us), dimension_notes}`（话术为区间说明，如"您的三围均落在 US 8 区间"）

**出参**: 200 SizeRecommendationResponse
**错误映射**: 404 `404501` / 422 `422501`（必填/类型）/ 422 `422502`（体征越界）/ 500 `50000`

---

## 2. STORE 分类与集合端点

### E-CAT-06 listStoreCategories — GET /api/store/categories （FLOW-P01, ALIGN-004）

**公开端点**：白名单 `/api/store/categories`。

**入参**: query `{ locale? }`
- V-CAT-017 locale 同 V-CAT-001

**业务步骤**:
- STEP-CAT-01 查 JetCache `catalog:categories:{locale}`（TTL 600s）命中即返回
- STEP-CAT-02 `SELECT category ORDER BY sort ASC, id ASC` 全量 → 内存组装三层树（parent_id 关联）
- STEP-CAT-03 product_count 聚合：`SELECT category_id, COUNT(*) FROM product WHERE status=published GROUP BY category_id`，自底向上累加到祖先节点（消费端口径=仅已发布）
- STEP-CAT-04 locale=es/fr → category_translation 批查覆盖 name，缺翻译回退 EN
- STEP-CAT-05 写 JetCache TTL 600s → `Cache-Control: s-maxage=600`（失效触发者：后台分类写，CACHE-CAT-005）

**出参**: 200 `{ items: StoreCategoryNode[] }`（含 children 递归）
**错误映射**: 500 `50000`

### E-CAT-07 listStoreCollections — GET /api/store/collections （ALIGN-004, 决策 29）

**公开端点**：白名单 `/api/store/collections`。

**入参**: query `{ locale?, group_id? }`
- V-CAT-018 locale 同 V-CAT-001
- V-CAT-019 group_id 可选正整数（非法 → 422 `422501`）

**业务步骤**:
- STEP-CAT-01 查 JetCache `catalog:collections:{group_id|all}:{locale}`（TTL 600s）命中即返回
- STEP-CAT-02 `SELECT collection_group`（group_id 给定则过滤；不存在 → 空 items）+ `SELECT collection WHERE status=enabled` 按 collection_group_id 分组
- STEP-CAT-03 product_count 聚合：product_collection JOIN product(status=published) GROUP BY collection_id（消费端口径=仅已发布）
- STEP-CAT-04 locale=es/fr → collection_group_translation.name / collection_translation.label 覆盖，缺翻译回退 EN；消费端不返回 cover
- STEP-CAT-05 写 JetCache TTL 600s → `Cache-Control: s-maxage=600`（失效触发者：后台集合/分组写，CACHE-CAT-006）

**出参**: 200 `{ items: StoreCollectionGroup[] }`
**错误映射**: 422 `422501` / 500 `50000`

---

## 3. ADMIN 商品端点（AdminBearerAuth + RBAC `/products`，不缓存）

### E-CAT-08 listAdminProducts — GET /api/admin/products （ALIGN-002）

**入参**: query `{ page?, page_size?, status?, category_id?, search? }`
- V-CAT-020 page/page_size 同 V-CAT-002
- V-CAT-021 status ∈ {all, draft, published} 缺省 all
- V-CAT-022 search maxLength 80（trim 后空 → 视为未提供）

**业务步骤**:
- STEP-CAT-01 组装条件：status≠all 过滤；category_id 含子树（同 E-CAT-01 STEP-CAT-02）；search → `(name LIKE %s% OR style_no LIKE %s%)`
- STEP-CAT-02 分页查询 ORDER BY sort ASC, id DESC
- STEP-CAT-03 批量装配派生列（单次 IN 批查防 N+1）：category_name、stock_total=SUM(sku.stock)、image_url=主图（kind=gallery sort=0）

**出参**: 200 Paginated`{ data: AdminProductListItem[] , ...}`
**错误映射**: 401 `40100` / 403 `40300` / 500 `50000`

### E-CAT-09 createAdminProduct — POST /api/admin/products （ALIGN-003, FLOW-P03 触发者）

**入参**: body AdminProductUpsert
- V-CAT-023 name 必填 trim 非空 ≤128
- V-CAT-024 slug 必填，匹配 `^[a-z0-9-]+$` 且 ≤128
- V-CAT-025 category_id 必填且分类存在（不存在 → 422 `422501` fields.category_id=not_exists；本端点契约无 404 响应）
- V-CAT-026 price 必填 ≥ 0
- V-CAT-027 compare_at 为空 或 compare_at ≥ price（js_guard，违反 → 422 `422501` fields.compare_at=lt_price）
- V-CAT-028 lead_time_days 必填 ≥ 1
- V-CAT-029 status 必填 ∈ {draft, published}
- V-CAT-030 sort ≥ 0（缺省 0）
- V-CAT-031 images[]：url 必填 ≤512（来自预签名上传 public_url）；kind ∈ {gallery,lifestyle,video,swatch}；sort ≥ 0；kind=gallery 的 sort=0 至多一张（主图 js_guard）；kind=swatch 时 color_name ≤32
- V-CAT-032 skus[]：sku_code 必填匹配 `^[A-Z0-9-]+$` ≤64；color 必填 ≤32；size 必填 ≤16；stock ≥ 0 缺省 0；提交集内 sku_code 不重复、(color,size) 组合不重复（重复 → 422 `422501` fields.skus）
- V-CAT-033 size_chart[]：us 必填 ≤8；uk/au ≤8；bust/waist/hips/hollow_to_floor ≥ 0
- V-CAT-034 collection_ids[]：去重；全部存在（不存在 → 422 `422501` fields.collection_ids=not_exists）
- V-CAT-035 translations[]：locale ∈ {es,fr} 且不重复；name ≤128 / subtitle ≤255 / seo_title ≤128 / seo_description ≤255
- V-CAT-036 文本长度上限（er-diagram 对齐）：subtitle ≤255、product_type ≤64、fabric_composition ≤128、model_height ≤32、model_size ≤16、model_body_type ≤32、country_of_origin ≤64、style_no ≤32、seo_title ≤128、seo_desc ≤255

**业务步骤（单事务 TX-CAT-001）**:
- STEP-CAT-01 slug 唯一性：`SELECT id FROM product WHERE slug=?`（uk_product_slug 兜底）命中 → 409 `409501`
- STEP-CAT-02 sku_code 全局唯一性：`SELECT sku_code FROM sku WHERE sku_code IN (...)`（uk_sku_code 兜底）命中 → 409 `409504`（details.sku_codes）
- STEP-CAT-03 INSERT product（含 is_new/is_best/recommend/installment 缺省 false；sales_30d=0、rating_avg=0、rating_count=0 冗余列初始化）
- STEP-CAT-04 批量 INSERT product_image / sku(version=0) / size_chart_row / product_collection / product_translation
- STEP-CAT-05 INSERT operation_log(action=创建商品)
- STEP-CAT-06 事务提交后：@CacheInvalidate `catalog:products:*`、`catalog:reco:*`、`catalog:categories:*`、`catalog:tags:*`（product_count 变化）→ MQ publish `content.invalidated {event_id, type:product_created, slug, locales:[en,es,fr]}`（status=published 才需 revalidate 路径；MQ 失败不回滚，TTL 兜底）

**出参**: 201 AdminProductDetail（全量回读，含 id/created_at/updated_at）
**错误映射**: 401 `40100` / 403 `40300` / 409 `409501`/`409504` / 422 `422501` / 500 `50000`,`50001`

### E-CAT-10 getAdminProduct — GET /api/admin/products/{id} （ALIGN-003）

**入参**: path id
- V-CAT-037 id 正整数 int64（非法 → 404 `404501` 同口径）

**业务步骤**:
- STEP-CAT-01 `SELECT product WHERE id=?`；不存在 → 404 `404501`
- STEP-CAT-02 批量取 images/skus（含 version 供编辑回传）/size_chart/collection_ids/translations（三语 tab 全量）
- STEP-CAT-03 装配 AdminProductDetail（EN 主表字段原样，不做 locale 解析）

**出参**: 200 AdminProductDetail
**错误映射**: 404 `404501` / 500 `50000`

### E-CAT-11 updateAdminProduct — PUT /api/admin/products/{id} （ALIGN-003, OP-011「保存并生成静态页」, FLOW-P03）

**入参**: path id；body AdminProductUpsert（已有 SKU 携带 id+version）
- 复用 V-CAT-023 ~ V-CAT-036（slug/sku_code 唯一性排除自身）
- V-CAT-038 skus[] 中带 id 的行：id 必须属于本商品（不属于 → 422 `422501` fields.skus=not_owned）且必须携带 version（缺 → 422 `422501` fields.skus=version_required）

**业务步骤（单事务 TX-CAT-002，整单覆盖）**:
- STEP-CAT-01 `SELECT product WHERE id=?`；不存在 → 404 `404501`
- STEP-CAT-02 slug 变更时查重（排除自身）→ 409 `409501`；sku_code 查重（排除本商品已有行）→ 409 `409504`
- STEP-CAT-03 并发防丢失（409508 PRODUCT_VERSION_CONFLICT）：对每个带 id 的 SKU 执行 `UPDATE sku SET ..., version=version+1 WHERE id=? AND version=?`，影响行数=0 → 抛 VersionConflict 整体回滚 → 409 `409508`（前端提示刷新重载表单）；无 SKU 的纯定制商品以 body 可选回传的 updated_at 与 DB 比对（不一致 → 409 `409508`）
- STEP-CAT-04 UPDATE product 主表（冗余列 sales_30d/rating_avg/rating_count **不受整单覆盖影响**，仅 MQ 消费者可写）
- STEP-CAT-05 子表整单覆盖：product_image/size_chart_row/product_tag/product_translation 先 DELETE 后批量 INSERT；sku 差异化处理——带 id 行 CAS UPDATE（STEP-CAT-03）、新行 INSERT(version=0)、缺席的既有行 DELETE（被订单快照引用不受影响，OrderLine 为快照）
- STEP-CAT-06 INSERT operation_log(action=编辑商品)
- STEP-CAT-07 提交后失效链（s-758「保存并生成静态页」语义）：@CacheInvalidate `catalog:product:{slug}:*`（新旧 slug 都失效）、`catalog:products:*`、`catalog:reco:*`、`catalog:search` 不主动失效（60s TTL 兜底）→ MQ publish `content.invalidated {type:product_updated, slug, old_slug?, locales:[en,es,fr]}` → 消费者 revalidatePath('/product/{slug}' × 3 locale 路径) + Cloudflare purge

**出参**: 200 AdminProductDetail
**错误映射**: 404 `404501` / 409 `409501`/`409504`/`409508` / 422 `422501` / 500 `50000`,`50001`

### E-CAT-12 deleteAdminProduct — DELETE /api/admin/products/{id} （product_lifecycle: draft→deleted）

**入参**: path id（V-CAT-039 同 V-CAT-037 口径）

**业务步骤（单事务 TX-CAT-003）**:
- STEP-CAT-01 `SELECT product WHERE id=?`；不存在 → 404 `404501`
- STEP-CAT-02 状态机 guard：status=published → 409 `409509` PRODUCT_NOT_DELETABLE（需先下架；state-machine product_lifecycle published→deleted guard 落地为**禁止直删**）
- STEP-CAT-03 物理删除 product 及子表行（image/sku/size_chart/product_tag/translation；订单行为快照不受影响）
- STEP-CAT-04 INSERT operation_log(action=删除商品)
- STEP-CAT-05 提交后失效 `catalog:products:*`、`catalog:reco:*`、`catalog:categories:*`、`catalog:tags:*`（计数变化）；draft 无消费端页面，不发 revalidate 事件

**出参**: 204
**错误映射**: 404 `404501` / 409 `409509` / 500 `50000`,`50001`

### E-CAT-13 toggleAdminProductStatus — PATCH /api/admin/products/{id}/status （ALIGN-002 行内 Toggle, product_lifecycle publish/unpublish, FLOW-P03）

**入参**: path id；body `{ status! }`
- V-CAT-040 status 必填 ∈ {draft, published}

**业务步骤（单事务 TX-CAT-004）**:
- STEP-CAT-01 商品不存在 → 404 `404501`
- STEP-CAT-02 幂等：目标态=当前态 → 直接返回当前行（不写审计不发事件）
- STEP-CAT-03 `UPDATE product SET status=?`；INSERT operation_log(action=商品上下架, changes={from,to})
- STEP-CAT-04 提交后失效链：@CacheInvalidate `catalog:product:{slug}:*`、`catalog:products:*`、`catalog:reco:*`、`catalog:categories:*`、`catalog:tags:*` → MQ `content.invalidated {type:product_status_changed, slug, locales}` → revalidatePath + purge（下架后 PDP 回 404501，列表移除）

**出参**: 200 AdminProductListItem
**错误映射**: 404 `404501` / 500 `50000`

### E-CAT-14 patchAdminProductFlags — PATCH /api/admin/products/{id}/flags （ALIGN-002 行内营销标记, 决策 29）

**入参**: path id；body `{ is_new?, is_best?, recommend?, sort? }`
- V-CAT-041 body 至少一个字段（minProperties=1；空对象 → 422 `422501` fields._body=empty）
- V-CAT-042 sort 给定时 ≥ 0

**业务步骤（单事务 TX-CAT-005）**:
- STEP-CAT-01 商品不存在 → 404 `404501`
- STEP-CAT-02 仅 UPDATE 提交的字段；INSERT operation_log(action=编辑商品, changes=flags before/after)
- STEP-CAT-03 提交后失效 `catalog:reco:*`（推荐位规则依赖 is_new/is_best/recommend/sort）、`catalog:products:*` → MQ `content.invalidated {type:product_flags_changed, slug, locales}`

**出参**: 200 AdminProductListItem
**错误映射**: 404 `404501` / 422 `422501` / 500 `50000`

---

## 4. ADMIN 分类端点（RBAC `/categories`，不缓存）

### E-CAT-15 listAdminCategories — GET /api/admin/categories （ALIGN-004）

**入参**: 无

**业务步骤**:
- STEP-CAT-01 全量 `SELECT category ORDER BY sort, id` → 组装三层树
- STEP-CAT-02 product_count 聚合（后台口径=全部商品含 draft：`GROUP BY category_id`，自底向上累加）—— js_guard canDelete 数据源
- STEP-CAT-03 批查 category_translation 装配三语 translations[]（admin 不做回退合并，原样给前端 tab）

**出参**: 200 `{ items: AdminCategoryNode[] }`（含 attribute_set_id/attr_overrides/level/product_count/children/translations）
**错误映射**: 403 `40300` / 500 `50000`

### E-CAT-16 createAdminCategory — POST /api/admin/categories （ALIGN-004, category_lifecycle: →active）

**入参**: body AdminCategoryUpsert
- V-CAT-043 name 必填 trim 非空 ≤64
- V-CAT-044 parent_id 为空（根分类）→ attribute_set_id 必填（缺 → 422 `422501` fields.attribute_set_id=required_for_root）且属性集存在（不存在 → 404 `404503`）
- V-CAT-045 parent_id 非空 → 父分类存在（不存在 → 404 `404502`）；level=parent.level+1 ≤ 3（超出 → 409 `409505` CATEGORY_LEVEL_EXCEEDED）
- V-CAT-046 translations[] locale ∈ {es,fr} 不重复；name ≤64
- V-CAT-047 attr_overrides 仅子分类允许（根分类提交 → 422 `422501` fields.attr_overrides=root_not_allowed）；JSON 形如 `{attributeKey: visibility}`，key 必须属于生效属性集（沿祖先链取最近的 attribute_set_id），value ∈ {visible,optional,hidden}（saveDrawer delta 语义：仅存与父级不同项）

**业务步骤（单事务 TX-CAT-006）**:
- STEP-CAT-01 计算 level（根=1，否则 parent.level+1）；sort 缺省取同层 MAX(sort)+1
- STEP-CAT-02 INSERT category + category_translation
- STEP-CAT-03 INSERT operation_log(action=创建分类)
- STEP-CAT-04 提交后失效 `catalog:categories:*` → MQ `content.invalidated {type:category_changed, locales}` → revalidate 导航相关路径 + purge

**出参**: 201 AdminCategoryNode（product_count=0, children=[]）
**错误映射**: 404 `404502`/`404503` / 409 `409505` / 422 `422501` / 500 `50000`

### E-CAT-17 updateAdminCategory — PUT /api/admin/categories/{id} （ALIGN-004 saveDrawer + 拖拽排序）

**入参**: path id；body AdminCategoryUpsert
- V-CAT-048 分类存在（不存在 → 404 `404502`）；parent_id 不可变更（与 DB 不一致 → 422 `422501` fields.parent_id=immutable，原型无移动节点交互）；其余复用 V-CAT-043/044/046/047（根分类改绑 attribute_set_id 时校验 404503）

**业务步骤（单事务 TX-CAT-007）**:
- STEP-CAT-01 UPDATE category(name, attribute_set_id[根], attr_overrides[子, delta 整体覆盖], sort)
- STEP-CAT-02 translation 整单覆盖（DELETE+INSERT）
- STEP-CAT-03 INSERT operation_log(action=编辑分类)
- STEP-CAT-04 提交后失效 `catalog:categories:*`、`catalog:products:*`（列表含 category_name 派生）→ MQ → revalidate + purge；改绑属性集/overrides 同时失效商品表单配置缓存（admin 实时读，无缓存，仅记录语义）

**出参**: 200 AdminCategoryNode
**错误映射**: 404 `404502`/`404503` / 409 `409505` / 422 `422501` / 500 `50000`

### E-CAT-18 deleteAdminCategory — DELETE /api/admin/categories/{id} （category_lifecycle: active→deleted, guard product_count===0）

**入参**: path id

**业务步骤（单事务 TX-CAT-008）**:
- STEP-CAT-01 分类不存在 → 404 `404502`
- STEP-CAT-02 guard①：子树（含自身）商品数 > 0 → 409 `409502` CATEGORY_HAS_PRODUCTS（details.product_count）
- STEP-CAT-03 guard②：存在子分类 → 409 `409502`（details.reason=has_children；契约口径"有子分类亦不可删"归并本码）
- STEP-CAT-04 物理删除 category + category_translation；INSERT operation_log(action=删除分类)
- STEP-CAT-05 提交后失效 `catalog:categories:*` → MQ → revalidate + purge

**出参**: 204
**错误映射**: 404 `404502` / 409 `409502` / 500 `50000`

---

## 5. ADMIN 属性集与属性字典端点（RBAC `/attribute-sets`，不缓存）

### E-CAT-19 listAdminAttributeSets — GET /api/admin/attribute-sets （ALIGN-004）

**入参**: 无（无 query/body；鉴权与 RBAC 走横切 0 节）

**业务步骤**:
- STEP-CAT-01 `SELECT attribute_set` + 批查 attribute_set_item（可见性矩阵明细行）
- STEP-CAT-02 category_count 派生：`SELECT attribute_set_id, COUNT(*) FROM category GROUP BY attribute_set_id`（删除约束判定数据源）

**出参**: 200 `{ items: AttributeSet[] }`（含 items[]{attribute_id, visibility} + category_count）
**错误映射**: 403 `40300` / 500 `50000`

### E-CAT-20 createAdminAttributeSet — POST /api/admin/attribute-sets

**入参**: body AttributeSetUpsert
- V-CAT-049 label 必填 trim 非空 ≤64
- V-CAT-050 items[] 必填（可为空数组）；每行 attribute_id 存在（不存在 → 422 `422501` fields.items=attribute_not_exists，本端点契约无 404）；visibility ∈ {visible,optional,hidden}（FLD-ATTRIBUTESETS-001/002/003）
- V-CAT-051 items[].attribute_id 不重复（重复 → 422 `422501` fields.items=duplicated）

**业务步骤（单事务 TX-CAT-009）**:
- STEP-CAT-01 INSERT attribute_set + 批量 INSERT attribute_set_item
- STEP-CAT-02 INSERT operation_log(action=创建属性集)

**出参**: 201 AttributeSet（category_count=0）
**错误映射**: 422 `422501` / 500 `50000`

### E-CAT-21 updateAdminAttributeSet — PUT /api/admin/attribute-sets/{id} （ALIGN-004 STATES/cycleState 三态矩阵整单保存, attribute_visibility_cycle）

**入参**: path id；body AttributeSetUpsert
- V-CAT-052 属性集存在（不存在 → 404 `404503`）；复用 V-CAT-049/050/051

**业务步骤（单事务 TX-CAT-010）**:
- STEP-CAT-01 UPDATE attribute_set.label
- STEP-CAT-02 attribute_set_item 全量重写（DELETE WHERE attribute_set_id=? + 批量 INSERT）——必填→可选→隐藏三态矩阵整单覆盖原子化（attribute_visibility_cycle 状态由整单提交承载，后端不维护逐项流转事件）
- STEP-CAT-03 INSERT operation_log(action=编辑属性集)
- STEP-CAT-04 提交后无消费端缓存可失效（属性集仅影响后台商品表单配置，admin 实时读）；列表页 ProductEdit 下次拉取即生效

**出参**: 200 AttributeSet
**错误映射**: 404 `404503` / 422 `422501` / 500 `50000`

### E-CAT-22 deleteAdminAttributeSet — DELETE /api/admin/attribute-sets/{id}

**入参**: path id（合法性同 V-CAT-037 口径，非法视同不存在 → 404 `404503`）

**业务步骤（单事务 TX-CAT-011）**:
- STEP-CAT-01 不存在 → 404 `404503`
- STEP-CAT-02 guard：`COUNT(category WHERE attribute_set_id=?) > 0` → 409 `409503` ATTRIBUTE_SET_IN_USE（details.category_count）
- STEP-CAT-03 物理删除 attribute_set + attribute_set_item；INSERT operation_log(action=删除属性集)

**出参**: 204
**错误映射**: 404 `404503` / 409 `409503` / 500 `50000`

### E-CAT-23 listAdminAttributeDefs — GET /api/admin/attribute-defs （ALIGN-004）

**入参**: 无（无 query/body）

**业务步骤**:
- STEP-CAT-01 `SELECT attribute_def ORDER BY id` + 批查 attribute_def_translation 装配三语 translations[]

**出参**: 200 `{ items: AttributeDef[] }`
**错误映射**: 403 `40300` / 500 `50000`

### E-CAT-24 createAdminAttributeDef — POST /api/admin/attribute-defs

**入参**: body AttributeDefUpsert
- V-CAT-053 key 必填，匹配 `^[a-z][a-z0-9_]*$` ≤64，全局唯一（重复 → 422 `422501` fields.key=exists；本端点契约无 409）
- V-CAT-054 label 必填 trim 非空 ≤64
- V-CAT-055 type 必填 ∈ {select, multiselect, text, toggle}
- V-CAT-056 options js_guard：type ∈ {select, multiselect} 时 options 必填非空且选项去重；type ∈ {text, toggle} 时禁止提交 options（违反 → 422 `422501` fields.options）

**业务步骤（单事务 TX-CAT-012）**:
- STEP-CAT-01 INSERT attribute_def + attribute_def_translation（V-CAT-058 适用）
- STEP-CAT-02 INSERT operation_log(action=创建属性定义)

**出参**: 201 AttributeDef
**错误映射**: 422 `422501` / 500 `50000`

### E-CAT-25 updateAdminAttributeDef — PUT /api/admin/attribute-defs/{id} （confirmAddOption/removeOption/confirmEdit 整单保存）

**入参**: path id；body AttributeDefUpsert
- V-CAT-057 属性定义存在（不存在 → 404 `404504`）；key 不可变更（与 DB 不一致 → 422 `422501` fields.key=immutable）；复用 V-CAT-054/055/056（type 不可变更，理由同 key——商品属性值已按 type 落库）
- V-CAT-058 translations[].options 给定时必须与主表 options **等长**（错位 → 422 `422501` fields.translations=options_length_mismatch）

**业务步骤（单事务 TX-CAT-013）**:
- STEP-CAT-01 UPDATE attribute_def(label, options 整单覆盖) + translation 整单覆盖
- STEP-CAT-02 INSERT operation_log(action=编辑属性定义)
- STEP-CAT-03 提交后无消费端缓存（同 E-CAT-21 STEP-CAT-04 语义，商品表单配置实时读）

**出参**: 200 AttributeDef
**错误映射**: 404 `404504` / 422 `422501` / 500 `50000`

### E-CAT-26 deleteAdminAttributeDef — DELETE /api/admin/attribute-defs/{id}

**入参**: path id（合法性同 V-CAT-037 口径，非法视同不存在 → 404 `404504`）

**业务步骤（单事务 TX-CAT-014）**:
- STEP-CAT-01 不存在 → 404 `404504`
- STEP-CAT-02 guard：`COUNT(attribute_set_item WHERE attribute_id=?) > 0` → 409 `409507` ATTRIBUTE_DEF_IN_USE（details.attribute_set_count）
- STEP-CAT-03 物理删除 attribute_def + attribute_def_translation；INSERT operation_log(action=删除属性定义)

**出参**: 204
**错误映射**: 404 `404504` / 409 `409507` / 500 `50000`

---

## 6. ADMIN 集合分组与集合端点（RBAC `/categories`，不缓存）

### E-CAT-27 listAdminCollectionGroups — GET /api/admin/collection-groups

**入参**: 无（无 query/body）

**业务步骤**:
- STEP-CAT-01 `SELECT collection_group` + collection_count 派生（`COUNT(collection) GROUP BY collection_group_id`，删除约束判定）+ 批查 translation

**出参**: 200 `{ items: CollectionGroup[] }`
**错误映射**: 403 `40300` / 500 `50000`

### E-CAT-28 createAdminCollectionGroup — POST /api/admin/collection-groups

**入参**: body CollectionGroupUpsert
- V-CAT-059 name 必填 trim 非空 ≤64；description ≤255
- V-CAT-060 translations[] locale ∈ {es,fr} 不重复；name ≤64

**业务步骤（单事务 TX-CAT-015）**:
- STEP-CAT-01 INSERT collection_group + translation；INSERT operation_log(action=创建集合分组)

**出参**: 201 CollectionGroup（collection_count=0）
**错误映射**: 422 `422501` / 500 `50000`

### E-CAT-29 updateAdminCollectionGroup — PUT /api/admin/collection-groups/{id}

**入参**: path id；body CollectionGroupUpsert
- V-CAT-061 维度存在（不存在 → 404 `404505`）；复用 V-CAT-059/060

**业务步骤（单事务 TX-CAT-016）**:
- STEP-CAT-01 UPDATE collection_group + translation 整单覆盖；INSERT operation_log(action=编辑集合分组)
- STEP-CAT-02 提交后失效 `catalog:collections:*` → MQ `content.invalidated {type:collection_changed, locales}`

**出参**: 200 CollectionGroup
**错误映射**: 404 `404505` / 422 `422501` / 500 `50000`

### E-CAT-30 deleteAdminCollectionGroup — DELETE /api/admin/collection-groups/{id}

**入参**: path id（合法性同 V-CAT-037 口径，非法视同不存在 → 404 `404505`）

**业务步骤（单事务 TX-CAT-017）**:
- STEP-CAT-01 不存在 → 404 `404505`
- STEP-CAT-02 guard：`COUNT(collection WHERE collection_group_id=?) > 0` → 409 `409506` COLLECTION_GROUP_IN_USE（details.collection_count）
- STEP-CAT-03 先删 collection_group_translation，再物理删除 collection_group；INSERT operation_log(action=删除集合分组)
- STEP-CAT-04 提交后失效 `catalog:collections:*` → MQ

**出参**: 204
**错误映射**: 404 `404505` / 409 `409506` / 500 `50000`

### E-CAT-31 listAdminCollections — GET /api/admin/collections

**入参**: query `{ group_id? }`
- V-CAT-062 group_id 可选正整数

**业务步骤**:
- STEP-CAT-01 `SELECT collection`（group_id 过滤）+ product_count 派生（product_collection 计数，后台口径=全部商品）+ 批查 translation + `fallback_cover_urls`

**出参**: 200 `{ items: Collection[] }`
**错误映射**: 403 `40300` / 500 `50000`

### E-CAT-32 createAdminCollection — POST /api/admin/collections

**入参**: body CollectionUpsert
- V-CAT-063 collection_group_id 必填且分组存在（不存在 → 404 `404505`）
- V-CAT-064 name 必填 trim 非空 ≤64
- V-CAT-065 status 必填 ∈ {enabled, disabled}
- V-CAT-066 Collection 无 cover 入参；响应 `fallback_cover_urls` 为集合前 4 张商品主图动态派生

**业务步骤（单事务 TX-CAT-018）**:
- STEP-CAT-01 INSERT collection + collection_translation；INSERT operation_log(action=创建集合)
- STEP-CAT-02 提交后失效 `catalog:collections:*` → MQ

**出参**: 201 Collection（product_count=0）
**错误映射**: 404 `404505` / 422 `422501` / 500 `50000`

### E-CAT-33 updateAdminCollection — PUT /api/admin/collections/{id}

**入参**: path id；body CollectionUpsert
- V-CAT-067 集合存在（不存在 → 404 `404505`）；复用 V-CAT-063~066（collection_group_id 可改=移动分组）

**业务步骤（单事务 TX-CAT-019）**:
- STEP-CAT-01 UPDATE collection + translation 整单覆盖；INSERT operation_log(action=编辑集合, changes 含 status 流转)
- STEP-CAT-02 提交后失效 `catalog:collections:*`、`catalog:reco:*`（shop_by_color 依赖）、`catalog:products:*`（collection_id 筛选）→ MQ `content.invalidated {type:collection_changed, locales}`

**出参**: 200 Collection
**错误映射**: 404 `404505` / 422 `422501` / 500 `50000`

### E-CAT-34 deleteAdminCollection — DELETE /api/admin/collections/{id}

**入参**: path id
- V-CAT-068 id 正整数 int64（非法视同不存在 → 404 `404505`）

**业务步骤（单事务 TX-CAT-020）**:
- STEP-CAT-01 不存在 → 404 `404505`
- STEP-CAT-02 先删 product_collection 与 collection_translation，再物理删除 collection；INSERT operation_log(action=删除集合)
- STEP-CAT-03 提交后失效 `catalog:collections:*`、`catalog:reco:*`、`catalog:products:*` → MQ

**出参**: 204
**错误映射**: 404 `404505` / 500 `50000`

### E-CAT-35 listAdminCollectionProducts — GET /api/admin/collections/{id}/products

- 集合不存在 → 404 `404505`；按 `product_collection.sort` 返回商品 id/name/slug/status/image_url/sort。
- 历史孤儿挂载中的缺失商品跳过，不影响其余商品展示。

### E-CAT-36 replaceAdminCollectionProducts — PUT /api/admin/collections/{id}/products

- body `product_ids[]` 全量覆盖；全部商品必须存在，否则返回 404 `404501`。
- 按数组顺序写入连续 sort；提交后失效 collections/reco/products 缓存族并发布 collection_changed。

### E-CAT-37 removeAdminCollectionProduct — DELETE /api/admin/collections/{id}/products/{productId}

- 集合不存在 → 404 `404505`；删除对应 product_collection 行（不存在时幂等成功）。
- 提交后失效 collections/reco/products 缓存族并发布 collection_changed。

---

## 7. ADMIN 上传端点（RBAC `/products`，决策 9 / FLOW-P17）

### E-CAT-38 presignAdminUpload — POST /api/admin/uploads/presign

媒体基建由 catalog 域代管：商品图廊/色样/分类/Banner/内容封面共用本端点（scope 区分对象 key 前缀）。

**入参**: body PresignRequest
- V-CAT-069 file_name 必填 ≤255；sanitize（去路径分隔符/控制字符，仅保留 `[A-Za-z0-9._-]`，空结果 → 422 `422501` fields.file_name）
- V-CAT-070 content_type 必填 ∈ MIME 白名单 {image/jpeg, image/png, image/webp, video/mp4}（白名单外 → 422 `422501` fields.content_type=unsupported）
- V-CAT-071 scope ∈ {product, category, banner, content} 缺省 product

**业务步骤**:
- STEP-CAT-01 生成对象 key：`{scope}/{雪花序id}/{sanitizedFileName}`
- STEP-CAT-02 调 S3 兼容存储 SDK 生成预签名 PUT URL（有效期 600s；超时 3s）
- STEP-CAT-03 S3 不可达/超时 → 502 `502501` OBJECT_STORAGE_UNAVAILABLE（决策 9 降级：前端提示稍后重试，表单其余字段可先保存）
- STEP-CAT-04 拼装 public_url（CDN 域名 + object_key，落库存此值）；本端点为读侧基建，不写 operation_log、不发 MQ、不缓存

**出参**: 200 `{ upload_url, object_key, public_url, expires_at }`
**错误映射**: 401 `40100` / 403 `40300` / 422 `422501` / 502 `502501` / 500 `50000`

---

## 8. 自检

- [x] 38 端点全覆盖（含集合商品管理 E-CAT-35~37 + 预签名 E-CAT-38）
- [x] 每端点四部分齐全（入参验证 / 业务步骤 / 出参构造 / 错误码映射）
- [x] V-CAT-001 ~ V-CAT-071 全域连续唯一；STEP-CAT-NN 每端点独立编号段（端点号 E-CAT-NN 提供唯一溯源前缀）
- [x] 错误码全部出自 catalog-api.openapi.yml 码表（404501~404505 / 409501~409509 / 422501 / 422502 / 502501）+ identity 复用码（40100/40300/50000/50001），无臆造；契约未声明 404 的创建端点关联校验一律落 422501
- [x] 公开端点全部标注 StoreJwtFilter 白名单条目（0.1 节，3 条 pattern 覆盖 7 端点）
- [x] 缓存键/TTL/失效触发者与 data-flow.md 缓存矩阵一致；写端点全部标注失效链 + MQ 事件 + OperationLog action
- [x] 事务边界 TX-CAT-001 ~ TX-CAT-020 与 catalog-data-detail.md 一一对应
- [x] 状态机迁移（product_lifecycle/category_lifecycle/collection_lifecycle/collection_group_lifecycle/attribute_visibility_cycle）全部落到端点 guard
