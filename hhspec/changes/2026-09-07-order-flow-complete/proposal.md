# 订单流程完整化改造方案（order-flow-complete）

日期：2026-09-07 · 范围：backend + portal-store + portal-admin · 目标：可直接上生产

## 0. 背景与调研结论

### 0.1 现状（代码调研）
- 订单七态（pending/paid/shipped/completed/cancelled/refunding/refunded），无 delivered、无制作阶段、无订单时间线/内部备注。
- **支付 stub 不闭环**：`StubStripeClient` 只返回 `requires_payment_method`，不触发 webhook；stub 下 `webhook-secret` 为空且验签 fail-closed，本地订单永远停在 pending，30 分钟后被 `OrderTimeoutScheduler` 取消。
- 物流：仅 `Order.carrier/tracking_no` 手填字段；无 shipment/轨迹实体、无部分发货、无承运商编码/跟踪链接、运费仅"分区×满额"二维、`GeoZoneResolver` 仅 4 区硬编码、承运商枚举双来源（`TradingParams.CARRIERS` vs `Carrier.name`）。
- 汇率：五币种纯手工维护，无外部行情、无定时刷新、无历史、无加价率；订单已锁汇快照（`Order.exchange_rate`），前台未展示锁汇说明。
- 税费：完全缺失（无税率表、金额恒等式无 tax 项、结算页仅静态 DDU 文案）。
- 地址：`country` 自由文本非 ISO 码，无省州字典/邮编校验。
- 邮件：仅 order.paid/order.shipped/refund.resolved 三类；取消、签收、退款受理、进入制作无邮件。
- 退款：仅整单终态，无部分退款累计；无用户确认收货；无 shipped→delivered→completed 自动推进。
- 消费端：stub 支付 Continue 仅跳转不调接口；订单详情无轨迹/确认收货/再次购买；状态文案硬编码英文未 i18n；无游客查单；无超时倒计时。
- 后台：订单详情缺时间线/备注/轨迹/部分发货/制作阶段；无税率页；汇率页无来源/同步/历史。

### 0.2 竞品结论（Azazie / JJ's House / Stacees / Cocomelody / Lulus / David's Bridal / BHLDN / Grace Loves Lace / Shopify 基线）
- 状态机：Shopify 三维（order × payment × fulfillment）+ 定制品对外"制作阶段"（Confirmed → In Production → Quality Check → Shipped → In Transit → Delivered → Completed）。
- 支付即确认（付款成功后发确认邮件并进入制作）；未支付超时取消（24h–5 天）。
- 物流：交期 = 制作周期 + 运输天数，结算页按地址重算；Standard/Express 分级；轨迹跟踪（17TRACK/AfterShip）；签收后 N 天自动完成。
- 多币种：展示币种可切换、下单锁汇、免责声明；核心币种本币结算。
- 税费：头部品牌趋势 DDP（结算价即最终价），其余 DDU 并在结算页明示；EU/UK VAT、AU GST、CA GST。
- 售后：定制品 Final Sale（仅质量问题）；退款 Requested → Approved → Received → Refunded；支持部分退款（不退运费/Rush）。
- 通知：下单/付款、进入制作、发货+单号、已签收、退款受理、退款到账、取消。
- 后台：时间线 + 内部备注、部分发货、手动标记状态、导出、游客查单（订单号 + 邮箱）。

## 1. 目标与范围

| 编号 | 模块 | 交付内容 |
|---|---|---|
| A | 支付闭环 | stub 模式付款时后端合成 `payment_intent.succeeded` 事件走同一 webhook 处理链（幂等/金额核对/CAS/MQ 全部复用），前端支付面板 Continue 调用确认端点；real 模式行为不变 |
| B | 订单状态机 | 新增 `DELIVERED`；新增制作阶段子状态 `production_stage`；订单事件时间线 `order_event`（状态变更/备注/发货/邮件/支付/退款）；用户确认收货；自动签收/自动完成调度；超时分钟可配 |
| C | 通知 | 新增 order.cancelled / order.delivered / order.production / refund.requested 四类邮件（en/es/fr/zh 模板） |
| D | 物流 | `shipment` + `shipment_event` 实体（部分发货、多包裹）；承运商 `code` + 跟踪链接模板；运费规则增加服务等级/运输天数；ISO 国家→区域全量映射（8 区）；轨迹供应商端口（stub + 17TRACK）+ 定时同步；预计送达；后台运费试算；游客查单 |
| E | 汇率 | 行情供应商端口（manual + Frankfurter/ECB）+ 每日定时刷新 + 手动刷新；加价率 spread；`exchange_rate_history`；手工覆盖标记；前台锁汇说明 |
| F | 税费 | `tax_rule`（国家/州 × 税种 × 税率 × DDP/DDU × 起征额）；报价/订单增加 `tax_amount` 与明细；恒等式升级；结算页税费行 + 关税提示；后台税率 CRUD + 种子 |
| G | 地址 | `country_code` ISO2 列 + 校验 + 存量回填；前台国家下拉 |
| H | 退款 | 部分退款累计（`refunded_amount`）；工单挂起期间订单 REFUNDING，部分批准/驳回还原到 `from_status` 快照，累计达 total 才 REFUNDED；同一订单单张挂起工单；退款受理事件 |
| I | 消费端 | 支付确认、6 步时间线、制作阶段、包裹轨迹、确认收货、再次购买、超时倒计时、税费/锁汇展示、状态 i18n、游客查单页 |
| J | 后台 | 订单详情时间线/备注/制作阶段/发货面板（部分发货/轨迹/签收）/部分退款；列表新增筛选列；物流页升级 + 试算；汇率页升级；税率页；结算配置新字段 |
| K | 质量 | 后端单测（新增域全覆盖 + 存量更新）；Playwright 全链路脚本（下单→付款→制作→发货→签收→完成→退款；游客查单；后台配置页）；类型检查/构建全绿 |

