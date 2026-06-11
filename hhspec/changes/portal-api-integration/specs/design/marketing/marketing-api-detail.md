# marketing API 详细设计（L2）

> 角色: l2_api_designer ｜ change: portal-api-integration ｜ domain: marketing
> 方法论：每端点四部分 — 入参验证(V-MKT-NNN，全域连续唯一) / 业务步骤(STEP-MKT-NN，每端点独立编号段，溯源以「端点编号 E-MKT-NN + STEP-MKT-NN」组合唯一) / 出参构造 / 错误码映射。
> 来源权威：api-contracts/marketing-api.openapi.yml（46 端点）+ data-flow.md（FLOW-P01/P03/P15/P19②③ + 缓存矩阵 + MQ 拓扑）+ error-strategy.md（marketing 域段 7，10 码）+ er-diagram.yml + state-machine.yml（banner/blog/coupon/flash/lookbook/guide/wedding 七状态机）。
> 伪代码级，不绑定 Spring 语法。线上响应统一 huihao R 包络 `{code,message,data}`；分页统一 huihao.page.Paginated（data/total_elements/page_number/page_size/number_of_elements/total_pages）；JSON 字段一律 snake_case。

## 0. 全局横切（所有端点适用）

- **鉴权过滤器**：
  - `/api/store/*` → StoreJwtFilter（STORE_JWT_SECRET）。本域消费端 12 端点中 **11 个为匿名公开**（内容读 ×9 + newsletter + contact），经**配置化公开路径白名单**放行（见 0.1）；仅 `POST /api/store/promotions/coupons/validate`（E-MKT-10）要求 StoreBearerAuth（结算前提，customer 主体仅做登录态校验，不参与券归属——本期券不绑用户）。
  - `/api/admin/*` → AdminJwtFilter（ADMIN_JWT_SECRET）+ RBAC 菜单权限 key 守卫：`/promotions`（coupons + flash-sales）、`/banners`（banners）、`/content/blog`（blogs）、`/content/weddings`（weddings）、`/content/lookbook`（lookbooks + guides）。缺权限 → 403 `40300`；跨端 token 误用 → 401 `40100`。
- **i18n**：store 读 `locale` query 参数（en/es/fr，缺省 en；与 Accept-Language 并存时 query 优先），文案按决策 13 翻译回退（ES/FR 命中 translation 附表逐字段覆盖，缺翻译字段回退 EN 主表）；admin 固定中文、translations 三语 tab 原样进出不做回退合并。
- **审计（admin 写操作，BE-DIM-7）**：AOP 切面写 operation_log；本域 action 枚举：`创建优惠券`/`编辑优惠券`/`删除优惠券`/`创建闪购`/`编辑闪购`/`删除闪购`/`创建Banner`/`编辑Banner`/`删除Banner`/`创建文章`/`编辑文章`/`删除文章`/`文章发布状态变更`/`创建婚礼案例`/`编辑婚礼案例`/`删除婚礼案例`/`案例发布状态变更`/`创建Lookbook`/`编辑Lookbook`/`删除Lookbook`/`Lookbook发布状态变更`/`创建指南`/`编辑指南`/`删除指南`/`指南发布状态变更`（Banner 行内 status Toggle 归入 `编辑Banner`，changes 记 status before/after——与 error-strategy 审计枚举一致）。
- **缓存（BE-DIM-8）**：消费端内容/闪购读端点按缓存矩阵走 JetCache 两级 + CDN s-maxage；后台写操作 `@CacheInvalidate` + MQ `content.invalidated` 失效链（FLOW-P03）；coupons/validate、newsletter、contact 不缓存；后台端点一律不缓存。key/TTL 详见 marketing-data-detail.md 第 8 节（CACHE-MKT-*）。
- **422 字段级错误结构**（error-strategy L2 要求 1）：`MethodArgumentNotValidException`/手工校验失败 → 422 `422704`，`details` 形如 `{ "fields": { "<field>": "<reason_key>" } }`（线上装入 R.data）；store 端 reason_key 由前端 next-intl 字典渲染，admin 端后端直出中文。
- **WAF 限流（决策 11）**：公开 POST（newsletter/contact）限流在 Cloudflare WAF 层（超限 429 边缘拦截，不经后端码表），后端不实现限流。

### 0.1 StoreJwtFilter 公开路径白名单（本域登记条目，error-strategy L2 要求 2）

白名单为配置化 pattern 列表（`dreamy.security.store-public-paths`，AntPath 风格，七域共用同一机制，禁止逐端点硬编码在 filter 内）。**marketing 域登记 4 条 pattern**：

| 白名单 pattern | 覆盖端点 |
|---|---|
| `/api/store/content/**` | E-MKT-01~08（banners/blogs/weddings/lookbooks/guides 全部内容读） |
| `/api/store/promotions/flash-sales` | E-MKT-09（**精确路径**，不得用 `/api/store/promotions/**`——否则误放行 coupons/validate） |
| `/api/store/newsletter` | E-MKT-11（公开 POST，WAF 限流） |
| `/api/store/contact` | E-MKT-12（公开 POST，WAF 限流） |

`POST /api/store/promotions/coupons/validate` **不入白名单**（StoreBearerAuth）。

### 0.2 本域设计决策（DEC-MKT，定稿归因）

- **DEC-MKT-1 主表 EN 文案列补齐**：er-diagram 将 Banner(title/subtitle/cta_text)、Lookbook(description)、RealWedding(title/story)、Guide(body)、Coupon(description) 的文案字段仅建模在 translation 附表（es/fr），但契约消费端出参 schema（StoreBanner/StoreLookbook/StoreRealWedding/StoreGuide/CouponValidateResponse.coupon）均要求这些字段「已按 locale 解析」且决策 13 规定 **EN 基准语存主表**。本设计在主表补齐对应**可空 EN 文案列**，并在对应 Upsert DTO 增加同名**可选**入参字段（additive 增量，不改既有契约字段、不影响契约消费者反序列化；无 EN 数据源则 EN 站点这些文案恒空，违背 StoreBanner 等出参语义——归因记录）。涉及端点：E-MKT-22/23（banner）、E-MKT-33/34（wedding）、E-MKT-38/39（lookbook）、E-MKT-43/44（guide）、E-MKT-14/15（coupon description）。
- **DEC-MKT-2 Banner 到期口径**：消费端读路径（E-MKT-01）恒按投放窗口过滤（status=published 且 now∈[start_time,end_time]，空窗端开放），FLOW-P15 权威口径「now>end → 移出（**状态不变**，读路径按窗口过滤）」；SCHED-MKT-01 扫描到窗口边界穿越（进入/移出投放）仅触发缓存失效链，**不翻转 Banner status**。state-machine `schedule_expire`（published→archived）迁移由「窗口过滤造成消费端下线」语义承载，管理端列表以「已过窗」派生标识展示；手动下线仍走 E-MKT-25（published→archived）。归因：避免定时任务与管理员手动 republish 的状态写竞争，且与 E-MKT-01 契约描述（按窗口过滤）一致。
- **DEC-MKT-3 Coupon/FlashSale 状态翻转**：与 Banner 不同，Coupon（scheduled→active→expiring→expired）与 FlashSale（scheduled→active→ended）状态列由 SCHED-MKT-01 **实际翻转落库**（data-flow FLOW-P15 显式 UPDATE 语义；s-760/s-761），FlashSale 翻转触发失效链，Coupon 翻转不发 MQ（无消费端缓存面，见 E-MKT-14 注记）。expiring 阈值 = end_at − now ≤ 72h（配置项 `dreamy.marketing.coupon-expiring-hours` 缺省 72）。
- **DEC-MKT-4 coupon.value 可解析约定**：value 为展示字符串（'15% OFF'/'$50 OFF'），校验端点需从中派生数值。Upsert 按 type 强制 pattern（V-MKT-022），保证 E-MKT-10 解析确定性：discount → `^\d{1,3}% OFF$`（取百分数）；fixed_amount → `^\$\d+(\.\d{1,2})? OFF$`（取金额）；free_shipping → 任意 ≤32 文案（不解析，discount_amount=0）。
- **DEC-MKT-5 total_limit 不限语义**：`>9999 视为不限`（er-diagram）。落库 NOT NULL，缺省 100000（不限）；核销 CAS 谓词统一 `used_count < total_limit`（与 trading RM-TRD-112 已发布 SQL 一致，不限券因 100000 上限实际不可耗尽）；管理端展示 >9999 → 「不限」。
- **DEC-MKT-6 blog views 近似计数**：阅读数异步累加不阻塞读路径（契约 E-MKT-03 描述）：源站命中详情时向 Redis `marketing:blog:views:{id}` INCR（fire-and-forget），SCHED-MKT-02 每分钟批量 flush 到 DB `views = views + delta`；CDN/JetCache 命中不计数——views 为近似值（显式语义，管理端列表展示用）。

