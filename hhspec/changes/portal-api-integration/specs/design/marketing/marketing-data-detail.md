# marketing 数据层详细设计（L2）

> 角色: l2_data_designer ｜ change: portal-api-integration ｜ domain: marketing
> 方法论：Entity Design / Repository 方法(RM-MKT) / DTO 映射(MAP-MKT) / 索引(IDX-MKT) / 事务边界(TX-MKT) / 数据校验(CV-MKT) / 领域服务接口(SVC-MKT，trading 进程内直调) / 领域事件与 MQ(EVT-MKT，含 q.invalidate 消费者) / 定时任务(SCHED-MKT) / 缓存设计(CACHE-MKT) / 完整 DDL（19 表）。
> 来源权威：er-diagram.yml（本域 9 实体 + 7 张 translation 附表）+ marketing-api.openapi.yml + data-flow.md（缓存矩阵/FLOW-P03/P05/P06/P15/P19/MQ 拓扑）+ state-machine.yml（7 状态机）+ 后端样板 /Volumes/MAC/workspace/dreamy/backend（huihao-mysql 基类 + identity 既有模式）+ code-patterns.md（CP-001~CP-031）+ 已发布 trading 设计（RM-TRD-112/113 / CouponPort 消费侧口径）。

## 1. Entity Design（基类选型 / 逻辑删除 / 审计字段）

### 1.1 基类与通用约定

- **基类**：全部实体继承 `huihao.mysql.auditable.LongAuditableEntity`（与 identity/catalog 同款）——`id BIGINT AUTO_INCREMENT 主键` + `created_at`/`updated_at DATETIME(3)` 审计列。决策 12：Long 自增主键、标准增表无迁移。
- **注解范式**（CP-015）：`@Table(indexes=...)` + `@TableName(value, autoResultMap=true)` + `@Column(name=<EntityDBConst 常量>)`；每实体配 `{Entity}DBConst extends CommonDBConst`（置于 `com.dreamy.marketing.domain.{聚合根}/consts/`）。
- **逻辑删除**：**不启用**（与 catalog/trading 同口径）。state-machine `deleted` 终态＝**物理删除**，删除端点的状态机 guard 先行保证安全（coupon 仅 draft/expired 且 used_count=0 可删 409703；flash 仅 draft 可删 409703；banner/blog/wedding/lookbook/guide 全态可删）。
- **枚举落地**（CP-003 catalog 同款收敛）：status/position/type/source/locale 等枚举列用 `VARCHAR + Java enum` 双保险（取值与契约字符串一致，便于种子数据直读；不加 DB CHECK）。
- **时间**：DATETIME(3) UTC ↔ LocalDateTime ↔ ISO8601（CP-014）。
- **包结构**：`com.dreamy.marketing/`（单模块多 domain，与 identity/catalog 平级）：`domain/{banner,blog,wedding,lookbook,guide,coupon,flashsale,subscriber,contact}/{entity,repository,service,consts}` + `controller/` + `dto/` + `mq/`（q.invalidate 消费者 + 发布器）+ `sched/`（SCHED-MKT）+ `config/`。
- **EN 文案列补齐（DEC-MKT-1，api-detail 0.2）**：banner.title/subtitle/cta_text、lookbook.description、real_wedding.title/story、guide.body、coupon.description 在主表建可空列（EN 基准，决策 13），translation 附表仅存 es/fr。

### 1.2 实体清单（9 实体 + 7 translation 附表 + 3 关联表 = 19 张表）

| 实体 | 表名 | 要点 |
|---|---|---|
| Banner | banner | position 三枚举；投放窗口 start_time/end_time（读路径过滤，DEC-MKT-2）；status 三枚举；sort；clicks 只读统计；EN 文案列 title/subtitle/cta_text |
| BannerTranslation | banner_translation | uk(banner_id, locale)；locale ∈ {es,fr} |
| BlogPost | blog_post | slug 唯一（发布必填）；status 三枚举；published_at（首次发布记）；views 只读近似计数（DEC-MKT-6） |
| BlogPostTranslation | blog_post_translation | uk(blog_post_id, locale)；title/excerpt/body/seo_title/seo_description |
| RealWedding | real_wedding | couple 必填；status 二枚举；EN 文案列 title/story |
| RealWeddingTranslation | real_wedding_translation | uk(real_wedding_id, locale) |
| RealWeddingProduct（关联） | real_wedding_product | uk(real_wedding_id, product_id)；Shop the Look nm（product_ids 落点，逻辑外键 catalog.product） |
| Lookbook | lookbook | title 必填；status 二枚举；EN 文案列 description |
| LookbookTranslation | lookbook_translation | uk(lookbook_id, locale) |
| LookbookProduct（关联） | lookbook_product | uk(lookbook_id, product_id) |
| Guide | guide | phase/title 必填；tasks_count；status 二枚举；EN 文案列 body |
| GuideTranslation | guide_translation | uk(guide_id, locale) |
| Coupon | coupon | code 唯一大写；type 三枚举；value 可解析串（DEC-MKT-4）；min_amount/total_limit（缺省 100000=不限，DEC-MKT-5）/used_count（仅核销 CAS 可写）；五态 status（SCHED 翻转）；EN 文案列 description |
| CouponTranslation | coupon_translation | uk(coupon_id, locale)；name/description |
| FlashSale | flash_sale | name/discount/start_at/end_at 必填；四态 status（SCHED 翻转 active/ended） |
| FlashSaleTranslation | flash_sale_translation | uk(flash_sale_id, locale)；name |
| FlashSaleProduct（关联） | flash_sale_product | uk(flash_sale_id, product_id)；参与商品 nm |
| NewsletterSubscriber | newsletter_subscriber | email 唯一（小写归一）；source 三枚举；locale 三枚举；subscribed_at |
| ContactMessage | contact_message | name/email/message 必填；subject 可选；submitted_at；本期无管理端读路径（运营直查库，决策 30） |

写权限约束：coupon.used_count 仅 SVC-MKT-01 核销/回滚 SQL 可写（管理端 PUT 的 SET 列表不含）；blog_post.views 仅 SCHED-MKT-02 flush 可写；banner.clicks 本期无写入端点（保留列，统计接入归后续 change——管理端展示 0 起始值）。