不在本次范围（列入后续）：Klarna/Afterpay 真实通道、Rush 加急制作费、阶梯扣费取消、Stripe Tax 实时税率、PDF 发票、短信。

## 2. 数据模型变更（huihao-mysql 注解自动 DDL；金额 decimal(12,2) 与存量一致）

### 2.1 orders 新增列
| 列 | 定义 | 说明 |
|---|---|---|
| `tax_amount` | decimal(12,2) NOT NULL DEFAULT 0 | 订单币种税费 |
| `tax_breakdown` | json NULL | `[{type,rate_scaled,base,amount,label}]` |
| `incoterm` | tinyint NULL | 1=DDP 2=DDU |
| `refunded_amount` | decimal(12,2) NOT NULL DEFAULT 0 | 已退款累计 |
| `production_stage` | tinyint NULL | 1=待审核 2=制作中 3=质检中 4=待发货 |
| `delivered_at` | datetime(3) NULL | |
| `shipping_service_level` | tinyint NULL | 1=STANDARD 2=EXPRESS |
| `estimated_delivery_from/to` | date NULL | 下单时预计送达区间快照 |
| `amount_version` | tinyint NOT NULL DEFAULT 1 | 金额算法版本：1=存量（无税，恒等式旧版）2=本次（含税） |

恒等式升级（amount_version=2）：`total_amount = subtotal + shipping_fee + gift_wrap_fee + tax_amount − discount_amount`；存量行 amount_version=1 且 tax_amount=0，旧恒等式与新恒等式等价，无需回填。

**金额兼容规则（TAX-LEGACY）**：PaymentIntent 金额始终 = 下单时 `total_amount`（无论版本），webhook 金额核对比较的是订单表 `total_amount`（Money.toMinor 同一路径），因此旧订单/旧 webhook 负载天然兼容；`amount_version` 仅用于展示与审计（前台 v1 订单不显示税费行）。**写规则**：`OrderCreateService.buildOrder` 对每张新订单固定写 2（单测断言）；v1 订单永不重算。税费计算：逐条规则 `base × rate_scaled / 10000` 在订单币种按 HALF_UP 取 2 位小数后相加；`Money.toMinor` 不变。单测固化 v1 订单 + 旧 webhook 负载夹具。

