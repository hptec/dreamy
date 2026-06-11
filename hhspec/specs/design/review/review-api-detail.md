# review API 详细设计（L2）

> 角色: l2_api_designer ｜ change: portal-api-integration ｜ domain: review
> 方法论：每端点四部分 — 入参验证(V-REV-NNN，全域连续唯一) / 业务步骤(STEP-REV-NN，每端点独立编号段，溯源以「端点编号 E-REV-NN + STEP-REV-NN」组合唯一) / 出参构造 / 错误码映射。
> 来源权威：api-contracts/review-api.openapi.yml v1.1.0（15 端点）+ data-flow.md（FLOW-P14 + FLOW-P17 + 缓存矩阵 + MQ 拓扑）+ error-strategy.md（review 域段 8，10 码）+ er-diagram.yml（Review/ReviewImage/ProductQuestion）+ state-machine.yml（review_moderation/review_image_visibility/question_answer_flow）+ decision.md 决策 2/3/9/15(评价审核归 ALIGN-014)/BE-DIM-6/7/8。
> 伪代码级，不绑定 Spring 语法。线上响应统一 huihao R 包络 `{code,message,data}`；分页统一 huihao.page.Paginated（data/total_elements/page_number/page_size/number_of_elements/total_pages）；评价/后台列表的聚合附加字段以 **Paginated 子类平铺**实现（定稿见 review-data-detail.md MAP-REV-006/007）；JSON 字段一律 snake_case。

## 0. 全局横切（所有端点适用）

- **鉴权过滤器**：
  - `/api/store/*` → StoreJwtFilter（STORE_JWT_SECRET）。本域 2 个消费端读端点（E-REV-01/E-REV-03）为**匿名公开**，经配置化公开路径白名单放行（见 0.1）；提交端点（E-REV-02/04/05）StoreBearerAuth，`customer_id = JWT subject`（BE-DIM-6 强隔离，请求体不接收 user_id 字段）。
  - `/api/admin/*` → AdminJwtFilter（ADMIN_JWT_SECRET）+ RBAC 菜单权限 key 守卫：本域全部后台端点统一 **`/reviews`**（本 change 新增权限点，含 admin-questions——契约 security 同 key）。缺权限 → 403 `40300`；跨端 token 误用 → 401 `40100`。
- **i18n**：评价/问答**内容数据不做多语翻译**（契约缓存标注：key 不含 locale）；store 端错误 message 按 `locale` query（en/es/fr，缺省 en）返回，admin 端固定中文；前端按 code 映射文案（决策 27）。
- **审计（admin 写操作，BE-DIM-7）**：AOP 切面写 operation_log；本域 action 枚举严格沿用 error-strategy 权威清单的 3 个：`评价审核` / `评价批量操作` / `回答提问`。归入规则（与 catalog「flags 归入编辑商品」同口径）：设/取消精选、官方回复创建/编辑/删除、图片驳回/恢复 → 归入 `评价审核`（changes 记录子类型 + before/after）；Q&A 可见性切换 → 归入 `回答提问`（changes 记录 visible from/to）。
- **缓存（BE-DIM-8）**：E-REV-01/E-REV-03 走 JetCache 两级 + CDN `s-maxage=60`（契约标注短缓存）；后台写端点提交后 `@CacheInvalidate` + MQ 失效链（EVT-REV-001/002，PDP 同步刷新）；提交/审核端点一律不缓存。key/TTL 详见 review-data-detail.md 第 7 节（CACHE-REV-*）。
- **422 字段级错误结构**（error-strategy L2 要求 1）：`MethodArgumentNotValidException`/手工校验失败 → 422 `422801`，`details` 形如 `{ "fields": { "<field>": "<reason_key>" } }`（线上装入 R.data）；store 端 reason_key 由 next-intl 字典渲染，admin 端后端直出中文。
- **跨域端口（决策 3，进程内直调防腐层，禁止跨域直查表）**：
  - `TradingPurchaseQueryPort`（trading 域实现，本域消费）：`hasCompletedOrderContaining(customerId, productId) -> boolean` —— 落地 SQL（trading 域内）：`EXISTS(SELECT 1 FROM orders o JOIN order_line ol ON ol.order_id=o.id WHERE o.customer_id=? AND o.status='completed' AND ol.product_id=?)`；403801 越权防护唯一数据源（s-756/s-762）
  - `CatalogSnapshotPort`（catalog 域实现）：`getProductBrief(productId) / getProductBriefs(productIds)` → `{id, slug, name, status}` —— 商品存在性/published 校验（不存在或未发布 → 透传 404 `404501`，与 trading V-TRD-002 同口径）+ admin 列表 product_name 派生 + 失效事件 slug 取得
  - `IdentityQueryPort`（identity 域既有）：`getUserName(customerId) -> String` —— customer_name/asker 姓名快照
  - `StoragePresignPort`（catalog 域代管媒体基建，FLOW-P17）：`presign(objectKey, contentType) -> {upload_url, public_url, expires_at}`；超时 3s，失败 → 502 `502801`
  - `ReviewQueryPort`（**本域提供**，catalog 域 EVT-CAT-002 消费）：`approvedRatingSummary(productId) -> {rating_avg, rating_count}`（RM-REV-002 同源聚合）
