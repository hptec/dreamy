# 数据流 - portal-api-integration（七域前后端对接）

本文档定义 catalog / review / trading / shipping / marketing / showroom / analytics 七个限界上下文核心业务流程的数据流转，并逐条响应 `decision.md` 决策 3/4/6/7/8/9/10/13/14/16/17/19/20/22/24/25/28/29 与「后端关键决策」（BE-DIM-4 ~ BE-DIM-8）。与 baseline identity data-flow.md 同构（流程清单 + 决策映射 + 逐流程时序图）。

**参与者命名**：`User`（消费者，portal-store Next.js）、`Guest`（Showroom 免注册访客）、`Admin`（管理员，portal-admin Vue3）、`CDN`（Cloudflare 边缘缓存/WAF）、`Next`（portal-store Node standalone 运行时，决策 22）、`StoreAPI`/`AdminAPI`（Controller + StoreJwtFilter/AdminJwtFilter）、`Svc`（领域服务，各域 module）、`JC`（JetCache 两级 Caffeine+Redis）、`DB`（MySQL，MyBatis-Plus）、`MQ`（RabbitMQ）、`Stripe`、`GA4`（GA4 Data API）、`S3`（S3 兼容对象存储）、`SMTP`（邮件）、`Sched`（@Scheduled 定时任务）。

## 各层数据转换约定（横切）

| 边界 | 转换 | 说明 |
|------|------|------|
| portal-store ⇄ StoreAPI | camelCase（TS 对象）⇄ snake_case（线上 JSON），`lib/api/case.ts` deepSnakeize/deepCamelize | 契约字段一律 snake_case；locale 经 query 参数透传 |
| portal-admin ⇄ AdminAPI | 同上，`src/api/client.ts` axios 拦截器转换 | 分页消费 `PageResult`（total_elements 映射 totalElements） |
| Controller ⇄ Svc | Request DTO（@Valid 校验）⇄ 领域入参；响应 payload 装入 **R 包络** `{code,message,data}` | 分页统一 `huihao.page.Paginated`：data/total_elements/page_number/page_size/number_of_elements/total_pages（L1.2 契约 MUST_FIX-1） |
| Svc ⇄ DB | 实体（huihao-mysql 基类，Long 自增主键）⇄ 表行；translation 附表按 locale 合并：ES/FR 命中取附表字段，缺翻译回退 EN 主表（决策 13） | 消费端读 DTO 输出「已按 locale 解析」的扁平文案字段 |
| Svc ⇄ JC | @Cached key 统一含 locale 维度（消费端只读）；@CacheInvalidate 写失效 | 见「缓存矩阵」 |
| Svc ⇄ Stripe | 金额按订单币种最小货币单位换算（决策 14 锁汇后金额 × 100 取整）；webhook 负载只消费 id/type/data.object | BE-DIM-5 防腐层 |
| Svc ⇄ MQ | 领域事件 JSON（event_id + type + payload），消费侧按 event_id 幂等 | 见「MQ 事件拓扑」 |

## 核心业务流程清单

| 流程编号 | 流程名称 | 域 | 触发条件 | 参与模块 | 验收 |
|---------|---------|----|---------|---------|------|
| FLOW-P01 | 消费端只读数据流（三层缓存命中） | catalog/marketing/review | 用户浏览列表/详情/内容页 | CDN, Next, StoreAPI, Svc, JC, DB | ALIGN-002/016, FUNC-006 |
| FLOW-P02 | 商品全文搜索 | catalog | /search 提交关键词 | StoreAPI, Svc, JC, DB(FULLTEXT) | ALIGN-020, s-773/774 |
| FLOW-P03 | 内容发布秒级失效链（含 OP-011 静态页） | catalog/marketing | 后台保存/发布/上下架 | AdminAPI, Svc, JC, MQ, Next, CDN | s-758, FUNC-006 |
| FLOW-P04 | 购物车（加购/改量/匿名合并） | trading | 用户操作购物车/登录 | StoreAPI, Svc, DB | 决策 8 |
| FLOW-P05 | 结算报价（多承运商/券/锁汇试算） | trading+shipping+marketing | 进入结算/切换选项 | StoreAPI, Svc(trading→shipping/marketing 直调), JC, DB | F-036, 决策 14/15/28, 20.4/20.6 |
| FLOW-P06 | 下单原子事务 + PaymentIntent | trading | 提交订单 | StoreAPI, Svc, DB, Stripe | FUNC-001, BE-DIM-4 |
| FLOW-P07 | Stripe webhook 幂等消费 | trading | Stripe 异步回调 | Stripe, StoreAPI, Svc, DB, MQ | 决策 7/25 |
| FLOW-P08 | 待支付订单超时取消 | trading | 每分钟定时 | Sched, Svc, DB, Stripe | BE-DIM-4 |
| FLOW-P09 | 发货与订单状态流转 | trading | 后台标记发货/完成 | AdminAPI, Svc, DB, MQ | OP-009, s-752 |
| FLOW-P10 | 退款流（申请→审核→Stripe Refund） | trading | 消费端申请/后台审核 | StoreAPI/AdminAPI, Svc, DB, Stripe, MQ | ALIGN-007, s-755, 决策 24/31 |
| FLOW-P11 | 交易/Showroom 邮件消费者（MailRecord） | trading/showroom | MQ 邮件事件 | MQ, Svc, DB, SMTP | ALIGN-019, 决策 16/20.5 |
| FLOW-P12 | Showroom 协作（guest 会话/投票/指派/提醒） | showroom | 访客进入/互动，新娘管理 | User/Guest, StoreAPI, Svc, DB, MQ | ALIGN-023, s-1041 |
| FLOW-P13 | Wishlist / BrowseHistory 幂等写 | trading | 收藏/浏览 PDP | StoreAPI, Svc, DB | ALIGN-021/027 |
| FLOW-P14 | 评价提交与审核（rating 回写） | review | 用户提交/后台审核 | StoreAPI/AdminAPI, Svc, DB, MQ, JC | ALIGN-014, s-756/762 |
| FLOW-P15 | 营销定时投放/闪购到期下线 | marketing | @Scheduled 扫描 | Sched, Svc, DB, JC, MQ, Next, CDN | ALIGN-008/009, s-760/761 |
| FLOW-P16 | Dashboard/Analytics 聚合 + GA4 代理 | analytics | 后台打开看板 | AdminAPI, Svc, JC, DB, GA4 | ALIGN-001/013/022, s-759/s-1043 |
| FLOW-P17 | 预签名上传（S3 直传） | catalog（代管） | 后台/买家秀传图 | AdminAPI/StoreAPI, Svc, S3 | 决策 9 |
| FLOW-P18 | 汇率维护与展示换算 | trading | 后台改汇率/前端读取 | AdminAPI/StoreAPI, Svc, JC, DB, CDN | 决策 14 |
| FLOW-P19 | 公开落表/纯函数端点（尺码推荐/订阅/联系表单） | catalog/marketing | 用户提交 Find My Size 问卷 / Newsletter 订阅 / 联系表单 | CDN(WAF), StoreAPI, Svc, DB | size_recommendation（s-1042）、newsletter_subscribe、contact_submit；决策 20.3/26/30 |