### 2.2 新实体
- `order_event`：order_id, type(tinyint: 1=STATUS_CHANGED 2=NOTE 3=SHIPMENT 4=PAYMENT 5=REFUND 6=EMAIL 7=PRODUCTION), actor_type(1=SYSTEM 2=CUSTOMER 3=ADMIN), actor_id, title varchar(128), detail varchar(512), payload json, customer_visible tinyint(1)。索引 (order_id, created_at)。写入始终与触发它的业务变更同事务。
- `shipment`：order_id, shipment_no varchar(24) uk, carrier_code varchar(32), carrier_name varchar(64), tracking_no varchar(64), tracking_url varchar(255), status tinyint(1=PENDING 2=IN_TRANSIT 3=OUT_FOR_DELIVERY 4=DELIVERED 5=EXCEPTION), shipped_at, delivered_at, last_event_at, last_event_desc, provider_ref varchar(64), idempotency_key varchar(64) NULL uk。索引 (order_id)、(status, last_event_at)；**uk (order_id, carrier_code, tracking_no)** 作为重复录入幂等闸。
- `shipment_line`：shipment_id, order_line_id, qty int。**uk (shipment_id, order_line_id)**；索引 (order_line_id)。分配约束：`SUM(shipment_line.qty WHERE order_line_id=X) ≤ order_line.qty`，在 Redisson 锁 `trading:order-ship:{orderId}` 内校验后写入，超量 → 422 `422906`。
- shipment 分配**不可变**：创建后不允许修改 shipment_line；`PATCH /shipments/{id}` 仅允许改 carrier_code/tracking_no（且仅当无 PROVIDER 事件、未 DELIVERED）；纠错走 `POST /shipments/{id}/cancel`（仅无 PROVIDER 事件且未 DELIVERED；status→CANCELLED(6)，释放分配；若订单已 SHIPPED 且释放后存在未发行，订单 CAS SHIPPED→PAID 并记录事件）。创建请求携带 `Idempotency-Key` 头（可选）：命中 `shipment.idempotency_key` uk 直接返回既有包裹。
- `shipment_event`：shipment_id, occurred_at datetime(3), status tinyint, location varchar(128), description varchar(255), source tinyint(1=MANUAL 2=PROVIDER 3=SYSTEM), provider_event_id varchar(64) NULL。**uk (shipment_id, provider_event_id)**（MANUAL 行为 NULL 不受限；PROVIDER 行 insertIgnore 去重；供应商无事件 id 时取 `sha1(occurred_at|status|description)` 前 40 位）。索引 (shipment_id, occurred_at)。
- `tax_rule`：country_code char(2), region varchar(8) NOT NULL DEFAULT ''（空串=国家级，避免 NULL 唯一键失效）, tax_type tinyint(1=VAT 2=GST 3=SALES_TAX 4=DUTY), rate_scaled int(10000=100%), applies_to_shipping tinyint(1), threshold_usd decimal(12,2) NULL, effective_from date NULL, effective_to date NULL, enabled tinyint(1), label varchar(64)。**uk (country_code, region, tax_type)**。匹配优先级：同 tax_type 下 (country, region 精确) 覆盖 (country, '')；仅取 enabled 且 today ∈ [effective_from, effective_to] 的规则；同 key 生效窗口重叠 → 422 `422907`。
- `tax_destination_policy`：country_code char(2) uk, incoterm tinyint(1=DDP 2=DDU), duties_notice tinyint(1), notice_text varchar(255)。**incoterm 从 tax_rule 中拆出**（一国一策，避免同国规则 incoterm 冲突）；无记录默认 DDU + 提示。
- `payment`：`refunded_amount` decimal(12,2) NOT NULL DEFAULT 0；`PaymentStatus` 新增 `PARTIALLY_REFUNDED`。每次退款批准仅向 Stripe 发起 **本次 delta** 金额的 refund；Payment 累计 < total → PARTIALLY_REFUNDED，累计 ≥ total → REFUNDED。`charge.refunded` webhook 以 `amount_refunded` 与 Payment.refunded_amount 对账，不一致仅告警。
- `event_outbox`：event_type varchar(64), routing_key varchar(64), payload json, status tinyint(1=PENDING 2=SENT 3=DEAD), attempts int, next_attempt_at datetime(3), last_error varchar(255)。索引 (status, next_attempt_at)。
- `exchange_rate_history`：base_currency char(3) DEFAULT 'USD', currency char(3), rate decimal(12,6), source tinyint(1=MANUAL 2=PROVIDER), quote_date date（供应商报价日/手工=当日）, recorded_at datetime(3)。**uk (currency, source, quote_date)**：同日同源重复刷新 insertIgnore → 追加语义幂等；手工修改同日多次仅保留首条历史（现值在 exchange_rate 表）。索引 (currency, recorded_at)。

### 2.3 存量实体扩列
- `carrier`：`code` varchar(32) uk（种子：FEDEX/UPS/DHL/USPS）、`tracking_url_template` varchar(255)（`{tracking_no}` 占位）。
- `shipping_rate`：**不改动**（保留旧 uk_zone 与数据，旧版本 jar 回滚可直接读取）。新增表 `shipping_option`：zone varchar(32), carrier_code varchar(32), service_level tinyint(1=STANDARD 2=EXPRESS), fee_under/fee_over decimal(12,2), threshold decimal(12,2) NULL, transit_days_min/max int, enabled tinyint(1)；**uk (zone, carrier_code, service_level)**。启动迁移器：`shipping_option` 为空时按 `shipping_rate` 每行生成 STANDARD 选项（carrier_code 由 zone 前缀/承运商名映射，无法映射者 carrier_code='ANY'）。`ShippingQuoteService` 改读 `shipping_option`；旧表在下一版本 contract 阶段移除。
- `exchange_rate`：`source` tinyint(1=MANUAL 2=PROVIDER)、`synced_at` datetime NULL、`manual_override` tinyint(1) DEFAULT 0。
- `checkout_config`：`auto_complete_days` int DEFAULT 7、`auto_deliver_days` int DEFAULT 30、`pending_timeout_minutes` int DEFAULT 30、`exchange_rate_spread_scaled` int DEFAULT 0、`production_days_default` int DEFAULT 21。
- `address`：`country_code` char(2) NULL、`region_code` varchar(8) NULL（启动回填：country 由名称/别名解析；region 仅对 US/CA/AU 按州/省字典（全称/缩写/别名）规范化为 ISO-3166-2 后缀，其余国家留空；无法解析留空，前台强制补选）。订单 `address_snapshot` 追加 `country_code`/`region_code`，税费匹配只读快照中的规范码。

