# trading API 详细设计（L2）

> 角色: l2_api_designer ｜ change: portal-api-integration ｜ domain: trading
> 方法论：每端点四部分 — 入参验证(V-TRD-NNN) / 业务逻辑流程(STEP-TRD-NN) / 出参构造 / 错误码映射。
> 来源权威：api-contracts/trading-api.openapi.yml v1.1.0（37 操作）+ shipping-api.openapi.yml（运费规则）+ data-flow.md（FLOW-P04~P10/P13/P18 + MQ 拓扑）+ error-strategy.md（trading 域段 6 共 19 码 + webhook 安全五条）+ er-diagram.yml + state-machine.yml（order/payment/refund lifecycle）。
> 伪代码级，不绑定 Spring 语法。所有 JSON 字段 snake_case；契约错误 Schema `{code,message,details}` 在线上装入 R 包络（details → R.data）。
>
> **约束 ID 约定**：`V-TRD-NNN` 全域唯一连续编号（V-TRD-001 ~ V-TRD-061，无重号）；`STEP-TRD-NN` 在端点内编号，全局引用名为 `<operationId>.STEP-TRD-NN`（如 `createOrder.STEP-TRD-03`），与 identity 样板（端点内 STEP 复位）同风格。

## 0. 全局横切（所有端点适用）

- **R 包络**：成功 `R{code:0, message:"ok", data:<payload>}`；失败 `R{code:<6位码>, message, data:<details>}`。本文各端点「出参」均指 data 载荷。
- **分页**：`huihao.page.Paginated`（snake_case：data / total_elements / page_number / page_size / number_of_elements / total_pages），与 backend UserOpsController `R<Paginated<T>>` 一致。
- **鉴权**：
  - `/api/store/*`：StoreJwtFilter 解析 store JWT，`customer_id = JWT subject`；本域两个匿名端点（`GET /api/store/exchange-rates`、`POST /api/store/payments/stripe/webhook`）纳入 StoreJwtFilter **配置化公开路径白名单**（`store.jwt.public-paths`，与 catalog/marketing/showroom 公开端点共用同一配置项）；webhook 在白名单豁免 JWT 后由专用 `StripeSignatureFilter` 验签（失败 401601）。
  - `/api/admin/*`：AdminJwtFilter + RBAC 菜单权限 key——本域权限点：`/orders`（admin-orders 全部）、`/refunds`（admin-refunds 全部）、`/settings`（汇率维护 + 结算配置）。缺权限 → 403 `40300`。
- **user_id 强隔离（BE-DIM-6）**：消费端全部个人资源查询带 `customer_id=?` 条件；跨用户/不存在一律 404（404601/404602/404603/404604/404605，防资源探测，不区分语义）。
- **i18n**：store 端 message 按 locale（en/es/fr，缺省 en）返回；admin 端固定中文；前端按 code 映射文案（决策 27）。
- **审计（BE-DIM-7）**：admin 写操作 AOP 写 operation_log，action 枚举：订单发货 / 订单状态变更 / 发起退款 / 退款审核通过 / 退款审核拒绝 / 汇率变更 / 结算配置变更。
- **缓存**：本域仅 `GET /api/store/exchange-rates` 缓存（CDN s-maxage + JetCache 两级 key `trading:exchange-rates` TTL 600s）；购物车/订单/地址/收藏/浏览历史/结算/支付一律不缓存（决策 4 第 3 层）。
- **跨域端口（决策 3，进程内直调防腐层，禁止跨域直查表）**：
  - `CatalogSnapshotPort`：`getProductBrief(productId, locale)` / `getSku(skuId)` / `assertPurchasable(productId, skuId?)`（商品不存在或未发布 → 透传 404 `404501`）
  - `ShippingQuotePort`（shipping 域）：`quoteOptions(country, subtotalUsd) -> List<ShippingOptionQuote{carrier, fee_usd, lead_time}>`
  - `CouponPort`（marketing 域）：`validate(code, subtotalUsd) -> {valid, discount_usd, reason_code?}`；`redeem(code)`（事务内核销，失败抛 422701/422702/422703）；`rollback(couponId)`
  - `DyeLotPort`（showroom 域）：`hintProductIds(customerId, productIds) -> List<Long>`（决策 20.4）
  - `StripePort`（BE-DIM-5）：`createPaymentIntent` / `retrievePaymentIntent` / `cancelPaymentIntent` / `createRefund` / `verifyWebhookSignature`；超时 10s，失败 → 502 `502601`，超时 → 504 `504601`
- **金额约定（决策 14/15/28）**：定价 USD 基准；订单币种金额 = USD 金额 × ExchangeRate.rate（`multi_currency_prices[currency]` 覆盖价优先），HALF_UP 保留 2 位；Stripe 金额 = total_amount × 100 取整（五币种均为 2 位小数币）；`total_amount === subtotal + shipping_fee + gift_wrap_fee - discount_amount`。
- **多承运商（F-036）**：报价/下单 carrier 枚举固定三值 `FedEx International Priority` / `UPS Worldwide Express` / `DHL Express`（与 Order.carrier、shipping Carrier.name 一致）；前端文案以 API 返回为准。

---

## 1. STORE CART（FLOW-P04，决策 8）

### 1.1 getCart — GET /api/store/cart

**入参**: query `locale?`
- V-TRD-001 locale ∈ {en,es,fr}，缺省 en（非法值按 422 `422601` details.field=locale）

**业务逻辑**:
- STEP-TRD-01 `SELECT cart_item WHERE customer_id=:jwt.subject ORDER BY id DESC`
- STEP-TRD-02 批量 `CatalogSnapshotPort.getProductBrief(productIds, locale)` + `getSku(skuIds)` 组装展示数据（一次批量取，防 N+1）；商品已下架（status=draft）仍返回并以 `product.status=draft` 标记不可购买
- STEP-TRD-03 `DyeLotPort.hintProductIds(customerId, productIds)` → `dye_lot_product_ids`（决策 20.4；showroom 域空结果返回空数组）

**出参**: 200 `CartResponse{ items:[CartItem], dye_lot_product_ids:[] }`（不含 merged_truncated_item_ids）
**错误映射**: 401 `40100` / 500 `50000`

### 1.2 addCartItem — POST /api/store/cart/items