## 2. Repository 方法（RM-MKT）

### BannerRepository / BannerTranslationRepository
- RM-MKT-001 `listStoreActive(position?) -> List<Banner>` —— status='published' AND 窗口谓词（E-MKT-01；IDX-MKT-007）ORDER BY sort, id
- RM-MKT-002 `listAdmin(position?) -> List<Banner>` —— ORDER BY sort, id（E-MKT-21）
- RM-MKT-003 `findById(id)` / RM-MKT-004 `insert` / RM-MKT-005 `update`（SET 不含 clicks）/ RM-MKT-006 `deleteById` / RM-MKT-007 `updateStatus(id, status)`
- RM-MKT-008 `listCrossedWindow(lastTick, now) -> List<Banner>` —— published 且 start_time/end_time ∈ (lastTick, now]（SCHED-MKT-01 窗口边界穿越检测）
- RM-MKT-010 `listTranslationsByBannerIds(ids)` / RM-MKT-011 `replaceTranslations(bannerId, rows[])`（DELETE+批量 INSERT）/ RM-MKT-012 `deleteByBannerId(bannerId)`

### BlogPostRepository / BlogPostTranslationRepository
- RM-MKT-020 `pageStorePublished(category?, page) -> Page<BlogPost>` —— status='published' ORDER BY published_at DESC（IDX-MKT-005）
- RM-MKT-021 `findBySlugPublished(slug) -> BlogPost?` —— uk_blog_slug 点查（E-MKT-03 热路径）
- RM-MKT-022 `pageAdmin(status?, search?, page)` —— title LIKE
- RM-MKT-023 `findById(id)` / RM-MKT-024 `existsBySlugExcept(slug, exceptId?) -> bool`（409702）
- RM-MKT-025 `insert` / RM-MKT-026 `update`（SET 不含 views）/ RM-MKT-027 `deleteById` / RM-MKT-028 `updateStatus(id, status, publishedAt?)`
- RM-MKT-029 `incrementViews(id, delta)` —— `UPDATE blog_post SET views=views+? WHERE id=?`（仅 SCHED-MKT-02）
- RM-MKT-030 `listTranslationsByPostIds(ids, locale?)` / RM-MKT-031 `replaceTranslations(postId, rows[])` / RM-MKT-032 `deleteByPostId(postId)`

### RealWeddingRepository / RealWeddingTranslationRepository / RealWeddingProductRepository
- RM-MKT-040 `pageStorePublished(page)` —— status='published' ORDER BY wedding_date DESC（IDX-MKT-008）
- RM-MKT-041 `findByIdPublished(id)` / RM-MKT-042 `pageAdmin(status?, page)` / RM-MKT-043 `findById(id)`
- RM-MKT-044 `insert` / RM-MKT-045 `update` / RM-MKT-046 `deleteById` / RM-MKT-047 `updateStatus(id, status)`
- RM-MKT-048 `listTranslationsByWeddingIds(ids, locale?)` / RM-MKT-049 `replaceTranslations(weddingId, rows[])` / RM-MKT-050 `deleteTransByWeddingId`
- RM-MKT-051 `listProductIdsByWeddingId(id)` / RM-MKT-052 `listProductIdsByWeddingIds(ids) -> Map`（admin 列表件数批查防 N+1）/ RM-MKT-053 `replaceProducts(weddingId, productIds[])` / RM-MKT-054 `deleteProductsByWeddingId`

### LookbookRepository / LookbookTranslationRepository / LookbookProductRepository
- RM-MKT-060 `listStorePublished()` / RM-MKT-061 `findByIdPublished(id)` / RM-MKT-062 `listAdmin(status?)` / RM-MKT-063 `findById(id)`
- RM-MKT-064 `insert` / RM-MKT-065 `update` / RM-MKT-066 `deleteById` / RM-MKT-067 `updateStatus(id, status)`
- RM-MKT-068 `listTranslationsByLookbookIds(ids, locale?)` / RM-MKT-069 `replaceTranslations(lookbookId, rows[])` / RM-MKT-070 `deleteTransByLookbookId`
- RM-MKT-071 `listProductIdsByLookbookId(id)` / RM-MKT-072 `listProductIdsByLookbookIds(ids) -> Map` / RM-MKT-073 `replaceProducts(lookbookId, productIds[])` / RM-MKT-074 `deleteProductsByLookbookId`

### GuideRepository / GuideTranslationRepository
- RM-MKT-080 `listStorePublished()` —— ORDER BY phase, id / RM-MKT-081 `listAdmin(status?)` / RM-MKT-082 `findById(id)`
- RM-MKT-083 `insert` / RM-MKT-084 `update` / RM-MKT-085 `deleteById` / RM-MKT-086 `updateStatus(id, status)`
- RM-MKT-087 `listTranslationsByGuideIds(ids, locale?)` / RM-MKT-088 `replaceTranslations(guideId, rows[])` / RM-MKT-089 `deleteTransByGuideId`

### CouponRepository / CouponTranslationRepository
- RM-MKT-100 `findByCode(code) -> Coupon?` —— uk_coupon_code 点查（E-MKT-10 / SVC-MKT-01）
- RM-MKT-101 `pageAdmin(status?, search?, page)` —— code/name LIKE（IDX-MKT-002）
- RM-MKT-102 `findById(id)` / RM-MKT-103 `existsByCodeExcept(code, exceptId?) -> bool`（409701）
- RM-MKT-104 `insert` / RM-MKT-105 `update`（SET 不含 used_count）/ RM-MKT-106 `deleteById`
- RM-MKT-107 `redeemCas(couponId) -> affected` —— `UPDATE coupon SET used_count=used_count+1 WHERE id=? AND used_count<total_limit`（**与 trading RM-TRD-112 同 SQL，本域为权威定义点**；affected=0 → 422703）
- RM-MKT-108 `rollbackRedeem(couponId)` —— `UPDATE coupon SET used_count=GREATEST(used_count-1,0) WHERE id=?`（与 RM-TRD-113 一致；FLOW-P08 超时取消回滚）
- RM-MKT-109 `flipStatusByWindow(now, expiringThreshold) -> List<id>` —— SCHED-MKT-01 三条批量 UPDATE：scheduled→active（start_at≤now）；active→expiring（end_at-now≤阈值）；active|expiring→expired（end_at<now）；返回受影响 id（审计日志用，不发 MQ）
- RM-MKT-110 `listTranslationsByCouponIds(ids, locale?)` / RM-MKT-111 `replaceTranslations(couponId, rows[])` / RM-MKT-112 `deleteTransByCouponId`

