# trading 前端详细设计（L2）

> 角色: l2_frontend_designer ｜ change: portal-api-integration ｜ domain: trading
> 两套技术栈：portal-store（Next.js 15 App Router, port 5173, EN/ES/FR, zustand + next-intl）+ portal-admin（Vue3 + Pinia + Vite + Tailwind/Headless-UI, port 5174, 中文）。
> 编号：页面路由(PAGE-TRD) / 状态管理(STORE-TRD) / 组件树(COMP-TRD) / 表单交互(FORM-TRD)。伪代码级 diff 设计——**以 /Volumes/MAC/workspace/dreamy/frontend 真实工程现有文件为基线，仅替换数据层（mock → API），不改布局结构与设计 token（原型强对照约束 1~3）**。
> 边界转换：portal-store `lib/api/case.ts` deepSnakeize/deepCamelize；portal-admin `src/api/client.ts` axios 拦截器解 R 包络 + PageResult（total_elements→totalElements）。错误一律按 code 分支（error-strategy 前端呈现约定）。

---

## A. portal-admin（Vue3 + Pinia，中文）

### A.1 页面路由（PAGE-TRD-A）

| 编号 | 路由 | 视图（既有文件，diff 改造） | 权限 key | API |
|---|---|---|---|---|
| PAGE-TRD-A01 | /orders | src/views/Orders.vue | /orders | listAdminOrders |
| PAGE-TRD-A02 | /orders/:id | src/views/OrderDetail.vue | /orders | getAdminOrder / shipAdminOrder / patchAdminOrderStatus / createAdminRefund |
| PAGE-TRD-A03 | /refunds | src/views/Refunds.vue | /refunds | listAdminRefunds / approveAdminRefund / rejectAdminRefund / patchAdminRefund |
| PAGE-TRD-A04 | /settings（汇率/结算 tab） | src/views/Settings.vue（新增 `rates` / `checkout` 两个 tab 面板，复用既有 tab + panel + data-table 风格——新增功能落点，非布局改造） | /settings | listAdminExchangeRates / updateAdminExchangeRate / getAdminCheckoutConfig / updateAdminCheckoutConfig |

路由 meta.permission 注册 `/orders`、`/refunds`、`/settings`（沿用 identity GUARD-01~04 守卫与菜单过滤）。

### A.2 API 模块（src/api/，新增三文件，沿用 client.ts 拦截器）

- `src/api/orders.ts`：`listOrders(params{page,pageSize,status,search,currency,from,to})` / `getOrder(id)` / `shipOrder(id,{carrier,trackingNo})` / `patchOrderStatus(id,{status})` / `createRefund(id,{amount,reason})`
- `src/api/refunds.ts`：`listRefunds(params)` / `approveRefund(id,{returnTrackingNo?})` / `rejectRefund(id,{reason})` / `patchRefund(id,{returnTrackingNo})`
- `src/api/tradingSettings.ts`：`listExchangeRates()` / `updateExchangeRate(currency,{rate})` / `getCheckoutConfig()` / `updateCheckoutConfig(body)`
- `src/api/types.ts` 增补：`AdminOrderListItem / AdminOrderDetail / OrderLine / PaymentSummary / AdminRefund / ExchangeRate / CheckoutConfig`（camelCase，PageResult<T> 复用）

### A.3 状态管理（STORE-TRD-A，Pinia）

- STORE-TRD-A01 `useOrdersStore`：{ list, total, page, pageSize, status, search, currency, from, to, loading, detail, fetchList(), fetchDetail(id), ship(), patchStatus(), createRefund() }；写操作成功后 `fetchDetail` 重载 + toast
- STORE-TRD-A02 `useRefundsStore`：{ list, total, page, status, search, loading, approve(id,trackingNo?), reject(id,reason), patchTracking(id,no) }；approve/reject 成功后行内刷新；502601/504601 → 弹窗「Stripe 暂不可用，工单保持待审批可重试」（error-strategy admin 呈现）
- STORE-TRD-A03 `useTradingSettingsStore`：{ rates[], checkoutConfig, fetchRates(), saveRate(currency,rate), fetchCheckoutConfig(), saveCheckoutConfig() }