**入参**: `{ product_id, sku_id?, qty, custom_size_data? }`
- V-TRD-002 product_id 必填且经 CatalogSnapshotPort 校验存在且 published → 否则 404 `404501`（透传 catalog）
- V-TRD-003 qty 必填整数 ≥1 → 否则 422 `422601`（details: {field:"qty"}）
- V-TRD-004 双模式 guard（决策 6，js_guard）：现货商品（custom_size_available=false 或未携带定制数据意图）必填 sku_id 且 sku 属于该商品；定制款 sku_id 可空但 custom_size_data 必填 → 违反 422 `422604 SKU_REQUIRED`
- V-TRD-005 custom_size_data 携带时 bust/waist/hips/hollow_to_floor 必填且 ≥0（height 选填 ≥0）→ 否则 422 `422601`

**业务逻辑**:
- STEP-TRD-01 现货行：读 Sku.stock，`qty + 既有同 SKU 条目 qty > stock` → 409 `409601 STOCK_INSUFFICIENT`（仅校验提示，不预占库存）
- STEP-TRD-02 合并判定：同 customer_id 下「同 sku_id」或「同 product_id 且 custom_size_data JSON 规范化后相等」的条目存在 → `UPDATE qty = qty + :qty`；否则 `INSERT cart_item`
- STEP-TRD-03 复用 getCart.STEP-TRD-02/03 组装购物车

**出参**: 201 `CartResponse`
**错误映射**: 401 40100 / 404 404501 / 409 409601 / 422 422601·422604 / 500 50000

### 1.3 updateCartItem — PATCH /api/store/cart/items/{id}

**入参**: path id；body `{ qty }`
- V-TRD-006 id 为 int64 路径参数
- V-TRD-007 qty 必填整数 ≥1 → 422 `422601`

**业务逻辑**:
- STEP-TRD-01 `SELECT cart_item WHERE id=? AND customer_id=:subject` → 无 404 `404603 CART_ITEM_NOT_FOUND`（含跨用户）
- STEP-TRD-02 现货行 qty > Sku.stock → 409 `409601`
- STEP-TRD-03 `UPDATE cart_item SET qty=:qty`；返回整车（同 getCart）

**出参**: 200 `CartResponse`
**错误映射**: 401 / 404 404603 / 409 409601 / 422 422601 / 500

### 1.4 removeCartItem — DELETE /api/store/cart/items/{id}

- V-TRD-008 id 路径参数 int64
- STEP-TRD-01 `DELETE FROM cart_item WHERE id=? AND customer_id=:subject`；affected=0 → 404 `404603`

**出参**: 204
**错误映射**: 401 / 404 404603 / 500

### 1.5 mergeCart — POST /api/store/cart/merge（FLOW-P04，决策 8）

**入参**: `{ anon_token, items:[CartItemCreate] }`
- V-TRD-009 anon_token 必填 ≤64 → 422 `422601`
- V-TRD-010 items 数组每项按 V-TRD-002~005 校验（单项 404501/422604 不阻断整批：该项跳过并不计入结果——批量合并容错；全部非法时返回 422 `422601` details.invalid_items）

**业务逻辑（TX-TRD-007 单事务）**:
- STEP-TRD-01 幂等：`INSERT cart_merge_record(customer_id, anon_token)`（uk_merge_customer_token 唯一索引）；冲突 → 该批已合并过，直接返回现车（幂等空操作）
- STEP-TRD-02 逐项 UPSERT：同 SKU/同定制数据合并 qty（同 addCartItem.STEP-TRD-02）
- STEP-TRD-03 现货超库存按 `stock` 截断（`qty = min(merged_qty, stock)`），被截断条目 id 记入 `merged_truncated_item_ids`
- STEP-TRD-04 事务提交后组装 CartResponse（含 merged_truncated_item_ids）

**出参**: 200 `CartResponse{ items, dye_lot_product_ids, merged_truncated_item_ids }`
**错误映射**: 401 / 422 422601 / 500

---

## 2. STORE ADDRESSES

### 2.1 listAddresses — GET /api/store/addresses

- STEP-TRD-01 `SELECT address WHERE customer_id=:subject ORDER BY is_default DESC, id DESC`

**出参**: 200 `{ items:[Address] }`
**错误映射**: 401 / 500

### 2.2 createAddress — POST /api/store/addresses

**入参**: `AddressUpsert{ receiver, phone?, line, city, state?, zip, country, is_default? }`
- V-TRD-011 receiver(≤64)/line(≤255)/city(≤64)/zip(≤16)/country(≤64) 必填非空白；state ≤64 → 422 `422601`（details 字段级）
- V-TRD-012 phone ≤32 → 422 `422601`

**业务逻辑（TX-TRD-008）**:
- STEP-TRD-01 is_default=true → 同事务 `UPDATE address SET is_default=0 WHERE customer_id=:subject AND is_default=1`
- STEP-TRD-02 `INSERT address(customer_id=:subject, ...)`；该用户首条地址强制 is_default=true

**出参**: 201 `Address`
**错误映射**: 401 / 422 422601 / 500

### 2.3 updateAddress — PUT /api/store/addresses/{id}

- V-TRD-013 body 同 V-TRD-011/012；id 路径参数
- STEP-TRD-01 `SELECT address WHERE id=? AND customer_id=:subject` → 无 404 `404602 ADDRESS_NOT_FOUND`
- STEP-TRD-02 is_default=true → 同 createAddress.STEP-TRD-01 切默认（TX-TRD-008）
- STEP-TRD-03 全字段覆盖 UPDATE

**出参**: 200 `Address`
**错误映射**: 401 / 404 404602 / 422 422601 / 500

### 2.4 deleteAddress — DELETE /api/store/addresses/{id}

- V-TRD-014 id 路径参数
- STEP-TRD-01 `DELETE FROM address WHERE id=? AND customer_id=:subject`；affected=0 → 404 `404602`
- STEP-TRD-02 订单地址为 address_snapshot 快照，删除不影响既有订单（无引用校验）；删除的若是默认地址不自动指定新默认（下次新增/结算时由用户选择）

**出参**: 204
**错误映射**: 401 / 404 404602 / 500

---

## 3. STORE CHECKOUT（FLOW-P05/P06）

### 3.1 quoteCheckout — POST /api/store/checkout/quote（FLOW-P05，F-036/决策 14/15/28/20.4/20.6）