### FlashSaleRepository / FlashSaleTranslationRepository / FlashSaleProductRepository
- RM-MKT-120 `listStoreActive() -> List<FlashSale>` —— status='active' ORDER BY end_at（IDX-MKT-003）
- RM-MKT-121 `listAdmin(status?)` / RM-MKT-122 `findById(id)`
- RM-MKT-123 `insert` / RM-MKT-124 `update` / RM-MKT-125 `deleteById`
- RM-MKT-126 `flipStatusByWindow(now) -> {activated:[id], ended:[id]}` —— SCHED-MKT-01 两条批量 UPDATE：scheduled→active（start_at≤now）；active→ended（end_at<now，s-761 自动下线）；任一非空触发失效链
- RM-MKT-127 `listTranslationsByFlashIds(ids, locale?)` / RM-MKT-128 `replaceTranslations(flashId, rows[])` / RM-MKT-129 `deleteTransByFlashId`
- RM-MKT-130 `listProductIdsByFlashId(id)` / RM-MKT-131 `listProductIdsByFlashIds(ids) -> Map` / RM-MKT-132 `replaceProducts(flashId, productIds[])` / RM-MKT-133 `deleteProductsByFlashId`

### NewsletterSubscriberRepository / ContactMessageRepository
- RM-MKT-140 `insertIgnoreDuplicate(subscriber) -> void` —— `INSERT ... ON DUPLICATE KEY UPDATE id=id`（uk_newsletter_email；幂等空操作，E-MKT-11）
- RM-MKT-141 `insert(contactMessage)`（E-MKT-12）

## 3. DTO ↔ Entity 映射（MAP-MKT）

- MAP-MKT-001 Banner→StoreBanner：id/name/image_url/position/sort + title/subtitle/cta_text（locale 解析后扁平输出）；**不暴露** status/start_time/end_time/clicks
- MAP-MKT-002 Banner→Banner DTO（admin）：BannerUpsert 全字段回显 + id/clicks + translations 三语 tab 原样（不回退合并）+ EN 文案列（DEC-MKT-1 可选字段）
- MAP-MKT-003 BlogPost→StoreBlogPostCard：id/title(locale)/slug/cover/category/author/excerpt(es/fr=translation.excerpt，en=content strip 截断 200)/published_at/views
- MAP-MKT-004 BlogPost→StoreBlogPostDetail：Card + content(es/fr=translation.body 回退 EN content)/seo_title(EN=title 派生)/seo_description(EN=excerpt 派生)
- MAP-MKT-005 BlogPost→BlogPost DTO（admin）：Upsert 全字段 + id/published_at/views + translations 原样
- MAP-MKT-006 RealWedding→StoreRealWedding：id/couple/location/theme/wedding_date/cover/status('published' 恒定)/title/story(locale 解析) + products[]（详情，ProductRef 经 catalogQueryPort）
- MAP-MKT-007 RealWedding→RealWedding DTO（admin）：Upsert + id + product_ids + translations 原样
- MAP-MKT-008 Lookbook→StoreLookbook：id/title(locale)/theme/description(locale) + products[]（详情）；Lookbook→Lookbook DTO（admin）同构 + product_ids
- MAP-MKT-009 Guide→StoreGuide：id/phase/timeframe/title(locale)/body(locale)/tasks_count；Guide→Guide DTO（admin）+ translations
- MAP-MKT-010 Coupon→Coupon DTO（admin）：Upsert 全字段 + id/used_count（只读）+ translations；Coupon→CouponValidateResponse.coupon：code/name(locale)/type/value/min_amount（**不暴露** used_count/total_limit/状态——校验语义经 reason_code 表达）
- MAP-MKT-011 FlashSale→StoreFlashSale：id/name(locale)/discount/start_at/end_at + products[]（ProductRef）；FlashSale→FlashSale DTO（admin）+ product_ids + translations
- MAP-MKT-012 ProductRef 装配：catalogQueryPort.listProductRefs(ids, locale) → {id, slug, name, price, image_url}（USD 基准价；缺失/下架商品静默剔除）
- MAP-MKT-013 枚举：Java enum ↔ VARCHAR 契约字符串（hero/featured/topbar、draft/published/archived、draft/scheduled/active/expiring/expired、draft/scheduled/active/ended、discount/fixed_amount/free_shipping、footer/modal/exit_intent、en/es/fr、es/fr）
- MAP-MKT-014 时间 LocalDateTime(UTC) ↔ ISO8601；出参 snake_case（前端 client 转 camelCase，CP-001）；Paginated 六字段（huihao.page.Paginated）

## 4. 索引设计（IDX-MKT）

