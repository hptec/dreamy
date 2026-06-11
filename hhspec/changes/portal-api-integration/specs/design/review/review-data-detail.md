# review 数据层详细设计（L2）

> 角色: l2_data_designer ｜ change: portal-api-integration ｜ domain: review
> 方法论：Entity Design / Repository 方法(RM-REV) / DTO 映射(MAP-REV，含 StoreReviewListResponse 聚合字段实现定稿) / 索引(IDX-REV) / 事务边界(TX-REV) / 数据校验(CV-REV) / 领域事件与端口(EVT-REV，review.moderated 与 catalog 消费者对齐) / 缓存设计(CACHE-REV) / 完整 DDL。
> 来源权威：er-diagram.yml（Review/ReviewImage/ProductQuestion）+ review-api.openapi.yml v1.1.0 + data-flow.md（FLOW-P14 + 缓存矩阵 + MQ 拓扑 q.catalog.rating）+ state-machine.yml 三机 + 后端样板 /Volumes/MAC/workspace/dreamy/backend（huihao-mysql 基类 + huihao.page.Paginated）+ code-patterns.md（CP-001~CP-031）+ catalog-data-detail.md EVT-CAT-002（rating 回写消费者，本域为事件生产者与回查接口提供方）。

## 1. Entity Design（基类选型 / 逻辑删除 / 审计字段）

### 1.1 基类与通用约定

- **基类**：全部实体继承 `huihao.mysql.auditable.LongAuditableEntity`（与 identity/catalog 同款）——`id BIGINT AUTO_INCREMENT` 主键 + `created_at`/`updated_at DATETIME(3)` 审计列。决策 12：Long 自增主键、标准增表无迁移。
- **注解范式**（CP-015）：`@Table(indexes=...)` + `@TableName(value)` + `@Column(name=<EntityDBConst 常量>)`；每实体配 `{Entity}DBConst extends CommonDBConst`（置于 `com.dreamy.review.domain.{聚合根}/consts/`）。
- **逻辑删除**：**不启用**（identity/catalog 同口径）。本域无删除端点（评价/提问不可删，rejected/hidden 即为下线态）；唯一物理清理路径不存在，状态机终态全部为可逆软状态。
- **枚举落地**（CP-003）：status/visible 用 `VARCHAR + Java enum` 双保险（取值与契约字符串一致：pending/approved/rejected、visible/hidden），与 catalog 同口径（消费端可读、种子数据直读）。
- **时间**：DATETIME(3) UTC ↔ LocalDateTime ↔ ISO8601（CP-014）。`submitted_at`/`asked_at` 为业务时间（写入时刻显式赋值），与基类 `created_at` 语义分离（er-diagram 字段保真）。
- **包结构**：`com.dreamy.review/`（单模块多 domain，与 catalog 平级）：`domain/{review,question}/{entity,repository,service,consts}` + `controller/` + `dto/` + `mq/`（事件发布器）+ `port/`（TradingPurchaseQueryPort 等出向端口声明 + ReviewQueryPort 入向实现）+ `config/`。

### 1.2 实体清单（3 实体 = 3 张表，无 translation 附表——评价内容不做多语翻译）

| 实体 | 表名 | 要点 |
|---|---|---|
| Review | review | user_id+product_id 唯一（409801）；rating 1..5；status 三态；featured 仅 approved 可为 1；官方回复三字段同表内嵌（reply_author/reply_content/reply_time，原型 reply 对象一对一无独立生命周期，不拆表）；customer_name 姓名快照 |
| ReviewImage | review_image | review_id 子表；url=预签名 public_url；rejected 驳回标记（shown/rejected 二态以 TINYINT(1) 承载） |
| ProductQuestion | product_question | product_id；asker 姓名快照；**user_id 设计派生列**（er-diagram 无此字段；溯源：契约 StoreBearerAuth「提问提交按 subject=user_id 强隔离」BE-DIM-6——落库提交者关联，后台治理/防滥用数据基础，不出契约 DTO）；answer 为空=unanswered；visible 枚举 |