---

## 1. STORE 内容读端点（全部匿名公开，白名单 `/api/store/content/**`）

### E-MKT-01 listStoreBanners — GET /api/store/content/banners （FLOW-P01/P15, ALIGN-009）

**入参**: query `{ position?, locale? }`
- V-MKT-001 position 可选 ∈ {hero, featured, topbar}（枚举外 → 422 `422704` fields.position=invalid_enum）
- V-MKT-002 locale ∈ {en, es, fr}，缺省 en（枚举外 → 422 `422704` fields.locale=invalid_enum）

**业务步骤**:
- STEP-MKT-01 查 JetCache `marketing:banners:{position|all}:{locale}`（TTL 300s）命中即返回
- STEP-MKT-02 `SELECT banner WHERE status='published' AND (start_time IS NULL OR start_time<=now) AND (end_time IS NULL OR end_time>now)`，position 给定则过滤，ORDER BY sort ASC, id ASC（投放窗口过滤=DEC-MKT-2 权威读口径）
- STEP-MKT-03 locale=es/fr → 批查 banner_translation(banner_id IN, locale)，命中字段覆盖 title/subtitle/cta_text，缺翻译回退 EN 主表列（DEC-MKT-1）
- STEP-MKT-04 装配 StoreBanner（id/name/image_url/position/sort/title/subtitle/cta_text，**不暴露** clicks/status/start_time/end_time）→ 写 JetCache TTL 300s（空集同样缓存）→ `Cache-Control: s-maxage=300`

**出参**: 200 `{ items: StoreBanner[] }`
**错误映射**: 422 `422704` / 500 `50000`

### E-MKT-02 listStoreBlogs — GET /api/store/content/blogs （FLOW-P01, ALIGN-010）

**入参**: query `{ category?, locale?, page?, page_size? }`
- V-MKT-003 category maxLength 64（trim 后空 → 视为未提供）
- V-MKT-004 page ≥ 1 缺省 1；page_size 1..100 缺省 20（越界 → 422 `422704`）
- locale 同 V-MKT-002

**业务步骤**:
- STEP-MKT-01 查 JetCache `marketing:blogs:{category|all}:{page}:{page_size}:{locale}`（TTL 300s）命中即返回
- STEP-MKT-02 `SELECT blog_post WHERE status='published'`（category 给定则 =），ORDER BY published_at DESC, id DESC，分页 LIMIT/OFFSET（IDX-MKT-005）
- STEP-MKT-03 卡片派生：excerpt——locale=en 取 content strip 标记后截断 200 字符；es/fr 取 blog_post_translation.excerpt，缺翻译回退 EN 派生值（决策 13）；title 同回退
- STEP-MKT-04 写 JetCache TTL 300s → `Cache-Control: s-maxage=300`

**出参**: 200 Paginated`{ data: StoreBlogPostCard[], total_elements, page_number, page_size, number_of_elements, total_pages }`
**错误映射**: 422 `422704` / 500 `50000`

### E-MKT-03 getStoreBlog — GET /api/store/content/blogs/{slug} （FLOW-P01/P03, s-758）

**入参**: path `slug`；query `{ locale? }`
- V-MKT-005 slug 匹配 `^[a-z0-9-]+$` 且 ≤128（不匹配 → 404 `404701`，与不存在同口径防探测）；locale 同 V-MKT-002

**业务步骤**:
- STEP-MKT-01 查 JetCache `marketing:blog:{slug}:{locale}`（TTL 300s，含 null 值缓存）命中：null → 404 `404701`；DTO → 跳 STEP-MKT-05
- STEP-MKT-02 `SELECT blog_post WHERE slug=? AND status='published'`；不存在/未发布 → 写 null 缓存 60s（穿透保护，BE-DIM-8）→ 404 `404701`
- STEP-MKT-03 locale=es/fr → blog_post_translation 覆盖 title/excerpt/content(=translation.body)/seo_title/seo_description，逐字段缺翻译回退 EN（EN seo_title=title、seo_description=excerpt 派生，主表不建 seo 列）
- STEP-MKT-04 写 JetCache TTL 300s → `Cache-Control: s-maxage=300`（ISR 文章页同源）
- STEP-MKT-05 views 异步累加：Redis INCR `marketing:blog:views:{id}`（fire-and-forget，失败仅日志，DEC-MKT-6），不阻塞响应

**出参**: 200 StoreBlogPostDetail
**错误映射**: 404 `404701` / 500 `50000`

### E-MKT-04 listStoreWeddings — GET /api/store/content/weddings （FLOW-P01, ALIGN-011）

**入参**: query `{ locale?, page?, page_size? }`（复用 V-MKT-002/004）

**业务步骤**:
- STEP-MKT-01 查 JetCache `marketing:weddings:{page}:{page_size}:{locale}`（TTL 300s）命中即返回
- STEP-MKT-02 `SELECT real_wedding WHERE status='published'` ORDER BY wedding_date DESC, id DESC，分页
- STEP-MKT-03 locale=es/fr → real_wedding_translation 覆盖 title/story，缺翻译回退 EN 主表列（DEC-MKT-1）
- STEP-MKT-04 装配 StoreRealWedding（列表**不带 products**，契约「详情返回」）→ 写 JetCache → `s-maxage=300`

**出参**: 200 Paginated`{ data: StoreRealWedding[], ... }`
**错误映射**: 422 `422704` / 500 `50000`

### E-MKT-05 getStoreWedding — GET /api/store/content/weddings/{id} （Shop the Look, ALIGN-011）

**入参**: path `id`；query `{ locale? }`
- V-MKT-006 id 正整数 int64（非法 → 404 `404701` 同口径）；locale 同 V-MKT-002

**业务步骤**:
- STEP-MKT-01 查 JetCache `marketing:wedding:{id}:{locale}`（TTL 300s，null 值 60s）命中即按 null/DTO 返回
- STEP-MKT-02 `SELECT real_wedding WHERE id=? AND status='published'`；不存在/draft → null 缓存 → 404 `404701`
- STEP-MKT-03 `SELECT real_wedding_product WHERE real_wedding_id=?` → 经 **catalog 领域服务接口**（进程内直调，决策 3）`catalogQueryPort.listProductRefs(productIds, locale)` 装配 ProductRef[]（仅 published 商品，缺失项静默剔除——商品下架不破坏案例页）
- STEP-MKT-04 translation 覆盖（同 E-MKT-04 STEP-MKT-03）→ 写 JetCache → `s-maxage=300`

**出参**: 200 StoreRealWedding（含 products[]）
**错误映射**: 404 `404701` / 500 `50000`

### E-MKT-06 listStoreLookbooks — GET /api/store/content/lookbooks （ALIGN-012）

**入参**: query `{ locale? }`（V-MKT-002）