### A.4 组件树（COMP-TRD-A，全部为既有视图内 diff 或复用既有组件风格）

- COMP-TRD-A01 Orders.vue 列表：删除 `@/data/mock` 引用 → ordersStore；既有 tabs（全部/待付款/待发货/已发货/已完成/退款中）增补 `cancelled 已取消` / `refunded 已退款` 两 tab（与 API status 枚举对齐，沿用 tab 样式）；搜索框绑 `search`（订单号/客户邮箱，防抖 300ms 服务端搜索）；新增筛选：币种下拉（USD/EUR/CAD/AUD/GBP）+ 时间范围（复用 field 样式）；表格列不变（订单号/客户/地区(address_snapshot.country)/商品数(lineCount)/金额(curSym+totalAmount)/支付方式/状态/下单时间/操作）；curSym 增补 `EUR:'€'`；Pagination 接 totalElements 服务端分页；loading 骨架 + EmptyState 复用
- COMP-TRD-A02 OrderDetail.vue：mock 详情 → `getAdminOrder(id)`；面板保持：客户信息 / 商品明细行（定制行展开 customSizeData 四围 + 「定制」徽章）/ 金额拆分（subtotal/运费/Gift Wrapping 行(giftWrap=true 时)/优惠/合计，决策 28）/ 支付信息（cardSummary/paymentIntentId/paidAt）/ 收货地址快照 / 状态时间线（createdAt→paidAt→shippedAt→completedAt）/ 关联退款工单列表
- COMP-TRD-A03 发货面板（OrderDetail 既有 showShip 折叠面板）：承运方 select 选项**由 API 返回的 Order.carrier 枚举三值渲染**，默认值=order.carrier（结算所选承运商沿用）；运单号 input；确认发货 → shipOrder；仅 status=paid 显示「标记发货」按钮（前端预判 + 后端 409602 兜底）
- COMP-TRD-A04 发起退款弹窗（OrderDetail「发起退款」按钮 → 复用既有弹窗风格）：金额 input（默认 totalAmount，≤totalAmount 前端预判）+ 原因 textarea；422602 → 弹提示「定制商品已投产，不可退款」（details.grace_deadline 展示）；422603 → 字段错误「超出可退上限」
- COMP-TRD-A05 完成/取消操作（OrderDetail 顶部操作区）：status=shipped → 「确认完成」；status=pending → 「取消订单」（二次确认）；status=paid 不出取消按钮（须走退款，hint 文案）
- COMP-TRD-A06 Refunds.vue 列表：mock → refundsStore；tabs（全部/待审批/已同意/已拒绝）+ 新增搜索框（工单号/订单号/邮箱，沿用 Orders 搜索样式）；行内「同意」→ 审批弹窗（选填退货物流单号 returnTrackingNo，决策 31）；「拒绝」→ 原因必填弹窗；已处理行显示 stripeRefundId / returnTrackingNo（登记入口：pending 行「登记退货单号」ghost 按钮 → patchRefund）；cur 映射补 EUR
- COMP-TRD-A07 Settings.vue 新增「汇率管理」tab：data-table 五行（币种/汇率/更新人/更新时间/操作）；USD 行只读（恒 1，编辑按钮禁用）；EUR/CAD/AUD/GBP 行内编辑 rate → 保存（422605/422601 错误回显）；提示文案「仅影响新订单锁汇」
- COMP-TRD-A08 Settings.vue 新增「结算配置」tab：礼品包装费 USD 数值输入（≥0）+ 定制退款宽限期小时数（1..168）两字段表单 + 保存按钮（field/btn-gold 复用）

### A.5 表单交互（FORM-TRD-A）