- **错误码边界**：本域只产出 review 域段 8 的 10 码 + 透传 catalog `404501`（商品引用校验，trading 已有先例）+ identity 复用码（40100/40300/50000/50001）。

### 0.1 StoreJwtFilter 公开路径白名单（本域登记条目，error-strategy L2 要求 2）

白名单为配置化 pattern 列表（`dreamy.security.store-public-paths`，AntPath 风格，七域共用同一机制）。本域两个公开端点与提交端点**同路径不同 method**（GET 公开 / POST 鉴权），因此白名单条目须支持 **`METHOD:pattern`** 形式（catalog/trading 既有条目无 method 前缀时缺省匹配全部 method，向后兼容）。**review 域登记 2 条**：

| 白名单条目 | 覆盖端点 | 不放行 |
|---|---|---|
| `GET:/api/store/reviews` | E-REV-01 评价列表 | `POST /api/store/reviews`（E-REV-02 仍强制鉴权） |
| `GET:/api/store/questions` | E-REV-03 Q&A 列表 | `POST /api/store/questions`（E-REV-04 仍强制鉴权） |

`/api/store/uploads/presign` **不入白名单**（StoreBearerAuth）。公开端点的滥用防护在 Cloudflare WAF 层（决策 11），后端不实现限流。

---

## 1. STORE 评价端点

### E-REV-01 listStoreReviews — GET /api/store/reviews （FLOW-P14 读侧, ALIGN-014, FLOW-P01 缓存口径）

**公开端点**：白名单 `GET:/api/store/reviews`。

**入参**: query `{ product_id!, sort?, page?, page_size? }`
- V-REV-001 product_id 必填正整数 int64（缺/非法 → 422 `422801` fields.product_id=required|invalid）
- V-REV-002 sort ∈ {newest, rating_desc, rating_asc, featured_first}，缺省 featured_first（枚举外 → 422 `422801` fields.sort=invalid_enum）
- V-REV-003 page ≥ 1 缺省 1；page_size 1..100 缺省 20（越界 → 422 `422801`）