聚合冗余说明：rating_avg/rating_count **不在本域落列**——已定稿为 catalog 域 Product 冗余列（catalog-data-detail §1.2，EVT-CAT-002 回写）；本域职责＝实时聚合查询（RM-REV-002）+ 事件发布（EVT-REV-001）+ 回查接口提供（ReviewQueryPort）。

## 2. Repository 方法（RM-REV）

### ReviewRepository
- RM-REV-001 `pageApprovedByProduct(productId, sort, page) -> Page<Review>` —— `WHERE product_id=? AND status='approved'`，sort 映射四枚举排序（E-REV-01 STEP-REV-02；IDX-REV-002）
- RM-REV-002 `aggregateApproved(productId) -> {ratingAvg, ratingCount, breakdown:Map<int,int>}` —— `SELECT rating, COUNT(*) FROM review WHERE product_id=? AND status='approved' GROUP BY rating` 单查内存汇总（avg 保留 2 位 HALF_UP，零评价 avg=0/count=0/breakdown 全 0）；**同时是 ReviewQueryPort.approvedRatingSummary 的数据源（catalog EVT-CAT-002 回查同源，口径强一致）**
- RM-REV-003 `existsByUserAndProduct(userId, productId) -> bool` —— 409801 预检（uk_review_user_product 兜底）
- RM-REV-004 `insert(Review)` —— 唯一索引冲突向上抛映射 409801（并发双提交）
- RM-REV-005 `findById(id) -> Review?` —— 404801
- RM-REV-006 `pageByAdminFilter(status?, rating?, featured?, productId?, search?, page) -> Page<Review>` —— search 双 LIKE（customer_name/content）；ORDER BY submitted_at DESC（E-REV-06）
- RM-REV-007 `countPending() -> long` —— `WHERE status='pending'`（pending_count 角标；IDX-REV-003）
- RM-REV-008 `casModerate(id, toStatus) -> affected` —— `UPDATE review SET status=:to, featured=(CASE WHEN :to='rejected' THEN 0 ELSE featured END) WHERE id=? AND status='pending'`（E-REV-07；affected=0 → 409802；CP-016 同型条件更新）
- RM-REV-009 `casSetFeatured(id) -> affected` —— `UPDATE review SET featured=1 WHERE id=? AND status='approved' AND featured=0`（E-REV-08；affected=0 且非幂等场景 → 409803）
- RM-REV-010 `unsetFeatured(id) -> affected` —— `UPDATE review SET featured=0 WHERE id=? AND featured=1`
- RM-REV-011 `listByIds(ids) -> List<Review>` —— 批量分拣（E-REV-09 STEP-REV-01）
- RM-REV-012 `casBatchTransit(id, expectStatus, toStatus, forceUnfeature) -> affected` —— `WHERE id=? AND status=:expect` 逐条 CAS（E-REV-09 STEP-REV-03，并发漂移转 skipped）
- RM-REV-013 `updateReply(id, author, content, time)` —— PUT 回复 UPSERT 语义（E-REV-10）
- RM-REV-014 `clearReply(id)` —— 三字段置 NULL（E-REV-11）

### ReviewImageRepository
- RM-REV-020 `listByReviewIds(reviewIds, excludeRejected) -> List<ReviewImage>` —— store 传 excludeRejected=true（`AND rejected=0`）、admin 传 false 全量；单次 IN 批查防 N+1（IDX-REV-004）
- RM-REV-021 `batchInsert(images[])` —— TX-REV-001 子表批插（rejected=0）
- RM-REV-022 `findByIdAndReviewId(imageId, reviewId) -> ReviewImage?` —— 归属校验（404803，E-REV-12）
- RM-REV-023 `updateRejected(id, rejected)` —— shown↔rejected 翻转