- FORM-TRD-A01 发货：carrier 必选 + trackingNo 必填非空白 ≤64 前端预校验 → shipOrder；409602 toast「当前订单状态不允许该操作」并刷新详情
- FORM-TRD-A02 代客退款：amount>0 且 ≤totalAmount、reason 必填 ≤255 → createRefund；409605 toast「该订单已有进行中的退款」
- FORM-TRD-A03 退款审批：approve（可附 returnTrackingNo）/ reject（reason 必填）均二次确认（CP-071）；409604 toast「工单已审核，不可重复操作」+ 行刷新；502601/504601 保留弹窗可重试
- FORM-TRD-A04 汇率编辑：rate>0 数值预校验；保存成功 toast + 重拉列表
- FORM-TRD-A05 结算配置：区间预校验（fee≥0、grace 1..168）→ 422 字段级 inline 回显

---

## B. portal-store（Next.js 15 App Router，EN/ES/FR）

### B.1 API 模块（lib/api/，新增，复用 client.ts 401→refresh 重放 + case.ts 转换）

- `lib/api/cart-api.ts`：getCart(locale) / addCartItem(body) / updateCartItem(id,{qty}) / removeCartItem(id) / mergeCart({anonToken,items})
- `lib/api/address-api.ts`：listAddresses / createAddress / updateAddress / deleteAddress
- `lib/api/checkout-api.ts`：quoteCheckout(body) / createOrder(body)
- `lib/api/order-api.ts`：listOrders({page,pageSize,status}) / getOrder(id) / cancelOrder(id) / retryPaymentIntent(id) / applyRefund(id,{reason})
- `lib/api/wishlist-api.ts`：listWishlist(locale) / addWishlistItem(productId) / removeWishlistItem(productId) / moveToCart(productId,body)
- `lib/api/browse-history-api.ts`：listBrowseHistory({limit,locale}) / recordBrowseHistory(productId)
- `lib/api/exchange-rate-api.ts`：listExchangeRates()（匿名公开，可静态化获取）

### B.2 状态管理（STORE-TRD-S，zustand）

- STORE-TRD-S01 `cartStore`：双态模型（决策 8）——匿名态 items 持久化 localStorage（`dreamy.cart.anon` + `dreamy.cart.anon_token` 前端生成 UUID）；登录态以服务端 CartResponse 为真值（本地仅缓存渲染）。`add/updateQty/remove`：未登录写 localStorage，已登录调 API 后回写；登录成功钩子（authStore.login 完成）→ `mergeCart(anonToken, localItems)` 一次性合并 → 清 localStorage → 以响应覆盖（mergedTruncatedItemIds 行内提示「已按库存调整数量」）；`dyeLotProductIds` 透出供购物车/结算提示条（决策 20.4）
- STORE-TRD-S02 `checkoutStore`：{ step, addressId|newAddressDraft, country, currency, carrier, couponCode, giftWrap, weddingDate, quote:CheckoutQuoteResponse|null, quoting, idempotencyKey }；`requestQuote()` 在进入 Shipping/Review 步与任一输入（carrier/coupon/giftWrap/地址/币种）变化时调用并整体替换 quote；**idempotencyKey 在进入结算流程时生成一次（crypto.randomUUID()），下单失败（5xx/网络）重试沿用，下单成功或主动离开结算后重置**（409603 → 静默跳 details.order_id 支付页）
- STORE-TRD-S03 `wishlistStore`：ids 集合 + items；add 幂等（201/200 同处理）；未登录点收藏 → 引导 /account/login?returnTo=（决策 18，不发请求）
- STORE-TRD-S04 `currencyStore`（替换硬编码汇率，决策 14）：rates 来自 GET /api/store/exchange-rates；展示换算 `display = multiCurrencyPrices?.[currency] ?? priceUsd × rate`（决策 14 客户端展示换算；下单锁汇以服务端为准）
- STORE-TRD-S05 `ordersStore`：我的订单分页列表 + 详情 + cancel/retryPayment/applyRefund 动作
- 现有 `components/store-provider.tsx` 的 cart/wishlist/recentlyViewed 上下文 → 迁移为以上 zustand store 的适配层（保持组件 props 接口不变，仅换数据源——强对照「仅动数据层」）