**业务步骤**:
- STEP-REV-01 查 JetCache `review:reviews:{product_id}:{sort}:{page}:{page_size}`（TTL 300s，key 不含 locale——评价内容不翻译）命中即返回
- STEP-REV-02 查询 `review WHERE product_id=? AND status='approved'`，排序映射：featured_first=`featured DESC, submitted_at DESC`；newest=`submitted_at DESC`；rating_desc=`rating DESC, submitted_at DESC`；rating_asc=`rating ASC, submitted_at DESC`；分页 LIMIT/OFFSET（IDX-REV-002）
- STEP-REV-03 批查 `review_image WHERE review_id IN (...) AND rejected=0`（前台排除驳回图 js_guard，单次 IN 防 N+1）
- STEP-REV-04 聚合派生：`aggregateApproved(product_id)` 单条 GROUP BY rating 查询 → rating_avg（DECIMAL 保留 2 位，零评价=0）/ rating_count / rating_breakdown{1..5 各档数量}
- STEP-REV-05 customer_name 脱敏输出（MAP-REV-001 规则：名 + 姓首字母.）
- STEP-REV-06 装配 `StoreReviewListDTO`（Paginated 子类，rating_avg/rating_count/rating_breakdown 与六分页字段平铺同层）→ 写 JetCache TTL 300s（空页/商品不存在同样缓存，穿透保护）→ 响应头 `Cache-Control: s-maxage=60`
- 商品不存在/未发布：契约本端点无 404 响应 → 返回空页（rating_count=0），与「商品无评价」同口径（防探测语义）

**出参**: 200 StoreReviewListResponse（Paginated 六字段 + rating_avg + rating_count + rating_breakdown 平铺）
**错误映射**: 422 `422801` / 500 `50000`

### E-REV-02 createStoreReview — POST /api/store/reviews （FLOW-P14, s-756/s-762, ALIGN-014）

**StoreBearerAuth**；customer_id=JWT subject。不缓存。

**入参**: body StoreReviewCreate `{ product_id!, rating!, content?, images?[] }`
- V-REV-004 product_id 必填且经 CatalogSnapshotPort 校验存在且 published（否则 404 `404501` 透传 catalog，契约 404 注记「商品不存在」）
- V-REV-005 rating 必填整数 1..5（0/6/非整数 → 422 `422801` fields.rating=out_of_range；bs-508/509）
- V-REV-006 content 可选 ≤5000；trim 后空串视为未提供（存 null）
- V-REV-007 images[] 可选 maxItems 9；每项 url 必填 ≤512 且必须命中本站对象存储 public_url 前缀 + `review/` 对象 key 段（决策 9 预签名归类；外链/越权前缀 → 422 `422801` fields.images=invalid_url）

**业务步骤（单事务 TX-REV-001）**:
- STEP-REV-01 越权防护（403801 核心，s-756/s-762）：`TradingPurchaseQueryPort.hasCompletedOrderContaining(customerId, product_id)`=false → 403 `403801` REVIEW_NOT_ALLOWED（跨 trading 领域服务接口校验，进程内直调，不直查 orders 表）
- STEP-REV-02 唯一性预检：`SELECT id FROM review WHERE user_id=? AND product_id=?` 命中 → 409 `409801` ALREADY_REVIEWED（uk_review_user_product 唯一索引兜底并发双提交，冲突同映射 409801）
- STEP-REV-03 `IdentityQueryPort.getUserName(customerId)` → customer_name 全名快照落库（store 读输出时脱敏，MAP-REV-001）
- STEP-REV-04 INSERT review(status='pending', featured=0, submitted_at=now) + 批量 INSERT review_image(rejected=0)
- STEP-REV-05 不失效缓存、不发 MQ（pending 前台不可见，审核通过才进入失效链）；不写 operation_log（非后台操作）

**出参**: 201 StoreReview（status=pending 回执；本人回执 images 全量、customer_name 原样不脱敏——本人可见自己提交内容）
**错误映射**: 401 `40100` / 403 `403801` / 404 `404501` / 409 `409801` / 422 `422801` / 500 `50000`,`50001`

---

## 2. STORE Q&A 端点

### E-REV-03 listStoreQuestions — GET /api/store/questions

**公开端点**：白名单 `GET:/api/store/questions`。

**入参**: query `{ product_id!, page?, page_size? }`
- V-REV-008 product_id 必填正整数 int64（同 V-REV-001 口径）
- V-REV-009 page/page_size 同 V-REV-003