横切流程沿用 identity 基建：**操作审计**（identity FLOW-17 AOP，七域后台写操作全部登记 action，BE-DIM-7）、**JWT 鉴权过滤**（StoreJwtFilter 配置化公开路径白名单 + showroom guest 旁路，L1.2 契约已标注、L2 定稿）。

## 决策响应映射

| 决策 | 本文档响应位置 |
|------|----------------|
| 决策 4/22 三层缓存 + 秒级失效链 | FLOW-P01 读命中、FLOW-P03 失效链、缓存矩阵 |
| 决策 6 双模式库存（乐观锁） | FLOW-P06 扣减、FLOW-P08/P10 回补 |
| 决策 7/25 Stripe 全家桶 | FLOW-P06/P07/P10 |
| 决策 8 购物车合并 | FLOW-P04 |
| 决策 9 预签名上传 | FLOW-P17 |
| 决策 10/19 聚合与 GA4 | FLOW-P16 |
| 决策 13 translation 回退 / 缓存 key 含 locale | 数据转换约定、缓存矩阵 |
| 决策 14/28 锁汇/礼品包装快照 | FLOW-P05/P06/P18 |
| 决策 16/20.5 MQ 邮件 + MailRecord | FLOW-P11 |
| 决策 17 FULLTEXT 搜索 | FLOW-P02 |
| 决策 20 Showroom（guest JWT/dye lot/ordered 推进） | FLOW-P12、FLOW-P07 扇出 |
| 决策 20.3 尺码推荐（区间匹配纯函数） | FLOW-P19（size_recommendation） |
| 决策 24/31 退款政策/审核制 | FLOW-P10 |
| 决策 26 Newsletter 仅落表（幂等 + WAF 限流） | FLOW-P19（newsletter_subscribe） |
| 决策 29 推荐位规则 + 销量回写 | FLOW-P01、MQ 拓扑（订单支付事件→回写） |
| 决策 30 联系表单落表 | FLOW-P19（contact_submit） |
| BE-DIM-4 事务/幂等/超时/MQ | FLOW-P06/P07/P08/P10、MQ 拓扑 |
| BE-DIM-5 外部集成降级 | FLOW-P07/P11/P16/P17 异常路径 |
| BE-DIM-6 user_id 隔离/RBAC | 各写流程鉴权注记；跨用户一律 404 防探测 |
| BE-DIM-7 审计 | 各后台写流程 OperationLog 注记 |
| BE-DIM-8 缓存分级/穿透保护 | 缓存矩阵（null 值短 TTL 缓存防穿透） |

---

## 缓存矩阵（BE-DIM-8：每条只读路径的缓存层级与失效触发者）

| 只读路径 | CDN | JetCache（key 维度） | 失效触发者 |
|----------|-----|---------------------|-----------|
| GET /api/store/products（列表/筛选） | s-maxage | 两级 `catalog:products:{filters}:{locale}` TTL 300s | 后台商品写/上下架/flags → FLOW-P03 |
| GET /api/store/products/{slug} | s-maxage（ISR 页同源） | 两级 `catalog:product:{slug}:{locale}` TTL 300s | 同上（含 SKU/尺码表/翻译变更） |
| GET /api/store/products/search | 不缓存 | 两级 `catalog:search:{q}:{locale}:{page}` TTL 60s | TTL 自然过期（决策 17 短 TTL 兜底） |
| GET /api/store/products/recommendations | s-maxage | 两级 `catalog:reco:{block}:{pid/tid}:{locale}` TTL 300s | 商品写 + 订单支付事件销量回写（FLOW-P07 扇出） |
| GET /api/store/categories | s-maxage | 两级 `catalog:categories:{locale}` TTL 600s | 后台分类写 |
| GET /api/store/tags | s-maxage | 两级 `catalog:tags:{dim}:{locale}` TTL 600s | 后台标签/维度写 |
| GET /api/store/content/banners·blogs·weddings·lookbooks·guides | s-maxage | 两级 `marketing:{res}:{params}:{locale}` TTL 300s | 后台内容写/发布 + 定时投放翻转（FLOW-P15） |
| GET /api/store/promotions/flash-sales | s-maxage（短） | 两级 `marketing:flash:{locale}` TTL 60s | 闪购写/到期下线（FLOW-P15） |
| GET /api/store/reviews、/api/store/questions | s-maxage 60s | 两级 `review:{res}:{product_id}:{page}` TTL 300s（评价不翻译，key 不含 locale） | 审核/回复/图片驳回/答复写（FLOW-P14） |
| GET /api/store/exchange-rates | s-maxage | 两级 `trading:exchange-rates` TTL 600s | 后台汇率维护（FLOW-P18） |
| 购物车/订单/地址/收藏/浏览历史/结算/支付/Showroom 全部 | 不缓存 | 不缓存（决策 4 第 3 层个人与交易数据） | — |
| GET /api/admin/dashboard、/api/admin/analytics/overview | 不经 CDN | 两级 `analytics:dashboard` / `analytics:overview:{range}` TTL 60s | TTL 自然过期（决策 10） |
| GET /api/admin/analytics/traffic | 不经 CDN | 两级 `analytics:traffic:{range}` TTL 300s | TTL 自然过期（决策 19） |
| shipping 规则（trading 进程内直调读） | — | 两级 `shipping:rates` / `shipping:carriers` TTL 600s | 本域写 @CacheInvalidate（进程内，无 MQ） |
| 后台全部列表/详情 | 不经 CDN | 不缓存（实时） | — |

