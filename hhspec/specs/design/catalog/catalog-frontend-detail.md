# catalog 前端详细设计（L2）

> 当前 Collection 命名与商品主图拼贴封面策略以 `catalog-contract-status.md` 为准。

> 角色: l2_frontend_designer ｜ change: portal-api-integration ｜ domain: catalog
> 两端：portal-admin（Vue3 + Pinia + Vite + Tailwind/Headless-UI，port 5174，中文）+ portal-store（Next.js 15 App Router，port 5173，EN/ES/FR，决策 22 Node standalone + ISR）。
> 编号：页面路由(PAGE-CAT) / 状态管理(STORE-CAT) / 组件树(COMP-CAT) / 表单交互(FORM-CAT)。伪代码级 diff 设计——**以真实工程现状为基线，仅替换数据源（mock → API）与补齐迭代 4 缺页，不改设计 token 与布局结构**（原型强对照约束 1~4）。
> API 契约消费：catalog-api-detail.md E-CAT-01~37；错误处理按 error-strategy 前端呈现约定（admin 按 code toast/表单分发，store 按 code 映射 next-intl 字典）。

## 0. 真实工程现状基线（设计前提，已核对 /Volumes/MAC/workspace/dreamy/frontend）

| 文件 | 现状 | 改造策略 |
|---|---|---|
| portal-admin/src/views/Products.vue（136 行） | 页面壳已有，`@/data/mock` 内存筛选，无「更多筛选」面板、无分页请求 | 保留布局，数据层换 API + 服务端分页；按原型 283 行版补齐更多筛选面板（仅数据行为，不改 token） |
| portal-admin/src/views/ProductEdit.vue（269 行） | 旧版表单壳（早于迭代 4 锚点布局） | 按原型 699 行版「复制+适配」迁移锚点布局（sec-basic/attrs/media/sku/size/price/content/seo + 三语 tab），数据层全接 API |
| portal-admin/src/views/Categories.vue（59 行） | **旧「品类与主题」版本**，与迭代 4 三层模型不符 | 迁移为三层树 + 属性 delta 抽屉 + 集合分组/集合 tab，数据层接 API |
| portal-admin/src/views/AttributeSets.vue | **真实工程缺失** | 按原型 209 行版新建（属性集×属性字典可见性矩阵 + cycleState），数据层接 API |
| portal-admin/src/api/ | 已有 client.ts（axios + R 解包 + snake↔camel + 401 跳登录）、auth/users/roles/… | **新增 `src/api/catalog.ts`**，复用 client 全部拦截器 |
| portal-store/app/{wedding-dresses,special-occasion,accessories,outdoor-weddings}/page.tsx | `@/data/products` mock | 改 RSC fetch 列表 API + ISR |
| portal-store/app/product/[slug]/page.tsx | `@/data/products` mock + generateStaticParams | 改 ISR 按需渲染 + PDP API |
| portal-store/app/search/page.tsx | 客户端内存 filter | 改调搜索 API（短 TTL 后端缓存） |
| portal-store/components/product/* | product-card/buy-box/gallery 等已有；**find-my-size-modal 缺失**（原型有） | 迁移 find-my-size-modal 并接尺码推荐 API |
| portal-store/lib/api/ | client.ts/case.ts（deepSnakeize/deepCamelize）/auth-api.ts 已有 | **新增 `lib/api/catalog-api.ts`** |

## A. portal-admin（Vue3 + Pinia）

### A.1 页面路由（PAGE-CAT-A）

| 编号 | 路由 | 视图 | 权限 key（meta.permission） | 消费端点 |
|---|---|---|---|---|
| PAGE-CAT-A01 | /products | Products.vue | /products | E-CAT-08 列表、E-CAT-13 status、E-CAT-14 flags、E-CAT-12 删除 |
| PAGE-CAT-A02 | /products/new 与 /products/:id/edit | ProductEdit.vue | /products | E-CAT-09/10/11、E-CAT-38 presign、E-CAT-15（品类级联）、E-CAT-19/23（属性配置）、E-CAT-31（集合选择器） |
| PAGE-CAT-A03 | /categories | Categories.vue | /categories | E-CAT-15/16/17/18、E-CAT-27/28/29/30（集合分组）、E-CAT-31/32/33/34（集合）、E-CAT-35/36/37（集合商品）、E-CAT-19（绑定属性集下拉） |
| PAGE-CAT-A04 | /attribute-sets | AttributeSets.vue | /attribute-sets | E-CAT-19/20/21/22（属性集）、E-CAT-23/24/25/26（属性字典） |

路由守卫沿用 identity GUARD-01~04（meta.permission ∉ permissionKeys 且非超管 → /403；菜单按权限过滤）。

### A.2 API 模块（src/api/catalog.ts，复用 client.ts）

```
listProducts(params)            GET  /admin/products            -> PageResult<AdminProductListItem>
getProduct(id)                  GET  /admin/products/{id}       -> AdminProductDetail
createProduct(body)             POST /admin/products
updateProduct(id, body)         PUT  /admin/products/{id}
deleteProduct(id)               DELETE /admin/products/{id}
toggleProductStatus(id, status) PATCH /admin/products/{id}/status
patchProductFlags(id, partial)  PATCH /admin/products/{id}/flags
listCategories()                GET  /admin/categories
createCategory(body) / updateCategory(id, body) / deleteCategory(id)
listAttributeSets() / createAttributeSet / updateAttributeSet / deleteAttributeSet
listAttributeDefs() / createAttributeDef / updateAttributeDef / deleteAttributeDef
listCollectionGroups() / createCollectionGroup / updateCollectionGroup / deleteCollectionGroup
listCollections(groupId?) / createCollection / updateCollection / deleteCollection
listCollectionProducts(id) / replaceCollectionProducts(id, productIds) / removeCollectionProduct(id, productId)
presignUpload({fileName, contentType, scope})  POST /admin/uploads/presign
uploadViaPresign(file, scope): presign → PUT 直传 upload_url（不经后端，FLOW-P17）→ 返回 public_url
```

分页消费 `PageResult`（totalElements ← total_elements，拦截器自动 camelize）；错误统一 ApiError{code,message,details}。

### A.3 状态管理（STORE-CAT-A，Pinia）

- STORE-CAT-A01 `useProductsStore`：{ list, totalElements, page, pageSize, filters{status,categoryId,search}, loading, fetch(), toggleStatus(row,status)（乐观更新，失败回滚+toast）, patchFlags(row,partial)（同前）, remove(id) }
- STORE-CAT-A02 `useCategoriesStore`：{ tree, loading, fetch(), create(), update(), remove() }——树为单次全量拉取，写后整树 refetch（量小，避免局部同步复杂度）；派生 getter `cascadeOptions`（ProductEdit 两级级联数据源）、`leafOf(categoryId)`
- STORE-CAT-A03 `useAttributeStore`：{ sets[], defs[], loading, fetchAll()（sets+defs 并行）, saveSet(), removeSet(), saveDef(), removeDef() }；派生 getter `resolveAttributeConfig(categoryId)`（沿分类祖先链取最近 attribute_set_id 的矩阵 + 子分类 attr_overrides delta 合并 → ProductEdit 属性区显隐/必填，prototype resolveAttributeConfig 同义复刻）
- STORE-CAT-A04 `useCollectionsStore`：{ groups[], collections[], loading, fetchGroups(), fetchCollections(groupId?), saveGroup/removeGroup/saveCollection/removeCollection }；派生 `collectionsByGroup(groupId)`（ProductEdit 集合选择器数据源）
- STORE-CAT-A05 上传复用：`useUpload()` composable —— uploadViaPresign 封装 + 进度态 + 502501 降级提示（"对象存储暂不可用，可先保存其他字段"）

### A.4 组件树（COMP-CAT-A）

- COMP-CAT-A01 `Products.vue`（PAGE-CAT-A01）：PageHeader + 筛选栏（search 防抖 300ms / category 级联 / status）+ 服务端分页 + Toggle/排序/删除交互。
- COMP-CAT-A02 `ProductEdit.vue`（PAGE-CAT-A02，原型锚点布局迁移）：
  - 左锚点导航（sec-basic/attrs/media/sku/size/price/content/seo，IntersectionObserver 高亮）
  - `sec-basic`：name/slug/subtitle/categoryId 两级级联（STORE-CAT-A02.cascadeOptions）/productType/集合选择器（STORE-CAT-A04.collectionsByGroup，按分组多选 chip）
  - `sec-attrs`：按 resolveAttributeConfig(categoryId) 渲染——visible=必填、optional=可选、hidden=不渲染；onParentChange 重置 train 等父级切换重置逻辑（原型同义）
  - `sec-media`：四区块（图廊/Lifestyle/视频/色样）→ COMP-CAT-A06 上传卡片；拖拽排序写 sort，第一张 gallery 即 sort=0 主图
  - `sec-sku`：颜色×尺码矩阵（skuColors/skuSizes 选择 → 矩阵生成行；单元格库存输入；已有行隐藏携带 id+version）+ sku_code 自动生成 `DRM-{颜色缩写}-{尺码}` 可改
  - `sec-size`：尺码表行编辑（us/uk/au/bust/waist/hips/hollow_to_floor）
  - `sec-price`：price/compare_at（前端 js_guard compare_at>=price 即时提示）/installment/multi_currency_prices 覆盖价
  - `sec-content`：description/designer_note/care 等 + **三语 tab（EN/ES/FR）**：EN 写主字段，ES/FR 写 translations[]（决策 13；可部分提交）
  - `sec-seo`：seo_title/seo_desc + slug 预览
  - 顶部操作：「保存草稿」（status=draft 提交）/「保存并生成静态页」（status=published 提交，OP-011；成功 toast"已保存，静态页失效链已触发"）
- COMP-CAT-A03 `Categories.vue`（PAGE-CAT-A03）：tab=品类树/集合；集合分组支持增改删，集合卡片封面由 `fallbackCoverUrls` 前 4 张商品主图按 1/2/3/4 图网格拼贴，不提供封面上传；集合商品抽屉支持添加、摘除和拖拽排序（E-CAT-35~37）。
- COMP-CAT-A04 `AttributeSets.vue`（PAGE-CAT-A04，新建迁移）：左=属性集列表（新增/改名/删除——409503 toast"被分类引用"）；右=可见性矩阵（行=attribute_def，列=属性集；单元格点击 cycleState visible→optional→hidden 循环，本地态变更 + hasUnsavedChanges 提示 + 「保存」整单提交 E-CAT-21）；属性字典管理区（新增定义/label 编辑/options 增删改 confirmAddOption/removeOption/confirmEdit + type 徽章 + 三语 label/options tab；删除 409507 toast）
- COMP-CAT-A05 `CategoryDrawer`：编辑抽屉子组件（Headless-UI Dialog，**根组件传 class 必须配 `as` prop**——CP-072 项目记忆）
- COMP-CAT-A06 `MediaUploadCard`：选文件 → MIME 前端白名单预检 → presign → PUT 直传（进度条）→ 预览 public_url；502501 → 卡片错误态"稍后重试"，不阻塞表单其余字段保存（决策 9 降级）

### A.5 表单交互（FORM-CAT-A）

- FORM-CAT-A01 商品保存：前端预校验（name/slug 正则/category/price/lead_time_days 必填、compare_at>=price、SKU 矩阵 sku_code 非空唯一）→ 提交 create/update → 409501 slug 字段 inline"slug 已存在"；409504 SKU 区 inline（details.sku_codes 定位行）；409508 → 弹窗"商品已被他人修改，请刷新后重试"+「重新加载」按钮（丢弃本地改动 refetch）；422501 details.fields 逐字段分发 inline
- FORM-CAT-A02 行内 Toggle/排序（Products）：乐观更新 → 失败回滚 + toast；幂等（同态重复点击不再发请求）
- FORM-CAT-A03 分类删除：前端 canDelete 预判（js_guard）置灰 + tooltip"分类下仍有商品/子分类"；后端 409502 兜底
- FORM-CAT-A04 属性集矩阵保存：整单覆盖提交；离开页有未保存变更 → 确认弹窗（hasUnsavedChanges）
- FORM-CAT-A05 集合分组删除：409506 → toast 引导先清空集合
- FORM-CAT-A06 三语 tab：ES/FR 空字段允许提交（缺翻译消费端回退 EN）；tab 上标注翻译完整度圆点（有任一字段即绿）

## B. portal-store（Next.js 15 App Router，决策 22 Node standalone）

### B.1 API 模块（lib/api/catalog-api.ts，复用 client.ts + case.ts deepCamelize）

```
fetchStoreProducts(params, locale)        GET /api/store/products            （RSC：fetch + next.revalidate）
searchStoreProducts(q, locale, page)      GET /api/store/products/search     （客户端 fetch，不缓存）
fetchRecommendations(block, opts, locale) GET /api/store/products/recommendations
fetchStoreProduct(slug, locale)           GET /api/store/products/{slug}
recommendSize(productId, answers, locale) POST /api/store/products/{id}/size-recommendation
fetchStoreCategories(locale)              GET /api/store/categories
fetchStoreCollections(locale, groupId?)  GET /api/store/collections
```

全部匿名公开（无 Authorization）；locale 取自路由前缀段（/es /fr，EN 无前缀，决策 27）。

### B.2 页面路由与 ISR/revalidate 策略（PAGE-CAT-S）

| 编号 | 路由（×3 locale 前缀） | 渲染 | 缓存/再生策略 | API |
|---|---|---|---|---|
| PAGE-CAT-S01 | /product/[slug] | RSC + ISR | **删除 generateStaticParams 全量预构建**，改 `dynamicParams=true` + `export const revalidate = 300`（TTL 兜底）；**秒级失效靠 on-demand**：MQ 失效消费者调内部 `POST /api/revalidate {paths}` → `revalidatePath('/product/{slug}')` ×3 locale（FLOW-P03/s-758）；404501 → `notFound()`（next 404 页） | E-CAT-04 + E-CAT-03（you_may_also_like / complete_the_look 区块）+ review 域评价区 |
| PAGE-CAT-S02 | /wedding-dresses、/special-occasion、/accessories、/outdoor-weddings | RSC + ISR | `revalidate = 300`；on-demand revalidatePath（商品/分类写事件）；分类 slug→category_id 映射经 fetchStoreCategories（同 ISR 周期） | E-CAT-01 + E-CAT-06 |
| PAGE-CAT-S03 | /search | RSC 壳 + 客户端结果区 | 页面壳静态；查询结果客户端 fetch（后端 JetCache 60s，CDN 不缓存）；URL ?q= 同步，防抖 350ms | E-CAT-02 |
| PAGE-CAT-S04 | /（首页推荐位区块） | RSC + ISR | `revalidate = 300` + on-demand（product_flags_changed / 销量回写失效仅清后端缓存，页面靠 TTL）；区块：new_arrivals / best_sellers / shop_by_color（色板 collection 来自 E-CAT-07） | E-CAT-03 + E-CAT-07 |
| PAGE-CAT-S05 | 导航/页脚分类菜单（layout 级） | RSC + ISR | layout 内 fetchStoreCategories `revalidate = 600` | E-CAT-06 |

`next.config.mjs`：去 `output:'export'` 改 `output:'standalone'`（决策 22，apply 阶段连带）；页面响应 `Cache-Control: s-maxage` 由 Next ISR 默认头 + CDN 前置承载。

### B.3 状态管理（STORE-CAT-S）

- STORE-CAT-S01 服务端数据不进客户端 store（RSC props 直传）；仅交互态入 zustand/useState
- STORE-CAT-S02 `useSearchState`：q/page/结果/loading/empty 态（/search 客户端区）
- STORE-CAT-S03 `useFindMySize`：表单 answers{height,bust,waist,hips,fitPreference} + result{matched,recommendedRow,explanation,dimensionNotes} + error 态；422502 → 字段红框 + 字典文案"输入超出可匹配范围"
- STORE-CAT-S04 i18n：next-intl 字典新增 catalog 命名空间（错误码 404501/422501/422502 文案 + size_reco.* 话术 key——与后端 explanation key 同名，后端返回已渲染文案、前端字典为降级冗余）

### B.4 组件树（COMP-CAT-S）

- COMP-CAT-S01 `ProductCard`（既有，components/product/product-card.tsx）：props 由 mock Product 类型切换为 StoreProductCard（camelCase 后字段对齐：imageUrl/swatches/ratingAvg/ratingCount/isNew/isBest/compareAt）；视觉零改动
- COMP-CAT-S02 `CollectionView`（既有）：接收 RSC 传入的 Paginated 数据 + 筛选/排序控件改为路由 searchParams 驱动（color/size/price/sort → URL → RSC refetch）；分页"加载更多"=page+1 路由更新
- COMP-CAT-S03 `ProductGallery` / `ProductBuyBox`（既有）：数据源切 StoreProductDetail；BuyBox 消费 skus（现货矩阵 stock=0 置灰）/customSizeAvailable（定制表单开关，A-007）/leadTimeDays+rushAvailable（交期展示，决策 20.6）；价格展示=USD 基准 + 客户端汇率换算（multi_currency_prices 覆盖价优先，trading 域 exchange-rates API，决策 14）
- COMP-CAT-S04 `FindMySizeModal`（**从原型迁移** prototype/components/product/find-my-size-modal.tsx）：原型本地 recommend() 硬编码码梯**删除**，改调 recommendSize API（E-CAT-05）；新增 weight 字段仅 UI 保留不上送（契约无此字段）；结果区：matched=true 显示 recommended_row.us + explanation + dimension_notes 列表；matched=false 显示建议话术 + 联系客服链接；fitPreference 三选（snug/regular/relaxed ←原型 fitted/true/relaxed 措辞按契约枚举适配）
- COMP-CAT-S05 `SizeGuideModal`（既有）：数据源切 StoreProductDetail.size_chart
- COMP-CAT-S06 推荐位区块组件（首页/PDP 复用）：`RecommendationRail(block, productId?/tagId?)` RSC 组件，空 items 整段不渲染（冷启动安全）
- COMP-CAT-S07 错误/空态：列表空 → 既有 EmptyState 风格；fetch 失败（5xx）→ 通用错误态组件 + 重试（error-strategy store 呈现约定）

### B.5 表单交互（FORM-CAT-S）

- FORM-CAT-S01 Find My Size：必填四维前端预校验（缺失红框，不发请求——原型行为保留）→ API → 422501/422502 按字段红框；成功滚动到结果区；建议码可一键选中 BuyBox 对应 size
- FORM-CAT-S02 搜索：Enter/防抖触发；q trim 为空不请求；结果空显示"无匹配"空态（不报错，契约空 data）
- FORM-CAT-S03 列表筛选：URL searchParams 单一事实源（可分享/SEO 友好）；price_min>price_max 前端预换位

## C. 原型对照表（prototype_source + conformance）

| 真实文件（frontend/） | prototype_source（hhspec/prototype/） | conformance |
|---|---|---|
| portal-admin/src/views/Products.vue | portal-admin/src/views/Products.vue | **layout-keep + data-swap**：布局/列/Toggle 形态不变；mock→API、内存筛选→服务端分页 |
| portal-admin/src/views/ProductEdit.vue | portal-admin/src/views/ProductEdit.vue | **copy-adapt**：真实页为旧版壳，按原型锚点布局整体迁移后仅动数据层；三语 tab/集合选择器/属性显隐 与原型同构 |
| portal-admin/src/views/Categories.vue | portal-admin/src/views/Categories.vue | **copy-adapt**：三层模型 + 集合分组/集合；分组删除使用 409506 guard，集合封面不可上传 |
| portal-admin/src/views/AttributeSets.vue（新建） | portal-admin/src/views/AttributeSets.vue | **copy-adapt**：真实工程缺页，按原型新建；cycleState 三态矩阵/字典增删改同构，保存改整单 API |
| portal-store/app/product/[slug]/page.tsx | app/product/[slug]/page.tsx | **layout-keep + data-swap**：视觉不变；generateStaticParams→ISR on-demand；尺码推荐/定制表单接 API |
| portal-store/components/product/find-my-size-modal.tsx（新建） | components/product/find-my-size-modal.tsx | **copy-adapt**：UI 同构；本地码梯算法→后端纯函数 API；weight 字段仅 UI 不上送（标注）；置信话术按决策 20.3 改区间说明（原型迭代 4 已同口径） |
| portal-store/app/{wedding-dresses,…}/page.tsx | app/ 对应聚合页 | **layout-keep + data-swap**：CollectionView 复用，筛选改 URL 驱动 |
| portal-store/app/search/page.tsx | app/search/page.tsx | **layout-keep + data-swap**：内存 filter→E-CAT-02 |

强对照红线复述：不改 tailwind token、不内联硬编码色值、新增 loading/error/empty 态复用既有组件风格、Headless-UI 根组件 class 必配 `as`。

## D. 自检

- [x] 两端齐备：portal-admin 4 页（PAGE-CAT-A01~A04）+ portal-store 5 路由组（PAGE-CAT-S01~S05）
- [x] 编号体系 COMP-CAT-A01~A06 / COMP-CAT-S01~S07、STORE-CAT-A01~A05 / STORE-CAT-S01~S04、FORM-CAT-A01~A06 / FORM-CAT-S01~S03、PAGE-CAT-A01~A04 / PAGE-CAT-S01~S05，无重号
- [x] ISR/revalidate 策略明确（dynamicParams + revalidate TTL 兜底 + MQ on-demand revalidatePath ×3 locale，对齐 FLOW-P03/决策 22/27）
- [x] 35 端点中两端消费全部映射到页面/组件（admin 28 端点 + store 7 端点）
- [x] 原型对照表含 prototype_source + conformance；两处显式偏离（维度删除收紧、weight 不上送）已标注归因
- [x] 错误呈现按 error-strategy 两端约定（409508 刷新弹窗 / 409502~409507 toast / 422 字段 inline / 502501 降级提示 / store 按 code 字典）