**业务步骤**:
- STEP-MKT-01 查 JetCache `marketing:lookbooks:{locale}`（TTL 300s）命中即返回
- STEP-MKT-02 `SELECT lookbook WHERE status='published'` ORDER BY id DESC
- STEP-MKT-03 translation 覆盖 title/description（EN description 取主表列，DEC-MKT-1）→ 列表不带 products → 写 JetCache → `s-maxage=300`

**出参**: 200 `{ items: StoreLookbook[] }`
**错误映射**: 500 `50000`

### E-MKT-07 getStoreLookbook — GET /api/store/content/lookbooks/{id}

**入参**: path `id`（V-MKT-006 口径）；query `{ locale? }`

**业务步骤**:
- STEP-MKT-01 查 JetCache `marketing:lookbook:{id}:{locale}`（TTL 300s，null 值 60s）
- STEP-MKT-02 `SELECT lookbook WHERE id=? AND status='published'`；不存在/draft → null 缓存 → 404 `404701`
- STEP-MKT-03 lookbook_product → catalogQueryPort.listProductRefs（同 E-MKT-05 STEP-MKT-03 剔除口径）
- STEP-MKT-04 translation 覆盖 → 写 JetCache → `s-maxage=300`

**出参**: 200 StoreLookbook（含 products[]）
**错误映射**: 404 `404701` / 500 `50000`

### E-MKT-08 listStoreGuides — GET /api/store/content/guides （ALIGN-012）

**入参**: query `{ locale? }`（V-MKT-002）

**业务步骤**:
- STEP-MKT-01 查 JetCache `marketing:guides:{locale}`（TTL 300s）命中即返回
- STEP-MKT-02 `SELECT guide WHERE status='published'` ORDER BY phase ASC, id ASC（phase 文本 'Phase 1'..，字典序即阶段序）
- STEP-MKT-03 translation 覆盖 title/body（EN body 主表列，DEC-MKT-1）→ 写 JetCache → `s-maxage=300`

**出参**: 200 `{ items: StoreGuide[] }`（id/phase/timeframe/title/body/tasks_count）
**错误映射**: 500 `50000`

---

## 2. STORE 促销端点

### E-MKT-09 listStoreFlashSales — GET /api/store/promotions/flash-sales （FLOW-P15, ALIGN-008, s-761；白名单精确路径）

**入参**: query `{ locale? }`（V-MKT-002）

**业务步骤**:
- STEP-MKT-01 查 JetCache `marketing:flash:{locale}`（**TTL 60s** 兜底倒计时新鲜度）命中即返回
- STEP-MKT-02 `SELECT flash_sale WHERE status='active'` ORDER BY end_at ASC（到期翻转由 SCHED-MKT-01 保证，读路径不再叠窗口过滤——以状态列为准，DEC-MKT-3）
- STEP-MKT-03 批查 flash_sale_product → catalogQueryPort.listProductRefs(productIds, locale) 装配 products[]
- STEP-MKT-04 flash_sale_translation 覆盖 name → 写 JetCache TTL 60s → `Cache-Control: s-maxage=60`（短）

**出参**: 200 `{ items: StoreFlashSale[] }`（id/name/discount/start_at/end_at/products；end_at 为前端倒计时依据）
**错误映射**: 500 `50000`

### E-MKT-10 validateStoreCoupon — POST /api/store/promotions/coupons/validate （FLOW-P05 切面, ALIGN-008；StoreBearerAuth，不缓存）

**核心口径**：券不可用一律 **200 + valid=false + reason_code（4227xx）**，不抛错（结算页就地提示）；仅请求格式校验失败抛 422 `422704`。核销不在本端点（下单事务内 trading 进程内直调，见 marketing-data-detail SVC-MKT-01）。

**入参**: body `{ code!, subtotal! }`
- V-MKT-007 code 必填，trim 后大写归一，匹配 `^[A-Z0-9]+$` 且 ≤32（缺/不匹配/超长 → 422 `422704` fields.code）
- V-MKT-008 subtotal 必填数值 ≥ 0（USD 基准，门槛判定）（缺/非数值/负 → 422 `422704` fields.subtotal）

**业务步骤**:
- STEP-MKT-01 `SELECT coupon WHERE code=?`（uk_coupon_code 点查，IDX-MKT-001）
- STEP-MKT-02 可用性判定（顺序固定，首个命中即返回 200 valid=false + reason_code）：
  1. 不存在 / status='draft' / （status∈{scheduled} 或 start_at>now）→ reason_code=`422701`（未开始）
  2. status='expired' 或 (end_at 非空且 now>end_at) → reason_code=`422701`（已过期；SCHED 翻转与实时判定双保险）
  3. used_count ≥ total_limit → reason_code=`422703`（已领完/用完，DEC-MKT-5）
  4. min_amount > 0 且 subtotal < min_amount → reason_code=`422702`（未达门槛）
- STEP-MKT-03 通过 → 减免计算（DEC-MKT-4 解析规则）：type=discount → discount_amount=round(subtotal × pct/100, 2)；fixed_amount → discount_amount=min(amount, subtotal)；free_shipping → discount_amount=0 且 free_shipping=true
- STEP-MKT-04 coupon.name 按 locale 解析（coupon_translation，缺翻译回退 EN）；locale 取 query/Accept-Language（缺省 en）
- STEP-MKT-05 装配 CouponValidateResponse{valid, reason_code?, discount_amount?, free_shipping?, coupon{code,name,type,value,min_amount}}（valid=false 时 coupon 仅在券存在时返回；不存在不回显任何券信息——不泄露码表）

**出参**: 200 CouponValidateResponse
**错误映射**: 401 `40100` / 422 `422704`（仅格式） / 500 `50000`

---

## 3. STORE 落表端点（FLOW-P19②③，公开 POST，WAF 限流，不缓存、不发 MQ、不写 OperationLog）

### E-MKT-11 subscribeNewsletter — POST /api/store/newsletter （ALIGN-030, 决策 26）

**入参**: body `{ email!, source!, locale! }`
- V-MKT-009 email 必填，RFC5322 格式且 ≤255（违反 → 422 `422704` fields.email；bs-543/544）
- V-MKT-010 source 必填 ∈ {footer, modal, exit_intent}（bs-545/943）
- V-MKT-011 locale 必填 ∈ {en, es, fr}（bs-546）

**业务步骤**:
- STEP-MKT-01 email 小写归一（trim + lowercase——幂等判重口径统一）
- STEP-MKT-02 `INSERT newsletter_subscriber(email, source, locale, subscribed_at=now) ON DUPLICATE KEY UPDATE id=id`（uk_newsletter_email；**重复订阅为空操作，首写胜出**，不更新 source/locale）
- STEP-MKT-03 无论新增或重复一律返回 `{subscribed:true}`（响应体/状态码/耗时特征完全一致，**不泄露邮箱是否已存在**——js_guard 幂等 + 防枚举）；不发码不发邮件（决策 26 显式降级）

**出参**: 200 `{ subscribed: true }`
**错误映射**: 422 `422704` / 500 `50000`,`50001`

### E-MKT-12 submitContactMessage — POST /api/store/contact （ALIGN-034, 决策 30）

**入参**: body `{ name!, email!, subject?, message! }`
- V-MKT-012 name 必填 trim 非空 ≤100（bs-385/547）
- V-MKT-013 email 必填格式 ≤255（bs-386/548/549）
- V-MKT-014 subject 可选 ≤200（bs-387/550）
- V-MKT-015 message 必填 trim 非空 ≤5000（bs-388/551）

**业务步骤**:
- STEP-MKT-01 `INSERT contact_message(name, email, subject?, message, submitted_at=now)`（无判重——同人多次留言均落表）
- STEP-MKT-02 返回 201（管理端本期不做查看页，运营直查库；无后续流转、无邮件）

