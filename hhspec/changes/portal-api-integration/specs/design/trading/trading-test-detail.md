# trading 测试设计（L2）

> 角色: l2_test_designer ｜ change: portal-api-integration ｜ domain: trading
> 多层测试骨架：单元(UT) / 集成(IT，DB+事务+并发) / 契约(CT) / API 端到端(AT) / 异步集成(MQ) / 韧性(RST) / 网络边界(NBT) / 前端组件(FCT) + 测试数据工厂。统一编号 **TC-TRD-NNN**（全域唯一无重号），层级缩写入标题。P0-P3 优先级。
> 来源：trading-api-detail.md（V-TRD/STEP-TRD）+ trading-data-detail.md（TX/EVT/SCHED/IDX-TRD）+ error-strategy.md trading 19 码 + state-machine.yml + boundary-scenarios.yml（订单/退款/支付 guard 场景）+ field-constraint-test-matrix.yml。

## 1. 优先级总览

- **P0**：下单原子事务/幂等/并发 CAS、webhook 验签+重放+金额核对、超时取消回补、退款审核原子+Stripe 回滚、状态机 guard、user_id 隔离防探测、双 JWT/白名单、CORS
- **P1**：购物车合并幂等、多承运商报价、锁汇/礼品包装快照、券核销回滚、MQ 事件与死信、迟到支付补偿、退款宽限期边界
- **P2**：收藏/浏览历史幂等与滚动清理、地址默认切换、汇率缓存失效链、processed_event 清理
- **P3**：列表分页/筛选/空态、展示派生字段、i18n 文案映射

## 2. 单元测试（UT，领域不变量与纯函数）

- TC-TRD-001 [UT] 金额恒等式：total = subtotal + shipping_fee + gift_wrap_fee − discount（多币种 HALF_UP 2 位，CV-TRD-003）【P0】
- TC-TRD-002 [UT] 币种换算：multi_currency_prices 覆盖价优先，否则 USD×rate；Stripe 金额 = total×100 取整（决策 14）【P0】
- TC-TRD-003 [UT] 双模式 guard（V-TRD-004）：现货缺 sku_id → 422604；定制缺 custom_size_data → 422604；定制四围缺一 → 422601【P0】
- TC-TRD-004 [UT] 定制退款宽限判定（决策 24）：paid_at+grace_hours 边界——now=deadline 可退、now=deadline+1s → 422602；未支付订单定制行可退；grace_hours 取 CheckoutConfig 实时值【P0】
- TC-TRD-005 [UT] 行级 refundable 派生（getStoreOrder.STEP-TRD-03/04）：含已投产定制行 → refund_eligible=false + reason_code=422602【P1】
- TC-TRD-006 [UT] 订单状态机 guard 矩阵（order_lifecycle 9 转换 + 全部非法转换拒绝 409602，TASK-038）【P0】
- TC-TRD-007 [UT] 支付状态机（payment_lifecycle 5 转换 + succeeded 收 failed 等非法事件拒绝，TASK-039）【P0】
- TC-TRD-008 [UT] 退款状态机（refund_lifecycle：pending→approved/rejected；非 pending 审核 → 409604，TASK-041）【P0】
- TC-TRD-009 [UT] 运费分区映射 + 规则行选择：「分区 / 承运商」行命中；分区缺承运商行回退无后缀兜底行；subtotal< / =threshold 取 fee_under/fee_over；fee_over=0 包邮（shipping-api 规则 1~3）【P1】
- TC-TRD-010 [UT] shipping_options 组装：仅 enabled 承运商出现；selected 规则（请求 carrier 命中 / 未传取最低价；disabled 承运商请求回退最低价）【P1】
- TC-TRD-011 [UT] 交期复核：today+max_lead_time > wedding_date → lead_time_warning=true；无 wedding_date 不告警（决策 20.6）【P2】
- TC-TRD-012 [UT] order_no/refund_no 生成模式断言 `^DRM-\d{8}-\d{4}$` / `^RFD-\d{8}-\d{4}$`；当日序号递增；uk 冲突重试 ×3【P1】
- TC-TRD-013 [UT] 退款金额上限：amount > total_amount（含 gift_wrap_fee）→ 422603；= 上限通过（决策 28）【P0】
- TC-TRD-014 [UT] 汇率守卫：USD 不可改 422605；rate≤0 → 422601；结算配置区间 fee≥0 / grace 1..168（V-TRD-058~061）【P1】

