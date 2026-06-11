# trading 数据层详细设计（L2）

> 角色: l2_data_designer ｜ change: portal-api-integration ｜ domain: trading
> 方法论：Entity 与 DDL / Repository 方法(RM-TRD) / DTO↔Entity 映射(MAP-TRD) / 索引(IDX-TRD) / 事务边界(TX-TRD，本域核心) / 数据校验(CV-TRD) / 领域事件(EVT-TRD) / 定时任务(SCHED-TRD) / 缓存设计(CACHE-TRD)。
> 来源权威：er-diagram.yml（Customer/Address/CartItem/Order/OrderLine/Payment/Refund/WishlistItem/BrowseHistory/ExchangeRate + 决策 28 CheckoutConfig）+ trading-api.openapi.yml v1.1.0 + data-flow.md FLOW-P04~P10/P13/P18 + state-machine.yml（order/payment/refund lifecycle）+ error-strategy.md（processed_event 90 天保留为强制项）。
> 实现基线：huihao-mysql `LongAuditableEntity`（Long 自增主键 + created_at/updated_at 审计列，列名以基类派生为准）+ MyBatis-Plus；无物理外键，逻辑外键 + 事务维护（CP-010）；本 DDL 为语义基线，L3 经 huihao `@Table/@Column/@Index` 注解 DdlAuto 派生落地（枚举可按 identity 既有落地映射 tinyint 码，取值语义与本文 CHECK 一致）。

## 0. 实体清单（12 表）

| 表 | 实体 | 说明 |
|---|---|---|
| `cart_item` | CartItem | 购物车条目（登录用户 DB 持久化，决策 8） |
| `cart_merge_record` | — | 匿名车合并幂等记录（mergeCart anon_token 去重，设计派生表） |
| `address` | Address | 地址簿（订单用 address_snapshot 快照，删除不波及） |
| `orders` | Order | 订单主单（表名取 `orders` 规避 MySQL 保留字 ORDER） |
| `order_line` | OrderLine | 订单行（商品/SKU/价格/图 快照 + custom_size_data） |
| `payment` | Payment | 支付单（Stripe PaymentIntent，webhook 驱动状态） |
| `refund` | Refund | 退款工单（决策 24/31，returnTrackingNo 登记字段） |
| `wishlist_item` | WishlistItem | 收藏（决策 18，customer+product 唯一幂等） |
| `browse_history` | BrowseHistory | 浏览历史（决策 23，upsert + 每用户 50 条滚动） |
| `exchange_rate` | ExchangeRate | 汇率表（决策 14，五币种种子，USD 恒 1） |
| `checkout_config` | CheckoutConfig | 结算配置单例（决策 24/28，id=1） |
| `processed_event` | — | Stripe webhook event_id 幂等存储（BE-DIM-4，90 天保留） |

Customer 为 identity 域既有实体（user 表），本域仅以 `customer_id` 逻辑外键引用，**不建表、不跨域 join**（订单列表客户姓名/邮箱经 identity 进程内查询接口批量联取）。Sku/Coupon 归属 catalog/marketing 域，本域仅经端口在事务内执行库存 CAS 与券核销 SQL（领域服务接口暴露，决策 3）。

---

## 1. Repository 方法（RM-TRD）

### CartItemRepository
- RM-TRD-001 `listByCustomerId(customerId) -> List<CartItem>` —— idx_cart_customer
- RM-TRD-002 `findByIdAndCustomerId(id, customerId) -> CartItem?` —— 隔离点查（404603）
- RM-TRD-003 `findMergeTarget(customerId, skuId?, customSizeHash?) -> CartItem?` —— 同 SKU/同定制数据合并判定（custom_size_data 规范化 JSON 的 SHA-256 入 `custom_size_hash` 生成列比较）
- RM-TRD-004 `insert(CartItem)` / RM-TRD-005 `updateQty(id, qty)` / RM-TRD-006 `deleteByIdAndCustomerId(id, customerId) -> affected`
- RM-TRD-007 `deleteAllByCustomerId(customerId)` —— 下单清车（TX-TRD-001 第 5 步）

### CartMergeRecordRepository
- RM-TRD-008 `insertIgnore(customerId, anonToken) -> affected` —— uk_merge_customer_token，affected=0 即已合并（TX-TRD-007 幂等闸）

### AddressRepository
- RM-TRD-010 `listByCustomerId(customerId)` —— is_default DESC, id DESC
- RM-TRD-011 `findByIdAndCustomerId(id, customerId) -> Address?`（404602）
- RM-TRD-012 `clearDefault(customerId)` —— `UPDATE address SET is_default=0 WHERE customer_id=? AND is_default=1`（TX-TRD-008）
- RM-TRD-013 `insert(Address)` / RM-TRD-014 `updateAll(Address)` / RM-TRD-015 `deleteByIdAndCustomerId -> affected`
- RM-TRD-016 `countByCustomerId(customerId)` —— 首条地址强制默认判定

### OrderRepository
- RM-TRD-020 `findByIdempotencyKey(key) -> Order?` —— uk_order_idem（createOrder 幂等预检）
- RM-TRD-021 `insert(Order) -> id` —— uk_order_idem/uk_order_no 唯一冲突向上抛（幂等竞态/订单号重试）
- RM-TRD-022 `findByIdAndCustomerId(id, customerId) -> Order?`（404601 防探测）/ RM-TRD-023 `findById(id) -> Order?`（admin）
- RM-TRD-024 `pageByCustomer(customerId, status?, page) -> Paginated<Order>` —— idx_order_customer_created
- RM-TRD-025 `pageByAdminFilter(status?, currency?, from?, to?, orderNoLike?, customerIds?, page)` —— idx_order_status_created
- RM-TRD-026 `casUpdateStatus(id, fromStatus, toStatus, setClause) -> affected` —— 状态机条件更新统一入口（guard 失败 affected=0 → 409602），落地形如 `UPDATE orders SET status=:to[, paid_at/shipped_at/completed_at/carrier/tracking_no=...] WHERE id=:id AND status=:from`
- RM-TRD-027 `listExpiredPending(now, limit) -> List<Order>` —— `WHERE status='pending' AND expires_at < :now LIMIT :limit`（idx_order_status_expires，SCHED-TRD-001 分页批量）
- RM-TRD-028 `findByPaymentIntentId(piId) -> Order?` —— 经 payment 表 uk_payment_intent 反查（webhook 定位）