**业务步骤**:
- STEP-REV-01 查 JetCache `review:questions:{product_id}:{page}:{page_size}`（TTL 300s）命中即返回
- STEP-REV-02 查询 `product_question WHERE product_id=? AND visible='visible' AND answer IS NOT NULL` ORDER BY asked_at DESC 分页（双条件过滤：未回答即使 visible 也不出前台，CV-REV-009；IDX-REV-005）
- STEP-REV-03 asker 脱敏输出（MAP-REV-004，同 MAP-REV-001 规则）
- STEP-REV-04 装配标准 Paginated → 写 JetCache TTL 300s（空页同样缓存）→ `Cache-Control: s-maxage=60`
- 商品不存在 → 空页（同 E-REV-01 口径）

**出参**: 200 StoreQuestionListResponse（标准 Paginated 六字段，无扩展）
**错误映射**: 422 `422801` / 500 `50000`

### E-REV-04 createStoreQuestion — POST /api/store/questions

**StoreBearerAuth**。不缓存。

**入参**: body `{ product_id!, question! }`
- V-REV-010 product_id 必填且存在且 published（否则 404 `404501` 透传）
- V-REV-011 question 必填，trim 后长度 1..1000（空/超长 → 422 `422801` fields.question）

**业务步骤（单事务 TX-REV-010）**:
- STEP-REV-01 `IdentityQueryPort.getUserName(customerId)` → asker 姓名快照
- STEP-REV-02 INSERT product_question(user_id=customerId, visible='hidden', asked_at=now, answer=null)（默认 hidden，待后台回答后置 visible——question_answer_flow 初始态 unanswered）
- STEP-REV-03 不失效缓存不发 MQ（hidden+未回答前台不可见）

**出参**: 201 StoreQuestion（answer/answer_time 为空；asker 本人回执原样）
**错误映射**: 401 `40100` / 404 `404501` / 422 `422801` / 500 `50000`,`50001`

---

## 3. STORE 买家秀上传端点（决策 9, FLOW-P17）

### E-REV-05 presignStoreUpload — POST /api/store/uploads/presign

**StoreBearerAuth**（不入白名单）。scope 固定 `review`（契约：仅用于评价图片直传）；复用 catalog 域代管的 S3 预签名基建（StoragePresignPort），本端点归 review 域 Controller。

**入参**: body `{ file_name!, content_type! }`
- V-REV-012 file_name 必填 ≤255；sanitize（去路径分隔符/控制字符，仅保留 `[A-Za-z0-9._-]`，空结果 → 422 `422801` fields.file_name）——与 catalog V-CAT-069 同口径
- V-REV-013 content_type 必填 ∈ {image/jpeg, image/png, image/webp}（买家秀仅图片，**不含 video/mp4**——区别于 admin presign；白名单外 → 422 `422801` fields.content_type=unsupported）

**业务步骤**:
- STEP-REV-01 生成对象 key：`review/{雪花序id}/{sanitizedFileName}`（review/ 前缀归类，V-REV-007 校验依据）
- STEP-REV-02 `StoragePresignPort.presign(objectKey, contentType)` 生成预签名 PUT URL（有效期 600s；超时 3s）
- STEP-REV-03 S3 不可达/超时 → 502 `502801` OBJECT_STORAGE_UNAVAILABLE（决策 9 降级：前端提示稍后重试，评价可先不带图提交）
- STEP-REV-04 拼装 public_url（CDN 域名 + object_key）；不写 operation_log、不发 MQ、不缓存

**出参**: 200 `{ upload_url, object_key, public_url, expires_at }`
**错误映射**: 401 `40100` / 422 `422801` / 502 `502801` / 500 `50000`

---

## 4. ADMIN 评价端点（AdminBearerAuth + RBAC `/reviews`，不缓存）

### E-REV-06 listAdminReviews — GET /api/admin/reviews （ALIGN-014 Reviews.vue 评价审核 tab）

**入参**: query `{ page?, page_size?, status?, rating?, featured?, product_id?, search? }`
- V-REV-014 page/page_size 同 V-REV-003
- V-REV-015 status ∈ {all, pending, approved, rejected} 缺省 all
- V-REV-016 rating 可选整数 1..5（越界 → 422 `422801`）
- V-REV-017 featured 可选 boolean（原型「精选」chip → status=all&featured=true）
- V-REV-018 product_id 可选正整数 int64
- V-REV-019 search ≤80（trim 后空视为未提供；客户名/评价内容模糊：`customer_name LIKE %s% OR content LIKE %s%`）