**出参**: 201 `{ submitted: true }`
**错误映射**: 422 `422704` / 500 `50000`,`50001`

---

## 4. ADMIN 优惠券端点（RBAC `/promotions`，不缓存；coupon 无消费端缓存面——写操作**不发** content.invalidated，validate 端点实时读库）

### E-MKT-13 listAdminCoupons — GET /api/admin/promotions/coupons

**入参**: query `{ page?, page_size?, status?, search? }`
- V-MKT-016 page/page_size 同 V-MKT-004
- V-MKT-017 status ∈ {all, draft, scheduled, active, expiring, expired} 缺省 all
- V-MKT-018 search ≤64（trim 后空 → 视为未提供）

**业务步骤**:
- STEP-MKT-01 组装条件：status≠all 过滤；search → `(code LIKE %s% OR name LIKE %s%)`
- STEP-MKT-02 分页查询 ORDER BY id DESC（IDX-MKT-002 status 过滤）
- STEP-MKT-03 批查 coupon_translation 装配 translations[]（三语 tab 原样）

**出参**: 200 Paginated`{ data: Coupon[], ... }`（含 used_count 只读列；total_limit>9999 前端展示「不限」）
**错误映射**: 401 `40100` / 403 `40300` / 500 `50000`

### E-MKT-14 createAdminCoupon — POST /api/admin/promotions/coupons （ALIGN-008, s-760, coupon_lifecycle 初态落库）

**入参**: body CouponUpsert
- V-MKT-019 code 必填，大写归一后匹配 `^[A-Z0-9]+$` 且 ≤32（bs-480/1582）
- V-MKT-020 name 必填 trim 非空 ≤64（bs-481）
- V-MKT-021 type 必填 ∈ {discount, fixed_amount, free_shipping}（bs-482）
- V-MKT-022 value 必填 ≤32 且按 type 匹配可解析 pattern（DEC-MKT-4；不匹配 → 422 `422704` fields.value=unparseable；bs-483）
- V-MKT-023 min_amount 可选 ≥ 0 缺省 0（bs-484）
- V-MKT-024 total_limit 可选 ≥ 0 缺省 100000（DEC-MKT-5；bs-485）
- V-MKT-025 start_at/end_at 可选；二者均给定时 end_at > start_at（js_guard，违反 → 422 `422704` fields.end_at=before_start；bs-486/487/488）
- V-MKT-026 status 必填 ∈ {draft, scheduled, active, expiring, expired} 且与时间窗一致（coupon_lifecycle guard 落地为提交校验）：scheduled 要求 start_at 非空且 > now（bs-786）；active 要求 (start_at 空或 ≤now) 且 (end_at 空或 >now)（bs-787 同族）；expiring/expired 不可作为创建态（违反 → 422 `422704` fields.status=inconsistent_with_window）
- V-MKT-027 translations[] locale ∈ {es, fr} 且不重复；name ≤64 / description ≤255

**业务步骤（单事务 TX-MKT-001）**:
- STEP-MKT-01 code 唯一性：`SELECT id FROM coupon WHERE code=?`（uk_coupon_code 兜底）命中 → 409 `409701`
- STEP-MKT-02 INSERT coupon（used_count=0 初始化；description EN 可选列，DEC-MKT-1）+ 批量 INSERT coupon_translation
- STEP-MKT-03 INSERT operation_log(action=创建优惠券)
- STEP-MKT-04 无缓存失效、无 MQ（coupon 仅 admin 列表 + validate 实时读，缓存矩阵无此面）

**出参**: 201 Coupon（全量回读含 id/used_count=0）
**错误映射**: 401 `40100` / 403 `40300` / 409 `409701` / 422 `422704` / 500 `50000`,`50001`

### E-MKT-15 updateAdminCoupon — PUT /api/admin/promotions/coupons/{id}

**入参**: path id；body CouponUpsert
- V-MKT-028 id 正整数 int64（非法视同不存在 → 404 `404702`）
- 复用 V-MKT-019 ~ V-MKT-027（code 唯一性排除自身；status 时间窗一致性放宽：编辑时允许保持 DB 当前 status∈{expiring,expired} 原值不动，仅禁止**改入**该两态）
- V-MKT-029 used_count 只读：请求体提交的 used_count 一律忽略（不比对不报错，er js_guard usedCount≤totalLimit 由核销 CAS 谓词保证）

**业务步骤（单事务 TX-MKT-002）**:
- STEP-MKT-01 `SELECT coupon WHERE id=?`；不存在 → 404 `404702`
- STEP-MKT-02 状态机 guard：DB status ∈ {active, expiring} 且提交 code ≠ DB code → 409 `409703` CONTENT_STATE_INVALID（已上线券改码会使用户手中券码失效——契约「已 active 券改 code 被拒」）
- STEP-MKT-03 code 变更时查重（排除自身）→ 409 `409701`
- STEP-MKT-04 UPDATE coupon 主表（SET 列表**不含** used_count）+ coupon_translation 整单覆盖（DELETE+批量 INSERT）
- STEP-MKT-05 INSERT operation_log(action=编辑优惠券, changes before/after)

**出参**: 200 Coupon
**错误映射**: 403 `40300` / 404 `404702` / 409 `409701`/`409703` / 422 `422704` / 500 `50000`,`50001`

### E-MKT-16 deleteAdminCoupon — DELETE /api/admin/promotions/coupons/{id} （coupon_lifecycle: draft|expired→deleted）

**入参**: path id（V-MKT-028 口径）

**业务步骤（单事务 TX-MKT-003）**:
- STEP-MKT-01 不存在 → 404 `404702`
- STEP-MKT-02 状态机 guard①：status ∉ {draft, expired} → 409 `409703`（仅 draft/expired 可删；scheduled/active/expiring 须先到期）
- STEP-MKT-03 guard②：used_count > 0 → 409 `409703`（details.reason=has_redemptions；已核销券保留对账依据——契约口径）
- STEP-MKT-04 物理删除 coupon + coupon_translation；INSERT operation_log(action=删除优惠券)

**出参**: 204
**错误映射**: 403 `40300` / 404 `404702` / 409 `409703` / 500 `50000`

---

## 5. ADMIN 闪购端点（RBAC `/promotions`，不缓存；写操作触发 `marketing:flash:*` 失效 + MQ）

### E-MKT-17 listAdminFlashSales — GET /api/admin/promotions/flash-sales

**入参**: query `{ status? }`
- V-MKT-030 status ∈ {all, draft, scheduled, active, ended} 缺省 all

**业务步骤**:
- STEP-MKT-01 `SELECT flash_sale`（status≠all 过滤）ORDER BY start_at DESC, id DESC
- STEP-MKT-02 批查 flash_sale_product 装配 product_ids[] + flash_sale_translation 装配 translations[]

**出参**: 200 `{ items: FlashSale[] }`
**错误映射**: 403 `40300` / 500 `50000`

### E-MKT-18 createAdminFlashSale — POST /api/admin/promotions/flash-sales （ALIGN-008, s-761, flash_sale_lifecycle）

**入参**: body FlashSaleUpsert
- V-MKT-031 name 必填 trim 非空 ≤64（bs-489）
- V-MKT-032 discount 必填 ≤32（bs-490）
- V-MKT-033 start_at/end_at 必填且 end_at > start_at（js_guard 422 `422704` fields.end_at=before_start；bs-491）
- V-MKT-034 status 必填 ∈ {draft, scheduled, active, ended} 且与时间窗一致：scheduled 要求 start_at>now（bs-798）；active 要求 start_at≤now<end_at；**ended 不可作为创建态**（→ 422 `422704` fields.status=inconsistent_with_window）
- V-MKT-035 product_ids[] 可选：去重；经 catalogQueryPort 校验全部存在（不存在 → 422 `422704` fields.product_ids=not_exists，本端点契约无 404；bs-699）
- V-MKT-036 translations[] locale ∈ {es, fr} 不重复；name ≤64