### OrderLineRepository
- RM-TRD-030 `batchInsert(List<OrderLine>)` / RM-TRD-031 `listByOrderId(orderId)`
- RM-TRD-032 `aggregateByOrderIds(orderIds) -> Map<orderId,{line_count, first_line_img}>` —— 列表派生字段一次聚合（NP 防 N+1）
- RM-TRD-033 `existsCustomLine(orderId) -> bool` —— `custom_size_data IS NOT NULL`（决策 24 投产判定）
- RM-TRD-034 `listSpotLines(orderId) -> List<OrderLine>` —— `sku_id IS NOT NULL`（库存扣减/回补对象）

### PaymentRepository
- RM-TRD-040 `insert(Payment)` / RM-TRD-041 `findByOrderId(orderId) -> Payment?`（一单一活跃支付单，重建 PI 原地 UPDATE）
- RM-TRD-042 `findByPaymentIntentId(piId) -> Payment?` —— uk_payment_intent（webhook 热路径）
- RM-TRD-043 `casUpdateStatus(id, fromStatuses, toStatus, set...) -> affected` —— payment_lifecycle guard
- RM-TRD-044 `rebindPaymentIntent(id, newPiId)` —— retryOrderPayment 重建凭据

### RefundRepository
- RM-TRD-050 `insert(Refund)` —— uk_refund_no 兜底
- RM-TRD-051 `findById(id) -> Refund?`（404605）/ RM-TRD-052 `listByOrderId(orderId)`
- RM-TRD-053 `existsPendingByOrderId(orderId) -> bool` —— idx_refund_order_status（409605）
- RM-TRD-054 `casApprove(id, stripeRefundId?, returnTrackingNo?) -> affected` / RM-TRD-055 `casReject(id, rejectReason) -> affected` —— `WHERE id=? AND status='pending'`（409604 并发双审防护）
- RM-TRD-056 `updateReturnTrackingNo(id, trackingNo)`（patchAdminRefund）
- RM-TRD-057 `pageByAdminFilter(status?, keyword?, page)` —— 联 orders 取 order_no、identity 取客户（applied_at DESC）

### WishlistItemRepository
- RM-TRD-060 `listByCustomerId(customerId)` —— id DESC
- RM-TRD-061 `insertIgnore(customerId, productId) -> affected` —— uk_wishlist_customer_product（affected=0 → 幂等 200）
- RM-TRD-062 `findByCustomerAndProduct(customerId, productId) -> WishlistItem?`
- RM-TRD-063 `deleteByCustomerAndProduct(customerId, productId) -> affected`（404604）

### BrowseHistoryRepository
- RM-TRD-070 `upsertViewedAt(customerId, productId, now)` —— `INSERT ... ON DUPLICATE KEY UPDATE viewed_at`（uk_browse_customer_product）
- RM-TRD-071 `listRecent(customerId, limit)` —— idx_browse_customer_viewed（viewed_at DESC）
- RM-TRD-072 `trimToLatest(customerId, keep=50)` —— 删除排名 50 之后最旧行

### ExchangeRateRepository
- RM-TRD-080 `listAll() -> List<ExchangeRate>`（五行）/ RM-TRD-081 `findByCurrency(currency) -> ExchangeRate?`
- RM-TRD-082 `updateRate(currency, rate, updatedBy)` —— uk_rate_currency

### CheckoutConfigRepository（单例）
- RM-TRD-090 `getSingleton() -> CheckoutConfig`（id=1，种子行）/ RM-TRD-091 `update(CheckoutConfig)`

### ProcessedEventRepository
- RM-TRD-100 `insertIgnore(eventId, eventType) -> affected` —— uk_event_id（affected=0 = 已消费，webhook 幂等闸；与业务变更同事务）
- RM-TRD-101 `deleteBefore(cutoff) -> affected` —— idx_event_received（SCHED-TRD-002，90 天清理，keyset 分批 CP-017）

### 跨域端口内 SQL（由对方域领域服务暴露，事务传播 REQUIRED 加入本域事务）
- RM-TRD-110 catalog `SkuStockService.deduct(skuId, qty, version) -> affected` —— `UPDATE sku SET stock=stock-:qty, version=version+1 WHERE id=? AND version=? AND stock>=:qty`（CAS，CP-016：`setSql` + `eq(version)`）
- RM-TRD-111 catalog `SkuStockService.restock(skuId, qty)` —— `UPDATE sku SET stock=stock+:qty, version=version+1 WHERE id=?`（回补无条件累加）
- RM-TRD-112 marketing `CouponService.redeem(couponId) -> affected` —— `UPDATE coupon SET used_count=used_count+1 WHERE id=? AND used_count<total_limit`
- RM-TRD-113 marketing `CouponService.rollbackRedeem(couponId)` —— `used_count=GREATEST(used_count-1,0)`

---

## 2. DTO ↔ Entity 映射（MAP-TRD）