**穿透保护（BE-DIM-8）**：JetCache 开启 `cacheNullValue`（null 短 TTL 60s），未命中且 DB 不存在的 slug/id 不反复打穿源库。

---

## FLOW-P01: 消费端只读数据流（三层缓存命中路径）

**触发条件**: 用户浏览商品列表/详情/内容页（任一 locale 路径 `/`、`/es`、`/fr`，决策 27）。

```mermaid
sequenceDiagram
    actor User
    participant CDN
    participant Next
    participant StoreAPI
    participant Svc
    participant JC
    participant DB

    User->>CDN: GET /es/product/aurelia-gown（页面）或 GET /api/store/products/aurelia-gown?locale=es
    alt CDN 边缘命中（s-maxage 内或 serve-stale）
        CDN-->>User: 200（边缘缓存，海外 RTT 消除，决策 4 第 1 层）
    else 未命中
        CDN->>Next: 回源（页面走 ISR/SSR；API 直透后端）
        Next->>StoreAPI: GET /api/store/products/{slug}?locale=es
        Note over StoreAPI: StoreJwtFilter 公开路径白名单放行（匿名）
        StoreAPI->>Svc: getProduct(slug, locale=es)
        Svc->>JC: GET catalog:product:{slug}:es
        alt JetCache 命中（本地 Caffeine → 远程 Redis 两级，决策 4 第 2 层）
            JC-->>Svc: DTO
        else 未命中
            Svc->>DB: SELECT Product+Sku+Image+SizeChart+Tag（status=published）
            Svc->>DB: SELECT ProductTranslation WHERE product_id=? AND locale='es'
            Note over Svc: 数据转换：ES 文案覆盖 EN 主表字段，缺翻译回退 EN（决策 13）；不存在→缓存 null 60s（穿透保护）
            Svc->>JC: PUT catalog:product:{slug}:es TTL 300s
        end
        Svc-->>StoreAPI: payload（USD 基准价，决策 14 L1 定夺）
        StoreAPI-->>Next: R{code,message,data}
        Next-->>CDN: 渲染页（Cache-Control: s-maxage）
        CDN-->>User: 200 并缓存
    end
    opt 登录用户浏览 PDP
        User->>StoreAPI: POST /api/store/browse-history {product_id}（FLOW-P13）
    end
```

**异常路径**: 商品不存在/未发布 → 404 `404501`（null 缓存防穿透）；源站故障 → CDN serve-stale 吐旧缓存（决策 22，商城不白屏）。

---

## FLOW-P02: 商品全文搜索（决策 17）

```mermaid
sequenceDiagram
    actor User
    participant StoreAPI
    participant Svc
    participant JC
    participant DB

    User->>StoreAPI: GET /api/store/products/search?q=lace&locale=es&page=1
    StoreAPI->>Svc: search(q, locale, page)
    Svc->>JC: GET catalog:search:{q}:es:1（TTL 60s）
    alt 未命中
        Svc->>DB: SELECT ... MATCH(name,subtitle) AGAINST(? IN NATURAL LANGUAGE MODE)（FULLTEXT ngram，EN 主表）
        Svc->>DB: UNION 按 locale 检索 product_translation FULLTEXT + 标签 label（决策 17）
        Svc->>JC: PUT 结果（Paginated）TTL 60s
    end
    Svc-->>StoreAPI: Paginated{data: StoreProductCard[]}
    StoreAPI-->>User: 200（空结果返回空 data，不报错）
```

---

## FLOW-P03: 内容发布秒级失效链（OP-011 / s-758，决策 4/22 核心）

**触发条件**: 后台保存商品（「保存并生成静态页」）、上下架、内容发布、Banner/分类/标签写操作。

```mermaid
sequenceDiagram
    actor Admin
    participant AdminAPI
    participant Svc
    participant DB
    participant JC
    participant MQ
    participant Next
    participant CDN

    Admin->>AdminAPI: PUT /api/admin/products/{id}（OP-011）
    Note over AdminAPI: AdminJwtFilter + RBAC(/products)
    AdminAPI->>Svc: updateProduct(...)
    Note over Svc,DB: 事务：主表+SKU(version 乐观锁 409508)+图片+尺码表+tag_ids+translations 整单覆盖
    Svc->>DB: UPDATE/INSERT/DELETE 各表
    Svc->>DB: INSERT OperationLog(action=编辑商品)  %% BE-DIM-7
    Svc->>JC: @CacheInvalidate catalog:product:{slug}:*、catalog:products:*、catalog:reco:*（全 locale）
    Svc->>MQ: publish content.invalidated {event_id, type:product_updated, slug, locales:[en,es,fr]}
    Svc-->>AdminAPI: R{data: AdminProductDetail}
    AdminAPI-->>Admin: 200（保存成功）

    Note over MQ,CDN: 异步失效消费者（决策 22 连带约束）
    MQ->>Next: POST /api/revalidate（内部端点，按 event 携带路径）
    Next->>Next: revalidatePath('/product/{slug}') + '/es/...' + '/fr/...'（按 locale 路径分别执行，决策 27）
    MQ->>CDN: Cloudflare purge API（zone token 后端配置，按 URL×3 locale）
    Note over CDN: 5 秒内边缘更新（FUNC-006 验收）；purge 失败重试，期间 TTL 兜底 + serve-stale
```