## 3. 集成测试（IT，DB+事务+并发，关键场景）

- TC-TRD-020 [IT] **下单原子事务全量提交**（TX-TRD-001）：orders+order_line+SKU 扣减+券核销+清车 一次成功，库存/used_count/购物车终态正确【P0】
- TC-TRD-021 [IT] **幂等键重复**：同 idempotency_key 二次提交 → 409603 + details.order_id=首单；DB 仅一单（IDX-TRD-001）【P0】
- TC-TRD-022 [IT] **幂等并发竞态**：同 key 并发 2 线程同时提交 → 恰一单创建，另一线程唯一索引冲突回滚返回 409603【P0】
- TC-TRD-023 [IT] **并发 CAS 扣库存**：stock=1，两单并发购同 SKU → 恰一单成功；失败单整体回滚（订单/行/券/购物车无残留）409601【P0】
- TC-TRD-024 [IT] CAS 重试：扣减遭遇 version 变更（非缺货）→ 重读重试 ≤3 次后成功【P1】
- TC-TRD-025 [IT] 券核销失败回滚：used_count=total_limit → 422703，订单与库存全回滚【P1】
- TC-TRD-026 [IT] 定制行不扣库存（决策 6）：纯定制单下单后任何 SKU stock 不变【P1】
- TC-TRD-027 [IT] 锁汇快照：下单后改汇率 → 订单 exchange_rate/金额不变；新订单用新汇率（决策 14）【P1】
- TC-TRD-028 [IT] 礼品包装快照：下单后改 gift_wrap_fee_usd → 既有订单 gift_wrap_fee 不变（决策 28）【P2】
- TC-TRD-029 [IT] **webhook 幂等重放**：同 event_id 投递 3 次 → 业务变更恰一次，2/3 次 200 空操作（uk_event_id，TX-TRD-002）【P0】
- TC-TRD-030 [IT] webhook 金额/币种不符：amount 差 1 分 → 订单不变更、processed_event 回滚、告警记录（可重投复核）【P0】
- TC-TRD-031 [IT] webhook 与消费端取消竞态：cancel 先提交 → succeeded 到达不复活订单，触发自动退款补偿 + 告警（TX-TRD-010）【P0】
- TC-TRD-032 [IT] **超时取消回补**（SCHED-TRD-001/TX-TRD-005）：过期 pending 单扫描 → cancelled + 现货回补 + 券回滚；未过期/非 pending 不动；单单事务一单失败不阻塞批次【P0】
- TC-TRD-033 [IT] **迟到支付补偿闭环**：订单超时取消后 succeeded 事件到达 → 订单保持 cancelled，Stripe Refund 调用发出（stub 断言），processed_event 落表【P1】
- TC-TRD-034 [IT] **退款审核原子 + Stripe 失败回滚**（TX-TRD-003）：StripePort.createRefund 抛错 → refund 仍 pending、订单仍 refunding、库存未回补、无 stripe_refund_id（整体回滚，502601）【P0】
- TC-TRD-035 [IT] 退款审核成功链：approved + stripe_refund_id + 订单 refunded + 现货回补 + payment refunded + operation_log + MQ refund.resolved【P0】
- TC-TRD-036 [IT] 并发双审：两管理员同时 approve → 恰一次生效，另一次 409604（RM-TRD-054 条件更新）【P0】
- TC-TRD-037 [IT] 退款拒绝还原：refunding→paid（未发货）/→shipped（已发货按 shipped_at），reject_reason 落库【P1】
- TC-TRD-038 [IT] 购物车合并幂等：同 anon_token 二次 merge → 数量不重复累加（uk_merge_customer_token，TX-TRD-007）；超库存条目截断并返回 merged_truncated_item_ids【P1】
- TC-TRD-039 [IT] 收藏幂等：重复 add → 200 且仅一行；移入购物车单事务（加车+删收藏同滚同成，TX-TRD-006）【P2】
- TC-TRD-040 [IT] 浏览历史 upsert + 滚动：重复浏览仅更新 viewed_at；第 51 个商品写入后最旧行被清（RM-TRD-070/072）【P2】
- TC-TRD-041 [IT] 默认地址不变量：连续两地址设默认 → 恒一行 is_default=1（TX-TRD-008）；删除地址不影响既有订单 address_snapshot【P2】
- TC-TRD-042 [IT] processed_event 90 天清理：cutoff 前行删除、after 保留（SCHED-TRD-002）【P2】
- TC-TRD-043 [IT] 汇率缓存失效：updateAdminExchangeRate 后 store 端点读到新值（@CacheInvalidate 生效，TTL 内不脏读）【P2】
- TC-TRD-044 [IT] user_id 隔离防探测：用户 B 访问 A 的订单/地址/购物车条目/工单 → 一律 404（404601/404602/404603/404605），响应体不泄露存在性【P0】