- MAP-TRD-001 CartItem→CartItemDTO：附 ProductBrief（catalog 端口按 locale 解析文案）+ sku 展示块（含实时 stock 供前端超量提示）；custom_size_data JSON 原样透出；隐藏 custom_size_hash
- MAP-TRD-002 Address→AddressDTO：全字段透出（id + AddressUpsert 字段）；订单内地址走 address_snapshot JSON 直出（不回查 address 表）
- MAP-TRD-003 Order→StoreOrderListItem：OrderBase 字段 + line_count/first_line_img（RM-TRD-032 派生）；金额字段一律订单币种 DECIMAL→number
- MAP-TRD-004 Order→StoreOrderDetail：+ lines[]（行级 refundable 派生，决策 24）+ address_snapshot + PaymentSummary + refund_eligible/refund_block_reason_code 派生 + refunds[]（StoreRefund 视图：隐藏 stripe_refund_id/return_tracking_no/customer_*）
- MAP-TRD-005 Order→AdminOrderListItem / AdminOrderDetail：+ customer_id/customer_name/customer_email（identity 批量联取）+ customer_phone + refunds[]（AdminRefund 视图）；gift_wrap/gift_wrap_fee 透出（决策 28 发货执行）
- MAP-TRD-006 Payment→PaymentSummary：暴露 provider/payment_intent_id/amount/currency/status/card_summary/paid_at；**client_secret 永不落库不入 DTO**（即取即用，脱敏规则）
- MAP-TRD-007 Refund→StoreRefund：id/refund_no/order_id/amount/currency/reason/status/applied_at
- MAP-TRD-008 Refund→AdminRefund：+ order_no（联表派生）/customer_id/customer_name/customer_email/stripe_refund_id/return_tracking_no；拒绝理由存 `reject_reason` 独立列（reason 保留申请原文，回执邮件取 reject_reason）
- MAP-TRD-009 WishlistItem→WishlistItemDTO / BrowseHistory→BrowseHistoryItemDTO：附 ProductBrief；viewed_at ISO8601 UTC
- MAP-TRD-010 ExchangeRate→DTO：store 视图隐藏 updated_by；admin 视图全字段
- MAP-TRD-011 金额 DECIMAL(12,2) ↔ BigDecimal ↔ JSON number；时间 DATETIME(3) UTC ↔ LocalDateTime ↔ ISO8601（CP-014）
- MAP-TRD-012 枚举 ↔ 字符串：order.status/payment.status/refund.status/currency/carrier 取值与契约 enum 严格一致（CP-003 双保险）

---

## 3. 索引设计（IDX-TRD）

| 编号 | 表 | 索引 | 类型 | 服务路径 |
|---|---|---|---|---|
| IDX-TRD-001 | orders | `uk_order_idem (idempotency_key)` | **UNIQUE（强制）** | 下单防重提交（409603，BE-DIM-4） |
| IDX-TRD-002 | orders | `uk_order_no (order_no)` | UNIQUE | 订单号全局唯一 + 后台搜索点查 |
| IDX-TRD-003 | orders | `idx_order_customer_created (customer_id, created_at)` | 普通 | 我的订单分页（RM-TRD-024） |
| IDX-TRD-004 | orders | `idx_order_status_expires (status, expires_at)` | 普通 | 超时取消扫描（SCHED-TRD-001） |
| IDX-TRD-005 | orders | `idx_order_status_created (status, created_at)` | 普通 | 后台状态 tab 分页 |
| IDX-TRD-006 | processed_event | `uk_event_id (event_id)` | **UNIQUE（强制）** | webhook 幂等闸（RM-TRD-100） |
| IDX-TRD-007 | processed_event | `idx_event_received (received_at)` | 普通 | 90 天清理扫描 |
| IDX-TRD-008 | payment | `uk_payment_intent (payment_intent_id)` | UNIQUE | webhook 定位热路径（RM-TRD-042） |
| IDX-TRD-009 | payment | `idx_payment_order (order_id)` | 普通 | 订单详情联取 |
| IDX-TRD-010 | refund | `uk_refund_no (refund_no)` | UNIQUE | 工单号唯一 + 搜索 |
| IDX-TRD-011 | refund | `idx_refund_order_status (order_id, status)` | 普通 | 进行中工单判定（409605） |
| IDX-TRD-012 | refund | `idx_refund_status_applied (status, applied_at)` | 普通 | 后台工单 tab 分页 |
| IDX-TRD-013 | order_line | `idx_line_order (order_id)` | 普通 | 行联取/聚合 |
| IDX-TRD-014 | cart_item | `idx_cart_customer (customer_id)` | 普通 | 读车/清车 |
| IDX-TRD-015 | cart_item | `idx_cart_customer_sku (customer_id, sku_id)` | 普通 | 同 SKU 合并判定 |
| IDX-TRD-016 | cart_merge_record | `uk_merge_customer_token (customer_id, anon_token)` | UNIQUE | 合并幂等（决策 8） |
| IDX-TRD-017 | address | `idx_addr_customer (customer_id, is_default)` | 普通 | 地址簿 + 默认切换 |
| IDX-TRD-018 | wishlist_item | `uk_wishlist_customer_product (customer_id, product_id)` | UNIQUE | 收藏幂等（决策 18） |
| IDX-TRD-019 | browse_history | `uk_browse_customer_product (customer_id, product_id)` | UNIQUE | upsert 幂等（决策 23） |
| IDX-TRD-020 | browse_history | `idx_browse_customer_viewed (customer_id, viewed_at)` | 普通 | 倒序拉取 + 滚动清理 |
| IDX-TRD-021 | exchange_rate | `uk_rate_currency (currency)` | UNIQUE | 五币种行唯一 |

查询优化补充：列表客户邮箱搜索先经 identity `findUserIdsByEmailLike`（identity 自有索引）得 customer_ids 再回本域 IN 查询，避免跨域 join；orders 大表分页保持 created_at 复合索引覆盖，深翻页按 keyset（created_at,id 游标）演进（CP-017）。

---

## 4. 事务边界（TX-TRD，本域核心）

> 全部事务默认隔离级别 **READ_COMMITTED**（MySQL InnoDB；行级竞争一律用「条件更新 CAS / 唯一索引」承载，不依赖间隙锁），传播 REQUIRED；跨域端口 SQL（库存/券）以同事务传播加入。每条列明回滚点。