**异常路径**: MQ 投递失败 → 本地事务已提交，JetCache 已失效，CDN 靠 s-maxage TTL 兜底过期；消费者失败 → 重试 ×3 → 死信队列告警（见 MQ 拓扑）。

---

## FLOW-P04: 购物车（决策 8）

```mermaid
sequenceDiagram
    actor User
    participant StoreAPI
    participant Svc
    participant DB

    Note over User: 匿名期购物车在 localStorage（前端态）
    User->>StoreAPI: POST /api/store/cart/merge {anon_token, items[]}（登录成功后一次性调用）
    Note over StoreAPI: StoreBearerAuth，customer_id=JWT subject（BE-DIM-6）
    StoreAPI->>Svc: mergeCart(customerId, anonToken, items)
    Note over Svc,DB: 幂等：anon_token 已合并过则直接返回现车
    Svc->>DB: 逐条 UPSERT CartItem（同 SKU/同定制数据合并 qty；现货超库存截断并记 merged_truncated_item_ids）
    Svc-->>StoreAPI: CartResponse
    StoreAPI-->>User: 200

    User->>StoreAPI: POST /api/store/cart/items {product_id, sku_id?, qty, custom_size_data?}
    Note over Svc: js_guard 422604：现货必填 sku_id；定制款 sku_id 空 + custom_size_data 必填（决策 6）
    Svc->>DB: INSERT/UPDATE CartItem（库存校验 409601 仅提示，不预占）
    Svc-->>StoreAPI: CartResponse（含 dye_lot_product_ids，showroom 域直调判定，决策 20.4）
```

---

## FLOW-P05: 结算报价（跨域同步直调，决策 3）

```mermaid
sequenceDiagram
    actor User
    participant StoreAPI
    participant Trading as Svc(trading)
    participant Shipping as Svc(shipping)
    participant Marketing as Svc(marketing)
    participant Showroom as Svc(showroom)
    participant JC
    participant DB

    User->>StoreAPI: POST /api/store/checkout/quote {address_id|country, currency, carrier?, coupon_code?, gift_wrap, wedding_date?}
    StoreAPI->>Trading: quote(...)
    Trading->>DB: 读当前用户 CartItem + 商品/SKU 快照
    Trading->>Shipping: 进程内直调 quoteOptions(country, subtotalUSD)
    Shipping->>JC: GET shipping:rates / shipping:carriers（TTL 600s）
    Note over Shipping: 地理分区映射 → 每个 enabled Carrier 取「<区域> / <承运商>」规则行计费；无后缀行为全承运商兜底（F-036）
    Shipping-->>Trading: ShippingOption[]{carrier, feeUSD, lead_time}
    opt coupon_code 提交
        Trading->>Marketing: 进程内直调 validateCoupon(code, subtotalUSD)
        Marketing-->>Trading: {valid, discountUSD | reason_code 4227xx}（无效不阻断报价）
    end
    Trading->>DB: 读 ExchangeRate（currency）→ 试算汇率（不锁定）
    Note over Trading: 金额换算：USD 基准 → 订单币种（multi_currency_prices 覆盖价优先）；total = subtotal + shipping_fee + gift_wrap_fee − discount（决策 14/15/28）
    Trading->>Showroom: 进程内直调 dyeLotHint(customerId, productIds)（24h 同款式已付订单，决策 20.4）
    Note over Trading: 交期复核：max(lead_time_days) 晚于 wedding_date → lead_time_warning=true（决策 20.6）
    Trading-->>StoreAPI: CheckoutQuoteResponse{shipping_options[], ...}
    StoreAPI-->>User: 200
```

---

## FLOW-P06: 下单原子事务 + PaymentIntent（FUNC-001 核心，BE-DIM-4）

```mermaid
sequenceDiagram
    actor User
    participant StoreAPI
    participant Svc
    participant DB
    participant Stripe

    User->>StoreAPI: POST /api/store/checkout/orders {idempotency_key, address_id, currency, carrier, coupon_code?, gift_wrap, wedding_date?, payment_method, locale}
    StoreAPI->>Svc: createOrder(...)
    Svc->>DB: SELECT order WHERE idempotency_key=?（唯一索引）
    alt 幂等键已存在
        Svc-->>StoreAPI: 409 {409603, details.order_id}（前端跳既有订单支付）
    else 首次提交
        Note over Svc,DB: BE-DIM-4 原子事务开始
        Svc->>DB: 订单号预生成 DRM-YYYYMMDD-NNNN + INSERT Order(status=pending, expires_at=now+30min, exchange_rate=当前锁汇, gift_wrap_fee 快照, carrier, wedding_date)
        Svc->>DB: INSERT OrderLine[]（商品/SKU/价格/img 快照 + custom_size_data）
        loop 每个现货行
            Svc->>DB: UPDATE Sku SET stock=stock-qty, version=version+1 WHERE id=? AND version=? AND stock>=qty
            Note over Svc: CAS 失败重读重试 ×3，仍失败 → 整体回滚 409601 STOCK_INSUFFICIENT（定制行不扣减，决策 6）
        end
        opt coupon_code
            Svc->>DB: UPDATE Coupon used_count=used_count+1 WHERE used_count<total_limit（marketing 直调核销，失败 422703 回滚）
        end
        Svc->>DB: DELETE CartItem WHERE customer_id=?（清车）
        Note over Svc,DB: 事务提交
        Svc->>Stripe: Create PaymentIntent(amount=total_amount 订单币种, currency, payment_method_types 由 Payment Element 承载, metadata.order_no)
        alt Stripe 失败/超时
            Stripe-->>Svc: error
            Svc-->>StoreAPI: 502 {502601} / 504 {504601}（订单保持 pending，可经 /orders/{id}/payment-intent 重试）
        else 成功
            Svc->>DB: INSERT Payment(status=created, payment_intent_id, amount, currency)
            Svc-->>StoreAPI: 201 OrderCreateResponse{order, payment{client_secret}}
            StoreAPI-->>User: Payment Element 按订单币种初始化（决策 25）
        end
    end
```