**入参**: `CheckoutQuoteRequest{ address_id?, country?, currency, carrier?, coupon_code?, gift_wrap?, wedding_date? }`
- V-TRD-015 currency 必填 ∈ {USD,EUR,CAD,AUD,GBP} → 否则 422 `422605 CURRENCY_NOT_SUPPORTED`
- V-TRD-016 address_id 与 country 至少其一；address_id 提供时校验归属当前用户 → 无 404 `404602`；二者均缺 → 422 `422601`（details.field=address_id）
- V-TRD-017 carrier 选填 ∈ 三承运商枚举 → 非法 422 `422601`
- V-TRD-018 coupon_code ≤32；V-TRD-019 wedding_date 为 ISO date 且 ≥ 今天 → 否则 422 `422601`
- V-TRD-020 当前用户购物车非空 → 空车 422 `422601`（details.reason="cart_empty"）

**业务逻辑（只读试算，不落库不缓存）**:
- STEP-TRD-01 读 cart_item + CatalogSnapshotPort 快照（含 lead_time_days / multi_currency_prices / 下架行剔除并在 details 提示）
- STEP-TRD-02 `subtotal_usd = Σ line.price_usd × qty`；按 V-TRD-015 币种换算行价与小计（覆盖价优先，HALF_UP 2 位）
- STEP-TRD-03 运费分区（shipping-api 规则）：`country = address.country || :country` → 地理分区映射（North America: US/CA/MX；Europe: GB/IE/FR/ES/DE/IT/…；Oceania: AU/NZ；其余 → Rest of World 兜底分区）→ `ShippingQuotePort.quoteOptions(country, subtotal_usd)`：对每个 status=enabled 的 Carrier 取 `"<分区> / <Carrier.name>"` 规则行计费（subtotal_usd < threshold 取 fee_under，否则 fee_over；fee_over=0 即满额包邮）；分区无带承运商后缀行时回退该分区**不含后缀兜底行**（价格对全部 enabled 承运商生效，时效仍取各 Carrier.lead_time）
- STEP-TRD-04 组装 `shipping_options[]`（fee 换算为订单币种）；`selected=true` 项 = 请求 carrier 对应项（carrier 未传或所传承运商已 disabled → 最低价项）；`shipping_fee = selected 项 fee`
- STEP-TRD-05 gift_wrap=true → `gift_wrap_fee = CheckoutConfig.gift_wrap_fee_usd × rate`（决策 28）；false → 0
- STEP-TRD-06 coupon_code 提交 → `CouponPort.validate(code, subtotal_usd)` → `coupon_valid` / `discount_amount`（换算订单币种）/ 无效时 `coupon_reason_code`（4227xx，**不阻断报价**，discount_amount=0）
- STEP-TRD-07 读 ExchangeRate(currency) 为试算汇率（**不锁定**；USD 恒 1）
- STEP-TRD-08 `total_amount = subtotal + shipping_fee + gift_wrap_fee - discount_amount`（js_guard 恒等式）
- STEP-TRD-09 交期复核（决策 20.6）：`max_lead_time_days = max(line.lead_time_days)`；wedding_date 提供且 `today + max_lead_time_days > wedding_date` → `lead_time_warning=true`
- STEP-TRD-10 `dye_lot_product_ids = DyeLotPort.hintProductIds(...)`（决策 20.4）

**出参**: 200 `CheckoutQuoteResponse{ currency, exchange_rate, subtotal, shipping_options[≥1], shipping_fee, gift_wrap_fee, discount_amount, total_amount, coupon_valid?, coupon_reason_code?, lead_time_warning?, max_lead_time_days?, dye_lot_product_ids? }`
**错误映射**: 401 / 404 404602 / 422 422601·422605 / 500

### 3.2 createOrder — POST /api/store/checkout/orders（FLOW-P06，FUNC-001 核心，BE-DIM-4）

**入参**: `OrderCreateRequest{ idempotency_key, address_id, currency, carrier, coupon_code?, gift_wrap?, wedding_date?, payment_method, locale? }`
- V-TRD-021 idempotency_key 必填 ≤64（客户端 UUID）→ 422 `422601`
- V-TRD-022 address_id 必填且归属当前用户 → 无 404 `404602`
- V-TRD-023 currency ∈ 五币种 → 422 `422605`
- V-TRD-024 carrier 必填 ∈ 三承运商枚举 → 422 `422601`
- V-TRD-025 payment_method ∈ {Stripe, Apple Pay, Google Pay, Klarna, Afterpay}（决策 25：PayPal 置灰不产生数据，传入 PayPal → 422 `422601`）
- V-TRD-026 购物车非空且逐行复核双模式 guard（现货 sku 有效 / 定制 custom_size_data 完整）→ 422 `422604`；行内商品已下架 → 422 `422601`（details.unavailable_product_ids）
- V-TRD-027 locale ∈ {en,es,fr} 缺省 en（交易邮件渲染语言，决策 16）

**业务逻辑**:
- STEP-TRD-01 幂等预检：`SELECT id FROM orders WHERE idempotency_key=?`（uk_order_idem 唯一索引）命中 → 409 `409603 DUPLICATE_SUBMISSION`，details `{order_id}`（前端静默跳既有订单支付页）
- STEP-TRD-02 锁汇：读 ExchangeRate(currency).rate 为 `exchange_rate` 快照（决策 14；后续汇率变更不影响本单）
- STEP-TRD-03 重算金额：复用 quoteCheckout.STEP-TRD-01~08 全部服务端重算（**不信任前端金额**）；shipping_fee 取请求 carrier 对应报价项；gift_wrap_fee 按 CheckoutConfig 快照
- STEP-TRD-04 订单号预生成：`order_no = "DRM-" + yyyyMMdd + "-" + 当日序号(4位)`（Redis `INCR trading:orderno:{yyyyMMdd}` TTL 48h；uk_order_no 唯一索引兜底，冲突重取 ×3）
- **STEP-TRD-05 原子事务开始（TX-TRD-001，READ_COMMITTED）**：
  1. `INSERT orders(order_no, customer_id, status=pending, currency, exchange_rate, wedding_date?, subtotal, shipping_fee, gift_wrap, gift_wrap_fee, discount_amount, total_amount, coupon_id?, payment_method, carrier, address_snapshot=地址 JSON 快照, expires_at=now+30min, idempotency_key)`；idempotency_key 唯一索引冲突（并发双击竞态）→ 回滚 → 409 `409603` + details.order_id（重查既有单）
  2. `INSERT order_line[]`（product_name/sku_code/color/size/unit_price(订单币种)/img 快照 + custom_size_data）
  3. 现货行逐行乐观锁扣减（决策 6，定制行不扣减）：`UPDATE sku SET stock=stock-:qty, version=version+1 WHERE id=:skuId AND version=:v AND stock>=:qty`；affected=0 → 重读 version 重试，CAS ×3 仍失败 → **整体回滚** → 409 `409601 STOCK_INSUFFICIENT`（details.sku_id）
  4. coupon_code 有效 → `CouponPort.redeem(code)`：`UPDATE coupon SET used_count=used_count+1 WHERE id=? AND used_count<total_limit`；affected=0 → 回滚 → 422 `422703 COUPON_EXHAUSTED`（无效/门槛不足 → 422701/422702 透传，均回滚）
  5. 清车：`DELETE FROM cart_item WHERE customer_id=:subject`
  6. 事务提交（回滚点：以上任一步失败整体回滚，订单/订单行/库存/券/购物车全部还原）