**业务步骤（单事务 TX-MKT-004）**:
- STEP-MKT-01 INSERT flash_sale + 批量 INSERT flash_sale_product（nm）+ flash_sale_translation
- STEP-MKT-02 INSERT operation_log(action=创建闪购)
- STEP-MKT-03 提交后（status=active 时）：@CacheInvalidate `marketing:flash:*` → MQ publish `content.invalidated {event_id, type:flash_sale_changed, locales:[en,es,fr]}`（draft/scheduled 无消费端可见性变化，不发——SCHED 翻转时再发）

**出参**: 201 FlashSale
**错误映射**: 403 `40300` / 422 `422704` / 500 `50000`,`50001`

### E-MKT-19 updateAdminFlashSale — PUT /api/admin/promotions/flash-sales/{id}

**入参**: path id；body FlashSaleUpsert
- V-MKT-037 id 正整数（非法视同不存在 → 404 `404703`）；复用 V-MKT-031~036（status 一致性放宽同 E-MKT-15：不可改入 ended，ended 由 SCHED 专有）

**业务步骤（单事务 TX-MKT-005）**:
- STEP-MKT-01 `SELECT flash_sale WHERE id=?`；不存在 → 404 `404703`
- STEP-MKT-02 状态机 guard：DB status='ended' → 409 `409703`（契约「ended 活动不可编辑」；flash_sale_lifecycle ended 为终态）
- STEP-MKT-03 UPDATE flash_sale + flash_sale_product/flash_sale_translation 整单覆盖（DELETE+批量 INSERT）
- STEP-MKT-04 INSERT operation_log(action=编辑闪购)
- STEP-MKT-05 提交后（DB 或目标 status 含 active）：@CacheInvalidate `marketing:flash:*` → MQ `content.invalidated {type:flash_sale_changed}`

**出参**: 200 FlashSale
**错误映射**: 403 `40300` / 404 `404703` / 409 `409703` / 422 `422704` / 500 `50000`,`50001`

### E-MKT-20 deleteAdminFlashSale — DELETE /api/admin/promotions/flash-sales/{id} （仅 draft 可删）

**入参**: path id（V-MKT-037 口径）

**业务步骤（单事务 TX-MKT-006）**:
- STEP-MKT-01 不存在 → 404 `404703`
- STEP-MKT-02 状态机 guard：status ≠ 'draft' → 409 `409703`（契约「仅 draft 可删」；scheduled/active/ended 保留运营痕迹）
- STEP-MKT-03 物理删除 flash_sale + flash_sale_product + flash_sale_translation；INSERT operation_log(action=删除闪购)
- STEP-MKT-04 draft 无消费端可见性，不失效不发 MQ

**出参**: 204
**错误映射**: 403 `40300` / 404 `404703` / 409 `409703` / 500 `50000`

---

## 6. ADMIN Banner 端点（RBAC `/banners`，不缓存；写操作触发 `marketing:banners:*` 失效链）

### E-MKT-21 listAdminBanners — GET /api/admin/banners

**入参**: query `{ position? }`
- V-MKT-038 position 可选 ∈ {hero, featured, topbar}

**业务步骤**:
- STEP-MKT-01 `SELECT banner`（position 过滤）ORDER BY sort ASC, id ASC（契约「按 sort 排序」）
- STEP-MKT-02 批查 banner_translation 装配 translations[]；派生 `已过窗` 展示标识（now>end_time，DEC-MKT-2，仅前端展示不入契约 schema——前端按 end_time 自行派生，后端不加字段）

**出参**: 200 `{ items: Banner[] }`（含 clicks 只读列）
**错误映射**: 403 `40300` / 500 `50000`

### E-MKT-22 createAdminBanner — POST /api/admin/banners （ALIGN-009, banner_lifecycle 初态）

**入参**: body BannerUpsert
- V-MKT-039 name 必填 trim 非空 ≤128；image_url 必填 ≤512（来自 catalog E-CAT-35 presign，scope=banner；bs-030/031）
- V-MKT-040 position 必填 ∈ {hero, featured, topbar}（bs-032/398）
- V-MKT-041 start_time/end_time 可选；均给定时 end_time > start_time（js_guard 422 `422704` fields.end_time=before_start；bs-033/034/399/400）
- V-MKT-042 status 必填 ∈ {draft, published, archived}；**创建态仅允许 draft/published**（archived → 422 `422704` fields.status=invalid_initial；banner_lifecycle initial=draft，publish guard name/image_url 非空由必填校验天然满足，bs-733）
- V-MKT-043 sort 必填 int ≥ 0（bs-035）
- V-MKT-044 translations[] locale ∈ {es, fr} 不重复；title ≤255 / subtitle ≤255 / cta_text ≤64；EN 文案走主表可选列 title/subtitle/cta_text（DEC-MKT-1，长度同限）

**业务步骤（单事务 TX-MKT-007）**:
- STEP-MKT-01 INSERT banner（clicks=0 初始化）+ banner_translation 批插
- STEP-MKT-02 INSERT operation_log(action=创建Banner)
- STEP-MKT-03 提交后（status=published）：@CacheInvalidate `marketing:banners:*` → MQ `content.invalidated {event_id, type:banner_changed, locales:[en,es,fr]}` → 消费者 revalidate `/` ×3 locale + purge（FLOW-P03）

**出参**: 201 Banner
**错误映射**: 403 `40300` / 422 `422704` / 500 `50000`,`50001`

### E-MKT-23 updateAdminBanner — PUT /api/admin/banners/{id} （保存并发布语义，含排序与定时投放）

**入参**: path id；body BannerUpsert
- V-MKT-045 id 正整数（非法视同不存在 → 404 `404701`）；复用 V-MKT-039~044（status 可提交三枚举任意值——本端点为整单保存，状态迁移合法性同 E-MKT-25 STEP-MKT-02 guard）
- V-MKT-046 clicks 只读：请求体提交一律忽略

**业务步骤（单事务 TX-MKT-008）**:
- STEP-MKT-01 `SELECT banner WHERE id=?`；不存在 → 404 `404701`
- STEP-MKT-02 status 变更时校验迁移合法性（banner_lifecycle）：draft→published / published→archived / archived→published 合法；其余迁移 → 409 `409703`（同 E-MKT-25 guard，整单保存复用）
- STEP-MKT-03 UPDATE banner（SET 不含 clicks）+ banner_translation 整单覆盖
- STEP-MKT-04 INSERT operation_log(action=编辑Banner)
- STEP-MKT-05 提交后：@CacheInvalidate `marketing:banners:*` → MQ `content.invalidated {type:banner_changed}`（契约「写成功触发失效链」，draft 间编辑亦发——sort/窗口变更影响在投放清单）

**出参**: 200 Banner
**错误映射**: 403 `40300` / 404 `404701` / 409 `409703` / 422 `422704` / 500 `50000`,`50001`

### E-MKT-24 deleteAdminBanner — DELETE /api/admin/banners/{id} （banner_lifecycle 全态可删）

**入参**: path id（V-MKT-045 口径）

**业务步骤（单事务 TX-MKT-009）**:
- STEP-MKT-01 不存在 → 404 `404701`
- STEP-MKT-02 物理删除 banner + banner_translation（draft/published/archived→deleted 均合法，状态机无 guard）；INSERT operation_log(action=删除Banner)
- STEP-MKT-03 提交后：@CacheInvalidate `marketing:banners:*` → MQ `content.invalidated {type:banner_changed}`

**出参**: 204
**错误映射**: 403 `40300` / 404 `404701` / 500 `50000`

### E-MKT-25 toggleAdminBannerStatus — PATCH /api/admin/banners/{id}/status （行内 Toggle online↔，banner_lifecycle publish/take_offline/republish）