### B.3 页面路由（PAGE-TRD-S，基线为现有文件 diff）

| 编号 | 路由（含 /es /fr 前缀镜像，决策 27） | 文件 | 渲染 | 接入 API |
|---|---|---|---|---|
| PAGE-TRD-S01 | /cart | app/cart/page.tsx | client | getCart / updateCartItem / removeCartItem |
| PAGE-TRD-S02 | /checkout | app/checkout/page.tsx | client（登录守卫，未登录跳 login?returnTo=/checkout） | listAddresses / createAddress / quoteCheckout / createOrder / retryPaymentIntent |
| PAGE-TRD-S03 | /order-success | app/order-success/page.tsx | client | getOrder（轮询支付确认态） |
| PAGE-TRD-S04 | /account/orders | app/account/orders/page.tsx | client | listStoreOrders |
| PAGE-TRD-S05 | /account/orders/[id] | app/account/orders/[id]/page.tsx（**新增路由文件**——列表既有 `Details` 链接指向此路径，原型缺失详情页，按订单卡片同 token 风格补全） | client | getStoreOrder / cancelOrder / retryPaymentIntent / applyRefund |
| PAGE-TRD-S06 | /account/wishlist | app/account/wishlist/page.tsx | client | listWishlist / removeWishlistItem / moveToCart + listBrowseHistory（Recently Viewed 区块） |
| PAGE-TRD-S07 | /account/addresses | app/account/addresses/page.tsx | client | listAddresses / create / update / delete |
| PAGE-TRD-S08 | PDP `app/product/[slug]`（trading 切面） | 既有 PDP 文件 | client 切面 | recordBrowseHistory（登录态进入 PDP 上报）/ addWishlistItem（心形按钮）/ addCartItem（加购，双模式 422604 预判） |

### B.4 组件树（COMP-TRD-S）

- COMP-TRD-S01 购物车页：mock cart → cartStore；行结构不变（图/名/色/码/qty stepper/小计/删）；qty 超 sku.stock → 行内提示（409601 映射 next-intl 文案）+ stepper 上限 stock；下架商品行置灰「No longer available」；dye lot 提示条（dyeLotProductIds 命中行上方，sage 风格信息条，决策 20.4）
- COMP-TRD-S02 结算 Stepper（保持 Address/Shipping/Payment/Review 四步结构）：
  - **Address 步**：原静态 Field 表单 → 地址簿模式（已有地址卡片单选 + 「Add new address」内联表单=原 Field 组合，保存即 createAddress）；country 取所选地址
  - **Shipping 步**：`shippingOptions` **改为 quote.shippingOptions 渲染**（radio 结构/样式不变）——label=option.carrier、副文案=option.leadTime、右侧价格=formatPrice(option.fee)（fee=0 显 Free）；**carrier 文案以 API 返回为准（FedEx International Priority / UPS Worldwide Express / DHL Express），原型 mock 标签（FedEx International / UPS Worldwide Saver）有出入勿照抄**；默认选中 selected=true 项；gift wrapping checkbox 绑 giftWrap（费用文案 = quote.giftWrapFee 动态金额，替换硬编码 +$15）；新增 wedding date 选填字段（决策 20.6，Showroom 婚期自动带入）+ leadTimeWarning=true 时交期复核提示条；coupon code 输入（couponValid=false → couponReasonCode 映射文案，不阻断）
  - **Payment 步**：六张支付选项卡片视觉保留；PayPal 卡片置灰 + 「Coming soon」徽章（决策 25 不可选）；选中卡/Apple Pay/Google Pay/Klarna/Afterpay 任一 → 下方原手填卡号表单**替换为 Stripe Payment Element 容器**（`<Elements>` 以 createOrder 返回的 clientSecret + 订单币种初始化）；交互顺序：点击「Review Order」前仅记录 payMethod，**Place Order 时才 createOrder 取 clientSecret**（见 FORM-TRD-S03）
  - **Review 步**：金额拆分以 quote 为准（Subtotal/Shipping/Gift Wrapping/Discount/Total 行，决策 28 拆分展示）；DDU 关税说明行（静态 i18n 文案，决策 15）；Place Order 按钮金额 = quote.totalAmount