- **TX-TRD-001 下单原子事务（FLOW-P06，createOrder.STEP-TRD-05）** ｜ READ_COMMITTED
  序列：① INSERT orders（uk_order_idem/uk_order_no 冲突即回滚）→ ② batchInsert order_line → ③ 现货行逐行 SKU CAS 扣减（RM-TRD-110，失败重读重试 ×3）→ ④ 券核销 RM-TRD-112 → ⑤ 清车 RM-TRD-007 → COMMIT。
  回滚点：①唯一冲突→409603（重查既有单返回 details.order_id）；③CAS×3 仍失败→**全量回滚**→409601；④affected=0→全量回滚→422703；任何异常→全量回滚（订单/行/库存/券/购物车原子还原）。
  边界外：Stripe createPaymentIntent 在**事务提交后**调用（外部调用不入本地事务，失败订单保持 pending —— error-strategy「事务一致性约束」）。
- **TX-TRD-002 webhook 支付成功（FLOW-P07，stripeWebhook.STEP-TRD-03）** ｜ READ_COMMITTED
  序列：① insertIgnore processed_event（affected=0 → 提前返回，不开业务变更）→ ② 金额币种核对（不符→事务内仅保留 processed_event？否：**核对不符整体回滚含 processed_event**，告警人工介入后允许 Stripe 重投复核）→ ③ `casUpdateStatus(order, pending→paid)` + `casUpdateStatus(payment, created/processing→succeeded)` → COMMIT → 事务外 MQ publish order.paid。
  回滚点：业务异常全量回滚（processed_event 同滚，保证 Stripe 重投可重入）；guard 不命中（已 paid/已 cancelled）不算异常——已 paid 幂等空提交、cancelled 走 TX-TRD-010。
- **TX-TRD-003 退款审核通过（FLOW-P10，approveAdminRefund.STEP-TRD-03）** ｜ READ_COMMITTED ｜ **Stripe 在事务内**
  序列：① `casApprove(refund, pending→approved)`（affected=0→回滚 409604）→ ② StripePort.createRefund（**失败/超时→全量回滚**→502601/504601，工单保持 pending 可重审）→ ③ 记 stripe_refund_id → ④ `casUpdateStatus(orders, refunding→refunded)` → ⑤ 现货行回补 RM-TRD-111 → ⑥ payment→refunded → ⑦ operation_log → COMMIT → MQ refund.resolved。
  说明：Stripe Refund 创建为幂等性较弱的外部写，置于事务内是「钱动账必须动」的决策（error-strategy 事务一致性约束第二式）；超时但 Stripe 实际成功的残留窗口由 charge.refunded webhook 对账告警闭环。
- **TX-TRD-004 发货/完成/后台取消（FLOW-P09）** ｜ READ_COMMITTED
  TX-TRD-004a 发货：casUpdateStatus(paid→shipped, set carrier/tracking_no/shipped_at) + operation_log → COMMIT → MQ order.shipped。
  TX-TRD-004b 完成：casUpdateStatus(shipped→completed, set completed_at) + operation_log。后台取消：guard pending，事务体同 TX-TRD-005 + operation_log。
  回滚点：guard affected=0 → 无变更直接 409602（无需回滚动作）。
- **TX-TRD-005 消费端/超时取消 + 回补（FLOW-P08，cancelStoreOrder / SCHED-TRD-001 单单事务）** ｜ READ_COMMITTED
  序列：① `casUpdateStatus(orders, pending→cancelled)`（affected=0 → 与 webhook 竞态，放弃本单）→ ② 现货行回补 RM-TRD-111 → ③ 券回滚 RM-TRD-113 → COMMIT → 事务外 cancelPaymentIntent（失败仅告警，迟到支付由 TX-TRD-010 兜底）+ MQ order.cancelled。
  回滚点：②③异常→全量回滚（status 还原 pending，下轮扫描重试）。
- **TX-TRD-006 收藏移入购物车（moveWishlistToCart）** ｜ READ_COMMITTED：UPSERT cart_item + DELETE wishlist_item 同事务；任一失败全回滚。
- **TX-TRD-007 匿名车合并（mergeCart）** ｜ READ_COMMITTED：① insertIgnore cart_merge_record（affected=0 → 提前返回现车）→ ② 批量 UPSERT cart_item（截断记录）→ COMMIT；异常全回滚（含幂等记录，可重试）。
- **TX-TRD-008 默认地址切换** ｜ READ_COMMITTED：clearDefault + insert/update 同事务，保证「恒至多一个 is_default」不变量。
- **TX-TRD-009 退款工单创建/拒绝** ｜ READ_COMMITTED
  TX-TRD-009a 消费端申请 / TX-TRD-009b 后台代客发起：INSERT refund + casUpdateStatus(orders, paid|shipped→refunding)；guard affected=0 → 回滚 409602。
  TX-TRD-009c 拒绝：casReject(pending→rejected) + casUpdateStatus(orders, refunding→paid|shipped 按 shipped_at 还原)；affected=0 → 409604。
- **TX-TRD-010 迟到支付补偿（FLOW-P08 注记 / webhook 安全第 4 条）** ｜ 无本地状态变更事务
  cancelled 订单收到 succeeded：processed_event 已落（TX-TRD-002 ①）；订单**不复活**；事务提交后 StripePort.createRefund(全额) + 告警日志（人工核对）；Refund 工单不自动生成（资金回退非业务退款）。
- **TX-TRD-011 汇率维护** ｜ READ_COMMITTED：updateRate + operation_log 同事务；提交后缓存失效链（EC：缓存失效失败不回滚 DB，TTL 600s 兜底收敛，CP-031/EC-002 同 identity）。
- **TX-TRD-012 结算配置维护** ｜ READ_COMMITTED：update checkout_config + operation_log。