### 2.4 枚举（com.dreamy.enums，IntEnum 整数契约）
`OrderStatus.DELIVERED(8)`；新增 `ProductionStage`、`ShipmentStatus`、`ShipmentEventSource`、`OrderEventType`、`OrderActorType`、`TaxType`、`Incoterm`、`ShippingServiceLevel`、`ExchangeRateSource`。

状态机（转换矩阵 + 触发者）：
```
PENDING   → PAID(webhook/stub confirm) | CANCELLED(用户/后台/超时)
PAID      → SHIPPED(全部行发出) | REFUNDING(退款申请) | CANCELLED(仅后台，须同事务创建并批准全额退款)
SHIPPED   → DELIVERED(全部包裹签收/自动) | COMPLETED(用户确认收货) | REFUNDING
DELIVERED → COMPLETED(用户确认/自动) | REFUNDING
REFUNDING → REFUNDED(全额批准) | PAID/SHIPPED/DELIVERED(驳回或部分批准，还原到 refund.from_status 快照)
COMPLETED / CANCELLED / REFUNDED 终态
```

**不变量与并发规则（STATE）**：
1. 所有主状态推进一律 `casUpdateStatus(id, from, to)`（affected=0 → 409602 或幂等跳过），沿用现有范式；时间戳 paid_at/shipped_at/delivered_at/completed_at 与状态同 CAS 写入。
2. `production_stage` 仅在 status=PAID 时非空且可编辑；PENDING→PAID 时置 PENDING_REVIEW(1)；PAID→SHIPPED 时置 NULL（历史保留在 order_event PRODUCTION）；REFUNDING→PAID 还原时置回 refund 快照的 stage。阶段转换仅允许 1→2→3→4 单向递进（后台可回退一档用于纠错，记录事件）。
3. 发货聚合：在 `trading:order-ship:{orderId}` Redisson 锁内执行「校验分配 → 插 shipment/shipment_line → 若 ∑已分配 == ∑order_line.qty 则 CAS PAID→SHIPPED」；锁获取失败 → 409 `409906`。
4. 签收聚合：包裹 DELIVERED（手工事件/供应商同步/`/deliver`）在同一锁内执行「更新 shipment → 若全部 shipment 均 DELIVERED 则 CAS SHIPPED→DELIVERED」；订单已 COMPLETED/REFUNDING 时仅更新包裹不动订单。
5. 用户确认收货与自动签收/自动完成互不阻塞：两者都是 CAS，谁先成功谁生效，后者 affected=0 幂等返回。
6. 退款：`Refund` 新增 `from_status` 与 `from_stage` 快照；申请时 CAS 当前态→REFUNDING；批准时在事务内 `refunded_amount += amount`（条件更新 `refunded_amount + amount ≤ total_amount`，否则 422 `422908`），随后 **单条 UPDATE** 判定：`refunded_amount ≥ total_amount → REFUNDED`，否则 → `from_status`；驳回 → `from_status`。同一订单同一时刻最多一张 pending 工单——**由数据库 CAS 保证**：申请/后台创建工单的事务内先 `casUpdateStatus(order, from∈{PAID,SHIPPED,DELIVERED}, REFUNDING)`，affected=0 即已有工单挂起或状态不允许 → 409 `409907`；工单插入在 CAS 之后同事务，因此并发申请只有一个能通过。
7. 后台取消已支付订单 = 同事务：创建 Refund(amount=total−refunded) → approve → CAS PAID→CANCELLED（区别于 REFUNDED：语义为商家取消）。库存回补沿用退款批准逻辑。

## 3. 接口契约（snake_case JSON，`R<T>` 信封，数字错误码）