- STEP-TRD-06 事务外调 `StripePort.createPaymentIntent(amount=total_amount×100 取整, currency, automatic_payment_methods=enabled（Payment Element 承载，决策 25）, metadata={order_no, order_id})`：
  - 失败 → 502 `502601`；超时 → 504 `504601`（**订单保持 pending**，可经 retryOrderPayment 重试；30min 未付走 FLOW-P08 超时取消）
  - 成功 → `INSERT payment(order_id, provider=stripe, payment_intent_id, amount=total_amount, currency, status=created)`（client_secret 即取即用不落库，脱敏规则）
- STEP-TRD-07 组装 StoreOrderDetail + PaymentCredential

**出参**: 201 `OrderCreateResponse{ order:StoreOrderDetail, payment:{payment_intent_id, client_secret} }`
**错误映射**: 401 / 404 404602 / 409 409601·409603 / 422 422601·422604·422605·422701·422702·422703(透传 marketing) / 502 502601 / 504 504601 / 500

---

## 4. STORE PAYMENTS — Stripe webhook（FLOW-P07，决策 7/25）

### 4.1 stripeWebhook — POST /api/store/payments/stripe/webhook

**入参**: header `Stripe-Signature`（必填）；body 为 Stripe Event 原始负载（仅消费 id/type/data.object）
- V-TRD-028 Stripe-Signature 头缺失或 HMAC 验签失败（webhook secret 仅后端配置）→ 401 `401601 WEBHOOK_SIGNATURE_INVALID`：**不读取负载、不写任何业务数据、不落 processed_event**，记脱敏告警日志（Stripe 按其退避策略重投）——webhook 安全第 1 条
- V-TRD-029 验签通过后解析 JSON 失败 → 401 `401601`（同等拒绝，不入业务）

**业务逻辑**:
- STEP-TRD-01 幂等闸（webhook 安全第 2 条）：`INSERT processed_event(event_id, event_type, received_at)`（uk_event_id 唯一索引）；冲突 → 已消费，直接 200 `{received:true}` 幂等空操作
- STEP-TRD-02 按 event.type 分支（未识别类型 → 仅落 processed_event，200 受理不处理）：
- STEP-TRD-03 **payment_intent.succeeded**（TX-TRD-002 单事务）：
  1. 按 `payment_intent_id` 定位 payment + orders（无匹配 → 告警日志，200 受理）
  2. 金额/币种核对（webhook 安全第 3 条，决策 14 连带）：`event.data.object.amount === round(order.total_amount×100) && currency 一致`；不符 → **不变更订单**，告警人工介入，200 受理
  3. 状态 guard（webhook 安全第 4 条）：order.status=pending → `UPDATE orders SET status=paid, paid_at=now` + `UPDATE payment SET status=succeeded, paid_at, card_summary`（payment_lifecycle: processing→succeeded）；order.status=cancelled（迟到支付）→ **不复活订单**，事务提交后 `StripePort.createRefund(payment_intent, 全额)` 自动退款补偿 + 告警（TX-TRD-010）；其余状态（paid 重复事件）→ 幂等跳过
  4. 事务提交后 `MQ publish order.paid{event_id, order_no, customer_id, locale, lines[]}`（EVT-TRD-001；扇出：邮件 order_confirmed / showroom ordered 推进 + dye lot 窗口 / 销量回写失效 reco 缓存）
- STEP-TRD-04 **payment_intent.payment_failed**：`UPDATE payment SET status=failed`（订单保持 pending 可重试支付；BNPL Klarna/Afterpay 异步拒绝同路）
- STEP-TRD-05 **charge.refunded**：按 payment_intent 定位订单；与 FLOW-P10 审核路径幂等汇合——若 Refund 已 approved 且 order 已 refunded → 空操作；否则核对 stripe_refund_id 推进 `payment.status=refunded`（异常态告警）
- STEP-TRD-06 处理过程任何业务异常 → 500（Stripe 将重投；processed_event 已落则重投走幂等闸——**落 processed_event 与业务变更同一事务**，失败一并回滚保证可重投）

**出参**: 200 `{ received: true }`
**错误映射**: 401 401601 / 500 50000
**传输约束（webhook 安全第 5 条）**：仅 POST + JSON；WAF 放行 Stripe 源；JWT 白名单豁免（见 §0）。

---

## 5. STORE ORDERS

### 5.1 listStoreOrders — GET /api/store/orders

**入参**: query page/page_size/status
- V-TRD-030 page ≥1 缺省 1；page_size 1..100 缺省 20 → 越界 422 `422601`
- V-TRD-031 status ∈ {all,pending,paid,shipped,completed,cancelled,refunding,refunded} 缺省 all

**业务逻辑**:
- STEP-TRD-01 `SELECT orders WHERE customer_id=:subject [AND status=:status] ORDER BY created_at DESC LIMIT/OFFSET`（idx_order_customer_created）
- STEP-TRD-02 批量取每单 `line_count` 与 `first_line_img`（order_line 按 order_id IN 一次聚合，防 N+1）

**出参**: 200 `Paginated<StoreOrderListItem>`（data/total_elements/page_number/page_size/number_of_elements/total_pages）
**错误映射**: 401 / 422 422601 / 500

### 5.2 getStoreOrder — GET /api/store/orders/{id}