并发控制汇总：SKU 库存 = version 乐观锁 CAS×3（决策 6）；订单/支付/退款状态机 = 条件更新 CAS（affected=0 即 guard 失败）；幂等 = 唯一索引（idempotency_key / event_id / anon_token / customer+product）；订单号 = Redis INCR + uk_order_no 兜底重试。

---

## 5. 数据校验（CV-TRD）

- CV-TRD-001 枚举落库前校验：order.status ∈ 7 态 / payment.status ∈ 5 态 / refund.status ∈ 3 态 / currency ∈ 5 币 / carrier ∈ 3 承运商（与契约/CHECK 双保险）
- CV-TRD-002 金额非负：subtotal/shipping_fee/gift_wrap_fee/discount_amount/total_amount/unit_price/amount ≥ 0，DECIMAL(12,2)
- CV-TRD-003 金额恒等式（js_guard）：`total_amount = subtotal + shipping_fee + gift_wrap_fee - discount_amount`（写入前断言，违反抛 50000 级内部错误——服务端自算不可能违反）
- CV-TRD-004 refund.amount ≤ orders.total_amount（含 gift_wrap_fee，决策 28；422603）
- CV-TRD-005 order_no 模式 `^DRM-\d{8}-\d{4}$`；refund_no 模式 `^RFD-\d{8}-\d{4}$`；idempotency_key ≤64；tracking_no/return_tracking_no ≤64
- CV-TRD-006 custom_size_data JSON Schema：bust/waist/hips/hollow_to_floor 必填 ≥0，height 选填 ≥0（422601/422604 入口校验 + 落库前复核）
- CV-TRD-007 cart_item 双模式不变量：`sku_id IS NOT NULL XOR custom_size_data IS NOT NULL`（应用层强制；现货行必有 sku_id，定制行必有定制数据）
- CV-TRD-008 address 必填列非空白：receiver/line/city/zip/country；长度上限同契约
- CV-TRD-009 exchange_rate.rate > 0；USD 行 rate 恒 1（更新入口禁改，种子断言）
- CV-TRD-010 checkout_config：gift_wrap_fee_usd ≥0；custom_refund_grace_hours ∈ [1,168]
- CV-TRD-011 引用完整性（逻辑外键，CP-010）：cart_item/wishlist_item/browse_history 写前经 CatalogSnapshotPort 校验 product/sku 存在；order_line 仅存快照不校验后续存在性；refund.order_id 写前校验订单存在且归属
- CV-TRD-012 expires_at = created_at + 30min（下单时落定，BE-DIM-4）；wedding_date ≥ 下单日（选填）

---

## 6. 领域事件（EVT-TRD，RabbitMQ：topic exchange `dreamy.events`）

| 编号 | routing key | 触发点 | payload | 消费者（队列） |
|---|---|---|---|---|
| EVT-TRD-001 | `order.paid` | TX-TRD-002 提交后 | `{event_id, order_no, order_id, customer_id, locale, currency, total_amount, lines:[{product_id, sku_id?, qty}]}` | q.mail（order_confirmed 邮件）/ q.showroom（成员 ordered 推进 + dye lot 窗口）/ q.catalog.sales（best_sellers 销量回写 + 失效 reco 缓存，决策 29） |
| EVT-TRD-002 | `order.shipped` | TX-TRD-004a 提交后 | `{event_id, order_no, customer_id, locale, carrier, tracking_no}` | q.mail（shipped 邮件） |
| EVT-TRD-003 | `order.cancelled` | TX-TRD-005/004b 提交后 | `{event_id, order_no, customer_id, locale, cancel_reason: timeout\|customer\|admin}` | q.mail（可选通知，MailRecord 类型沿用决策 16 范围外不发——仅审计消费）；q.catalog.sales（销量口径无需回写，忽略） |
| EVT-TRD-004 | `refund.resolved` | TX-TRD-003/009c 提交后 | `{event_id, refund_no, order_no, customer_id, locale, result: approved\|rejected, amount, currency, reject_reason?}` | q.mail（refund_result 邮件） |
| EVT-TRD-005 | `content.invalidated`（type=exchange_rates_updated） | TX-TRD-011 提交后 | `{event_id, type, purge_paths:["/api/store/exchange-rates"]}` | q.invalidate（Cloudflare purge；JetCache 已由 @CacheInvalidate 同步失效） |

**可靠性参数（error-strategy L2 设计要求 3 落地）**：
- 生产侧：事务提交后发布；publish 失败**不回滚本地事务**，记告警日志（邮件/回写类人工补偿；缓存类靠 TTL 兜底）。
- 消费侧：手动 ack；异常 nack → 重试队列 `q.<name>.retry`（TTL 指数退避 5s/30s/180s，x-dead-letter 回主队列）×3 → 死信 `dreamy.dlq`（DLX：`dreamy.dlx`，告警 + 人工重放）。
- 消费幂等：按 `event_id` 去重（消费者各自 processed 记录或 Redis SETNX `mq:consumed:{queue}:{event_id}` TTL 7d）；回写类操作天然可重入（UPSERT/覆盖写）。
- 顺序性：同一 order_no 事件经 routing key 单队列串行即可，不要求全局有序。

---

## 7. 定时任务（SCHED-TRD）

- **SCHED-TRD-001 待支付订单超时取消（FLOW-P08，BE-DIM-4）**
  - 触发：`@Scheduled(cron = "0 * * * * *")` 每分钟；分布式锁 `onIdLock("trading:order-timeout")`（huihao-redis，多实例单飞）
  - 逻辑：RM-TRD-027 `listExpiredPending(now, limit=200)` 分页批量 → 逐单执行 TX-TRD-005（**单单独立事务**，一单失败不影响其余）→ 事务外 cancelPaymentIntent（失败告警）→ MQ order.cancelled
  - 验收锚点：TASK-052 / boundary「pending 超时 guard」