### 3.1 Store（/api/store）
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/orders/{id}/payment/confirm` | **A** 控制器 Bean 以 `@ConditionalOnProperty(dreamy.stripe.mode=stub)` 注册——real 模式下端点根本不存在（404），**不受任何其他开关影响**；入参为空，金额/币种/locale 全部取自服务端 Payment/Order 记录；事件 id 确定性 `evt_stub_{payment_intent_id}`；返回 `StoreOrderDetail` |
| POST | `/orders/{id}/confirm-delivery` | **B** SHIPPED/DELIVERED → COMPLETED（CAS，非法态 409602） |
| POST | `/orders/{id}/reorder` | **I** 行商品回购物车（SKU 缺货/下架跳过并返回 skipped 列表） |
| POST | `/orders/track` | **D** 游客查单 `{order_no, email}`，公开白名单 + IP 频控（10 次/时），返回脱敏 `OrderTrackView`（状态、阶段、包裹与轨迹、预计送达） |
| GET | `/orders/{id}` | 扩展：`production_stage, delivered_at, tax_amount, tax_breakdown, incoterm, refunded_amount, estimated_delivery_from/to, shipping_service_level, shipments[] (含 events[])、events[] (customer_visible)` |
| POST | `/checkout/quote` | 扩展入参 `service_level`；出参 `shipping_options[]` 增 `carrier_code, carrier_name, service_level, transit_days_min/max, estimated_delivery_from/to`；新增 `tax_amount, tax_breakdown[], incoterm, duties_notice(bool), exchange_rate_locked_note(bool)` |
| POST | `/checkout/orders` | 扩展入参 `service_level`；`carrier` 改为 `carrier_code`（兼容旧 name） |
| GET | `/shipping/countries` | ISO 国家列表 `[{code,name,zone,supported}]`（公开、缓存） |

### 3.2 Admin（/api/admin）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/orders/{id}` | 扩展同上 + `events[]` 全量 + `shipments[]` |
| GET | `/orders` | 新增筛选 `production_stage, wedding_before, has_shipment`；返回增 `production_stage, wedding_days_left, delivered_at` |
| PATCH | `/orders/{id}/status` | 扩展支持 3→8、8→4、2→5（后台取消已支付：必须同时创建并批准全额退款） |
| PATCH | `/orders/{id}/production-stage` | `{stage}` 仅 PAID 态；进入 IN_PRODUCTION 触发 order.production 邮件 |
| POST | `/orders/{id}/notes` | `{content, customer_visible}` → order_event NOTE |
| POST | `/orders/{id}/shipments` | `{carrier_code, tracking_no, lines?:[{order_line_id, qty}]}`；lines 省略=全部未发行；超量 422906、重复单号 409908、锁冲突 409906；全部行发出 → 订单 PAID→SHIPPED（原 `/ship` 保留为该端点的兼容别名） |
| PATCH | `/shipments/{id}` | `{carrier_code?, tracking_no?}` |
| POST | `/shipments/{id}/events` | `{status, occurred_at?, location?, description}` 手工轨迹；status=DELIVERED 时包裹签收 |
| POST | `/shipments/{id}/deliver` | 包裹签收；全部包裹签收 → 订单 SHIPPED→DELIVERED + order.delivered 邮件 |
| POST | `/shipments/{id}/sync` | 手动拉取供应商轨迹（provider=stub 时 204 无操作） |
| GET/POST/PUT/DELETE | `/tax-rules` | CRUD + `PATCH /tax-rules/{id}/enabled`；生效窗口重叠 422907 |
| GET/PUT | `/tax-destination-policies/{country_code}` | 目的国 DDP/DDU 与关税提示 |
| POST | `/shipping/quote-preview` | `{country_code, subtotal_usd, service_level?}` → 各承运商报价 + 税费预估 |
| GET | `/exchange-rates` | 增 `source, synced_at, manual_override, effective_rate(含 spread)` |
| POST | `/exchange-rates/refresh` | 触发供应商刷新（manual 模式 409 `409905`） |
| GET | `/exchange-rates/{currency}/history?days=30` | 历史 |
| PUT | `/exchange-rates/{currency}` | 扩展 `{rate, manual_override}` |
| GET/PUT | `/checkout-config` | 新字段 |
| POST | `/orders/{id}/refunds` | 部分退款：`amount ≤ total − refunded_amount`（422908）；已有挂起工单 409907 |

### 3.3 配置（application.yml，环境变量注入）
```yaml
dreamy:
  stripe:
    mode: ${STRIPE_MODE:stub}                 # stub 支付确认端点仅在 stub 模式注册，无独立开关
  tracking:
    mode: ${TRACKING_MODE:stub}               # stub | track17
    track17-api-key: ${TRACK17_API_KEY:}
    sync-cron: "0 */30 * * * *"
  exchange-rate:
    mode: ${EXCHANGE_RATE_MODE:manual}        # manual | frankfurter
    refresh-cron: "0 5 3 * * *"
    provider-timeout-ms: 5000
```

## 4. 关键流程

### 4.1 stub 支付闭环（A）
1. `createOrder` → `StubStripeClient.createPaymentIntent` 返回 `requires_confirmation`（client_secret 仍为 `..._secret_stub`，前端据此识别 stub）。
2. 前端支付面板 Continue → `POST /orders/{id}/payment/confirm`（无请求体）。
3. `StubPaymentConfirmService`（**仅 stub 模式注册**，与控制器同条件）：校验订单归属（customer_id）与 status=PENDING（否则 409602）；读取 Payment（必须 CREATED/PROCESSING）；**完全由服务端记录**构造事件 `{id:"evt_stub_{payment_intent_id}", type:"payment_intent.succeeded", data.object:{id:payment_intent_id, amount:toMinor(order.total_amount), currency:order.currency, metadata:{locale:order.locale_snapshot}, charges.data[0].payment_method_details.card:{brand:"visa",last4:"4242"}}}`。
4. 调用 `StripeWebhookService.processVerified(JsonNode)`——**包级私有**（`domain.payment.service` 包内），从现 `handle()` 拆出验签之后的处理体；`handle()` 保持「验签 → 解析 → processVerified」，签名校验逻辑与 fail-closed 行为零改动。
5. 同事务：processed_event 幂等闸（事件 id 确定性 → 重复/并发确认只有一次副作用，其余落 200 幂等）、金额核对、CAS→PAID、Payment→SUCCEEDED、order_event(PAYMENT)、afterCommit MQ order.paid → 邮件/showroom/销量。stub 内存表标记 PI succeeded。
6. 响应返回最新 `StoreOrderDetail`（status=2），前端跳 order-success（轮询首帧即命中）。
7. real 模式：控制器与服务 Bean 均不注册 → 404；`HttpStripeClient` + 真实 webhook 路径不变。单测：real profile 上下文不含该 Bean；并发 10 次确认仅 1 次 order.paid 发布；伪造/缺失签名 401601 回归。