**业务步骤**:
- STEP-REV-01 组装条件（status≠all 过滤 + rating + featured + product_id + search）；分页查询 ORDER BY submitted_at DESC
- STEP-REV-02 批查 review_image（**全量含 rejected=true**，后台审图需要）+ CatalogSnapshotPort.getProductBriefs(productIds) 派生 product_name（单次批量防 N+1；商品已删除 → product_name=null 容忍）
- STEP-REV-03 `countPending()` 全表 pending 总数（不随筛选变化，状态 chips 角标派生）
- STEP-REV-04 装配 `AdminReviewListDTO`（Paginated 子类 + pending_count 平铺同层）

**出参**: 200 AdminReviewListResponse
**错误映射**: 401 `40100` / 403 `40300` / 422 `422801` / 500 `50000`

### E-REV-07 patchAdminReviewStatus — PATCH /api/admin/reviews/{id}/status （review_moderation: pending→approved/rejected, FLOW-P14）

**入参**: path id；body `{ status! }`
- V-REV-020 id 正整数 int64（非法视同不存在 → 404 `404801`）
- V-REV-021 status 必填 ∈ {approved, rejected}（枚举外 → 422 `422801`；bs-510）

**业务步骤（单事务 TX-REV-002）**:
- STEP-REV-01 `SELECT review WHERE id=?`；不存在 → 404 `404801`
- STEP-REV-02 CAS 状态机 guard（js_guard 后端兜底 + 并发双审防护 bs-591）：`UPDATE review SET status=:to, featured=(CASE WHEN :to='rejected' THEN 0 ELSE featured END) WHERE id=? AND status='pending'`；affected=0 → 409 `409802` REVIEW_STATE_INVALID（reject 强制 featured=false，state-machine guard）
- STEP-REV-03 INSERT operation_log(action=评价审核, changes={from:'pending', to, featured_forced?})
- STEP-REV-04 提交后失效链：@CacheInvalidate `review:reviews:{product_id}:*` → MQ publish `review.moderated {event_id, product_id, review_id, status, occurred_at}`（EVT-REV-001，catalog q.catalog.rating 回写 rating_avg/rating_count）+ `content.invalidated {type:review_changed, slug, locales:[en,es,fr]}`（EVT-REV-002，q.invalidate → revalidatePath('/product/{slug}')×3 + Cloudflare purge，PDP 同步刷新；slug 经 CatalogSnapshotPort 取得；MQ 失败不回滚，TTL 兜底）

**出参**: 200 AdminReview（全量回读，images 含 rejected）
**错误映射**: 403 `40300` / 404 `404801` / 409 `409802` / 422 `422801` / 500 `50000`,`50001`

### E-REV-08 patchAdminReviewFeatured — PATCH /api/admin/reviews/{id}/featured （review_moderation: set_featured/unset_featured）

**入参**: path id；body `{ featured! }`
- V-REV-022 id 同 V-REV-020 口径
- V-REV-023 featured 必填 boolean（缺 → 422 `422801`）

**业务步骤（单事务 TX-REV-003）**:
- STEP-REV-01 review 不存在 → 404 `404801`
- STEP-REV-02 幂等：目标值=当前值 → 直接返回当前行（不写审计不发事件）
- STEP-REV-03 featured=true：CAS `UPDATE review SET featured=1 WHERE id=? AND status='approved'`；affected=0 → 409 `409803` FEATURED_REQUIRES_APPROVED（js_guard 后端兜底；并发 batch_reject+set_featured 仅一方成功，bs-593）。featured=false：`UPDATE review SET featured=0 WHERE id=?`（任意状态允许取消）
- STEP-REV-04 INSERT operation_log(action=评价审核, changes={featured from/to})（归入规则，0 节）
- STEP-REV-05 提交后失效 `review:reviews:{product_id}:*` + MQ `content.invalidated {type:review_changed, slug, locales}`（**不发 review.moderated**——精选不改变 rating 聚合）