### ProductQuestionRepository
- RM-REV-030 `pageVisibleAnsweredByProduct(productId, page) -> Page<ProductQuestion>` —— `WHERE product_id=? AND visible='visible' AND answer IS NOT NULL` ORDER BY asked_at DESC（E-REV-03；IDX-REV-005）
- RM-REV-031 `pageByAdminFilter(productId?, answered?, page) -> Page<ProductQuestion>` —— answered 映射 `answer IS [NOT] NULL`（E-REV-13）
- RM-REV-032 `findById(id) -> ProductQuestion?` —— 404802
- RM-REV-033 `insert(ProductQuestion)` —— visible='hidden', answer=NULL（E-REV-04）
- RM-REV-034 `saveAnswer(id, answer, answerTime, firstAnswer) -> affected` —— firstAnswer=true 时附加 `visible='visible'`（E-REV-14 STEP-REV-02 两分支单方法承载）
- RM-REV-035 `updateVisible(id, visible)` —— E-REV-15

## 3. DTO ↔ Entity 映射（MAP-REV）

- MAP-REV-001 Review→StoreReview（消费端列表）：id/product_id/rating/content/status(恒 approved)/featured/submitted_at/reply_author/reply_content/reply_time + images[]（**仅 rejected=false**，RM-REV-020 excludeRejected）+ customer_name **脱敏规则**：按空格切分，输出「首段 + 末段首字母.」（如 `Madison Reyes` → `Madison R.`；单段名原样；空快照输出 `Guest`）；**不暴露** user_id
- MAP-REV-002 Review→StoreReview（E-REV-02 提交回执）：同上但 customer_name 原样、images 全量（本人可见自己内容；status=pending）
- MAP-REV-003 Review→AdminReview：StoreReview 全字段（customer_name **不脱敏**）+ user_id + product_name（CatalogSnapshotPort 批量派生，商品已删除容忍 null）+ images[] **全量含 rejected=true**
- MAP-REV-004 ProductQuestion→StoreQuestion：id/product_id/question/asked_at/answer/answer_time + asker 脱敏（同 MAP-REV-001 规则）；**不暴露** visible/user_id
- MAP-REV-005 ProductQuestion→AdminQuestion：StoreQuestion 全字段（asker 不脱敏）+ visible + product_name 派生
- MAP-REV-006 **StoreReviewListResponse 聚合字段实现定稿（L1 feasibility 备忘 + error-strategy L2 要求 5）**：采用 **Paginated 子类（继承）方案** —— `StoreReviewListDTO<StoreReview> extends huihao.page.Paginated<StoreReview>`，新增字段 `rating_avg(BigDecimal)/rating_count(int)/rating_breakdown(Map<String,Integer>, key="1".."5")`。依据：①huihao.page.Paginated 为可继承 POJO（backend UserOpsController `new Paginated<>()` + setter 实证，非 final）；②继承下 Jackson 序列化天然**字段平铺同层**，精确命中契约 schema（六字段+聚合字段并列），零自定义 serializer；③portal-admin PageResult/portal-store deepCamelize 消费端零改动。备选「组合包装 {page:{...}, rating_avg}」被否：嵌套层级偏离契约 required 平铺结构。
- MAP-REV-007 AdminReviewListResponse：同方案 —— `AdminReviewListDTO extends Paginated<AdminReview>` 新增 `pending_count(long)` 平铺；AdminQuestionListResponse/StoreQuestionListResponse 用标准 `Paginated<T>` 无扩展
- MAP-REV-008 枚举：Java enum ↔ VARCHAR 契约字符串（pending/approved/rejected、visible/hidden）；ReviewImage.rejected TINYINT(1) ↔ boolean（state-machine shown/rejected 二态由 rejected=0/1 承载）
- MAP-REV-009 时间字段 LocalDateTime(UTC) ↔ ISO8601 snake_case 出参（CP-001/CP-014）；content/question/answer/reply_content 落库前 trim（CV-REV-006）

## 4. 索引设计（IDX-REV）

| ID | 表 | 索引 | 支撑路径 |
|---|---|---|---|
| IDX-REV-001 | review | `UNIQUE uk_review_user_product(user_id, product_id)` | 409801 同用户同商品唯一（并发兜底） |
| IDX-REV-002 | review | `idx_review_product_status(product_id, status, featured, submitted_at)` | E-REV-01 approved 过滤 + featured_first/newest 排序左前缀 |
| IDX-REV-003 | review | `idx_review_status_submitted(status, submitted_at)` | E-REV-06 status 筛选 + 默认排序；RM-REV-007 pending_count |
| IDX-REV-004 | review_image | `idx_ri_review(review_id)` | 子表批查 / 404803 归属点查 |
| IDX-REV-005 | product_question | `idx_pq_product_visible(product_id, visible, asked_at)` | E-REV-03 双条件过滤 + 排序；E-REV-13 product_id 筛选左前缀 |