## 4. 契约测试（CT）

- TC-TRD-050 [CT] 37 操作请求/响应 schema 对齐 trading-api.openapi.yml v1.1.0（含 Paginated 六字段形状、ShippingOption 必填 carrier/fee/selected）【P0】
- TC-TRD-051 [CT] 错误响应 {code,message,details} 结构 + 19 个 trading 码 code↔HTTP 高 3 位一致；R 包络 details→data 装载【P0】
- TC-TRD-052 [CT] 枚举一致性：status/currency/carrier/payment_method 取值与契约逐字相等（含承运商三值全称）【P1】

## 5. API 端到端（AT，HTTP 全链路）

- TC-TRD-060 [AT] 购物全链路 happy path：加购→改量→quote（三承运商选项断言）→createOrder→（stub webhook succeeded）→订单 paid→admin 发货→completed（TASK-051/053）【P0】
- TC-TRD-061 [AT] 退款全链路：申请（订单 refunding）→admin approve（stub Stripe）→refunded + 邮件事件（TASK-054）【P0】
- TC-TRD-062 [AT] 19 个 trading 错误码逐一触达断言（404601~605/409601~605/410601/422601~605/401601/502601/504601 各至少一条路径，MUST_TEST）【P0】
- TC-TRD-063 [AT] retryOrderPayment 矩阵：pending 未超时 → 200 client_secret；已超时 → 410601；已支付 → 409602；PI canceled → 重建新 PI【P1】
- TC-TRD-064 [AT] webhook 安全：无签名/坏签名 → 401601 且无任何 DB 写入（含 processed_event）；正确签名未识别 type → 200 received 仅落 processed_event【P0】
- TC-TRD-065 [AT] quote 矩阵：未传 carrier 默认最低价 selected；gift_wrap/coupon/币种切换金额联动；空车 422601；无效券 200+coupon_valid=false 不阻断【P1】
- TC-TRD-066 [AT] 鉴权矩阵：store token 访问 /api/admin/orders → 401 40100；admin 无 /refunds 权限 → 403 40300；匿名访问 /api/store/cart → 401；匿名访问 /api/store/exchange-rates → 200（白名单）【P0】
- TC-TRD-067 [AT] 分页/筛选：admin orders status+currency+时间窗+邮箱搜索组合；refunds 工单号/订单号搜索；store orders status tab（Paginated 字段断言）【P3】
- TC-TRD-068 [AT] Wishlist/BrowseHistory 端点四件套 + limit 边界（51 → 422601；50 → 200）【P2】

## 6. 异步集成（MQ）

- TC-TRD-070 [MQ] order.paid 扇出：q.mail 收 order_confirmed（MailRecord 幂等键 orderId+type）、q.showroom 推进 ordered、q.catalog.sales 销量回写（消费各自幂等，重复 event_id 不重复入账）【P1】
- TC-TRD-071 [MQ] order.shipped / refund.resolved 邮件事件 payload 完备（locale/carrier/tracking_no/reject_reason）【P1】
- TC-TRD-072 [MQ] 消费失败重试链：抛错 → retry 队列指数退避 ×3 → dreamy.dlq 死信 + 告警（EVT 可靠性参数）【P1】
- TC-TRD-073 [MQ] publish 失败不回滚：MQ broker 不可达时下单/发货事务仍提交，告警日志记录（降级矩阵）【P1】

## 7. 韧性测试（RST，外部依赖降级，BE-DIM-5）

- TC-TRD-080 [RST] Stripe createPaymentIntent 失败/超时 → 502601/504601；订单保持 pending；随后 retryOrderPayment 成功恢复【P0】
- TC-TRD-081 [RST] Stripe Refund 超时 → 504601 回滚；重审通过 → 成功；若 Stripe 实际已退（超时假失败）→ charge.refunded 对账告警不双退【P1】
- TC-TRD-082 [RST] cancelPaymentIntent 失败仅告警不阻断取消流（webhook guard 兜底）【P1】
- TC-TRD-083 [RST] Redis 序号发生器不可用 → 下单降级路径（uk_order_no 兜底重试或明确 50000，不产生重号订单）【P2】