| ID | 表 | 索引 | 支撑路径 |
|---|---|---|---|
| IDX-MKT-001 | coupon | `UNIQUE uk_coupon_code(code)` | E-MKT-10/SVC-MKT-01 点查 / 409701 |
| IDX-MKT-002 | coupon | `idx_coupon_status(status)` | admin status 筛选 + SCHED 翻转扫描 |
| IDX-MKT-003 | flash_sale | `idx_flash_status_end(status, end_at)` | store active 读 + SCHED 到期扫描 |
| IDX-MKT-004 | blog_post | `UNIQUE uk_blog_slug(slug)` | E-MKT-03 点查 / 409702（slug 可空，MySQL 唯一索引多 NULL 共存——draft 未填 slug 不冲突） |
| IDX-MKT-005 | blog_post | `idx_blog_status_published(status, published_at)` | store 列表倒序 / admin 筛选 |
| IDX-MKT-006 | blog_post | `idx_blog_category(category)` | store category 筛选 |
| IDX-MKT-007 | banner | `idx_banner_status_position(status, position)` | E-MKT-01 投放清单（窗口谓词走该索引后过滤） |
| IDX-MKT-008 | real_wedding | `idx_wedding_status(status, wedding_date)` | store 列表 / admin 筛选 |
| IDX-MKT-009 | lookbook | `idx_lookbook_status(status)` | store/admin 筛选 |
| IDX-MKT-010 | guide | `idx_guide_status_phase(status, phase)` | store 按阶段排序 |
| IDX-MKT-011 | banner_translation | `UNIQUE uk_bt(banner_id, locale)` | 翻译合并/整单覆盖 |
| IDX-MKT-012 | blog_post_translation | `UNIQUE uk_bpt(blog_post_id, locale)` | 同上 |
| IDX-MKT-013 | real_wedding_translation | `UNIQUE uk_rwt(real_wedding_id, locale)` | 同上 |
| IDX-MKT-014 | lookbook_translation | `UNIQUE uk_lbt(lookbook_id, locale)` | 同上 |
| IDX-MKT-015 | guide_translation | `UNIQUE uk_gt(guide_id, locale)` | 同上 |
| IDX-MKT-016 | coupon_translation | `UNIQUE uk_cpt(coupon_id, locale)` | 同上 |
| IDX-MKT-017 | flash_sale_translation | `UNIQUE uk_fst(flash_sale_id, locale)` | 同上 |
| IDX-MKT-018 | flash_sale_product | `UNIQUE uk_fsp(flash_sale_id, product_id)` + `idx_fsp_product(product_id)` | nm 幂等 / 商品反查 |
| IDX-MKT-019 | real_wedding_product | `UNIQUE uk_rwp(real_wedding_id, product_id)` + `idx_rwp_product(product_id)` | 同上 |
| IDX-MKT-020 | lookbook_product | `UNIQUE uk_lbp(lookbook_id, product_id)` + `idx_lbp_product(product_id)` | 同上 |
| IDX-MKT-021 | newsletter_subscriber | `UNIQUE uk_newsletter_email(email)` | E-MKT-11 幂等判重 |
| IDX-MKT-022 | contact_message | `idx_contact_submitted(submitted_at)` | 运营直查库时间序（决策 30） |

查询优化补充：
- NP-MKT-001 防 N+1：admin 列表 translations/product_ids 一律 ids IN 批查（RM-MKT-010/030/048/052/068/072/087/110/127/131）
- NP-MKT-002 store 详情 ProductRef 单次 catalogQueryPort 批调（一个 ids 集合一次进程内调用，禁止逐 id 循环）
- QP-MKT-001 内容表量级（百级）下 LIMIT/OFFSET 分页安全；total 走 COUNT 同条件（MyBatis-Plus Page）

## 5. 事务边界（TX-MKT）

| ID | 端点/流程 | 边界与回滚语义 |
|---|---|---|
| TX-MKT-001 | E-MKT-14 创建优惠券 | 单事务：coupon + coupon_translation 批插 + operation_log；uk_coupon_code 冲突映射 409701 回滚；无缓存失效无 MQ |
| TX-MKT-002 | E-MKT-15 编辑优惠券 | 单事务：主表 UPDATE（不含 used_count）+ translation 整单覆盖 + operation_log；状态机 guard（active 改 code 409703）在事务内复查 |
| TX-MKT-003 | E-MKT-16 删除优惠券 | 单事务：guard(409703 状态/核销数) → 双表物理删除 + operation_log |
| TX-MKT-004/005/006 | 闪购创建/编辑/删除 | 单事务：主表 + flash_sale_product + translation 整单覆盖 + operation_log；guard（ended 不可编辑 / 仅 draft 可删）事务内复查；**失效与 MQ 在提交后**（CP-031，MQ 失败不回滚 TTL 兜底） |
| TX-MKT-007/008/009/010 | Banner 创建/编辑/删除/状态 | 单事务：主表 + translation + operation_log；E-MKT-25 幂等短路不开事务 |
| TX-MKT-011/012/013/014 | 文章创建/编辑/删除/状态 | 单事务：主表 + translation + operation_log；uk_blog_slug 冲突 → 409702 回滚；views 不参与 SET |
| TX-MKT-015/016/017/018 | 案例创建/编辑/删除/状态 | 单事务：主表 + real_wedding_product + translation + operation_log |
| TX-MKT-019/020/021/022 | Lookbook 创建/编辑/删除/状态 | 单事务：主表 + lookbook_product + translation + operation_log |
| TX-MKT-023/024/025/026 | 指南创建/编辑/删除/状态 | 单事务：主表 + translation + operation_log |
| TX-MKT-027 | E-MKT-11 newsletter | 单语句事务（INSERT ON DUPLICATE 原子幂等，无显式事务编排） |
| TX-MKT-028 | E-MKT-12 contact | 单语句 INSERT |
| TX-MKT-029 | SCHED-MKT-01 状态翻转 | 任务内单事务：coupon 三段 UPDATE + flash 两段 UPDATE 批量翻转；**失效链与 MQ 在提交后**；分布式锁内执行（防多实例双跑，huihao-redis `onIdLock("sched:marketing-promo")`） |
| TX-MKT-030 | SCHED-MKT-02 views flush | 逐 id `incrementViews` 独立短事务（单 key 失败不影响其余）；Redis GETDEL 先取后清，DB 写失败回投 Redis（INCRBY 补偿） |
| EC-MKT-001 | 券核销并发 | SVC-MKT-01 redeem 为单条 CAS UPDATE（谓词 used_count<total_limit），**不重试**：affected=0 即业务性耗尽 → trading 事务整体回滚 422703（与 FLOW-P06 一致） |
| EC-MKT-002 | 缓存失效失败 | 不回滚 DB；记告警，JetCache TTL（60~300s）+ CDN s-maxage 自然过期收敛（catalog EC-CAT-002 同口径） |

## 6. 数据校验与引用完整性（CV-MKT）