### 4.2 发货→签收→完成（B/D）
- 创建 shipment（可部分）：锁 `trading:order-ship:{orderId}` → 校验每行 ∑已分配+新分配 ≤ qty → 插 shipment + shipment_line → order_event(SHIPMENT) → 若全部行分配完毕则 CAS PAID→SHIPPED + shipped_at + `Order.carrier/tracking_no` 写最新包裹快照 + MQ order.shipped。重复单号命中 uk → 409 `409908`。
- 轨迹：手工事件或供应商同步（PROVIDER 事件 insertIgnore 去重）；包裹 status 单向推进 PENDING→IN_TRANSIT→OUT_FOR_DELIVERY→DELIVERED（EXCEPTION 可从任意非终态进入并可恢复）；包裹 DELIVERED → 同锁内判定全部包裹签收 → CAS SHIPPED→DELIVERED + delivered_at + MQ order.delivered。
- 用户 confirm-delivery（SHIPPED/DELIVERED → COMPLETED，SHIPPED 起跳时同写 delivered_at）或 `OrderAutoCompleteScheduler`（每小时，Redisson 锁，批 200，逐单独立事务：delivered_at + auto_complete_days → COMPLETED；shipped_at + auto_deliver_days 且无签收 → DELIVERED）。

### 4.3 报价（D/E/F）
`quote = 锁汇(provider 或 manual 现值 × (1 + spread_scaled/10000)，HALF_UP 6 位) → 行价 → 运费选项(zone × carrier × level，含运输天数) → 礼品包装 → 券 → 税费(按 country_code[+region] 匹配 enabled & 生效窗口内规则；base = subtotal − discount [+ shipping if applies_to_shipping]；threshold_usd 以 USD 基准比较；DDP 计入 total，DDU 仅 duties_notice 不计入) → total（amount_version=2）→ 预计送达 = today + production_days(max(商品制作天数, production_days_default)) + transit_days_min..max`。

### 4.4 事件投递（Outbox）
`TradingEventsPublisher.publish*` 改为：业务事务内插入 `event_outbox(PENDING)` → afterCommit 立即尝试投递 → 成功置 SENT；失败保留 PENDING 并写 last_error。`OutboxRelayScheduler`（每分钟，Redisson 锁，批 200）重投 `next_attempt_at ≤ now` 的 PENDING，指数退避（1m/5m/15m/1h，最多 8 次）后置 DEAD + `[ALERT]`；后台无 UI，运维通过 SQL 将 DEAD 重置为 PENDING 即可重放。MQ stub 模式下投递即本地同步消费（现有行为），outbox 仍落表以便审计。

### 4.5 状态转换 → 订单事件矩阵（每条转换在同事务写一条 order_event，服务测试逐条断言）
| 转换 | 触发 | type | actor | customer_visible |
|---|---|---|---|---|
| 创建 PENDING | 下单 | STATUS_CHANGED | CUSTOMER | 是 |
| PENDING→PAID | webhook / stub confirm | PAYMENT | SYSTEM | 是 |
| PENDING→CANCELLED | 用户取消 / 超时调度 / 后台 | STATUS_CHANGED | CUSTOMER/SYSTEM/ADMIN | 是 |
| 迟到支付补偿 | webhook | PAYMENT（detail=auto refund） | SYSTEM | 否 |
| 支付失败 | webhook payment_failed | PAYMENT | SYSTEM | 是 |
| production_stage 变更 | 后台 | PRODUCTION | ADMIN | 是 |
| 创建/取消 shipment | 后台 | SHIPMENT | ADMIN | 是 |
| 包裹轨迹事件 | 手工/供应商 | SHIPMENT | ADMIN/SYSTEM | 是 |
| PAID→SHIPPED / SHIPPED→DELIVERED | 聚合 | STATUS_CHANGED | SYSTEM | 是 |
| →COMPLETED | 用户确认 / 自动 | STATUS_CHANGED | CUSTOMER/SYSTEM | 是 |
| →REFUNDING | 申请 / 后台创建 | REFUND | CUSTOMER/ADMIN | 是 |
| 退款批准（部分/全额）/ 驳回 | 后台 | REFUND | ADMIN | 是 |
| PAID→CANCELLED（后台取消已支付） | 后台 | STATUS_CHANGED + REFUND | ADMIN | 是 |
| 内部备注 | 后台 | NOTE | ADMIN | 按选择 |
| 邮件已发送 | 邮件消费者 | EMAIL | SYSTEM | 否 |