- COMP-TRD-S03 `<PaymentElementPanel>`（新组件，token 同源）：封装 stripe-js loadStripe(订单币种 locale) + PaymentElement + confirmPayment(return_url=/order-success?order_id=)；BNPL（Klarna/Afterpay）重定向流由 Payment Element 承载；失败 error.message 行内展示 + 可重试
- COMP-TRD-S04 order-success 页：按 order_id getOrder 轮询（2s ×15）等待 webhook 落账——status=paid → 成功态（订单号/金额/邮件提示）；超时仍 pending → 「Payment is being confirmed」中间态 + 查看订单链接（BNPL 异步确认场景）
- COMP-TRD-S05 我的订单列表：filter chips 改绑 API status 枚举（All/Pending/Paid/Shipped/Completed/Cancelled/Refunding/Refunded → i18n 文案；原型四 chip 标签语义映射：Processing→pending、In Production→paid、Shipped→shipped、Delivered→completed，**以 API 枚举为准扩全**）；卡片结构不变（首图叠图=firstLineImg+lineCount、金额、状态徽章、Details 链接）；服务端分页加载更多
- COMP-TRD-S06 订单详情页（新增，PAGE-TRD-S05）：状态徽章 + 时间线、行列表（定制行展示 customSizeData）、地址快照、支付摘要、金额拆分；动作区按状态渲染——pending：「Pay now」（retryPaymentIntent → PaymentElementPanel）+「Cancel order」（二次确认）；paid/shipped：「Request refund」（refundEligible=true 时；false → 置灰 + refundBlockReasonCode=422602 三语政策说明，决策 24）；refunds[] 工单状态条；410601 → 「Order expired」提示态
- COMP-TRD-S07 Wishlist 页：mock → wishlistStore；网格 ProductCard 复用；卡片悬浮「Move to bag」→ 现货弹 SKU 选择（色/码）后 moveToCart，定制款跳 PDP 定制表单（422604 预判）；Recently Viewed 区块数据源 → listBrowseHistory（匿名不调用，登录前不展示该区块）
- COMP-TRD-S08 地址簿页：列表卡片 + 默认徽章 + 新增/编辑表单（AddressUpsert 字段映射）+ 删除二次确认（404602/422601 处理）
- COMP-TRD-S09 价格展示横切：formatPrice(priceUsd, currency) 内部改读 currencyStore.rates（替换 data/ 硬编码汇率表，决策 14；视觉零变化）

### B.5 表单交互（FORM-TRD-S）

- FORM-TRD-S01 加购（PDP/快速加购）：现货未选 SKU → 前端阻断「请选择规格」（422604 预判）；定制款必填四围表单完成才可加购；409601 → 行内库存提示
- FORM-TRD-S02 结算报价：地址/承运商/币种/礼品包装/券码任一变化 → 防抖 400ms requestQuote；quote 失败 422605 → 币种切换器回退 USD + toast
- FORM-TRD-S03 下单支付主流程：Review 步点 Place Order → ① createOrder(idempotencyKey, addressId, currency, carrier, couponCode?, giftWrap, weddingDate?, paymentMethod, locale) → ② 201 取 payment.clientSecret 挂 PaymentElementPanel（同页内嵌支付面板态）→ ③ confirmPayment → 跳 /order-success?order_id=。错误分支：409603 → 静默跳 data.orderId 既有订单支付页（error-strategy）；409601 → 回购物车提示调整数量；502601/504601 → 「支付服务暂不可用」可重试（订单已建，重试走 retryPaymentIntent）；422703 → 券失效提示并去券重报价
- FORM-TRD-S04 取消订单：pending 单二次确认 → cancelOrder → 详情刷新（409602 → 状态已变提示刷新）
- FORM-TRD-S05 申请退款：reason 必填 ≤255 弹窗 → applyRefund；422602 → 政策说明弹层（graceDeadline 倒计时展示）；409605 → 「已有进行中的退款」
- FORM-TRD-S06 收藏：心形 toggle 乐观更新，失败回滚；未登录 → 跳登录（决策 18）
- FORM-TRD-S07 浏览上报：PDP mount 且已登录 → recordBrowseHistory（fire-and-forget，失败静默）