- CV-MKT-001 枚举值落库前校验 ∈ 取值集（Java enum 反序列化失败 → 422704；DB VARCHAR 不加 CHECK；bs-398/401/402/505/499 等枚举族）
- CV-MKT-002 长度上限与 er-diagram/契约一致（见 DDL 列定义；超长 → 422704；bs-480/489/506~551 等长度族）
- CV-MKT-003 数值域：min_amount ≥0、total_limit ≥0、used_count ≥0、sort ≥0、tasks_count ≥0、views/clicks ≥0、subtotal ≥0
- CV-MKT-004 时间窗 js_guard 应用层校验：coupon end_at>start_at（V-MKT-025）、flash end_at>start_at（V-MKT-033）、banner end_time>start_time（V-MKT-041）；DB 不建跨列 CHECK
- CV-MKT-005 逻辑外键（CP-010 无物理 FK）：flash_sale_product.product_id / real_wedding_product.product_id / lookbook_product.product_id 写入前经 catalogQueryPort 校验存在（V-MKT-035/062/070；bs-699/700/701）；translation 附表 entity_id 由聚合内写入天然有效
- CV-MKT-006 跨域引用反向口径：catalog 商品删除/下架**不级联清理**本域 nm 行——读路径 ProductRef 装配时静默剔除（MAP-MKT-012），避免跨域级联事务
- CV-MKT-007 translation locale 仅 {es, fr}（EN 存主表，决策 13 + DEC-MKT-1）；uk(entity_id, locale) 防重；提交集内 locale 不重复（应用层先校验 422704）
- CV-MKT-008 coupon.code / newsletter email 归一化：code trim+大写、email trim+小写后落库（判重口径统一）
- CV-MKT-009 coupon.value 按 type pattern 可解析（V-MKT-022，DEC-MKT-4）——E-MKT-10 解析失败兜底：按 reason_code=422701 处置并告警（存量脏数据防御，正常路径不可达）
- CV-MKT-010 used_count ≤ total_limit 不变量：由 RM-MKT-107 CAS 谓词维护（er js_guard 落点）；rollback GREATEST(...,0) 防负
- CV-MKT-011 状态-时间窗一致性（coupon/flash 创建/编辑校验 V-MKT-026/034）+ SCHED 翻转兜底——E-MKT-10 校验时叠加实时窗口判定双保险（STEP-MKT-02）
- CV-MKT-012 blog 发布不变量：status=published ⇒ slug 非空（V-MKT-052 / E-MKT-31 STEP-MKT-03）

## 7. 领域服务接口（SVC-MKT，决策 3 进程内直调；trading 为消费方——CouponPort 的提供侧权威定义）

```java
// com.dreamy.marketing.domain.coupon.service.CouponDomainService（实现 trading 声明的 CouponPort）
public interface CouponDomainService {

  /** 结算/报价校验（FLOW-P05 STEP-TRD-06 / E-MKT-10 共用内核）。
      无效不抛异常，返回 reasonCode（422701/422702/422703）。 */
  CouponQuote validate(String code, BigDecimal subtotalUsd, String locale);
  // CouponQuote { boolean valid; Long couponId; BigDecimal discountUsd;
  //               boolean freeShipping; Integer reasonCode; CouponBrief coupon; }

  /** 下单事务内核销（FLOW-P06；参与 trading TX-TRD-002，本方法不自启事务）。
      内部：findByCode → 复跑 validate 状态/窗口判定（防 TOCTOU）→ RM-MKT-107 redeemCas。
      affected=0 → 抛 CouponExhausted(422703)；无效/门槛 → 抛 CouponInvalid(422701)/CouponMinAmountNotMet(422702)；
      由 trading 事务整体回滚。返回 couponId（落 orders.coupon_id）。 */
  Long redeem(String code, BigDecimal subtotalUsd);

  /** 核销回滚（FLOW-P08 超时取消 / 下单失败补偿；参与调用方事务）。RM-MKT-108。 */
  void rollbackRedeem(Long couponId);
}
```

- 与 trading 已发布设计对齐：`CouponPort.validate(code, subtotalUsd)` / `redeem(code)` / `rollback(couponId)`（trading-api-detail §0）；RM-TRD-112/113 的 SQL 以本域 RM-MKT-107/108 为权威定义点（同文本）。
- **本域消费的跨域接口**：`catalogQueryPort.listProductRefs(productIds, locale) -> List<ProductRef>`（仅 published；catalog 域提供）——E-MKT-05/07/09 与 V-MKT-035/062/070 存在性校验复用（`existsAll(ids)` 语义由同接口空集比对承载）。

## 8. 缓存设计（CACHE-MKT，JetCache 两级 Caffeine+Redis，对齐 data-flow 缓存矩阵 `marketing:{res}:{params}:{locale}`）

| ID | key 模板 | TTL | 装载点 | 失效触发者（@CacheInvalidate + MQ content.invalidated） |
|---|---|---|---|---|
| CACHE-MKT-001 | `marketing:banners:{position\|all}:{locale}` | 300s | E-MKT-01 | Banner 创建/编辑/删除/状态（TX-MKT-007~010）+ SCHED-MKT-01 窗口穿越（DEC-MKT-2） |
| CACHE-MKT-002 | `marketing:blogs:{category\|all}:{page}:{page_size}:{locale}` | 300s | E-MKT-02 | 文章写/发布状态变更（TX-MKT-011~014） |
| CACHE-MKT-003 | `marketing:blog:{slug}:{locale}` | 300s（null 60s） | E-MKT-03 | 同上（新旧 slug 都失效） |
| CACHE-MKT-004 | `marketing:weddings:{page}:{page_size}:{locale}` | 300s | E-MKT-04 | 案例写/发布状态变更（TX-MKT-015~018） |
| CACHE-MKT-005 | `marketing:wedding:{id}:{locale}` | 300s（null 60s） | E-MKT-05 | 同上 |
| CACHE-MKT-006 | `marketing:lookbooks:{locale}` | 300s | E-MKT-06 | Lookbook 写/状态（TX-MKT-019~022） |
| CACHE-MKT-007 | `marketing:lookbook:{id}:{locale}` | 300s（null 60s） | E-MKT-07 | 同上 |
| CACHE-MKT-008 | `marketing:guides:{locale}` | 300s | E-MKT-08 | 指南写/状态（TX-MKT-023~026） |
| CACHE-MKT-009 | `marketing:flash:{locale}` | **60s**（倒计时新鲜度兜底） | E-MKT-09 | 闪购写（TX-MKT-004/005）+ SCHED-MKT-01 翻转（FLOW-P15） |