查询优化补充：
- NP-REV-001 防 N+1：列表 images（RM-REV-020）与 product_name（CatalogSnapshotPort.getProductBriefs）一律 IN 批查/批量端口
- NP-REV-002 聚合 rating_breakdown 单条 GROUP BY（RM-REV-002），禁止逐星级 COUNT ×5
- QP-REV-001 rating_desc/asc 排序无独立索引（千级评价量 filesort 可接受；IDX-REV-002 已覆盖热路径 featured_first/newest）
- QP-REV-002 admin search 双 LIKE `%s%` 不走索引（后台低频 + 数据量级小，可接受；不建 FULLTEXT——与商品搜索不同无 SLA）

## 5. 事务边界（TX-REV）

| ID | 端点/流程 | 边界与回滚语义 |
|---|---|---|
| TX-REV-001 | E-REV-02 提交评价 | 单事务：购买资格校验（端口只读，事务内首步）→ 唯一预检 → review + review_image 批插；uk 冲突映射 409801 回滚；无缓存/MQ 副作用 |
| TX-REV-002 | E-REV-07 审核 | 单事务：CAS casModerate（409802 并发双审防护）+ operation_log；**缓存失效与 MQ publish 在事务提交后**（CP-031） |
| TX-REV-003 | E-REV-08 精选 | 单事务：CAS casSetFeatured/unsetFeatured + operation_log；幂等短路不开事务 |
| TX-REV-004 | E-REV-09 批量 | 单事务：逐条 CAS（≤200）+ operation_log；任一 UPDATE 异常整体回滚（updated/skipped 与 DB 终态强一致）；提交后按 product_id 去重发事件 |
| TX-REV-005/006 | E-REV-10/11 回复创建编辑/删除 | 单事务：guard(409804) → UPDATE 回复三字段 + operation_log；删除幂等短路不开事务 |
| TX-REV-007 | E-REV-12 图片驳回/恢复 | 单事务：归属校验(404803) → updateRejected + operation_log；幂等短路 |
| TX-REV-008 | E-REV-14 回答 | 单事务：saveAnswer（首答附带 visible 翻转原子完成）+ operation_log |
| TX-REV-009 | E-REV-15 可见性 | 单事务：updateVisible + operation_log；幂等短路 |
| TX-REV-010 | E-REV-04 提问 | 单事务：单表 INSERT（visible=hidden） |
| EC-REV-001 | 缓存失效失败 | 不回滚 DB；记告警，JetCache TTL 300s + CDN s-maxage 60s 自然过期收敛（catalog EC-CAT-002 同口径） |
| EC-REV-002 | MQ publish 失败 | 本地事务不回滚（data-flow 降级矩阵）：rating 回写靠 EVT-CAT-003 每日补偿任务收敛；PDP 新鲜度退化为 TTL 级；记告警日志 |

## 6. 数据校验与引用完整性（CV-REV）