**出参**: 200 AdminReview
**错误映射**: 403 `40300` / 404 `404801` / 409 `409803` / 422 `422801` / 500 `50000`

### E-REV-09 batchAdminReviews — POST /api/admin/reviews/batch （ALIGN-014 batchSet, review_moderation: batch_approve/batch_reject）

**入参**: body `{ ids!, action! }`
- V-REV-024 ids 必填数组 minItems 1，元素正整数，去重后处理；上限 200（超出 → 422 `422801` fields.ids=too_many，防滥用——原型单页 10 条全选远低于此）
- V-REV-025 action 必填 ∈ {approve, reject, feature, unfeature}

**业务步骤（单事务 TX-REV-004）**:
- STEP-REV-01 `SELECT review WHERE id IN (...)`；不存在的 id → 直接归入 skipped_ids（批量语义不 404）
- STEP-REV-02 逐条按 action guard 分拣（state-machine：batchSet 不限当前状态；guard 不满足跳过，契约 skipped_ids 语义）：
  - approve：status≠approved → 置 approved（pending→approved 与 rejected→batch_approve 均合法）；已 approved → skipped
  - reject：status≠rejected → 置 rejected 且**强制 featured=0**（state-machine guard）；已 rejected → skipped
  - feature：status='approved' 且 featured=0 → 置 featured=1；其余（pending/rejected/已精选）→ skipped（409803 的批量语义=跳过不报错）
  - unfeature：featured=1 → 置 0；其余 → skipped
- STEP-REV-03 可更新集逐条 CAS UPDATE（WHERE id=? AND status=<读取态>；并发被他人抢先 → affected=0 转入 skipped_ids，量级 ≤200 单事务内循环可接受，精确保证 updated/skipped 语义）
- STEP-REV-04 INSERT operation_log(action=评价批量操作, changes={action, updated_ids, skipped_ids})
- STEP-REV-05 updated_ids 非空时提交后失效链：按受影响 product_id 集合逐一 @CacheInvalidate `review:reviews:{pid}:*`；approve/reject → 按 product_id 去重逐一 publish `review.moderated`（rating 回写）+ `content.invalidated`；feature/unfeature → 仅 `content.invalidated`

**出参**: 200 `{ updated_ids, skipped_ids }`
**错误映射**: 403 `40300` / 422 `422801` / 500 `50000`,`50001`

### E-REV-10 putAdminReviewReply — PUT /api/admin/reviews/{id}/reply （Reviews.vue saveReply）

**入参**: path id；body `{ reply_content! }`
- V-REV-026 id 同 V-REV-020 口径
- V-REV-027 reply_content 必填，trim 后长度 1..2000（trim 空 → 422 `422801` fields.reply_content=blank，js_guard `replyDraft.trim()` 后端兜底）

**业务步骤（单事务 TX-REV-005）**:
- STEP-REV-01 review 不存在 → 404 `404801`
- STEP-REV-02 guard：status≠'approved' → 409 `409804` REPLY_REQUIRES_APPROVED（js_guard detailReview.status=='approved' 后端兜底）
- STEP-REV-03 `UPDATE review SET reply_author=:displayName, reply_content=:trimmed, reply_time=now WHERE id=?`——reply_author 固定写入操作者展示名（配置项 `dreamy.review.reply-author`，缺省 `"Dreamy Team"`，与原型署名一致）；创建与编辑同一 UPSERT 语义（PUT）
- STEP-REV-04 INSERT operation_log(action=评价审核, changes={reply before/after})（归入规则）
- STEP-REV-05 提交后失效 `review:reviews:{product_id}:*` + MQ `content.invalidated {type:review_changed, slug, locales}`

**出参**: 200 AdminReview
**错误映射**: 403 `40300` / 404 `404801` / 409 `409804` / 422 `422801` / 500 `50000`

### E-REV-11 deleteAdminReviewReply — DELETE /api/admin/reviews/{id}/reply （Reviews.vue deleteReply）