- 穿透保护（BE-DIM-8）：`cacheNullValue=true`，null 短 TTL 60s（不存在 slug/id 不反复打穿源库）
- key 一律含 locale 维度（决策 13）；**不含 currency**（本域无价格换算，ProductRef 仅 USD 基准价）
- 不缓存：E-MKT-10 validate（实时读券）、E-MKT-11/12（写）、全部 admin 端点
- CDN 层：E-MKT-01~08 响应 `Cache-Control: s-maxage=300`；E-MKT-09 `s-maxage=60`；秒级失效由 q.invalidate 消费者 revalidatePath + Cloudflare purge 完成（EVT-MKT-002）

## 9. 领域事件与 MQ（EVT-MKT，RabbitMQ topic exchange `dreamy.events`）

### 9.1 本域发布

| 事件 | routing key | 触发 | payload |
|---|---|---|---|
| 内容失效 | `content.invalidated` | TX-MKT-004/005（含 active 面）、TX-MKT-007~026 提交后；SCHED-MKT-01 翻转/窗口穿越后 | `{event_id(UUID), type: banner_changed\|blog_changed\|wedding_changed\|lookbook_changed\|guide_changed\|flash_sale_changed, slug?, old_slug?, id?, locales:[en,es,fr], occurred_at}` |

coupon 全部写操作与 SCHED 翻转**不发**事件（无消费端缓存面/无静态页，归因见 api-detail E-MKT-14 STEP-MKT-04）。newsletter/contact 不发（FLOW-P19 显式约定）。

### 9.2 本域消费 —— EVT-MKT-002 `q.invalidate` 失效消费者（FLOW-P03 落点；TASK-056 消费者侧，catalog task-allocation shared_with 声明归本域承载）

| 项 | 设计 |
|---|---|
| 绑定 | `dreamy.events` topic，binding key `content.invalidated`（catalog + marketing 两域生产者共用） |
| 幂等 | event_id 查 `processed_event` 表（INSERT 唯一索引冲突即跳过；表归 trading 域 DDL，error-strategy L2 要求 3 共表复用） |
| 步骤 | ① event_id 幂等 ② 按 type 查路径映射表（下表）③ `POST {NEXT_INTERNAL_URL}/api/revalidate {paths[]}`（内部端点，header `x-revalidate-token` 共享密钥，仅内网）④ Cloudflare purge API（zone token 后端配置，按完整 URL 列表）⑤ 任一步失败 nack |
| 重试 | nack → `dreamy.retry.q.invalidate`（x-message-ttl 阶梯 1s/4s/16s + DLX 回投）×3 → `dreamy.dlq` 告警人工重放；期间 CDN 靠 s-maxage TTL + serve-stale 兜底（决策 22） |
| 队列参数 | durable、prefetch=8、x-dead-letter-exchange=dreamy.dlq |

**type → revalidate 路径映射表**（每条路径 ×3 locale：EN 无前缀、`/es`、`/fr`，决策 27；purge URL 同列表）：

| type | 路径 |
|---|---|
| banner_changed | `/`（首页 hero/featured/topbar） |
| flash_sale_changed | `/`（首页闪购区块） |
| blog_changed | `/blog`、`/blog/{slug}`（old_slug 给定时旧路径一并失效） |
| wedding_changed | `/real-weddings`、`/real-weddings/{id}`、`/`（首页 Real Weddings 区块） |
| lookbook_changed | `/inspiration` |
| guide_changed | `/wedding-guides` |
| product_created/updated/status_changed/flags_changed（catalog 产）| `/product/{slug}`（old_slug 同）、`/wedding-dresses`、`/special-occasion`、`/accessories`、`/outdoor-weddings`、`/` |
| category_changed / tag_changed（catalog 产）| 四聚合页 + `/` |

## 10. 定时任务（SCHED-MKT）

| ID | 周期 | 逻辑 |
|---|---|---|
| SCHED-MKT-01 营销定时投放/到期下线（FLOW-P15，ALIGN-008/009，TASK-060） | 每分钟 @Scheduled | ① huihao-redis 分布式锁 `onIdLock("sched:marketing-promo")`（多实例防双跑，拿不到锁直接跳过本 tick）② TX-MKT-029：RM-MKT-109 coupon 翻转（scheduled→active→expiring→expired，DEC-MKT-3 阈值 72h 配置）+ RM-MKT-126 flash 翻转（scheduled→active；active→ended 自动下线 s-761）③ 提交后：flash 任一翻转 → @CacheInvalidate `marketing:flash:*` + MQ `content.invalidated {type:flash_sale_changed}` ④ RM-MKT-008 banner 窗口穿越检测（lastTick 持久于 Redis key `sched:marketing-promo:last-tick`）→ 任一穿越 → @CacheInvalidate `marketing:banners:*` + MQ `{type:banner_changed}`（**状态不翻转**，DEC-MKT-2）⑤ coupon 翻转不发 MQ（无缓存面） |
| SCHED-MKT-02 blog views flush（DEC-MKT-6） | 每分钟 @Scheduled | ① 同款分布式锁 `onIdLock("sched:blog-views")` ② SCAN `marketing:blog:views:*` → 逐 key GETDEL 取 delta ③ TX-MKT-030 RM-MKT-029 `views=views+delta` ④ DB 失败 INCRBY 回投补偿；不失效内容缓存（views 非缓存敏感字段，列表 TTL 自然收敛） |

## 11. 完整 DDL（MySQL 8.0，utf8mb4_0900_ai_ci，InnoDB；与 huihao-mysql 注解建表等价的权威 SQL）