---

## FLOW-P07: Stripe webhook 幂等消费（决策 7/25）

```mermaid
sequenceDiagram
    participant Stripe
    participant StoreAPI
    participant Svc
    participant DB
    participant MQ

    Stripe->>StoreAPI: POST /api/store/payments/stripe/webhook (Stripe-Signature)
    Note over StoreAPI: 白名单豁免 JWT → 签名验证（失败 401 {401601}，Stripe 按其重试策略重投）
    StoreAPI->>Svc: handleEvent(event)
    Svc->>DB: INSERT processed_event(event_id) ON DUPLICATE → 已处理
    alt event_id 已消费（至少一次投递）
        Svc-->>StoreAPI: 200 {received:true}（幂等空操作）
    else payment_intent.succeeded
        Note over Svc,DB: 事务：Payment→succeeded, Order→paid(paid_at)
        Svc->>DB: UPDATE Payment/Order（金额按订单币种核对，决策 14 连带约束，不符告警拒绝）
        Svc->>MQ: publish order.paid {event_id, order_no, customer_id, locale, lines[]}
        Note over MQ: 扇出：邮件(order_confirmed)→FLOW-P11；showroom 成员 ordered 推进+linked_customer_id 回填；商品销量冗余字段回写(best_sellers，决策 29)→失效 catalog:reco:*；analytics 实时聚合自然可见
        Svc-->>StoreAPI: 200
    else payment_intent.payment_failed
        Svc->>DB: UPDATE Payment status=failed（订单保持 pending 可重试支付）
        Svc-->>StoreAPI: 200
    else charge.refunded
        Svc->>DB: 关联 Refund/Order 状态推进（与 FLOW-P10 审核路径幂等汇合）
        Svc-->>StoreAPI: 200
    end
```

BNPL（Klarna/Afterpay）异步确认沿用同一链路（决策 25）：下单后 PaymentIntent 处于 processing，最终态由 webhook 驱动。

---

## FLOW-P08: 待支付订单超时取消（BE-DIM-4）

```mermaid
sequenceDiagram
    participant Sched
    participant Svc
    participant DB
    participant Stripe

    Note over Sched: 每分钟 @Scheduled
    Sched->>Svc: cancelExpiredOrders()
    Svc->>DB: SELECT Order WHERE status=pending AND expires_at < now（分页批量）
    loop 每单
        Note over Svc,DB: 原子事务：取消 + 回补
        Svc->>DB: UPDATE Order status=cancelled
        Svc->>DB: UPDATE Sku stock=stock+qty, version=version+1（仅现货行回补）
        opt 已核销券
            Svc->>DB: UPDATE Coupon used_count=used_count-1（回滚核销）
        end
        Svc->>Stripe: Cancel PaymentIntent（失败仅告警：webhook 侧 processed_event + 订单状态 guard 防迟到支付落账，迟到 succeeded 事件按 cancelled 订单走自动退款补偿）
    end
```

---

## FLOW-P09: 发货与订单状态流转（OP-009 / s-752）

```mermaid
sequenceDiagram
    actor Admin
    participant AdminAPI
    participant Svc
    participant DB
    participant MQ

    Admin->>AdminAPI: POST /api/admin/orders/{id}/ship {carrier, tracking_no}
    Note over AdminAPI: RBAC(/orders)
    AdminAPI->>Svc: ship(orderId, carrier, trackingNo)
    alt status != paid
        Svc-->>AdminAPI: 409 {409602 ORDER_STATE_INVALID}
    else
        Svc->>DB: UPDATE Order status=shipped, carrier, tracking_no, shipped_at（物流单号手填，BE-DIM-5）
        Svc->>DB: INSERT OperationLog(action=订单发货)
        Svc->>MQ: publish order.shipped → 邮件(shipped) FLOW-P11
        Svc-->>AdminAPI: 200 AdminOrderDetail
    end

    Admin->>AdminAPI: PATCH /api/admin/orders/{id}/status {completed|cancelled}
    Note over Svc: 状态机 guard：shipped→completed（解锁评价 s-756）；pending→cancelled（回补库存同 FLOW-P08）；其余 409602
```

---

## FLOW-P10: 退款流（决策 24/31，s-755）

```mermaid
sequenceDiagram
    actor User
    actor Admin
    participant StoreAPI
    participant AdminAPI
    participant Svc
    participant DB
    participant Stripe
    participant MQ

    User->>StoreAPI: POST /api/store/orders/{id}/refunds {reason}
    Note over Svc: 资格判定（决策 24）：现货未发货全额/已发货退货后退；定制行（custom_size_data 非空）paid_at+宽限期(配置默认24h)内可退，超时 422 {422602}；进行中工单 409 {409605}
    Svc->>DB: INSERT Refund(refund_no, status=pending, amount=可退上限含 gift_wrap_fee, currency=订单币种) + UPDATE Order status=refunding
    StoreAPI-->>User: 201 StoreRefund

    Admin->>AdminAPI: POST /api/admin/refunds/{id}/approve {return_tracking_no?}
    Note over AdminAPI: RBAC(/refunds)；已发货单为审核制：线下确认收到退货后才点通过（决策 31）
    alt status != pending
        Svc-->>AdminAPI: 409 {409604 REFUND_STATE_INVALID}
    else
        Note over Svc,DB: BE-DIM-4 原子事务：Refund+Order+库存回补，Stripe 失败整体回滚
        Svc->>Stripe: Create Refund(payment_intent, amount 原币种原金额（决策 14）)
        alt Stripe 失败/超时
            Stripe-->>Svc: error → 回滚，工单保持 pending（502601/504601 可重试）
        else
            Svc->>DB: UPDATE Refund status=approved, stripe_refund_id, return_tracking_no? + Order status=refunded + Sku 回补（现货行）
            Svc->>DB: INSERT OperationLog(action=退款审核通过)
            Svc->>MQ: publish refund.resolved {result:approved} → 邮件(refund_result) FLOW-P11
            Svc-->>AdminAPI: 200 AdminRefund
        end
    end
    Note over Admin: reject 路径：Refund→rejected + Order 回 paid/shipped + MQ refund.resolved{result:rejected} + 审计
```