**入参**: path id；body `{ status! }`
- V-MKT-047 status 必填 ∈ {draft, published, archived}

**业务步骤（单事务 TX-MKT-010）**:
- STEP-MKT-01 不存在 → 404 `404701`
- STEP-MKT-02 幂等：目标态=当前态 → 直接返回当前行（不写审计不发事件）
- STEP-MKT-03 迁移 guard（banner_lifecycle）：合法集 {draft→published（Toggle 首次上线）, published→archived（take_offline）, archived→published（republish）}；其余（published→draft / archived→draft / draft→archived）→ 409 `409703`
- STEP-MKT-04 `UPDATE banner SET status=?`；INSERT operation_log(action=编辑Banner, changes={status: from→to})
- STEP-MKT-05 提交后：@CacheInvalidate `marketing:banners:*` → MQ `content.invalidated {type:banner_changed}` → revalidate `/` ×3 + purge

**出参**: 200 Banner
**错误映射**: 403 `40300` / 404 `404701` / 409 `409703`（契约 PATCH 响应未列 409——guard 命中时按通用 409 Conflict 信封返回，码表内 409703，登记为契约响应表增补归因） / 500 `50000`

---

## 7. ADMIN 博客端点（RBAC `/content/blog`，不缓存；写操作触发 `marketing:blogs:*`/`marketing:blog:*` 失效链）

### E-MKT-26 listAdminBlogs — GET /api/admin/content/blogs

**入参**: query `{ page?, page_size?, status?, search? }`
- V-MKT-048 page/page_size 同 V-MKT-004；status ∈ {all, draft, published, archived} 缺省 all
- V-MKT-049 search ≤80（title LIKE）

**业务步骤**:
- STEP-MKT-01 条件组装 + 分页查询 ORDER BY COALESCE(published_at, created_at) DESC, id DESC
- STEP-MKT-02 批查 blog_post_translation 装配 translations[]（列表行含 views/published_at 只读列）

**出参**: 200 Paginated`{ data: BlogPost[], ... }`
**错误映射**: 403 `40300` / 500 `50000`

### E-MKT-27 createAdminBlog — POST /api/admin/content/blogs （ALIGN-010, blog_post_lifecycle 初态）

**入参**: body BlogPostUpsert
- V-MKT-050 title 必填 trim 非空 ≤200（blog_post_lifecycle publish guard title!=null 前移为必填；bs-039）
- V-MKT-051 cover ≤512 / category ≤64 / author ≤64 可选（bs-040/041/042）
- V-MKT-052 slug 可选，匹配 `^[a-z0-9-]+$` 且 ≤128；**status=published 时必填**（发布生成静态文章页路径，缺 → 422 `422704` fields.slug=required_for_publish）；唯一 → 409 `409702`
- V-MKT-053 status 必填 ∈ {draft, published, archived}；创建态仅 draft/published（archived → 422 `422704` fields.status=invalid_initial）
- V-MKT-054 translations[] locale ∈ {es, fr} 不重复；title ≤200 / excerpt ≤500 / body TEXT / seo_title ≤128 / seo_description ≤255

**业务步骤（单事务 TX-MKT-011）**:
- STEP-MKT-01 slug 非空时查重：`SELECT id FROM blog_post WHERE slug=?`（uk_blog_slug 兜底）命中 → 409 `409702`
- STEP-MKT-02 INSERT blog_post（status=published 时记 published_at=now；views=0 初始化）+ blog_post_translation 批插
- STEP-MKT-03 INSERT operation_log(action=创建文章)
- STEP-MKT-04 提交后（status=published）：@CacheInvalidate `marketing:blogs:*` + `marketing:blog:{slug}:*` → MQ `content.invalidated {type:blog_changed, slug, locales}` → revalidate `/blog` + `/blog/{slug}` ×3 locale + purge

**出参**: 201 BlogPost
**错误映射**: 403 `40300` / 409 `409702` / 422 `422704` / 500 `50000`,`50001`

### E-MKT-28 getAdminBlog — GET /api/admin/content/blogs/{id} （文章编辑详情）

**入参**: path id
- V-MKT-055 id 正整数 int64（非法视同不存在 → 404 `404701`）

**业务步骤**:
- STEP-MKT-01 `SELECT blog_post WHERE id=?`；不存在 → 404 `404701`
- STEP-MKT-02 批查 blog_post_translation 装配 translations[]（三语 tab 全量原样，admin 不回退合并）

**出参**: 200 BlogPost（含 content 全文）
**错误映射**: 404 `404701` / 500 `50000`

### E-MKT-29 updateAdminBlog — PUT /api/admin/content/blogs/{id} （已发布保存即触发失效链, s-758）

**入参**: path id；body BlogPostUpsert
- 复用 V-MKT-050~054（slug 查重排除自身；status 整单保存迁移合法性同 E-MKT-31 guard）
- V-MKT-056 published_at/views 只读：请求体提交一律忽略

**业务步骤（单事务 TX-MKT-012）**:
- STEP-MKT-01 `SELECT blog_post WHERE id=?`；不存在 → 404 `404701`
- STEP-MKT-02 status 变更时迁移 guard（blog_post_lifecycle）：draft→published（记 published_at）/ published→archived / archived→published 合法；其余 → 409 `409703`
- STEP-MKT-03 slug 变更查重（排除自身）→ 409 `409702`
- STEP-MKT-04 UPDATE blog_post（SET 不含 views；published_at 仅在 draft→published 时写入，已发布文章编辑不刷新发布时间）+ translation 整单覆盖
- STEP-MKT-05 INSERT operation_log(action=编辑文章)
- STEP-MKT-06 提交后（DB 或目标 status=published，或 published→archived 下线）：@CacheInvalidate `marketing:blogs:*` + `marketing:blog:{slug}:*`（新旧 slug 都失效）→ MQ `content.invalidated {type:blog_changed, slug, old_slug?}` → revalidate + purge（秒级失效，s-758）

**出参**: 200 BlogPost
**错误映射**: 403 `40300` / 404 `404701` / 409 `409702`/`409703` / 422 `422704` / 500 `50000`,`50001`

### E-MKT-30 deleteAdminBlog — DELETE /api/admin/content/blogs/{id} （blog_post_lifecycle 全态可删）

**入参**: path id（V-MKT-055 口径）

**业务步骤（单事务 TX-MKT-013）**:
- STEP-MKT-01 不存在 → 404 `404701`
- STEP-MKT-02 物理删除 blog_post + blog_post_translation；INSERT operation_log(action=删除文章)
- STEP-MKT-03 提交后（原 status=published）：失效 `marketing:blogs:*` + `marketing:blog:{slug}:*` → MQ `content.invalidated {type:blog_changed, slug}`（文章页 revalidate 后 404701，列表移除）

**出参**: 204
**错误映射**: 403 `40300` / 404 `404701` / 500 `50000`

### E-MKT-31 patchAdminBlogStatus — PATCH /api/admin/content/blogs/{id}/status （发布/下线, blog_post_lifecycle）

**入参**: path id；body `{ status! }`
- V-MKT-057 status 必填 ∈ {draft, published, archived}

**业务步骤（单事务 TX-MKT-014）**:
- STEP-MKT-01 不存在 → 404 `404701`
- STEP-MKT-02 幂等：目标态=当前态 → 直接返回（不写审计不发事件）
- STEP-MKT-03 迁移 guard：draft→published（**slug 必须非空** → 否则 422 `422704` fields.slug=required_for_publish；记 published_at=now）/ published→archived（unpublish）/ archived→published（republish，published_at 保持原值）合法；其余（draft→archived / published→draft / archived→draft）→ 409 `409703`（bs-739~743）
- STEP-MKT-04 `UPDATE blog_post SET status=?[, published_at=?]`；INSERT operation_log(action=文章发布状态变更, changes={from,to})
- STEP-MKT-05 提交后：失效 + MQ + revalidate `/blog`、`/blog/{slug}` ×3 + purge（同 E-MKT-29 STEP-MKT-06）