- V-TRD-032 id 路径参数
- STEP-TRD-01 `SELECT orders WHERE id=? AND customer_id=:subject` → 无 404 `404601 ORDER_NOT_FOUND`（跨用户同码防探测）
- STEP-TRD-02 联取 order_line[]（含 custom_size_data 透出）、payment 摘要、refunds[]
- STEP-TRD-03 行级退款资格派生（决策 24）：
  - 定制行（custom_size_data 非空）：`refundable = (order.paid_at == null) || (now <= paid_at + CheckoutConfig.custom_refund_grace_hours)`（默认 24h，可配置）
  - 现货行：`refundable = status ∈ {paid}（未发货全额退）|| status ∈ {shipped}（退货后退，审核制）`
- STEP-TRD-04 整单 `refund_eligible`：status ∈ {paid,shipped} 且无进行中工单且不含「已投产定制行」；含已投产定制行 → `refund_eligible=false, refund_block_reason_code=422602`（前端入口置灰 + 三语说明文案 key）

**出参**: 200 `StoreOrderDetail{ ...OrderBase, lines[](含 refundable), address_snapshot, payment?, refund_eligible, refund_block_reason_code?, refunds[] }`
**错误映射**: 401 / 404 404601 / 500

### 5.3 cancelStoreOrder — POST /api/store/orders/{id}/cancel

- V-TRD-033 id 路径参数
- STEP-TRD-01 定位订单（customer_id 隔离）→ 404 `404601`
- STEP-TRD-02 状态机 guard：status≠pending → 409 `409602 ORDER_STATE_INVALID`
- STEP-TRD-03 **TX-TRD-005 单事务**：`UPDATE orders SET status=cancelled WHERE id=? AND status='pending'`（条件更新防与 webhook 竞态，affected=0 → 重读返回 409602）+ 现货行回补 `UPDATE sku SET stock=stock+qty, version=version+1` + 已核销券 `CouponPort.rollback(coupon_id)`
- STEP-TRD-04 事务提交后 `StripePort.cancelPaymentIntent`（失败仅告警：webhook 幂等闸 + cancelled guard + 迟到支付自动退款兜底）+ `MQ publish order.cancelled`（EVT-TRD-003）

**出参**: 200 `StoreOrderDetail`（status=cancelled）
**错误映射**: 401 / 404 404601 / 409 409602 / 500

### 5.4 retryOrderPayment — POST /api/store/orders/{id}/payment-intent

- V-TRD-034 id 路径参数
- STEP-TRD-01 定位订单 → 404 `404601`
- STEP-TRD-02 status=cancelled 且因超时取消（expires_at < now）→ 410 `410601 ORDER_EXPIRED`；status≠pending（paid/shipped/...）→ 409 `409602`
- STEP-TRD-03 status=pending 且 now ≥ expires_at（调度器未及时取消）→ 内联执行超时取消（同 SCHED-TRD-001 单单事务）→ 410 `410601`
- STEP-TRD-04 `StripePort.retrievePaymentIntent(payment.payment_intent_id)`：状态 ∈ {requires_payment_method, requires_confirmation, requires_action} → 复用返回 client_secret；∈ {canceled} 或检索失败 → 重建 PaymentIntent（金额/币种同原单）并 `UPDATE payment SET payment_intent_id=新值, status=created`
- STEP-TRD-05 Stripe 失败/超时 → 502 `502601` / 504 `504601`

**出参**: 200 `PaymentCredential{ payment_intent_id, client_secret }`
**错误映射**: 401 / 404 404601 / 409 409602 / 410 410601 / 502 502601 / 504 504601 / 500

### 5.5 applyStoreRefund — POST /api/store/orders/{id}/refunds（FLOW-P10，决策 24/31）

**入参**: `{ reason }`
- V-TRD-035 reason 必填非空白 ≤255 → 422 `422601`

**业务逻辑（TX-TRD-009a 单事务）**:
- STEP-TRD-01 定位订单（customer_id 隔离）→ 404 `404601`
- STEP-TRD-02 状态 guard：status ∉ {paid, shipped} → 409 `409602`
- STEP-TRD-03 进行中工单检查：`EXISTS refund WHERE order_id=? AND status='pending'` → 409 `409605 REFUND_ALREADY_EXISTS`
- STEP-TRD-04 定制投产判定（决策 24，后端双重校验）：订单含定制行且 `now > paid_at + grace_hours` → 422 `422602 CUSTOM_ITEM_NOT_REFUNDABLE`（details: {grace_deadline: paid_at+grace_hours ISO8601}）
- STEP-TRD-05 `refund_no = "RFD-" + yyyyMMdd + "-" + 当日序号(4位)`（同 order_no 机制，uk_refund_no 兜底）
- STEP-TRD-06 `INSERT refund(refund_no, order_id, customer_id, amount=order.total_amount（全额含 gift_wrap_fee，决策 28）, currency=order.currency, reason, status=pending, applied_at=now)` + `UPDATE orders SET status=refunding WHERE id=? AND status IN ('paid','shipped')`（条件更新，affected=0 → 回滚 409602）

**出参**: 201 `StoreRefund{ id, refund_no, order_id, amount, currency, reason, status=pending, applied_at }`
**错误映射**: 401 / 404 404601 / 409 409602·409605 / 422 422601·422602 / 500

---

## 6. STORE WISHLIST（FLOW-P13，决策 18）

### 6.1 listWishlist — GET /api/store/wishlists

- V-TRD-036 locale ∈ {en,es,fr} 缺省 en
- STEP-TRD-01 `SELECT wishlist_item WHERE customer_id=:subject ORDER BY id DESC` + 批量 ProductBrief（含 status=draft 不可购买标记）

**出参**: 200 `{ items:[WishlistItem{id, product_id, product}] }`
**错误映射**: 401 / 500

### 6.2 addWishlistItem — POST /api/store/wishlists

**入参**: `{ product_id }`
- V-TRD-037 product_id 必填且商品存在（含 draft 也可收藏？否——经 CatalogSnapshotPort 校验存在且 published）→ 404 `404501`

**业务逻辑**:
- STEP-TRD-01 `INSERT IGNORE wishlist_item(customer_id, product_id)`（uk_wishlist_customer_product）
- STEP-TRD-02 affected=1 → 201（首次收藏）；affected=0 → 200（重复请求幂等返回既有，js_guard）

**出参**: 201/200 `WishlistItem`
**错误映射**: 401 / 404 404501 / 500

### 6.3 removeWishlistItem — DELETE /api/store/wishlists/{productId}