---

## FLOW-P11: 邮件消费者（MQ → MailRecord → SMTP，决策 16/20.5）

```mermaid
sequenceDiagram
    participant MQ
    participant Svc as MailConsumer
    participant DB
    participant SMTP

    MQ->>Svc: order.paid / order.shipped / refund.resolved / showroom.invite / showroom.remind
    Note over Svc: 事件类型 → MailRecord.type：order_confirmed / shipped / refund_result / showroom_invite / showroom_assign（决策 20.5 扩展枚举）
    Svc->>DB: INSERT MailRecord(type, order_id|showroom 关联, recipient, locale, status=pending) ON DUPLICATE(orderId+type 幂等键) → 已存在则跳过（防重发）
    Svc->>SMTP: send(template(type, locale))  %% 按用户 locale 三语渲染；dev 落日志 stub
    alt 发送成功
        Svc->>DB: UPDATE MailRecord status=sent, sent_at
    else 临时失败
        Svc->>DB: UPDATE status=failed, retry_count+1
        Note over MQ: nack → 延迟重试队列（指数退避 ×3）
        alt 超过重试上限
            MQ->>MQ: 路由死信队列 mail.dlq
            Svc->>DB: UPDATE MailRecord status=dead（告警，人工补发）
        end
    end
```

---

## FLOW-P12: Showroom 协作（决策 20，s-1041）

```mermaid
sequenceDiagram
    actor Guest
    actor User as Owner
    participant StoreAPI
    participant Svc
    participant DB
    participant MQ

    Guest->>StoreAPI: POST /api/store/showrooms/guest-session {invite_token, nickname}
    Note over StoreAPI: 公开白名单端点；WAF 限流（决策 11）
    Svc->>DB: SELECT Showroom WHERE invite_token=?（不可猜 UUID）
    alt token 无效/已重置
        Svc-->>StoreAPI: 401 {401101} / 410 {410101}
    else
        Svc->>DB: SELECT/INSERT ShowroomMember(nickname 同房唯一，复用即返回；新建 assign_status=unassigned)
        Svc-->>StoreAPI: 200 GuestSession{guest_token(短期受限 JWT：showroom_id+member_id+invite 版本号), member}
    end

    Guest->>StoreAPI: PUT /showrooms/{id}/items/{itemId}/vote {vote} （或 POST .../comments）
    Note over StoreAPI: guest JWT 旁路校验：仅绑定 showroom 可写（越权 403 {403102}）
    Svc->>DB: UPSERT ShowroomVote(member_id+item_id 唯一，重复投票覆盖)
    Svc-->>Guest: 200 {like_count, dislike_count, my_vote}（实时聚合，不缓存）

    User->>StoreAPI: POST /showrooms/{id}/members/{mid}/assign {assigned_item_id, email}
    Note over Svc: owner 校验（非 owner 403 {403101}）；ordered 后不可再指派 409 {409103}
    Svc->>DB: UPDATE ShowroomMember assigned_item_id, email, assign_status=assigned
    User->>StoreAPI: POST .../members/{mid}/remind
    Svc->>MQ: publish showroom.remind {member_id, email, locale} → FLOW-P11（MailRecord 幂等）
    Svc->>DB: UPDATE assign_status=reminded

    Note over MQ,DB: order.paid 事件（FLOW-P07 扇出）：linked_customer_id 命中成员 + 订单行命中 assigned_item → assign_status=ordered；同时刷新 24h dye lot 窗口数据（决策 20.4）
```

**邀请重置**: POST /showrooms/{id}/invite/reset → 新 UUID 落库 + invite 版本号自增 → 旧 guest JWT 即时失效（401101）。

---

## FLOW-P13: Wishlist / BrowseHistory（决策 18/23）

```mermaid
sequenceDiagram
    actor User
    participant StoreAPI
    participant Svc
    participant DB

    User->>StoreAPI: POST /api/store/wishlists {product_id}
    Note over Svc: customer_id+product_id 唯一：已存在幂等返回 200，新增 201（未登录由前端引导登录，不出请求）
    Svc->>DB: INSERT IGNORE WishlistItem

    User->>StoreAPI: POST /api/store/browse-history {product_id}（登录用户 PDP 上报；匿名不调用）
    Svc->>DB: UPSERT BrowseHistory(viewed_at=now)
    Svc->>DB: DELETE 超出每用户 50 条的最旧记录（滚动清理）
    StoreAPI-->>User: 204
```

---

## FLOW-P14: 评价提交与审核（s-756/s-762 + rating 回写）