```sql
-- 1. banner 广告位（含 EN 文案列，DEC-MKT-1）
CREATE TABLE banner (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  name       VARCHAR(128) NOT NULL COMMENT '内部名称',
  image_url  VARCHAR(512) NOT NULL COMMENT '预签名上传 public_url（scope=banner）',
  position   VARCHAR(16)  NOT NULL COMMENT 'hero|featured|topbar',
  start_time DATETIME(3)  NULL COMMENT '投放开始（空=立即）',
  end_time   DATETIME(3)  NULL COMMENT '投放结束（空=长期）；读路径窗口过滤（DEC-MKT-2）',
  status     VARCHAR(16)  NOT NULL DEFAULT 'draft' COMMENT 'draft|published|archived',
  sort       INT          NOT NULL DEFAULT 0 COMMENT '排序',
  clicks     INT          NOT NULL DEFAULT 0 COMMENT '点击统计只读（本期无写入端点）',
  title      VARCHAR(255) NULL COMMENT '文案标题(EN 基准)',
  subtitle   VARCHAR(255) NULL COMMENT '文案副题(EN 基准)',
  cta_text   VARCHAR(64)  NULL COMMENT 'CTA 文案(EN 基准)',
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_banner_status_position (status, position)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站点广告位（首页Hero/推荐位/顶部条）';

-- 2. banner_translation
CREATE TABLE banner_translation (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  banner_id  BIGINT       NOT NULL COMMENT '逻辑外键 banner.id',
  locale     VARCHAR(8)   NOT NULL COMMENT 'es|fr（EN 存主表）',
  title      VARCHAR(255) NULL,
  subtitle   VARCHAR(255) NULL,
  cta_text   VARCHAR(64)  NULL,
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_bt (banner_id, locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Banner 多语言附表';

-- 3. blog_post
CREATE TABLE blog_post (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  title        VARCHAR(200) NOT NULL COMMENT '标题(EN 基准)',
  cover        VARCHAR(512) NULL,
  category     VARCHAR(64)  NULL COMMENT '文章栏目',
  author       VARCHAR(64)  NULL,
  content      TEXT         NULL COMMENT '正文(EN 基准)',
  slug         VARCHAR(128) NULL COMMENT '静态文章页路径 ^[a-z0-9-]+$；published 必填（CV-MKT-012）',
  status       VARCHAR(16)  NOT NULL DEFAULT 'draft' COMMENT 'draft|published|archived',
  published_at DATETIME(3)  NULL COMMENT '首次发布时间（republish 不刷新）',
  views        INT          NOT NULL DEFAULT 0 COMMENT '阅读数近似计数（SCHED-MKT-02 flush，DEC-MKT-6）',
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_blog_slug (slug),
  KEY idx_blog_status_published (status, published_at),
  KEY idx_blog_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Blog 婚礼策划文章';

-- 4. blog_post_translation
CREATE TABLE blog_post_translation (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  blog_post_id    BIGINT       NOT NULL,
  locale          VARCHAR(8)   NOT NULL COMMENT 'es|fr',
  title           VARCHAR(200) NULL,
  excerpt         VARCHAR(500) NULL,
  body            TEXT         NULL,
  seo_title       VARCHAR(128) NULL,
  seo_description VARCHAR(255) NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_bpt (blog_post_id, locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='博客多语言附表';

-- 5. real_wedding（含 EN 文案列）
CREATE TABLE real_wedding (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  couple       VARCHAR(64)  NOT NULL COMMENT '如 Emma & James',
  location     VARCHAR(128) NULL,
  theme        VARCHAR(32)  NULL,
  wedding_date VARCHAR(16)  NULL COMMENT '如 2025-06',
  cover        VARCHAR(512) NULL,
  status       VARCHAR(16)  NOT NULL DEFAULT 'draft' COMMENT 'draft|published',
  title        VARCHAR(200) NULL COMMENT '案例标题(EN 基准)',
  story        TEXT         NULL COMMENT '婚礼故事(EN 基准)',
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_wedding_status (status, wedding_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='真实婚礼案例（Shop the Look）';

-- 6. real_wedding_translation
CREATE TABLE real_wedding_translation (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  real_wedding_id BIGINT       NOT NULL,
  locale          VARCHAR(8)   NOT NULL COMMENT 'es|fr',
  title           VARCHAR(200) NULL,
  story           TEXT         NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_rwt (real_wedding_id, locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='案例多语言附表';

-- 7. real_wedding_product（Shop the Look nm）
CREATE TABLE real_wedding_product (
  id              BIGINT      NOT NULL AUTO_INCREMENT,
  real_wedding_id BIGINT      NOT NULL,
  product_id      BIGINT      NOT NULL COMMENT '逻辑外键 catalog.product.id',
  created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_rwp (real_wedding_id, product_id),
  KEY idx_rwp_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='案例-商品挂载';

-- 8. lookbook（含 EN description）
CREATE TABLE lookbook (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  title       VARCHAR(128) NOT NULL COMMENT '画册标题(EN 基准)',
  theme       VARCHAR(32)  NULL COMMENT 'Vineyard/Beach/Forest',
  status      VARCHAR(16)  NOT NULL DEFAULT 'draft' COMMENT 'draft|published',
  description VARCHAR(500) NULL COMMENT '画册描述(EN 基准)',
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_lookbook_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Lookbook 主题画册';

-- 9. lookbook_translation
CREATE TABLE lookbook_translation (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  lookbook_id BIGINT       NOT NULL,
  locale      VARCHAR(8)   NOT NULL COMMENT 'es|fr',
  title       VARCHAR(128) NULL,
  description VARCHAR(500) NULL,
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_lbt (lookbook_id, locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Lookbook 多语言附表';

-- 10. lookbook_product
CREATE TABLE lookbook_product (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  lookbook_id BIGINT      NOT NULL,
  product_id  BIGINT      NOT NULL COMMENT '逻辑外键 catalog.product.id',
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_lbp (lookbook_id, product_id),
  KEY idx_lbp_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Lookbook-商品挂载';

-- 11. guide（含 EN body）
CREATE TABLE guide (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  phase       VARCHAR(32)  NOT NULL COMMENT '备婚阶段，如 Phase 1',
  timeframe   VARCHAR(64)  NULL COMMENT '如 12+ months out',
  title       VARCHAR(128) NOT NULL COMMENT '指南标题(EN 基准)',
  tasks_count INT          NOT NULL DEFAULT 0 COMMENT '待办任务数',
  status      VARCHAR(16)  NOT NULL DEFAULT 'draft' COMMENT 'draft|published',
  body        TEXT         NULL COMMENT '指南正文(EN 基准)',
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_guide_status_phase (status, phase)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='备婚指南（按阶段）';

-- 12. guide_translation
CREATE TABLE guide_translation (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  guide_id   BIGINT       NOT NULL,
  locale     VARCHAR(8)   NOT NULL COMMENT 'es|fr',
  title      VARCHAR(128) NULL,
  body       TEXT         NULL,
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_gt (guide_id, locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='指南多语言附表';

-- 13. coupon（含 EN description；used_count 仅核销 CAS 可写）
CREATE TABLE coupon (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  code        VARCHAR(32)   NOT NULL COMMENT '券码 ^[A-Z0-9]+$ 唯一（大写归一）',
  name        VARCHAR(64)   NOT NULL COMMENT '券名(EN 基准)',
  type        VARCHAR(16)   NOT NULL COMMENT 'discount|fixed_amount|free_shipping',
  value       VARCHAR(32)   NOT NULL COMMENT '展示串，按 type pattern 可解析（DEC-MKT-4）',
  min_amount  DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '门槛金额 USD 基准',
  total_limit INT           NOT NULL DEFAULT 100000 COMMENT '限量；>9999 视为不限（DEC-MKT-5）',
  used_count  INT           NOT NULL DEFAULT 0 COMMENT '核销计数，仅 RM-MKT-107/108 可写',
  start_at    DATETIME(3)   NULL,
  end_at      DATETIME(3)   NULL COMMENT 'js_guard end_at>start_at',
  status      VARCHAR(16)   NOT NULL DEFAULT 'draft' COMMENT 'draft|scheduled|active|expiring|expired（SCHED 翻转）',
  description VARCHAR(255)  NULL COMMENT '券说明(EN 基准)',
  created_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_coupon_code (code),
  KEY idx_coupon_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='优惠券（折扣/满减/包邮）';

-- 14. coupon_translation
CREATE TABLE coupon_translation (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  coupon_id   BIGINT       NOT NULL,
  locale      VARCHAR(8)   NOT NULL COMMENT 'es|fr',
  name        VARCHAR(64)  NULL,
  description VARCHAR(255) NULL,
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_cpt (coupon_id, locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='优惠券多语言附表';

-- 15. flash_sale
CREATE TABLE flash_sale (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  name       VARCHAR(64) NOT NULL COMMENT '活动名(EN 基准)',
  discount   VARCHAR(32) NOT NULL COMMENT '如 最高 40% OFF',
  start_at   DATETIME(3) NOT NULL,
  end_at     DATETIME(3) NOT NULL COMMENT 'js_guard end_at>start_at；到期 SCHED 自动 ended（s-761）',
  status     VARCHAR(16) NOT NULL DEFAULT 'draft' COMMENT 'draft|scheduled|active|ended',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_flash_status_end (status, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='闪购活动（限时秒杀）';

-- 16. flash_sale_translation
CREATE TABLE flash_sale_translation (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  flash_sale_id BIGINT      NOT NULL,
  locale        VARCHAR(8)  NOT NULL COMMENT 'es|fr',
  name          VARCHAR(64) NULL,
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_fst (flash_sale_id, locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='闪购多语言附表';

-- 17. flash_sale_product（参与商品 nm）
CREATE TABLE flash_sale_product (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  flash_sale_id BIGINT      NOT NULL,
  product_id    BIGINT      NOT NULL COMMENT '逻辑外键 catalog.product.id',
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_fsp (flash_sale_id, product_id),
  KEY idx_fsp_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='闪购-商品挂载';

-- 18. newsletter_subscriber（决策 26）
CREATE TABLE newsletter_subscriber (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  email         VARCHAR(255) NOT NULL COMMENT '小写归一，唯一（幂等判重）',
  source        VARCHAR(16)  NOT NULL COMMENT 'footer|modal|exit_intent',
  locale        VARCHAR(8)   NOT NULL COMMENT 'en|es|fr',
  subscribed_at DATETIME(3)  NOT NULL COMMENT '订阅时间',
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_newsletter_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Newsletter 订阅（仅落表，不发码不发邮件）';

-- 19. contact_message（决策 30）
CREATE TABLE contact_message (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  name         VARCHAR(100)  NOT NULL,
  email        VARCHAR(255)  NOT NULL,
  subject      VARCHAR(200)  NULL,
  message      VARCHAR(5000) NOT NULL,
  submitted_at DATETIME(3)   NOT NULL COMMENT '提交时间',
  created_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_contact_submitted (submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='联系表单消息（管理端本期不做查看页）';
```