**入参**: path id
- V-REV-028 id 同 V-REV-020 口径

**业务步骤（单事务 TX-REV-006）**:
- STEP-REV-01 review 不存在 → 404 `404801`
- STEP-REV-02 幂等：reply_content 已为空 → 直接 204（不写审计不发事件）
- STEP-REV-03 `UPDATE review SET reply_author=NULL, reply_content=NULL, reply_time=NULL WHERE id=?`（契约：清空三字段）
- STEP-REV-04 INSERT operation_log(action=评价审核, changes={reply_deleted, before})
- STEP-REV-05 提交后失效 + MQ `content.invalidated {type:review_changed, slug, locales}`

**出参**: 204
**错误映射**: 403 `40300` / 404 `404801` / 500 `50000`

### E-REV-12 patchAdminReviewImage — PATCH /api/admin/reviews/{id}/images/{imageId} （Reviews.vue rejectImage/restoreImage, review_image_visibility）

**入参**: path id + imageId；body `{ rejected! }`
- V-REV-029 id/imageId 正整数 int64（非法视同不存在 → 404 `404801`/`404803`）
- V-REV-030 rejected 必填 boolean（缺 → 422 `422801`）

**业务步骤（单事务 TX-REV-007）**:
- STEP-REV-01 review 不存在 → 404 `404801`
- STEP-REV-02 `SELECT review_image WHERE id=:imageId AND review_id=:id`；不存在或不属于该评价 → 404 `404803` REVIEW_IMAGE_NOT_FOUND
- STEP-REV-03 幂等：目标值=当前值 → 直接返回当前行（并发重复驳回/恢复只执行一次副作用，bs-594/595）
- STEP-REV-04 `UPDATE review_image SET rejected=? WHERE id=?`（shown↔rejected 双向，state-machine review_image_visibility）
- STEP-REV-05 INSERT operation_log(action=评价审核, changes={image_id, rejected from/to})（归入规则）
- STEP-REV-06 提交后失效 `review:reviews:{product_id}:*` + MQ `content.invalidated {type:review_changed, slug, locales}`（rejected=true 后前台立即不展示该图）

**出参**: 200 ReviewImage
**错误映射**: 403 `40300` / 404 `404801`,`404803` / 422 `422801` / 500 `50000`

---

## 5. ADMIN Q&A 端点（AdminBearerAuth + RBAC `/reviews`，不缓存）

### E-REV-13 listAdminQuestions — GET /api/admin/questions （Reviews.vue Q&A 管理 tab）

**入参**: query `{ page?, page_size?, product_id?, answered? }`
- V-REV-031 page/page_size 同 V-REV-003
- V-REV-032 product_id 可选正整数 int64
- V-REV-033 answered ∈ {all, answered, unanswered} 缺省 all（answered→`answer IS NOT NULL`；unanswered→`answer IS NULL`）

**业务步骤**:
- STEP-REV-01 组装条件分页查询 ORDER BY asked_at DESC（含未回答与 hidden 提问——后台全量视角）
- STEP-REV-02 CatalogSnapshotPort.getProductBriefs 批量派生 product_name（防 N+1）
- STEP-REV-03 装配标准 Paginated（AdminQuestion 含 visible/product_name）

**出参**: 200 AdminQuestionListResponse
**错误映射**: 403 `40300` / 422 `422801` / 500 `50000`

### E-REV-14 putAdminQuestionAnswer — PUT /api/admin/questions/{id}/answer （Reviews.vue saveAnswer, question_answer_flow: save_answer/edit_answer）

**入参**: path id；body `{ answer! }`
- V-REV-034 id 正整数 int64（非法视同不存在 → 404 `404802`）
- V-REV-035 answer 必填，trim 后长度 1..2000（trim 空 → 422 `422801` fields.answer=blank，js_guard `answerDraft.trim()` 后端兜底——state-machine save_answer/edit_answer guard）