**出参**: 200 BlogPost
**错误映射**: 403 `40300` / 404 `404701` / 409 `409703` / 422 `422704`（契约响应未列 422——publish 缺 slug 场景按码表 422704 返回，登记归因） / 500 `50000`

---

## 8. ADMIN 婚礼案例端点（RBAC `/content/weddings`，不缓存；失效 `marketing:weddings:*`/`marketing:wedding:*`）

### E-MKT-32 listAdminWeddings — GET /api/admin/content/weddings

**入参**: query `{ page?, page_size?, status? }`
- V-MKT-058 page/page_size 同 V-MKT-004；status ∈ {all, draft, published} 缺省 all

**业务步骤**:
- STEP-MKT-01 分页查询 ORDER BY id DESC（status 过滤）
- STEP-MKT-02 批查 real_wedding_product 装配 product_ids[]（Shop the Look 件数派生）+ real_wedding_translation 装配 translations[]

**出参**: 200 Paginated`{ data: RealWedding[], ... }`
**错误映射**: 403 `40300` / 500 `50000`

### E-MKT-33 createAdminWedding — POST /api/admin/content/weddings （ALIGN-011, real_wedding_publish 初态）

**入参**: body RealWeddingUpsert
- V-MKT-059 couple 必填 trim 非空 ≤64（bs-225）
- V-MKT-060 location ≤128 / theme ≤32 / wedding_date ≤16 / cover ≤512 可选（bs-226~229/500~504）
- V-MKT-061 status 必填 ∈ {draft, published}（bs-230/505）
- V-MKT-062 product_ids[] 可选：去重；catalogQueryPort 校验存在（不存在 → 422 `422704` fields.product_ids=not_exists；bs-701）
- V-MKT-063 translations[] locale ∈ {es, fr} 不重复；title ≤200 / story TEXT；EN title/story 走主表可选列（DEC-MKT-1）

**业务步骤（单事务 TX-MKT-015）**:
- STEP-MKT-01 INSERT real_wedding + real_wedding_product + real_wedding_translation 批插
- STEP-MKT-02 INSERT operation_log(action=创建婚礼案例)
- STEP-MKT-03 提交后（status=published）：@CacheInvalidate `marketing:weddings:*` + `marketing:wedding:*` → MQ `content.invalidated {type:wedding_changed, id, locales}` → revalidate `/real-weddings`、`/real-weddings/{id}`、`/` ×3 locale + purge

**出参**: 201 RealWedding
**错误映射**: 403 `40300` / 422 `422704` / 500 `50000`,`50001`

### E-MKT-34 updateAdminWedding — PUT /api/admin/content/weddings/{id}

**入参**: path id；body RealWeddingUpsert
- V-MKT-064 id 正整数（非法视同不存在 → 404 `404701`）；复用 V-MKT-059~063

**业务步骤（单事务 TX-MKT-016）**:
- STEP-MKT-01 不存在 → 404 `404701`
- STEP-MKT-02 UPDATE real_wedding + real_wedding_product/real_wedding_translation 整单覆盖；status 变更等价 publish/unpublish（real_wedding_publish 双向均合法，无非法迁移）
- STEP-MKT-03 INSERT operation_log(action=编辑婚礼案例)
- STEP-MKT-04 提交后失效 + MQ（同 E-MKT-33 STEP-MKT-03，draft 间编辑不发）

**出参**: 200 RealWedding
**错误映射**: 403 `40300` / 404 `404701` / 422 `422704` / 500 `50000`,`50001`

### E-MKT-35 deleteAdminWedding — DELETE /api/admin/content/weddings/{id}

**入参**: path id（V-MKT-064 口径）

**业务步骤（单事务 TX-MKT-017）**:
- STEP-MKT-01 不存在 → 404 `404701`
- STEP-MKT-02 物理删除 real_wedding + real_wedding_product + real_wedding_translation；INSERT operation_log(action=删除婚礼案例)
- STEP-MKT-03 提交后（原 published）失效 + MQ `content.invalidated {type:wedding_changed, id}`

**出参**: 204
**错误映射**: 403 `40300` / 404 `404701` / 500 `50000`

### E-MKT-36 patchAdminWeddingStatus — PATCH /api/admin/content/weddings/{id}/status （real_wedding_publish）

**入参**: path id；body `{ status! }`
- V-MKT-065 status 必填 ∈ {draft, published}

**业务步骤（单事务 TX-MKT-018）**:
- STEP-MKT-01 不存在 → 404 `404701`
- STEP-MKT-02 幂等：同态 → 直接返回（bs-888/889 重复 publish 仅一次副作用）
- STEP-MKT-03 draft→published（publish）/ published→draft（unpublish）双向合法（real_wedding_publish 两迁移；二态机无非法迁移，409 仅同请求并发冲突由幂等短路吸收，bs-589/590）
- STEP-MKT-04 UPDATE status；INSERT operation_log(action=案例发布状态变更)
- STEP-MKT-05 提交后失效 + MQ + revalidate + purge（同 E-MKT-33 STEP-MKT-03）

**出参**: 200 RealWedding
**错误映射**: 403 `40300` / 404 `404701` / 409 `409703`（保留映射：未来扩展态/并发整单保存冲突兜底） / 500 `50000`

---

## 9. ADMIN Lookbook 端点（RBAC `/content/lookbook`，不缓存；失效 `marketing:lookbooks:*`/`marketing:lookbook:*`）

### E-MKT-37 listAdminLookbooks — GET /api/admin/content/lookbooks

**入参**: query `{ status? }`
- V-MKT-066 status ∈ {all, draft, published} 缺省 all

**业务步骤**:
- STEP-MKT-01 `SELECT lookbook`（status 过滤）ORDER BY id DESC + 批查 lookbook_product（件数派生）+ lookbook_translation

**出参**: 200 `{ items: Lookbook[] }`
**错误映射**: 403 `40300` / 500 `50000`

### E-MKT-38 createAdminLookbook — POST /api/admin/content/lookbooks （ALIGN-012, lookbook_publish 初态）

**入参**: body LookbookUpsert
- V-MKT-067 title 必填 trim 非空 ≤128（bs-215）
- V-MKT-068 theme ≤32 可选（bs-216/493）；description EN ≤500 可选（DEC-MKT-1）
- V-MKT-069 status 必填 ∈ {draft, published}（bs-217/494）
- V-MKT-070 product_ids[] 去重 + catalogQueryPort 存在性校验（→ 422 `422704` fields.product_ids=not_exists；bs-700）
- V-MKT-071 translations[] locale ∈ {es, fr} 不重复；title ≤128 / description ≤500

**业务步骤（单事务 TX-MKT-019）**:
- STEP-MKT-01 INSERT lookbook + lookbook_product + lookbook_translation 批插
- STEP-MKT-02 INSERT operation_log(action=创建Lookbook)
- STEP-MKT-03 提交后（published）：@CacheInvalidate `marketing:lookbooks:*` + `marketing:lookbook:*` → MQ `content.invalidated {type:lookbook_changed, id, locales}` → revalidate `/inspiration` ×3 + purge

**出参**: 201 Lookbook
**错误映射**: 403 `40300` / 422 `422704` / 500 `50000`,`50001`

### E-MKT-39 updateAdminLookbook — PUT /api/admin/content/lookbooks/{id}

**入参**: path id；body LookbookUpsert
- V-MKT-072 id 正整数（非法视同不存在 → 404 `404701`）；复用 V-MKT-067~071