- V-TRD-038 productId 路径参数 int64
- STEP-TRD-01 `DELETE FROM wishlist_item WHERE customer_id=:subject AND product_id=?`；affected=0 → 404 `404604 WISHLIST_ITEM_NOT_FOUND`

**出参**: 204
**错误映射**: 401 / 404 404604 / 500

### 6.4 moveWishlistToCart — POST /api/store/wishlists/{productId}/move-to-cart

**入参**: `{ sku_id?, qty?=1, custom_size_data? }`
- V-TRD-039 qty 整数 ≥1 缺省 1 → 422 `422601`
- V-TRD-040 双模式 guard 同 V-TRD-004（现货必填 sku_id；定制必填 custom_size_data）→ 422 `422604`

**业务逻辑（TX-TRD-006 单事务）**:
- STEP-TRD-01 `SELECT wishlist_item WHERE customer_id=:subject AND product_id=?` → 无 404 `404604`
- STEP-TRD-02 现货库存校验 → 不足 409 `409601`
- STEP-TRD-03 同事务：加入购物车（复用 addCartItem.STEP-TRD-02 合并逻辑）+ `DELETE wishlist_item`
- STEP-TRD-04 返回整车 CartResponse

**出参**: 200 `CartResponse`
**错误映射**: 401 / 404 404604 / 409 409601 / 422 422604 / 500

---

## 7. STORE BROWSE HISTORY（FLOW-P13，决策 23）

### 7.1 listBrowseHistory — GET /api/store/browse-history

- V-TRD-041 limit 整数 1..50 缺省 20 → 越界 422 `422601`；locale 同 V-TRD-001
- STEP-TRD-01 `SELECT browse_history WHERE customer_id=:subject ORDER BY viewed_at DESC LIMIT :limit` + 批量 ProductBrief

**出参**: 200 `{ items:[BrowseHistoryItem{id, product_id, viewed_at, product}] }`
**错误映射**: 401 / 500

### 7.2 recordBrowseHistory — POST /api/store/browse-history

**入参**: `{ product_id }`
- V-TRD-042 product_id 必填且商品存在 published → 404 `404501`

**业务逻辑**:
- STEP-TRD-01 `INSERT browse_history(customer_id, product_id, viewed_at=now) ON DUPLICATE KEY UPDATE viewed_at=now`（uk_browse_customer_product，js_guard 重复浏览 upsert）
- STEP-TRD-02 滚动清理：`DELETE FROM browse_history WHERE customer_id=:subject AND id NOT IN (按 viewed_at 倒序前 50)`（超过 50 条删最旧；匿名用户前端不调用）

**出参**: 204
**错误映射**: 401 / 404 404501 / 500

---

## 8. STORE EXCHANGE RATES（FLOW-P18，决策 14）

### 8.1 listStoreExchangeRates — GET /api/store/exchange-rates（匿名，公开白名单）

- STEP-TRD-01 JetCache 读 `trading:exchange-rates`（两级 TTL 600s）；未命中 → `SELECT exchange_rate ORDER BY FIELD(currency,'USD','EUR','CAD','AUD','GBP')` → 回填缓存
- STEP-TRD-02 响应头 `Cache-Control: s-maxage=600`（CDN 边缘缓存）；payload 不含 updated_by（仅 admin 端返回）
- STEP-TRD-03 失效链：后台 updateAdminExchangeRate `@CacheInvalidate` → MQ content.invalidated → CDN purge（见 EVT-TRD-005）

**出参**: 200 `{ items:[ExchangeRate{currency, rate, updated_at}] }`（USD 恒 rate=1；下单锁汇以服务端为准，本端点仅展示换算）
**错误映射**: 500

---

## 9. ADMIN ORDERS（FLOW-P09）

### 9.1 listAdminOrders — GET /api/admin/orders（RBAC /orders）

**入参**: page/page_size/status/search/currency/from/to
- V-TRD-043 page ≥1；page_size 1..100 → 422 `422601`
- V-TRD-044 status ∈ 八枚举缺省 all；V-TRD-045 search ≤80；V-TRD-046 currency ∈ 五币种
- V-TRD-047 from/to 为 ISO date-time 且 from ≤ to → 422 `422601`

**业务逻辑**:
- STEP-TRD-01 动态条件分页：`WHERE [status=:s] [AND currency=:c] [AND created_at BETWEEN :from AND :to] [AND (order_no LIKE :kw OR customer_id IN (按邮箱模糊查 identity user))] ORDER BY created_at DESC`
- STEP-TRD-02 customer_name/customer_email 经 identity 用户快照批量联取（进程内 user 查询接口，防 N+1）

**出参**: 200 `Paginated<AdminOrderListItem>`
**错误映射**: 401 / 403 40300 / 422 422601 / 500

### 9.2 getAdminOrder — GET /api/admin/orders/{id}（RBAC /orders）

- V-TRD-048 id 路径参数
- STEP-TRD-01 `SELECT orders WHERE id=?` → 无 404 `404601`
- STEP-TRD-02 组装：客户信息（identity 联取 name/email/phone）、lines[]（定制明细 custom_size_data）、payment（Stripe 摘要 card_summary/payment_intent_id）、address_snapshot、礼品包装标记 gift_wrap/gift_wrap_fee（决策 28 发货执行）、refunds[]（AdminRefund 含 return_tracking_no/stripe_refund_id）、状态时间线（created_at/paid_at/shipped_at/completed_at 派生）

**出参**: 200 `AdminOrderDetail`
**错误映射**: 403 / 404 404601 / 500

### 9.3 shipAdminOrder — POST /api/admin/orders/{id}/ship（OP-009/s-752）

**入参**: `{ carrier, tracking_no }`
- V-TRD-049 carrier 必填 ∈ 三承运商枚举 → 422 `422601`
- V-TRD-050 tracking_no 必填非空白 ≤64（物流单号手填，BE-DIM-5 无真实物流 API）→ 422 `422601`

**业务逻辑（TX-TRD-004a）**:
- STEP-TRD-01 定位订单 → 404 `404601`；状态机 guard：`UPDATE orders SET status=shipped, carrier=:carrier, tracking_no=:t, shipped_at=now WHERE id=? AND status='paid'`；affected=0 → 409 `409602`
- STEP-TRD-02 同事务 `INSERT operation_log(action=订单发货, target=order_no, changes={carrier,tracking_no})`
- STEP-TRD-03 提交后 `MQ publish order.shipped{order_no, customer_id, locale, carrier, tracking_no}`（EVT-TRD-002 → 发货邮件 FLOW-P11）