- CV-REV-001 rating 整数 1..5（V-REV-005/016；bs-508/509）；DB TINYINT 承载，应用层单保险（CP-003 口径，不建 CHECK——与 catalog 同基线）
- CV-REV-002 枚举落库前校验 ∈ 取值集：review.status{pending,approved,rejected}（bs-510）、product_question.visible{visible,hidden}（bs-511）；Java enum 反序列化失败 → 422801
- CV-REV-003 长度上限：content ≤5000、question ≤1000、reply_content/answer ≤2000、url ≤512、customer_name/asker/reply_author ≤64（超长 → 422801）
- CV-REV-004 唯一约束：uk_review_user_product 兜底 409801（应用层预检 RM-REV-003 + 索引双保险）
- CV-REV-005 逻辑外键（CP-010 无物理 FK）：review.product_id / product_question.product_id 写前经 CatalogSnapshotPort 校验存在且 published（404501 透传；bs-702/704）；review_image.review_id 仅经 TX-REV-001 内聚合根写入（API 面无独立插图入口，bs-703 由事务边界天然保证）
- CV-REV-006 trim 非空不变量：reply_content/answer 落库前 trim，空串拒绝（422801）；content trim 后空存 NULL
- CV-REV-007 featured 不变量：`status≠approved ⇒ featured=0` —— 全部写路径维护（casModerate reject 分支强制清零、casSetFeatured 带 status 条件、批量 reject 强制清零）；任何读到 featured=1 的行必为 approved
- CV-REV-008 images ≤9 且 url 命中本站 public_url 前缀 + `review/` key 段（V-REV-007，防外链注入）
- CV-REV-009 store 读路径双条件口径：Q&A 前台展示 = `visible='visible' AND answer IS NOT NULL`（visible 单独为 true 的未回答提问不出前台；后台允许任意切换 visible，不设写侧 guard——原型 Toggle 行为保真）
- CV-REV-010 姓名快照不可变：customer_name/asker 提交时一次性快照，用户改名不回溯更新（快照语义，契约「提交时用户资料姓名快照」）

## 7. 缓存设计（CACHE-REV，JetCache 两级 Caffeine+Redis，对齐 data-flow 缓存矩阵）

| ID | key 模板 | TTL | 装载点 | 失效触发者（@CacheInvalidate + MQ） |
|---|---|---|---|---|
| CACHE-REV-001 | `review:reviews:{product_id}:{sort}:{page}:{page_size}` | 300s | E-REV-01 | E-REV-07 审核 / E-REV-08 精选 / E-REV-09 批量 / E-REV-10·11 回复 / E-REV-12 图片驳回恢复（按 product_id 前缀失效 `review:reviews:{pid}:*`） |
| CACHE-REV-002 | `review:questions:{product_id}:{page}:{page_size}` | 300s | E-REV-03 | E-REV-14 回答 / E-REV-15 可见性（前缀失效 `review:questions:{pid}:*`） |

- **key 不含 locale**（契约/缓存矩阵明示：评价内容不做多语翻译）；不含 currency（无价格字段）
- 穿透保护（BE-DIM-8）：`cacheNullValue=true`，空页结果同样缓存（不存在的 product_id 不反复打穿源库）
- 前缀失效：JetCache `@CacheInvalidate` 按 `review:{res}:{product_id}:*` 前缀批量失效（remote Redis SCAN+DEL 封装，catalog 同机制）
- CDN 层：E-REV-01/03 响应 `Cache-Control: s-maxage=60`（契约「CDN 短缓存 60s」）；秒级失效由 q.invalidate 消费者 revalidatePath + Cloudflare purge 完成（EVT-REV-002）；提交/后台端点一律不缓存

## 8. 领域事件与跨域端口（EVT-REV，RabbitMQ topic exchange `dreamy.events`）

### 8.1 本域发布

| ID | 事件 | routing key | 触发 | payload |
|---|---|---|---|---|
| EVT-REV-001 | 评价审核态变更 | `review.moderated` | TX-REV-002 提交后（E-REV-07）；TX-REV-004 提交后 action∈{approve,reject} 按 product_id 去重逐一发布（E-REV-09） | `{event_id(UUID), product_id, review_id?(批量为该商品最后一条), status(approved\|rejected), occurred_at}` |
| EVT-REV-002 | 内容失效 | `content.invalidated` | E-REV-07/08/09/10/11/12/14/15 提交后（全部前台可见写操作，契约缓存失效标注） | `{event_id(UUID), type: review_changed\|question_changed, slug, product_id, locales:[en,es,fr], occurred_at}`（slug 经 CatalogSnapshotPort.getProductBrief 取得；商品已删除则跳过发布——无 PDP 可失效） |