**业务步骤（单事务 TX-MKT-020）**:
- STEP-MKT-01 不存在 → 404 `404701`
- STEP-MKT-02 UPDATE lookbook + 子表整单覆盖；INSERT operation_log(action=编辑Lookbook)
- STEP-MKT-03 提交后失效 + MQ（同 E-MKT-38 STEP-MKT-03 口径）

**出参**: 200 Lookbook
**错误映射**: 403 `40300` / 404 `404701` / 422 `422704` / 500 `50000`,`50001`

### E-MKT-40 deleteAdminLookbook — DELETE /api/admin/content/lookbooks/{id}

**入参**: path id（V-MKT-072 口径）

**业务步骤（单事务 TX-MKT-021）**:
- STEP-MKT-01 不存在 → 404 `404701`
- STEP-MKT-02 物理删除 lookbook + lookbook_product + lookbook_translation；INSERT operation_log(action=删除Lookbook)
- STEP-MKT-03 提交后（原 published）失效 + MQ

**出参**: 204
**错误映射**: 403 `40300` / 404 `404701` / 500 `50000`

### E-MKT-41 patchAdminLookbookStatus — PATCH /api/admin/content/lookbooks/{id}/status （lookbook_publish）

**入参**: path id；body `{ status! }`
- V-MKT-073 status 必填 ∈ {draft, published}

**业务步骤（单事务 TX-MKT-022）**:
- STEP-MKT-01 不存在 → 404 `404701`；同态幂等短路（bs-885/886）
- STEP-MKT-02 publish/unpublish 双向合法 → UPDATE status；INSERT operation_log(action=Lookbook发布状态变更)
- STEP-MKT-03 提交后失效 + MQ + revalidate `/inspiration` ×3 + purge

**出参**: 200 Lookbook
**错误映射**: 403 `40300` / 404 `404701` / 409 `409703`（扩展态兜底保留） / 500 `50000`

---

## 10. ADMIN 指南端点（RBAC `/content/lookbook`（契约口径：与 Lookbook 同页同权限），不缓存；失效 `marketing:guides:*`）

### E-MKT-42 listAdminGuides — GET /api/admin/content/guides

**入参**: query `{ status? }`
- V-MKT-074 status ∈ {all, draft, published} 缺省 all

**业务步骤**:
- STEP-MKT-01 `SELECT guide`（status 过滤）ORDER BY phase ASC, id ASC + 批查 guide_translation

**出参**: 200 `{ items: Guide[] }`
**错误映射**: 403 `40300` / 500 `50000`

### E-MKT-43 createAdminGuide — POST /api/admin/content/guides （ALIGN-012, guide_publish 初态）

**入参**: body GuideUpsert
- V-MKT-075 phase 必填 trim 非空 ≤32（bs-219）
- V-MKT-076 timeframe ≤64 可选（bs-220/497）；body EN TEXT 可选（DEC-MKT-1）
- V-MKT-077 title 必填 trim 非空 ≤128（bs-221/498）
- V-MKT-078 tasks_count 可选 int ≥ 0 缺省 0（bs-222）
- V-MKT-079 status 必填 ∈ {draft, published}（bs-223/499）
- V-MKT-080 translations[] locale ∈ {es, fr} 不重复；title ≤128 / body TEXT

**业务步骤（单事务 TX-MKT-023）**:
- STEP-MKT-01 INSERT guide + guide_translation 批插
- STEP-MKT-02 INSERT operation_log(action=创建指南)
- STEP-MKT-03 提交后（published）：@CacheInvalidate `marketing:guides:*` → MQ `content.invalidated {type:guide_changed, locales}` → revalidate `/wedding-guides` ×3 + purge

**出参**: 201 Guide
**错误映射**: 403 `40300` / 422 `422704` / 500 `50000`,`50001`

### E-MKT-44 updateAdminGuide — PUT /api/admin/content/guides/{id}

**入参**: path id；body GuideUpsert
- V-MKT-081 id 正整数（非法视同不存在 → 404 `404701`）；复用 V-MKT-075~080

**业务步骤（单事务 TX-MKT-024）**:
- STEP-MKT-01 不存在 → 404 `404701`
- STEP-MKT-02 UPDATE guide + translation 整单覆盖；INSERT operation_log(action=编辑指南)
- STEP-MKT-03 提交后失效 + MQ（同 E-MKT-43 STEP-MKT-03 口径）

**出参**: 200 Guide
**错误映射**: 403 `40300` / 404 `404701` / 422 `422704` / 500 `50000`,`50001`

### E-MKT-45 deleteAdminGuide — DELETE /api/admin/content/guides/{id}

**入参**: path id（V-MKT-081 口径）

**业务步骤（单事务 TX-MKT-025）**:
- STEP-MKT-01 不存在 → 404 `404701`
- STEP-MKT-02 物理删除 guide + guide_translation；INSERT operation_log(action=删除指南)
- STEP-MKT-03 提交后（原 published）失效 + MQ

**出参**: 204
**错误映射**: 403 `40300` / 404 `404701` / 500 `50000`

### E-MKT-46 patchAdminGuideStatus — PATCH /api/admin/content/guides/{id}/status （guide_publish）

**入参**: path id；body `{ status! }`
- V-MKT-082 status 必填 ∈ {draft, published}

**业务步骤（单事务 TX-MKT-026）**:
- STEP-MKT-01 不存在 → 404 `404701`；同态幂等短路（bs-887）
- STEP-MKT-02 publish/unpublish 双向合法 → UPDATE status；INSERT operation_log(action=指南发布状态变更)
- STEP-MKT-03 提交后失效 + MQ + revalidate `/wedding-guides` ×3 + purge

**出参**: 200 Guide
**错误映射**: 403 `40300` / 404 `404701` / 409 `409703`（扩展态兜底保留） / 500 `50000`

---

## 11. 自检

- [x] 46 端点全覆盖（store content 8 + flash-sales 1 + coupons/validate 1 + newsletter 1 + contact 1 = 12；admin coupons 4 + flash-sales 4 + banners 5 + blogs 6 + weddings 5 + lookbooks 5 + guides 5 = 34）＝ E-MKT-01 ~ E-MKT-46
- [x] 每端点四部分齐全（入参验证 / 业务步骤 / 出参构造 / 错误码映射）
- [x] V-MKT-001 ~ V-MKT-082 全域连续唯一；STEP-MKT-NN 每端点独立编号段（端点号 E-MKT-NN 提供唯一溯源前缀）
- [x] 错误码全部出自 marketing-api.openapi.yml 码表（404701~404703 / 409701~409703 / 422701~422704）+ identity 复用码（40100/40300/50000/50001），无臆造；状态机 guard 一律 409703；字段/时间窗/引用校验一律 422704
- [x] 券校验 200+valid=false+reason_code 口径落地（E-MKT-10 STEP-MKT-02 顺序固定，仅格式错误抛 422704）；核销归 trading 事务内直调（marketing-data-detail SVC-MKT-01）
- [x] 闪购状态机自动下线（DEC-MKT-3 + SCHED-MKT-01）；Banner 窗口过滤口径定稿（DEC-MKT-2）
- [x] 四类内容发布状态机（blog/wedding/lookbook/guide）+ banner/coupon/flash 共七状态机全部落到端点 guard 或 SCHED；三语 translation tab 进出口径明确（admin 原样 / store 回退合并）
- [x] Newsletter 幂等不泄露存在性（E-MKT-11 STEP-MKT-03）；公开端点白名单 4 条 pattern 登记（0.1 节，coupons/validate 显式排除）
- [x] 缓存键/TTL/失效触发者与 data-flow 缓存矩阵一致；写端点全部标注失效链 + MQ 事件 + OperationLog action；coupon 不发 MQ 归因明确
- [x] 事务边界 TX-MKT-001 ~ TX-MKT-026 与 marketing-data-detail.md 一一对应；DEC-MKT-1~6 设计决策归因记录