**出参**: 200 `AdminOrderDetail`
**错误映射**: 403 / 404 404601 / 409 409602 / 422 422601 / 500

### 9.4 patchAdminOrderStatus — PATCH /api/admin/orders/{id}/status

**入参**: `{ status: completed|cancelled }`
- V-TRD-051 status 必填 ∈ {completed, cancelled} → 422 `422601`

**业务逻辑（TX-TRD-004b）**:
- STEP-TRD-01 定位订单 → 404 `404601`
- STEP-TRD-02 status=completed：guard 当前 shipped → `UPDATE orders SET status=completed, completed_at=now WHERE id=? AND status='shipped'`；affected=0 → 409 `409602`（确认完成解锁评价提交，s-756，review 域按 completed 订单校验购买资格）
- STEP-TRD-03 status=cancelled：guard 当前 pending（paid 取消需先走退款流程 → 409 `409602`，details.hint="paid 订单请走退款"）→ 取消 + 现货回补 + 券回滚 + 作废 PaymentIntent（复用 cancelStoreOrder.STEP-TRD-03/04 事务体）
- STEP-TRD-04 同事务 `INSERT operation_log(action=订单状态变更)`；提交后 status=cancelled 发 `MQ order.cancelled`

**出参**: 200 `AdminOrderDetail`
**错误映射**: 403 / 404 404601 / 409 409602 / 500

### 9.5 createAdminRefund — POST /api/admin/orders/{id}/refunds（ALIGN-006「发起退款」）

**入参**: `{ amount, reason }`
- V-TRD-052 amount 必填 number ≥0 → 422 `422601`
- V-TRD-053 reason 必填非空白 ≤255 → 422 `422601`

**业务逻辑（TX-TRD-009b 单事务，校验同消费端）**:
- STEP-TRD-01 定位订单 → 404 `404601`；状态 guard status ∈ {paid, shipped} → 否则 409 `409602`
- STEP-TRD-02 进行中工单 → 409 `409605`
- STEP-TRD-03 定制投产判定同 applyStoreRefund.STEP-TRD-04（决策 24 后台同样生效）→ 422 `422602`（details 提示原因与 grace_deadline）
- STEP-TRD-04 金额上限（决策 28）：amount > order.total_amount（含礼品包装费）→ 422 `422603 REFUND_AMOUNT_EXCEEDED`（details: {max_refundable: total_amount}）
- STEP-TRD-05 INSERT refund(pending, amount=:amount 订单币种) + orders→refunding（同 applyStoreRefund.STEP-TRD-05/06）+ `operation_log(action=发起退款)`

**出参**: 201 `AdminRefund`
**错误映射**: 403 / 404 404601 / 409 409602·409605 / 422 422601·422602·422603 / 500

---

## 10. ADMIN REFUNDS（FLOW-P10，决策 24/31，s-755）

### 10.1 listAdminRefunds — GET /api/admin/refunds（RBAC /refunds）

- V-TRD-054 page ≥1 / page_size 1..100 / status ∈ {all,pending,approved,rejected} 缺省 all / search ≤80 → 422 `422601`
- STEP-TRD-01 `WHERE [status=:s] [AND (refund_no LIKE :kw OR order_no LIKE :kw OR 客户邮箱命中)] ORDER BY applied_at DESC` 分页；联取 order_no / customer_name / customer_email（派生）

**出参**: 200 `Paginated<AdminRefund>`
**错误映射**: 401 / 403 / 500

### 10.2 approveAdminRefund — POST /api/admin/refunds/{id}/approve

**入参**: `{ return_tracking_no? }`
- V-TRD-055 return_tracking_no 选填 ≤64（退货物流单号登记，决策 31）→ 422 `422601`

**业务逻辑（TX-TRD-003 原子事务，BE-DIM-4 核心；已发货单为审核制：管理员线下确认收到退货后才点通过）**:
- STEP-TRD-01 `SELECT refund WHERE id=?` → 无 404 `404605 REFUND_NOT_FOUND`
- STEP-TRD-02 js_guard：status≠pending → 409 `409604 REFUND_STATE_INVALID`
- STEP-TRD-03 **事务开始（Stripe 调用置于事务内，失败整体回滚——保证不出现「钱动账不动」）**：
  1. `UPDATE refund SET status=approved, return_tracking_no=COALESCE(:t, return_tracking_no) WHERE id=? AND status='pending'`（条件更新防并发双审，affected=0 → 回滚 409 `409604`）
  2. `StripePort.createRefund(payment.payment_intent_id, amount=refund.amount×100 取整, 原币种)`（决策 14 原币种原金额）：失败 → **整体回滚** → 502 `502601`；超时 → 504 `504601`（工单保持 pending 可重试；若超时后 Stripe 实际成功，charge.refunded webhook 幂等汇合对账告警）
  3. `UPDATE refund SET stripe_refund_id=:rid`
  4. `UPDATE orders SET status=refunded WHERE id=:orderId AND status='refunding'`（order_lifecycle: refunding→refunded）
  5. 现货行库存回补：`UPDATE sku SET stock=stock+qty, version=version+1`（定制行不回补，决策 6）
  6. `UPDATE payment SET status=refunded`（payment_lifecycle: succeeded→refunded）
  7. `INSERT operation_log(action=退款审核通过, target=refund_no, changes={amount, stripe_refund_id, return_tracking_no})`
- STEP-TRD-04 提交后 `MQ publish refund.resolved{result:approved, refund_no, order_no, customer_id, locale, amount, currency}`（EVT-TRD-004 → 退款结果邮件）

**出参**: 200 `AdminRefund`（status=approved，含 stripe_refund_id）
**错误映射**: 403 / 404 404605 / 409 409604 / 502 502601 / 504 504601 / 500

### 10.3 rejectAdminRefund — POST /api/admin/refunds/{id}/reject

**入参**: `{ reason }`
- V-TRD-056 reason 必填非空白 ≤255（拒绝原因回执消费端与邮件）→ 422 `422601`