> 备注：①`processed_event` 幂等表归 trading 域 DDL（本域 q.invalidate 消费者复用）；②种子数据（决策 21）按本 DDL 灌入 portal-admin mock.js coupons/flashSales/banners/blogPosts/lookbooks/guides/realWeddings + portal-store data/content.ts（含三语 translation 行；contact/newsletter 不灌样例——纯收集表生产空表起步），归 L3 种子脚本任务；③banner.clicks 列保留 0 起始（点击统计接入归后续 change，er 字段保真）。

## 12. 自检

- [x] er-diagram 本域 9 实体 + 7 translation 附表全部建模；新增 3 张 nm 关联表（flash_sale_product/real_wedding_product/lookbook_product）承载 relations；19 表 DDL 完整，列定义与 er maxlen/枚举/必填一致
- [x] 基类选型（LongAuditableEntity）/ 逻辑删除（不启用，物理删除 + 状态机 guard）/ 审计字段显式声明；DEC-MKT-1 EN 文案列补齐归因
- [x] RM-MKT-001~141（分段编号，无重号）；MAP-MKT-001~014；IDX-MKT-001~022；TX-MKT-001~030 + EC-MKT-001/002；CV-MKT-001~012；SVC-MKT-01；EVT-MKT（发布 + q.invalidate 消费者）；SCHED-MKT-01/02；CACHE-MKT-001~009
- [x] coupon 核销与 trading 接口定稿（SVC-MKT CouponDomainService = trading CouponPort 提供侧；RM-MKT-107/108 与 RM-TRD-112/113 同 SQL 权威定义；redeem 参与 trading 事务不自启）
- [x] content.invalidated 发布面完整 + q.invalidate 消费者（TASK-056 消费者侧）路径映射表 ×3 locale + 重试/DLX 队列参数（error-strategy L2 要求 3 本域落点）
- [x] SCHED-MKT-01 闪购自动下线/券状态翻转/banner 窗口穿越（FLOW-P15 全要素；分布式锁防双跑）；DEC-MKT-2/3 口径定稿
- [x] 缓存 key/TTL/失效触发者与 data-flow 缓存矩阵逐行一致（flash 60s 短 TTL）；key 含 locale；cacheNullValue 穿透保护；validate/newsletter/contact 不缓存
- [x] 事务边界与 api-detail TX 引用一一对应；缓存失效/MQ 一律事务提交后（CP-031）