- **SCHED-TRD-002 processed_event 90 天清理（error-strategy webhook 安全第 2 条保留策略）**
  - 触发：`@Scheduled(cron = "0 30 4 * * *")` 每日 04:30；分布式锁 `onIdLock("trading:processed-event-cleanup")`
  - 逻辑：RM-TRD-101 `deleteBefore(now - 90d)`，keyset 分批（每批 1000，CP-017）；删除量入日志
- **SCHED-TRD-003 cart_merge_record 滚动清理（设计派生）**
  - 触发：每日 04:40；删除 created_at < now-30d 记录（匿名 token 合并窗口远小于 30 天）

---

## 8. 缓存设计（CACHE-TRD，BE-DIM-8）

| 编号 | key | 策略 | TTL | 失效触发者 |
|---|---|---|---|---|
| CACHE-TRD-001 | `trading:exchange-rates` | JetCache 两级（Caffeine+Redis）+ CDN s-maxage=600 | 600s | updateAdminExchangeRate `@CacheInvalidate` → EVT-TRD-005 → Cloudflare purge（FLOW-P18） |
| CACHE-TRD-002 | `trading:orderno:{yyyyMMdd}` | Redis 原生 INCR（非缓存，序号发生器） | 48h | 自然过期 |
| CACHE-TRD-003 | 购物车/订单/地址/收藏/浏览历史/结算/支付 | **不缓存**（决策 4 第 3 层） | — | — |

穿透保护：exchange_rate 为固定 5 行种子数据无穿透面；JetCache 全局 `cacheNullValue=true`（null 60s）随基建开启。shipping `shipping:rates`/`shipping:carriers`（TTL 600s）由 shipping 域自管，本域报价仅经端口读取。

---

## 9. 完整 DDL（语义基线，MySQL 8.0+ / InnoDB / utf8mb4_0900_ai_ci）

```sql
-- =============================================================================
-- trading 限界上下文 DDL ｜ change: portal-api-integration ｜ 12 表
-- 约定：Long 自增主键（huihao-mysql LongAuditableEntity）；DATETIME(3) UTC；
--       无物理 FOREIGN KEY（逻辑外键+事务，CP-010）；枚举 VARCHAR+CHECK（L3 可按
--       huihao 落地映射 tinyint，取值语义不变）；金额 DECIMAL(12,2) 订单币种。
-- =============================================================================
SET NAMES utf8mb4;

-- 1. cart_item 购物车条目（决策 8）
CREATE TABLE `cart_item` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `customer_id`      BIGINT       NOT NULL COMMENT '逻辑外键→user.id（BE-DIM-6 隔离）',
  `product_id`       BIGINT       NOT NULL COMMENT '逻辑外键→product.id',
  `sku_id`           BIGINT       NULL     COMMENT '现货必填；定制款 NULL（决策 6）',
  `qty`              INT          NOT NULL COMMENT '数量 >=1',
  `custom_size_data` JSON         NULL     COMMENT '定制尺寸 {bust,waist,hips,hollow_to_floor,height?}',
  `custom_size_hash` CHAR(64)     GENERATED ALWAYS AS (SHA2(JSON_UNQUOTE(`custom_size_data`),256)) STORED COMMENT '定制数据合并判定生成列',
  `created_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_cart_customer` (`customer_id`),
  KEY `idx_cart_customer_sku` (`customer_id`,`sku_id`),
  CONSTRAINT `ck_cart_qty` CHECK (`qty` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='购物车条目';

-- 2. cart_merge_record 匿名车合并幂等（决策 8）
CREATE TABLE `cart_merge_record` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `customer_id` BIGINT      NOT NULL,
  `anon_token`  VARCHAR(64) NOT NULL COMMENT '前端匿名购物车标识（合并幂等键）',
  `created_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merge_customer_token` (`customer_id`,`anon_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='匿名购物车合并幂等记录';