```mermaid
sequenceDiagram
    actor User
    actor Admin
    participant StoreAPI
    participant AdminAPI
    participant Svc
    participant DB
    participant MQ
    participant JC

    User->>StoreAPI: POST /api/store/reviews {product_id, rating, content, images[]}
    Note over Svc: 越权防护：当前 user_id 存在含该商品的 completed 订单（trading 直调校验，否则 403 {403801}）；同商品已评 409 {409801}
    Svc->>DB: INSERT Review(status=pending) + ReviewImage[]
    StoreAPI-->>User: 201（前台不可见，待审核）

    Admin->>AdminAPI: PATCH /api/admin/reviews/{id}/status {approved|rejected}
    Note over Svc: js_guard：仅 pending 可审（409802）；reject 强制 featured=false
    Svc->>DB: UPDATE Review status + OperationLog(action=评价审核)
    Svc->>JC: @CacheInvalidate review:reviews:{product_id}:*
    Svc->>MQ: publish review.moderated {product_id}
    Note over MQ: 消费者回写 Product.rating_avg/rating_count 冗余字段（review→catalog 反向依赖，读不跨域 join）→ 失效 catalog:product:* / reco:*
```

---

## FLOW-P15: 营销定时投放 / 闪购到期下线（ALIGN-008/009）

```mermaid
sequenceDiagram
    participant Sched
    participant Svc
    participant DB
    participant JC
    participant MQ

    Note over Sched: 每分钟 @Scheduled 扫描
    Sched->>DB: Banner：now ∈ [start,end] 且 published → 进入投放；now > end → 移出（状态不变，读路径按窗口过滤）
    Sched->>DB: UPDATE Coupon status: scheduled→active→expiring→expired（按 start_at/end_at）
    Sched->>DB: UPDATE FlashSale status: scheduled→active；now>end_at → ended（自动下线，s-761）
    opt 任一状态翻转
        Svc->>JC: @CacheInvalidate marketing:flash:* / marketing:banners:*
        Svc->>MQ: publish content.invalidated → FLOW-P03 失效链（revalidate + purge）
    end
```

---

## FLOW-P16: Dashboard / Analytics 聚合 + GA4 代理（决策 10/19）

```mermaid
sequenceDiagram
    actor Admin
    participant AdminAPI
    participant Svc
    participant JC
    participant DB
    participant GA4

    Admin->>AdminAPI: GET /api/admin/analytics/overview?range=30d
    Note over AdminAPI: RBAC(/analytics)
    Svc->>JC: GET analytics:overview:30d（TTL 60s）
    alt 未命中
        Note over Svc,DB: 决策 3 例外：只读跨域 SQL 聚合（Order/Refund/Category/Product/OrderLine）
        Svc->>DB: GMV/订单数/客单价/退款率 + gmv_trend 按日 + category_sales + top_products（金额按 exchange_rate 折回 USD 口径）
        Svc->>JC: PUT TTL 60s
    end
    Svc-->>Admin: 200

    Admin->>AdminAPI: GET /api/admin/analytics/traffic?range=30d
    Svc->>JC: GET analytics:traffic:30d（TTL 300s）
    alt 未命中
        Svc->>GA4: runReport(sessionSource / deviceCategory / 电商事件漏斗)（service account，凭证仅后端）
        alt GA4 失败/超时
            Svc-->>Admin: 200 {source_status:unavailable, 字段 null}（降级"数据暂不可用"，交易指标不受影响，决策 19）
        else
            Svc->>JC: PUT TTL 300s
            Svc-->>Admin: 200 {source_status:ok, traffic_sources, device_share, funnel}
        end
    end
```

消费端埋点：gtag 标准电商事件 + Consent Mode v2 由 portal-store 直连 GA4，不经后端（决策 19 连带约束）。

---

## FLOW-P17: 预签名上传（决策 9）

```mermaid
sequenceDiagram
    actor Client as Admin/User
    participant API as AdminAPI/StoreAPI
    participant Svc
    participant S3

    Client->>API: POST /api/admin/uploads/presign（或 /api/store/uploads/presign 买家秀）{file_name, content_type, scope}
    Note over Svc: MIME 白名单校验（image/*、video/mp4）；对象 key=scope/雪花序/file_name
    Svc->>S3: 生成预签名 PUT URL（短时效）
    alt S3 不可达
        Svc-->>API: 502 {502501/502801}（降级：提示稍后重试，表单其余字段可先保存）
    else
        Svc-->>API: {upload_url, object_key, public_url, expires_at}
        Client->>S3: PUT 文件直传（不经后端）
        Client->>API: 落库时仅提交 public_url/object_key（CDN 直出）
    end
```

---

## FLOW-P18: 汇率维护与展示换算（决策 14）

```mermaid
sequenceDiagram
    actor Admin
    actor User
    participant AdminAPI
    participant StoreAPI
    participant Svc
    participant DB
    participant JC
    participant CDN

    Admin->>AdminAPI: PUT /api/admin/exchange-rates/{currency} {rate}
    Note over Svc: USD 恒为 1 不可改（422605）；仅影响新订单锁汇，既有订单 exchange_rate 不变
    Svc->>DB: UPDATE ExchangeRate(rate, updated_by, updated_at) + OperationLog(action=汇率变更)
    Svc->>JC: @CacheInvalidate trading:exchange-rates → MQ → CDN purge

    User->>CDN: GET /api/store/exchange-rates（公开白名单，s-maxage）
    Note over User: 客户端展示换算：multi_currency_prices 覆盖价优先，否则 USD×rate（决策 14 L1 定夺）；下单锁汇以服务端为准
```

---

## FLOW-P19: 公开落表 / 纯函数端点（size_recommendation / newsletter_subscribe / contact_submit）

**触发条件**: 用户提交 Find My Size 问卷（PDP，决策 20.3，s-1042）、Newsletter 订阅（footer/弹窗/Exit Intent，决策 26）、联系表单（/contact，决策 30）。三条流程共性：匿名公开端点（StoreJwtFilter 白名单）、无跨域依赖、写限流全部在 Cloudflare WAF 层（决策 11），合并为一图。