**与 catalog 消费者对齐（EVT-CAT-002 契约）**：`q.catalog.rating` 绑定 `review.moderated`，消费逻辑＝① event_id 幂等（processed_event 表，与 trading 共表）② 仅取 `payload.product_id` ③ 经 **ReviewQueryPort.approvedRatingSummary(product_id)** 回查权威聚合（不信任事件载荷计算值，覆盖写天然可重入）④ RM-CAT-099 覆盖写 Product.rating_avg/rating_count ⑤ 失效 catalog 商品缓存。本域 payload 中 review_id/status 为观测冗余字段，消费侧不依赖——**载荷契约最小依赖面 = event_id + product_id**，双方以此为兼容基线。

`content.invalidated` 消费者 `q.invalidate`（基建侧，FLOW-P03 复用）：type=review_changed/question_changed → `revalidatePath('/product/{slug}')` ×3 locale 路径 + Cloudflare purge（PDP 评价/Q&A 区同步刷新）；nack ×3 指数退避 → `dreamy.dlq`。

队列参数（error-strategy L2 要求 3 本域落点）：本域为纯生产者，不自建队列；发布失败按 EC-REV-002 降级。消费侧参数归 catalog（q.catalog.rating：durable、prefetch=8、重试经 `dreamy.retry.q.catalog.rating` x-message-ttl 阶梯 1s/4s/16s + DLX 回投、超限路由 `dreamy.dlq`）与基建（q.invalidate）分册，本表交叉引用不重复定义。

### 8.2 本域提供的进程内端口（决策 3 反向依赖通路）

| 端口 | 方法 | 消费方 | 实现 |
|---|---|---|---|
| ReviewQueryPort | `approvedRatingSummary(productId) -> {ratingAvg, ratingCount}` | catalog EVT-CAT-002 消费者 | RM-REV-002 同源（avg 2 位 HALF_UP；零评价 0/0），保证回写口径与 E-REV-01 聚合展示强一致 |

### 8.3 本域消费的进程内端口（出向，声明于 `com.dreamy.review.port`）

| 端口 | 方法 | 提供方 | 用途 |
|---|---|---|---|
| TradingPurchaseQueryPort | `hasCompletedOrderContaining(customerId, productId)` | trading | 403801 越权防护（E-REV-02 STEP-REV-01） |
| CatalogSnapshotPort | `getProductBrief(s)` | catalog | 商品校验（404501 透传）/ product_name 派生 / slug 取得 |
| IdentityQueryPort | `getUserName(customerId)` | identity | customer_name/asker 快照 |
| StoragePresignPort | `presign(objectKey, contentType)` | catalog（媒体基建代管） | E-REV-05 买家秀预签名（scope=review） |

## 9. 完整 DDL（MySQL 8.0，utf8mb4_0900_ai_ci，InnoDB；与 huihao-mysql 注解建表等价的权威 SQL）