### 4.6 外部供应商失败语义
- 汇率 provider：HTTP 超时 5s，单次 run 不重试；失败记 `[ALERT]` 日志 + Micrometer `dreamy.exchange.refresh.failure`，保留现值，下一 cron 再试；`manual_override=1` 的币种跳过覆盖。
- 轨迹 provider：每 30 分钟拉取 IN_TRANSIT/OUT_FOR_DELIVERY/PENDING 且 24h 内未同步的包裹，单包裹失败不影响批次；连续失败 3 次仅告警不改状态。
- 游客查单频控：Redis 计数（沿用 `OtpRateLimiter` 模式，key `trading:track:{ip}`，10 次/小时）。

## 5. 执行拆分（串行后端 + 并行前端，含集成检查点）

| 阶段 | 执行者 | 所有权（文件级） | 检查点 |
|---|---|---|---|
| P0 契约骨架 | 主会话 | `enums/*`（OrderStatus.DELIVERED + 9 个新枚举）、`domain/order/entity/Order.java` + `OrderDBConst` 扩列、`domain/checkout/entity/CheckoutConfig.java` 扩列、`domain/refund/entity/Refund.java`(from_status/from_stage)、`dto/TradingDtos.java` 全部新增/扩展 record、`config/props/StripeProperties` 等新 props、`application.yml` 配置块 | `./gradlew compileJava` 通过；`git commit` P0 |
| P1-A 交易核心 | BE-A 子代理（单个） | `domain/order/**`（含 order_event 子域）、`domain/payment/**`、`domain/refund/**`、`infra/stripe/**`、`infra/mail/**` + `resources/i18n/trading-mail*`、`mq/TradingEventsPublisher` + `MailEventConsumer`、`sched/OrderTimeoutScheduler`/`OrderAutoCompleteScheduler`、`controller/StoreOrderController`/`StoreCheckoutController`/`StorePaymentConfirmController`(新)/`AdminOrderController`/`AdminRefundController`、上述测试 | `./gradlew test` 全绿；重启后端；主会话 API 冒烟（下单→确认→paid）；`git commit` P1-A |
| P1-B 物流/汇率/税费/地址 | BE-B 子代理（单个，P1-A 完成后启动） | `domain/shipment/**`(新)、`domain/tax/**`(新)、`domain/shippingrate/**`（含新 `shipping_option`）、`domain/carrier/**`、`domain/exchangerate/**`、`domain/address/**`、`port/ShippingQuotePort`/`ShippingOptionQuote`/`TrackingProviderPort`(新)/`ExchangeRateProviderPort`(新)、`infra/track17/**`、`infra/exchangerate/**`、`sched/ShipmentTrackingSyncScheduler`/`ExchangeRateRefreshScheduler`、`config/ShippingSeedInitializer`/`TradingSeedInitializer`、`controller/AdminShippingRateController`/`AdminCarrierController`/`AdminTaxRuleController`(新)/`AdminShipmentController`(新)/`AdminCheckoutSettingsController`/`StoreShippingController`(新)/`StoreOrderTrackController`(新)；**允许触碰的 P1-A 文件（限定方法）**：`CheckoutQuoteService.compute()` 加税费/运费扩展、`OrderCreateService.buildOrder()` 写税费/预计送达/版本字段、`AdminOrderService.ship()` 改为委托 `ShipmentService`、`StoreOrderService/AdminOrderService` 详情装配追加 shipments/events 字段、`TradingEventsPublisher.publishOrderDelivered` 调用 | `./gradlew test` 全绿；重启后端；主会话冒烟（报价含税/运费选项、发货→签收）；`git commit` P1-B |
| P2-S 消费端 | FE-store 子代理 | `frontend/portal-store/**` | `next build` 零错误 |
| P2-A 后台 | FE-admin 子代理（与 P2-S 并行，无共享文件） | `frontend/portal-admin/**` | `vue-tsc --noEmit` 零错误 |
| P3 验证 | 验证子代理 + 主会话 | `tests/ui-verification/*.verify.mjs`、`tests/api-integration/*`、回归修复（修复需回到对应所有者范围） | §6 全部通过；`git commit` 按域分组 |

冲突规避：后端两阶段严格串行；P1-B 对 P1-A 文件的改动限定在上表列明的方法内，且必须保持 P1-A 测试全绿；DTO 只增不改（snake_case 字段追加，前端向后兼容）。

## 6. 验收标准（P3）