```mermaid
sequenceDiagram
    actor User
    participant WAF as CDN(WAF)
    participant StoreAPI
    participant Svc
    participant DB

    Note over User,WAF: 三端点均匿名公开；POST 写限流在 WAF 层拦截（超限 429，不经后端码表）

    rect rgb(245,245,245)
    Note over User,DB: ① size_recommendation（catalog，纯函数）
    User->>StoreAPI: POST /api/store/products/{id}/size-recommendation {height,bust,waist,hips,fit_preference}
    StoreAPI->>Svc: recommendSize(productId, 问卷, locale)
    Svc->>DB: SELECT SizeChartRow WHERE product_id=?（仅读，无写副作用，不缓存）
    Note over Svc: 区间匹配：身高/胸/腰/臀落码段 + 松紧偏好偏移一档（决策 20.3）；话术为区间说明，不虚构买家占比
    alt 三围落入码段
        Svc-->>StoreAPI: 200 {matched:true, recommended_row, explanation, dimension_notes}
    else 无法匹配 / 商品无尺码表
        Svc-->>StoreAPI: 200 {matched:false, explanation 建议话术}
    else 输入超出可校验范围
        Svc-->>StoreAPI: 422 {422502 SIZE_INPUT_OUT_OF_RANGE}
    end
    end

    rect rgb(245,245,245)
    Note over User,DB: ② newsletter_subscribe（marketing，决策 26）
    User->>StoreAPI: POST /api/store/newsletter {email, source(footer|modal|exit_intent), locale}
    StoreAPI->>Svc: subscribe(...)
    Svc->>DB: INSERT NewsletterSubscriber(email 唯一索引) ON DUPLICATE → 幂等返回成功
    Note over Svc: 仅落表收集：不发码不发邮件（弹窗文案为纯订阅确认，显式功能降级）
    Svc-->>StoreAPI: 200 {subscribed:true}（重复订阅同样 200，不泄露邮箱是否已存在）
    end

    rect rgb(245,245,245)
    Note over User,DB: ③ contact_submit（marketing，决策 30）
    User->>StoreAPI: POST /api/store/contact {name, email, subject?, message}
    StoreAPI->>Svc: submitContact(...)
    Svc->>DB: INSERT ContactMessage(submitted_at=now)
    Note over Svc: 管理端本期不做查看页（运营直查库）；无后续流转
    Svc-->>StoreAPI: 201 {submitted:true}
    end
```

**异常路径**: 字段校验失败 → 422 `422502`（尺码输入越界）/ `422704`（邮箱格式/长度等 marketing 通用校验码）；WAF 超限 → 429（边缘拦截，决策 11）。三端点均不缓存、不发 MQ 事件、不写 OperationLog（非后台操作）。

---

## MQ 事件拓扑（RabbitMQ，BE-DIM-4）

```mermaid
flowchart LR
    subgraph Producers[生产者]
        T[trading：order.paid / order.shipped / order.cancelled / refund.resolved]
        C[catalog/marketing：content.invalidated]
        R[review：review.moderated]
        S[showroom：showroom.invite / showroom.remind]
    end
    EX{{topic exchange: dreamy.events}}
    T --> EX
    C --> EX
    R --> EX
    S --> EX
    EX -->|order.*| Q1[q.mail 邮件消费者<br/>FLOW-P11 MailRecord 幂等]
    EX -->|order.paid| Q2[q.showroom 成员 ordered 推进<br/>+ dye lot 窗口（FLOW-P12）]
    EX -->|order.paid| Q3[q.catalog.sales 销量冗余回写<br/>best_sellers（决策 29）+ 失效 reco 缓存]
    EX -->|review.moderated| Q4[q.catalog.rating rating_avg/count 回写<br/>+ 失效商品缓存]
    EX -->|content.invalidated| Q5[q.invalidate 失效消费者<br/>revalidatePath + Cloudflare purge（FLOW-P03）]
    EX -->|showroom.*| Q1
    EX -->|refund.resolved| Q1
    Q1 & Q2 & Q3 & Q4 & Q5 -->|nack ×3 指数退避| DLX{{dlx: dreamy.dlq}}
    DLX --> DLQ[死信队列：告警 + 人工重放<br/>邮件类同步标记 MailRecord=dead]
```

**消费幂等规范**：全部消费者按 `event_id` 去重（processed_event 表 / Redis SETNX）；回写类消费者操作天然可重入（UPSERT/覆盖写）。**顺序性**：同一 order_no 的事件按 routing key 单队列串行消费即可，不要求全局有序。

---

## 检查清单

- [x] 七域核心业务流程全部有数据流图（FLOW-P01~P19 + MQ 拓扑，覆盖 ALIGN-001~035 关键链路与 business-flow.yml 全部流程，含 size_recommendation / newsletter_subscribe / contact_submit）
- [x] 数据流图包含正常路径和异常路径（库存冲突/Stripe 失败/GA4 降级/token 失效/死信）
- [x] 参与者命名清晰（User/Guest/Admin/CDN/Next/StoreAPI/AdminAPI/Svc/JC/DB/MQ/Stripe/GA4/S3/SMTP/Sched）
- [x] 各层数据转换显式定义（snake_case ⇄ camelCase、R 包络/Paginated、translation 回退、锁汇换算、Stripe 金额单位）
- [x] 每条只读路径标注缓存层级与失效触发者（缓存矩阵，BE-DIM-8）
- [x] 三层缓存失效链与静态页 revalidate+purge 落图（FLOW-P03，决策 4/22，s-758/OP-011）
- [x] MQ 拓扑含死信重试与消费幂等（BE-DIM-4），邮件类型含 showroom_invite/showroom_assign（决策 20.5）
- [x] 外部集成数据流（Stripe/GA4/S3/SMTP）含超时与降级路径（BE-DIM-5）
- [x] 数据流与 L1.2 七份 OpenAPI 契约端点一一对应；逐条响应 decision.md 决策（见映射表）