**业务步骤（单事务 TX-REV-008）**:
- STEP-REV-01 `SELECT product_question WHERE id=?`；不存在 → 404 `404802` QUESTION_NOT_FOUND
- STEP-REV-02 状态机分支：原 answer IS NULL（首次回答 save_answer）→ `UPDATE SET answer=:trimmed, answer_time=now, visible='visible'`（**首次回答自动置 visible**，契约）；原 answer 非空（edit_answer）→ `UPDATE SET answer=:trimmed, answer_time=now`（visible 保持现值，后台手动隐藏不被编辑覆盖）
- STEP-REV-03 INSERT operation_log(action=回答提问, changes={first_answer?, answer before/after})
- STEP-REV-04 提交后失效 `review:questions:{product_id}:*` + MQ `content.invalidated {type:question_changed, slug, locales}`（PDP Q&A 区同步刷新）

**出参**: 200 AdminQuestion
**错误映射**: 403 `40300` / 404 `404802` / 422 `422801` / 500 `50000`

### E-REV-15 patchAdminQuestionVisibility — PATCH /api/admin/questions/{id}/visibility （Reviews.vue Toggle q.visible）

**入参**: path id；body `{ visible! }`
- V-REV-036 id 同 V-REV-034 口径
- V-REV-037 visible 必填 ∈ {visible, hidden}（枚举外 → 422 `422801`；bs-511——UI Toggle 布尔映射 visible/hidden 枚举，er-diagram INFERRED 注记定稿）

**业务步骤（单事务 TX-REV-009）**:
- STEP-REV-01 question 不存在 → 404 `404802`
- STEP-REV-02 幂等：目标值=当前值 → 直接返回当前行（不写审计不发事件）
- STEP-REV-03 `UPDATE product_question SET visible=? WHERE id=?`（未回答提问允许置 visible——前台双条件过滤仍不展示，CV-REV-009 读路径口径）
- STEP-REV-04 INSERT operation_log(action=回答提问, changes={visible from/to})（归入规则）
- STEP-REV-05 提交后失效 `review:questions:{product_id}:*` + MQ `content.invalidated {type:question_changed, slug, locales}`

**出参**: 200 AdminQuestion
**错误映射**: 403 `40300` / 404 `404802` / 422 `422801` / 500 `50000`

---

## 6. 自检

- [x] **15 端点全覆盖**（store reviews 2 + store questions 2 + store uploads 1 + admin reviews 7 + admin questions 3）＝ E-REV-01 ~ E-REV-15
- [x] 每端点四部分齐全（入参验证 / 业务步骤 / 出参构造 / 错误码映射）
- [x] V-REV-001 ~ V-REV-037 全域连续唯一；STEP-REV-NN 每端点独立编号段（E-REV-NN 提供唯一溯源前缀）
- [x] 错误码全部出自 review-api.openapi.yml 码表（403801 / 404801~404803 / 409801~409804 / 422801 / 502801，10 码全部至少出现一次）+ 透传 catalog 404501（trading 先例）+ identity 复用码（40100/40300/50000/50001），无臆造
- [x] 403801 越权防护经 TradingPurchaseQueryPort 跨 trading 领域服务接口校验（决策 3，禁止直查 orders 表）
- [x] 公开端点白名单 method-aware 设计定稿（GET 公开 / 同路径 POST 鉴权，0.1 节 2 条目）
- [x] 状态机三机全部落到端点 guard：review_moderation（E-REV-07/08/09 CAS + 409802/409803 + reject 强制 featured=false + batchSet 不限当前状态）、review_image_visibility（E-REV-12 双向幂等）、question_answer_flow（E-REV-14 save/edit + trim guard + 首次回答自动 visible）
- [x] 缓存键/TTL/失效触发者与 data-flow 缓存矩阵一致（key 不含 locale）；写端点全部标注失效链 + MQ 事件（review.moderated 仅审核态变更，content.invalidated 全部前台可见写）+ OperationLog action（3 枚举 + 归入规则）
- [x] 事务边界 TX-REV-001 ~ TX-REV-010 与 review-data-detail.md 一一对应