---

## C. 原型对照表（强对照约束 4：diff 级，仅动数据层）

| 原型文件（hhspec/prototype/） | 真实工程基线（frontend/） | 改造类型 |
|---|---|---|
| portal-admin/src/views/Orders.vue | portal-admin/src/views/Orders.vue | mock→API + tab/筛选补全（结构不变） |
| portal-admin/src/views/OrderDetail.vue | portal-admin/src/views/OrderDetail.vue | mock→API + 发货/退款/状态动作接线（面板结构不变） |
| portal-admin/src/views/Refunds.vue | portal-admin/src/views/Refunds.vue | mock→API + 审批弹窗/登记入口（表格结构不变） |
| portal-admin/src/views/Settings.vue | portal-admin/src/views/Settings.vue | 新增汇率/结算配置 tab（复用 tab+panel 风格，决策 14/24/28 落点） |
| app/checkout/page.tsx | portal-store/app/checkout/page.tsx | 四步结构保持；Shipping radio 改 API shipping_options（**carrier 文案以 API 为准，原型 mock 标签有出入**）；Payment 改 Payment Element；金额改 quote |
| app/cart/page.tsx | portal-store/app/cart/page.tsx | mock→API + 库存/下架/dye lot 提示 |
| app/account/orders/page.tsx | portal-store/app/account/orders/page.tsx | mock→API + 状态枚举对齐 |
| —（原型无订单详情页） | portal-store/app/account/orders/[id]/page.tsx | 新增路由（同源 token，列表卡片风格延展） |
| app/account/wishlist/page.tsx | portal-store/app/account/wishlist/page.tsx | mock→API（Wishlist + Recently Viewed 双数据源） |
| —（账户地址） | portal-store/app/account/addresses/page.tsx | mock→API |
| app/order-success | portal-store/app/order-success/page.tsx | 静态成功页 → 按 order_id 轮询确认态 |

**强对照红线**：不改 tailwind.config 设计 token；不内联硬编码色值；新增组件（PaymentElementPanel/交期提示条/dye lot 提示条/订单详情页）一律复用 eyebrow/btn-primary/field/badge 等既有类与 EmptyState/骨架风格；Headless-UI Vue 组件传 class 必须配 `as`（CP-072）。

## D. 自检

- [x] portal-admin 4 页（Orders/OrderDetail/Refunds/Settings 增量 tab）+ portal-store 8 页面切面全部映射 PAGE-TRD 编号
- [x] 37 端点全部被前端模块消费或显式标注消费方（webhook 为 server-to-server 无前端；admin exchange-rates/checkout-config 落 Settings tab）
- [x] Stripe Payment Element 集成（订单币种初始化、BNPL 重定向、confirmPayment→webhook→order-success 轮询闭环，决策 25）
- [x] checkout 三承运商 radio 以 API shipping_options 渲染，carrier 文案以 API 返回为准（F-036，原型 mock 标签勿照抄已标注）
- [x] 错误呈现遵循 error-strategy 两端约定（409603 静默跳转 / 422602 置灰+三语说明 / 409601 行内提示 / 502601 可重试）
- [x] 约束 ID（PAGE/STORE/COMP/FORM-TRD-A/S）无重号