## 8. 网络边界（NBT，强制 — 5173/5174 ↔ backend 8080）

- TC-TRD-090 [NBT] CORS Preflight：OPTIONS /api/store/cart（5173 origin）与 /api/admin/orders（5174 origin）返回正确 ACAO/Methods/Headers【P0】
- TC-TRD-091 [NBT] 跨域实际请求：双端带 Authorization + Accept-Language 成功；非白名单 origin 被拒（无 ACAO）【P0】
- TC-TRD-092 [NBT] webhook 直连：无 Origin 的 server-to-server POST 不受 CORS 影响；仅 POST+JSON 被接受【P1】
- TC-TRD-093 [NBT] 公开白名单边界：/api/store/exchange-rates 与 /api/store/payments/stripe/webhook 免 JWT；白名单外 /api/store/** 一律 401【P0】

## 9. 前端组件（FCT，逻辑断言，与 ui-test-spec 视觉互补）

- TC-TRD-100 [FCT] cartStore 双态：匿名 localStorage 读写；登录触发 mergeCart 恰一次（anon_token 复用）；截断提示渲染【P1】
- TC-TRD-101 [FCT] checkoutStore：输入变化触发 requestQuote 防抖；idempotencyKey 失败重试复用、成功后重置；409603 静默跳转既有订单【P0】
- TC-TRD-102 [FCT] Shipping 步 radio 渲染 quote.shippingOptions（API 文案直出，无硬编码标签）；selected 默认选中；giftWrap 联动 total【P1】
- TC-TRD-103 [FCT] PaymentElementPanel：clientSecret 初始化、confirmPayment 成功跳 order-success、失败行内可重试；PayPal 卡片禁用【P1】
- TC-TRD-104 [FCT] 订单详情动作渲染矩阵：pending=Pay now+Cancel；refund_eligible=false → 入口置灰 + 422602 文案 key【P1】
- TC-TRD-105 [FCT] admin ordersStore/refundsStore：409604/409602 toast 并刷新；502601 保留可重试弹窗【P2】

## 10. 测试数据工厂（FACTORY）

- F-Customer（identity user 复用 helper）/ F-Address(default/non-default)
- F-Product+Sku(spot stock=N, version) / F-ProductCustom(custom_size_available, lead_time_days) —— catalog 域工厂复用
- F-CartItem(spot/custom) / F-Order(七态 × 币种 × gift_wrap × wedding_date 变体，expires_at 可注入) / F-OrderLine(spot/custom)
- F-Payment(五态, payment_intent_id) / F-Refund(三态, return_tracking_no)
- F-WishlistItem / F-BrowseHistory(50 条边界集) / F-ExchangeRate(五币种种子) / F-CheckoutConfig(默认+越界变体)
- F-StripeEvent(succeeded/payment_failed/charge.refunded/unknown_type, 签名生成 helper + 坏签名变体)
- F-Tokens：store/admin 双密钥签发 + 无权限 admin 变体

## 11. 覆盖矩阵自检

| 关键场景（任务要求） | 用例 |
|---|---|
| 幂等（下单/合并/收藏/webhook） | TC-TRD-021/022/029/038/039 |
| 并发 CAS | TC-TRD-022/023/024/036 |
| webhook 重放/验签/金额核对 | TC-TRD-029/030/064 |
| 超时取消回补 | TC-TRD-032 |
| 迟到支付补偿 | TC-TRD-031/033 |
| 退款审核原子 + Stripe 回滚 | TC-TRD-034/035/081 |
| 定制退款宽限 | TC-TRD-004/005/062 |
| 多承运商报价 | TC-TRD-009/010/065/102 |
| 锁汇/快照 | TC-TRD-027/028 |
| 隔离/鉴权/白名单 | TC-TRD-044/066/093 |

- [x] TC-TRD-001 ~ TC-TRD-105 全域唯一无重号（编号留段：UT 001-019 / IT 020-049 / CT 050-059 / AT 060-069 / MQ 070-079 / RST 080-089 / NBT 090-099 / FCT 100-109）
- [x] 19 个 trading 错误码全部纳入 AT MUST_TEST（TC-TRD-062）
- [x] 每用例可追溯 V-TRD/STEP-TRD/TX-TRD/EVT-TRD/SCHED-TRD 或决策编号
- [x] P0-P3 排序覆盖 TASK-038/039/041/051/052/053/054/057 验收条目