### 6.1 构建与单测
1. `./gradlew test` 全绿；portal-store `next build`、portal-admin `vue-tsc --noEmit` 零错误。
2. 新增单测覆盖：`StubPaymentConfirmService`（归属校验、非 PENDING 409、并发 10 线程仅 1 次发布、事件 id 确定性）、`StripeWebhookService`（伪造签名 401601、重放同 event_id 幂等、v1 旧订单旧负载金额核对通过、金额不符回滚）、`OrderStatus` 转换矩阵全量、`ShipmentService`（超量 422906、重复单号 409908、部分→全部发货聚合、并发发货锁）、`ShipmentTrackingSync`（PROVIDER 事件去重、失败不改状态）、`RefundService`（部分批准还原 from_status、累计达 total → REFUNDED、第二张 pending 409907、超额 422908）、`TaxRuleService`（精确/国家级优先级、生效窗口、重叠 422907、DDP/DDU、起征额、HALF_UP 取整）、`ExchangeRateRefresh`（provider 超时保留现值、manual_override 跳过、history uk 幂等）、`OrderAutoCompleteScheduler`（两条规则、单单失败不阻塞）、`GeoZoneResolver` ISO 全量映射、地址 country_code 回填。

### 6.2 浏览器全链路（Playwright，tests/ui-verification）
3. `order-flow-e2e.verify.mjs`：登录 → 加购（现货 + 定制各一）→ 结算（ISO 国家下拉、Standard/Express 切换运费与 ETA 变化、税费行、锁汇说明）→ 下单 → stub 付款 → order-success 首帧已支付 → 后台：制作阶段 1→2（前台可见）→ 部分发货 1 件 → 手工轨迹 → 剩余发货（订单 SHIPPED）→ 两包裹签收（订单 DELIVERED）→ 前台时间线/轨迹/确认收货（COMPLETED）→ 后台部分退款批准 → 前台 refunded_amount 与状态还原 → 再次购买加入购物车。
4. `order-flow-negative.verify.mjs`：重复点击确认支付仅一次跳转且订单 paid_at 不变；pending 订单倒计时展示；超量发货后台报错；重复单号报错；已完成订单不可再确认收货。
5. `guest-track.verify.mjs`：订单号 + 邮箱查单成功；错误邮箱 404；第 11 次请求 429。
6. `admin-trading-settings.verify.mjs`：税率 CRUD + 重叠拒绝、目的国政策 DDP/DDU、运费规则服务等级/运输天数、运费试算、汇率手动刷新（manual 模式 409905 提示）+ 历史、结算配置新字段保存。

### 6.3 兼容与回归
7. `STRIPE_MODE=real` 启动：确认端点 404、`StripeSignatureVerifier` 行为不变（集成测试 profile）。
8. 存量订单（amount_version=1）在前台/后台详情正常展示，不显示税费行；旧 webhook 负载单测通过。
9. 既有 Playwright/spec 脚本（identity/catalog/portal-api-integration/site-decoration）回归通过。
10. 邮件 stub 日志出现四类新邮件；MQ stub 消费无异常。
11. 迁移演练：以当前 pd-mysql 数据库 `mysqldump` 副本建 `dreamy_rehearsal` 库，用新版本启动完成自动 DDL + 回填，再用**上一提交**的 jar 指向同一库启动并读取订单/运费/汇率接口正常（回滚证据）。
12. Outbox：单测模拟 afterCommit 投递抛异常 → outbox 保留 PENDING → relay 重投成功置 SENT；8 次失败置 DEAD。
13. 调度器：单测覆盖锁未获取直接返回、批内单单异常不阻塞、重复执行幂等。
14. 性能门槛：游客查单 P95 < 300ms（本地 50 并发 k6/自写脚本）、轨迹同步批次 200 包裹 < 60s（stub provider）。

## 7. 上线与运维

- **迁移（expand/contract）**：本版本只做 expand——全部 DDL 为纯增量（新表 / 可空或带默认值的新列 / 唯一键仅作用于新表），不修改任何存量列与索引（`shipping_rate` 原样保留）。回滚 = 部署上一版本 jar，新表/新列对旧代码不可见；§6.3 第 11 条为回滚证据门槛。contract（删 `shipping_rate`、删 `Order.carrier/tracking_no` 冗余）留待下一版本。
- **灰度开关**：`dreamy.tracking.mode`（stub 缺省）、`dreamy.exchange-rate.mode`（manual 缺省）、`dreamy.stripe.mode`（生产 real）；stub 支付端点在生产不存在。
- **可观测**：Micrometer 计数器 `dreamy.payment.confirm.total`、`dreamy.webhook.mismatch.total`、`dreamy.shipment.sync.failure.total`、`dreamy.exchange.refresh.failure.total`、`dreamy.order.autocomplete.total`；关键异常沿用 `[ALERT]` 日志标记供告警抓取。
- **数据修复**：后台可手工纠正包裹状态/轨迹、回退制作阶段一档、手工覆盖汇率（manual_override）；订单主状态不提供后台任意跳转，仅矩阵内转换。
- **Runbook**：提供 `hhspec/changes/2026-09-07-order-flow-complete/runbook.md`（配置项、开关、告警含义、常见故障处置）。