**业务逻辑（TX-TRD-009c 单事务）**:
- STEP-TRD-01 定位工单 → 404 `404605`；js_guard pending → 409 `409604`
- STEP-TRD-02 `UPDATE refund SET status=rejected, reason=reason || ' | 拒绝: ' + :reason WHERE id=? AND status='pending'`（保留申请原因，追加拒绝理由入 details 字段方案见 MAP-TRD-008）
- STEP-TRD-03 订单回原状态（order_lifecycle: refunding→paid/shipped）：`UPDATE orders SET status = IF(shipped_at IS NULL,'paid','shipped') WHERE id=:orderId AND status='refunding'`
- STEP-TRD-04 `operation_log(action=退款审核拒绝)`；提交后 `MQ refund.resolved{result:rejected, reject_reason}`

**出参**: 200 `AdminRefund`（status=rejected）
**错误映射**: 403 / 404 404605 / 409 409604 / 422 422601 / 500

### 10.4 patchAdminRefund — PATCH /api/admin/refunds/{id}（决策 31 登记退货单号）

**入参**: `{ return_tracking_no }`
- V-TRD-057 return_tracking_no 必填非空白 ≤64 → 422 `422601`
- STEP-TRD-01 定位工单 → 404 `404605`；js_guard：status≠pending → 409 `409604`（已审结不可改登记）
- STEP-TRD-02 `UPDATE refund SET return_tracking_no=:t`（不触发状态变化、不写状态机事件；登记类操作不发 MQ）

**出参**: 200 `AdminRefund`
**错误映射**: 403 / 404 404605 / 409 409604 / 500

---

## 11. ADMIN EXCHANGE RATES（FLOW-P18，决策 14，RBAC /settings）

### 11.1 listAdminExchangeRates — GET /api/admin/exchange-rates

- STEP-TRD-01 `SELECT exchange_rate ORDER BY FIELD(currency, 'USD','EUR','CAD','AUD','GBP')`（实时直查，不走缓存）；admin 端返回含 updated_by/updated_at

**出参**: 200 `{ items:[ExchangeRate{id, currency, rate, updated_by, updated_at}] }`
**错误映射**: 403 / 500

### 11.2 updateAdminExchangeRate — PUT /api/admin/exchange-rates/{currency}

**入参**: path `currency ∈ {EUR,CAD,AUD,GBP}`；body `{ rate }`
- V-TRD-058 currency 路径枚举校验；传 USD → 422 `422605`（details.reason="USD 恒为 1 不可改"）；五币种之外 → 422 `422605`
- V-TRD-059 rate 必填 number > 0（exclusiveMinimum）→ 422 `422601`

**业务逻辑（TX-TRD-011）**:
- STEP-TRD-01 `UPDATE exchange_rate SET rate=:rate, updated_by=:adminId, updated_at=now WHERE currency=:c`
- STEP-TRD-02 同事务 `operation_log(action=汇率变更, changes={currency, before, after})`
- STEP-TRD-03 提交后失效链：`@CacheInvalidate trading:exchange-rates` → `MQ publish content.invalidated{type:exchange_rates_updated, paths:["/api/store/exchange-rates"]}` → 失效消费者 Cloudflare purge（EVT-TRD-005）
- STEP-TRD-04 语义说明：仅影响**新订单锁汇**；既有订单 exchange_rate 快照不变（决策 14）

**出参**: 200 `ExchangeRate`
**错误映射**: 403 / 422 422601·422605 / 500

---

## 12. ADMIN CHECKOUT CONFIG（决策 24/28，RBAC /settings）

### 12.1 getAdminCheckoutConfig — GET /api/admin/checkout-config

- STEP-TRD-01 `SELECT checkout_config WHERE id=1`（单例行，建表种子初始化 gift_wrap_fee_usd=15.00 / custom_refund_grace_hours=24）

**出参**: 200 `CheckoutConfig{ gift_wrap_fee_usd, custom_refund_grace_hours }`
**错误映射**: 403 / 500

### 12.2 updateAdminCheckoutConfig — PUT /api/admin/checkout-config

**入参**: `CheckoutConfig{ gift_wrap_fee_usd, custom_refund_grace_hours }`
- V-TRD-060 gift_wrap_fee_usd 必填 number ≥0（USD 基准价，决策 28）→ 422 `422601`
- V-TRD-061 custom_refund_grace_hours 必填整数 1..168（决策 24 宽限期可配置）→ 422 `422601`

**业务逻辑（TX-TRD-012）**:
- STEP-TRD-01 `UPDATE checkout_config SET ... WHERE id=1` + `operation_log(action=结算配置变更, changes before/after)`
- STEP-TRD-02 生效语义：gift_wrap_fee_usd 影响后续报价/下单（既有订单 gift_wrap_fee 为快照）；custom_refund_grace_hours 影响后续退款资格判定（判定时实时读取）

**出参**: 200 `CheckoutConfig`
**错误映射**: 403 / 422 422601 / 500

---

## 13. 自检

- [x] 37 操作全部覆盖：store-cart 5 + store-addresses 4 + store-checkout 2 + store-payments 1 + store-orders 5 + store-wishlist 4 + store-browse-history 2 + store-exchange-rates 1 + admin-orders 5 + admin-refunds 4 + admin-exchange-rates 2 + admin-checkout-config 2 = **37/37**
- [x] 每端点含 入参验证(V-TRD) / 业务逻辑(STEP-TRD) / 出参 / 错误码映射 四部分
- [x] V-TRD-001 ~ V-TRD-061 全域唯一无重号；STEP-TRD 端点内编号（引用名 operationId 限定）
- [x] 错误码仅用契约已定义码：trading 19 码（404601~605/409601~605/410601/422601~605/401601/502601/504601）+ identity 复用（40100/40300/50000）+ 跨域透传（404501 catalog、422701/422702/422703 marketing）
- [x] 下单原子事务：订单号预生成 + idempotency_key 唯一索引 + SKU 乐观锁 CAS×3 + 券核销 + 清车（createOrder.STEP-TRD-05）
- [x] webhook：验签(401601) → processed_event 幂等 → 金额币种核对 → 状态 guard 推进 → 迟到支付自动退款补偿（stripeWebhook 五条安全约束全落）
- [x] 多承运商报价组装（quoteCheckout.STEP-TRD-03/04，「分区 × 承运商」规则行 + 兜底行）
- [x] 定制退款宽限 422602（applyStoreRefund / createAdminRefund / getStoreOrder 派生三处一致）
- [x] webhook 端点标注 JWT 白名单豁免 + Stripe 签名校验链（§0 + §4）
- [x] 事务边界引用 TX-TRD 编号（详见 trading-data-detail.md）