```sql
-- 1. review 商品评价（ALIGN-014 / s-756 / s-762）
CREATE TABLE review (
  id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  product_id    BIGINT        NOT NULL COMMENT '逻辑外键 product.id（写前经 CatalogSnapshotPort 校验）',
  user_id       BIGINT        NOT NULL COMMENT '逻辑外键 user.id（JWT subject，BE-DIM-6 强隔离）',
  customer_name VARCHAR(64)   NULL COMMENT '提交时用户姓名快照（store 输出脱敏 MAP-REV-001）',
  rating        TINYINT       NOT NULL COMMENT '评分 1..5（应用层校验 CV-REV-001）',
  content       TEXT          NULL COMMENT '评价内容 <=5000，trim 后空存 NULL；不做多语翻译',
  status        VARCHAR(16)   NOT NULL DEFAULT 'pending' COMMENT 'pending|approved|rejected（review_moderation）',
  featured      TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '精选；不变量 status!=approved => 0（CV-REV-007）',
  submitted_at  DATETIME(3)   NOT NULL COMMENT '提交时间（业务时间，列表排序键）',
  reply_author  VARCHAR(64)   NULL COMMENT '官方回复署名（缺省 "Dreamy Team"，配置化）',
  reply_content VARCHAR(2000) NULL COMMENT '官方回复内容，trim 非空（CV-REV-006）',
  reply_time    DATETIME(3)   NULL COMMENT '官方回复时间（服务端生成）',
  created_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_review_user_product (user_id, product_id),
  KEY idx_review_product_status (product_id, status, featured, submitted_at),
  KEY idx_review_status_submitted (status, submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品评价（消费端提交/后台审核/精选/官方回复）';

-- 2. review_image 买家秀图片（决策 9 预签名直传）
CREATE TABLE review_image (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  review_id  BIGINT       NOT NULL COMMENT '逻辑外键 review.id（仅经 TX-REV-001 聚合根写入）',
  url        VARCHAR(512) NOT NULL COMMENT '预签名上传 public_url（review/ 前缀，CV-REV-008）',
  rejected   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '驳回标记；1=前台不展示（review_image_visibility shown/rejected）',
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_ri_review (review_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评价买家秀图片（可单独驳回/恢复）';

-- 3. product_question 商品 Q&A
CREATE TABLE product_question (
  id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  product_id  BIGINT        NOT NULL COMMENT '逻辑外键 product.id',
  user_id     BIGINT        NOT NULL COMMENT '逻辑外键 user.id（设计派生列：BE-DIM-6 提交者强关联，不出契约 DTO）',
  asker       VARCHAR(64)   NULL COMMENT '提问者姓名快照（store 输出脱敏）',
  question    VARCHAR(1000) NOT NULL COMMENT '提问内容 trim 1..1000',
  asked_at    DATETIME(3)   NOT NULL COMMENT '提问时间（业务时间，列表排序键）',
  answer      VARCHAR(2000) NULL COMMENT '官方回答；NULL=unanswered（question_answer_flow）',
  answer_time DATETIME(3)   NULL COMMENT '回答时间（服务端生成）',
  visible     VARCHAR(16)   NOT NULL DEFAULT 'hidden' COMMENT 'visible|hidden；首次回答自动置 visible（E-REV-14）',
  created_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_pq_product_visible (product_id, visible, asked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品 Q&A（后台回答并控制前台可见性）';
```

> 备注：① `processed_event` 幂等表归 trading 域 DDL，catalog 消费者复用（本域为生产者不落表）；②种子数据（决策 21）：portal-admin mock.js reviews/productQuestions 样例转为本 3 表种子行（reviews 含三态/精选/带图/带回复变体、questions 含已答/未答/hidden 变体；user_id 关联 identity 种子用户、product_id 关联 catalog 种子商品；submitted_at/asked_at 保留 mock 相对时间），仅 dev/staging 灌入（UGC 属订单类口径），归 L3 种子脚本任务；③官方回复内嵌主表（reply 三字段），不拆 review_reply 表——一对一、无独立生命周期、原型 r.reply 对象语义保真。

## 10. 自检

- [x] er-diagram 本域 3 实体全部建模；字段/maxlen/枚举/必填与 er-diagram + 契约逐一对齐；product_question.user_id 为唯一设计派生列（溯源 BE-DIM-6 显式标注）
- [x] 基类选型（LongAuditableEntity）/ 逻辑删除（不启用，无删除端点）/ 审计字段（created_at/updated_at 与业务时间 submitted_at/asked_at 分离）显式声明
- [x] RM-REV-001~035（分段编号，无重号）；MAP-REV-001~009；IDX-REV-001~005；TX-REV-001~010 + EC-REV-001/002；CV-REV-001~010；EVT-REV-001~002 + 双向端口表；CACHE-REV-001~002
- [x] **StoreReviewListResponse 聚合字段实现方式定稿**（MAP-REV-006：Paginated 子类继承平铺，含依据与备选否决；AdminReviewListResponse 同方案 MAP-REV-007）——L1 feasibility 备忘 + error-strategy L2 要求 5 闭环
- [x] review.moderated 事件 payload 与 catalog EVT-CAT-002 消费者对齐（最小依赖面 event_id+product_id；ReviewQueryPort 回查口径与展示聚合同源 RM-REV-002）
- [x] 缓存 key/TTL/失效触发者与 data-flow 缓存矩阵逐行一致；key 不含 locale（评价不翻译）；cacheNullValue 穿透保护；CDN s-maxage 60s
- [x] 3 张表完整 DDL；事务边界与 review-api-detail TX 引用一一对应；featured 不变量（CV-REV-007）全写路径维护