-- 3. address 地址簿
CREATE TABLE `address` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `customer_id` BIGINT       NOT NULL,
  `receiver`    VARCHAR(64)  NOT NULL COMMENT '收件人',
  `phone`       VARCHAR(32)  NULL,
  `line`        VARCHAR(255) NOT NULL COMMENT '街道地址',
  `city`        VARCHAR(64)  NOT NULL,
  `state`       VARCHAR(64)  NULL,
  `zip`         VARCHAR(16)  NOT NULL,
  `country`     VARCHAR(64)  NOT NULL COMMENT '运费分区映射输入',
  `is_default`  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '恒至多一个默认（TX-TRD-008）',
  `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_addr_customer` (`customer_id`,`is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收货地址簿（订单存快照）';

-- 4. orders 订单主单（表名规避保留字 ORDER）
CREATE TABLE `orders` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT,
  `order_no`         VARCHAR(20)   NOT NULL COMMENT 'DRM-YYYYMMDD-NNNN（预生成）',
  `customer_id`      BIGINT        NOT NULL,
  `status`           VARCHAR(16)   NOT NULL DEFAULT 'pending' COMMENT 'order_lifecycle 七态',
  `currency`         CHAR(3)       NOT NULL COMMENT 'USD/EUR/CAD/AUD/GBP（决策 14）',
  `exchange_rate`    DECIMAL(12,6) NOT NULL COMMENT '下单锁定 USD→订单币种汇率（决策 14）',
  `wedding_date`     DATE          NULL     COMMENT '婚期（交期复核，决策 20.6）',
  `subtotal`         DECIMAL(12,2) NOT NULL COMMENT '订单币种行小计求和',
  `shipping_fee`     DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '所选承运商报价快照（F-036）',
  `gift_wrap`        TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '礼品包装（决策 28）',
  `gift_wrap_fee`    DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '礼品包装费快照（决策 28）',
  `discount_amount`  DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '券减免（订单币种）',
  `total_amount`     DECIMAL(12,2) NOT NULL COMMENT '= subtotal+shipping_fee+gift_wrap_fee-discount_amount',
  `coupon_id`        BIGINT        NULL     COMMENT '逻辑外键→coupon.id（marketing）',
  `payment_method`   VARCHAR(32)   NULL     COMMENT 'Stripe/Apple Pay/Google Pay/Klarna/Afterpay（决策 25）',
  `address_snapshot` JSON          NOT NULL COMMENT '下单地址快照（删地址不波及）',
  `carrier`          VARCHAR(64)   NULL     COMMENT '承运商快照枚举三值（F-036）',
  `tracking_no`      VARCHAR(64)   NULL     COMMENT '物流单号（手填，BE-DIM-5）',
  `idempotency_key`  VARCHAR(64)   NOT NULL COMMENT '客户端 UUID 防重（BE-DIM-4）',
  `expires_at`       DATETIME(3)   NOT NULL COMMENT 'created_at+30min 超时取消（BE-DIM-4）',
  `paid_at`          DATETIME(3)   NULL,
  `shipped_at`       DATETIME(3)   NULL,
  `completed_at`     DATETIME(3)   NULL,
  `created_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_idem` (`idempotency_key`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_order_customer_created` (`customer_id`,`created_at`),
  KEY `idx_order_status_expires` (`status`,`expires_at`),
  KEY `idx_order_status_created` (`status`,`created_at`),
  CONSTRAINT `ck_order_status`   CHECK (`status` IN ('pending','paid','shipped','completed','cancelled','refunding','refunded')),
  CONSTRAINT `ck_order_currency` CHECK (`currency` IN ('USD','EUR','CAD','AUD','GBP')),
  CONSTRAINT `ck_order_carrier`  CHECK (`carrier` IS NULL OR `carrier` IN ('FedEx International Priority','UPS Worldwide Express','DHL Express')),
  CONSTRAINT `ck_order_amounts`  CHECK (`subtotal`>=0 AND `shipping_fee`>=0 AND `gift_wrap_fee`>=0 AND `discount_amount`>=0 AND `total_amount`>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单主单';

-- 5. order_line 订单行（快照）
CREATE TABLE `order_line` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT,
  `order_id`         BIGINT        NOT NULL,
  `product_id`       BIGINT        NOT NULL,
  `sku_id`           BIGINT        NULL COMMENT '定制款 NULL',
  `product_name`     VARCHAR(128)  NOT NULL COMMENT '快照',
  `sku_code`         VARCHAR(64)   NULL,
  `color`            VARCHAR(32)   NULL,
  `size`             VARCHAR(16)   NULL,
  `qty`              INT           NOT NULL,
  `unit_price`       DECIMAL(12,2) NOT NULL COMMENT '订单币种单价快照',
  `img`              VARCHAR(512)  NULL COMMENT '快照图',
  `custom_size_data` JSON          NULL COMMENT '定制行判定依据（决策 24）',
  `created_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_line_order` (`order_id`),
  CONSTRAINT `ck_line_qty` CHECK (`qty` >= 1),
  CONSTRAINT `ck_line_price` CHECK (`unit_price` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单行快照';

-- 6. payment 支付单（Stripe）
CREATE TABLE `payment` (
  `id`                BIGINT        NOT NULL AUTO_INCREMENT,
  `order_id`          BIGINT        NOT NULL,
  `provider`          VARCHAR(16)   NOT NULL DEFAULT 'stripe',
  `payment_intent_id` VARCHAR(64)   NULL COMMENT '可落库可入日志（非敏感引用）；client_secret 永不落库',
  `amount`            DECIMAL(12,2) NOT NULL COMMENT '订单币种',
  `currency`          CHAR(3)       NOT NULL,
  `status`            VARCHAR(16)   NOT NULL DEFAULT 'created' COMMENT 'payment_lifecycle 五态',
  `card_summary`      VARCHAR(64)   NULL COMMENT '如 Stripe · Visa ···4242',
  `paid_at`           DATETIME(3)   NULL,
  `created_at`        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_intent` (`payment_intent_id`),
  KEY `idx_payment_order` (`order_id`),
  CONSTRAINT `ck_payment_provider` CHECK (`provider` IN ('stripe')),
  CONSTRAINT `ck_payment_status`   CHECK (`status` IN ('created','processing','succeeded','failed','refunded')),
  CONSTRAINT `ck_payment_currency` CHECK (`currency` IN ('USD','EUR','CAD','AUD','GBP'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付单';

-- 7. refund 退款工单（决策 24/31）
CREATE TABLE `refund` (
  `id`                 BIGINT        NOT NULL AUTO_INCREMENT,
  `refund_no`          VARCHAR(20)   NOT NULL COMMENT 'RFD-YYYYMMDD-NNNN',
  `order_id`           BIGINT        NOT NULL,
  `customer_id`        BIGINT        NOT NULL,
  `amount`             DECIMAL(12,2) NOT NULL COMMENT '<= orders.total_amount 含礼品包装费（决策 28）',
  `currency`           CHAR(3)       NOT NULL COMMENT '原币种原金额退款（决策 14）',
  `reason`             VARCHAR(255)  NULL COMMENT '申请原因',
  `reject_reason`      VARCHAR(255)  NULL COMMENT '拒绝原因（回执邮件与消费端）',
  `status`             VARCHAR(16)   NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected',
  `stripe_refund_id`   VARCHAR(64)   NULL COMMENT '审核通过后写入',
  `return_tracking_no` VARCHAR(64)   NULL COMMENT '退货物流单号登记（决策 31，无 RMA 节点）',
  `applied_at`         DATETIME(3)   NOT NULL,
  `created_at`         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_no` (`refund_no`),
  KEY `idx_refund_order_status` (`order_id`,`status`),
  KEY `idx_refund_status_applied` (`status`,`applied_at`),
  CONSTRAINT `ck_refund_status`   CHECK (`status` IN ('pending','approved','rejected')),
  CONSTRAINT `ck_refund_currency` CHECK (`currency` IN ('USD','EUR','CAD','AUD','GBP')),
  CONSTRAINT `ck_refund_amount`   CHECK (`amount` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='退款工单';

-- 8. wishlist_item 收藏（决策 18）
CREATE TABLE `wishlist_item` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `customer_id` BIGINT      NOT NULL,
  `product_id`  BIGINT      NOT NULL,
  `created_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wishlist_customer_product` (`customer_id`,`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收藏清单';

-- 9. browse_history 浏览历史（决策 23）
CREATE TABLE `browse_history` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `customer_id` BIGINT      NOT NULL,
  `product_id`  BIGINT      NOT NULL,
  `viewed_at`   DATETIME(3) NOT NULL COMMENT 'upsert 更新；每用户保留最近 50 条',
  `created_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_browse_customer_product` (`customer_id`,`product_id`),
  KEY `idx_browse_customer_viewed` (`customer_id`,`viewed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Recently Viewed 浏览历史';

-- 10. exchange_rate 汇率（决策 14；种子五行，USD 恒 1）
CREATE TABLE `exchange_rate` (
  `id`         BIGINT        NOT NULL AUTO_INCREMENT,
  `currency`   CHAR(3)       NOT NULL,
  `rate`       DECIMAL(12,6) NOT NULL COMMENT '相对 USD；USD 恒 1',
  `updated_by` BIGINT        NULL COMMENT '逻辑外键→admin_user.id',
  `created_at` DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rate_currency` (`currency`),
  CONSTRAINT `ck_rate_currency` CHECK (`currency` IN ('USD','EUR','CAD','AUD','GBP')),
  CONSTRAINT `ck_rate_positive` CHECK (`rate` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='汇率表';

-- 11. checkout_config 结算配置单例（决策 24/28）
CREATE TABLE `checkout_config` (
  `id`                        INT           NOT NULL COMMENT '单例 =1',
  `gift_wrap_fee_usd`         DECIMAL(12,2) NOT NULL DEFAULT 15.00 COMMENT '礼品包装固定费 USD 基准（决策 28）',
  `custom_refund_grace_hours` INT           NOT NULL DEFAULT 24 COMMENT '定制款退款宽限期小时 1..168（决策 24）',
  `created_at`                DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`                DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  CONSTRAINT `ck_cfg_singleton` CHECK (`id` = 1),
  CONSTRAINT `ck_cfg_fee`       CHECK (`gift_wrap_fee_usd` >= 0),
  CONSTRAINT `ck_cfg_grace`     CHECK (`custom_refund_grace_hours` BETWEEN 1 AND 168)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结算配置单例';

-- 12. processed_event Stripe webhook 幂等存储（BE-DIM-4；90 天保留入 DDL 注释级策略）
CREATE TABLE `processed_event` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `event_id`    VARCHAR(64)  NOT NULL COMMENT 'Stripe Event id（evt_...）',
  `event_type`  VARCHAR(64)  NOT NULL COMMENT 'payment_intent.succeeded 等',
  `received_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_id` (`event_id`),
  KEY `idx_event_received` (`received_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='webhook 幂等消费记录；保留 90 天，SCHED-TRD-002 每日清理';

-- 种子数据
INSERT INTO `exchange_rate` (`currency`,`rate`) VALUES
  ('USD',1.000000),('EUR',0.920000),('CAD',1.360000),('AUD',1.520000),('GBP',0.790000);
INSERT INTO `checkout_config` (`id`,`gift_wrap_fee_usd`,`custom_refund_grace_hours`) VALUES (1,15.00,24);
```

> 种子说明：EUR/CAD/AUD/GBP 初值取原型前端硬编码汇率口径（决策 14 上线后由管理端维护接管）；gift_wrap_fee_usd=15.00 与原型 checkout 「Add gift wrapping (+$15)」一致（决策 28）；grace_hours=24 为决策 24 默认。dev/staging 订单类种子数据由决策 21 种子脚本灌入（仅非生产）。

---

## 10. 数据保留

- processed_event：90 天（SCHED-TRD-002，error-strategy 强制项）。
- cart_merge_record：30 天（SCHED-TRD-003）。
- browse_history：每用户 50 条滚动（RM-TRD-072，写时清理）。
- orders/order_line/payment/refund：交易数据永久保留（财务凭证）；customer 匿名化（identity FLOW-16）后订单按 customer_id 弱引用保留，PII 仅存在于 address_snapshot——identity 匿名化任务扩展点：置换本域 address_snapshot 为脱敏占位（receiver/phone 抹除），订单金额与状态不动。

## 11. 自检

- [x] 12 表全部有 DDL、Repository 方法、索引与校验；表/列名与 trading-api 契约字段 snake_case 一致
- [x] **uk_order_idem（idempotency_key）与 uk_event_id（processed_event）唯一索引均已含**（IDX-TRD-001/006）
- [x] TX-TRD-001~012 逐事务列隔离级别（READ_COMMITTED）与回滚点；Stripe 在事务内（退款）/事务外（下单）的取舍与 error-strategy 一致
- [x] EVT-TRD-001~005 覆盖订单支付/发货/取消/退款结果事件发布 + MQ 重试/死信/幂等参数
- [x] SCHED-TRD-001 订单超时取消（每分钟 + 分布式锁 + 单单事务）；SCHED-TRD-002 processed_event 90 天保留策略入 DDL
- [x] 汇率 JetCache 缓存设计（CACHE-TRD-001 两级 + CDN + 失效链）；个人交易数据不缓存
- [x] 约束 ID（RM/MAP/IDX/TX/CV/EVT/SCHED/CACHE-TRD）无重号
