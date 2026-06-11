# analytics API 详细设计（L2）

> 角色: l2_api_designer ｜ change: portal-api-integration ｜ domain: analytics
> 方法论：每端点四部分 — 入参验证(V-ANA-NNN) / 业务逻辑流程(STEP-ANA-NN) / 出参构造 / 错误码映射。
> 来源权威：api-contracts/analytics-api.openapi.yml v1.1.0（3 操作）+ data-flow.md FLOW-P16 + 缓存矩阵 analytics 行 + error-strategy.md（analytics 域段 0 共 3 码 + GA4 降级矩阵）+ tech-profile.yml（ga4-data-api service-account / 5min 缓存 / 降级约束）+ er-diagram.yml（AnalyticsDashboard 聚合视图字段）+ 决策 3（只读跨域 SQL 例外）/ 10 / 19。
> 伪代码级，不绑定 Spring 语法。所有 JSON 字段 snake_case；契约错误 Schema `{code,message,details}` 在线上装入 R 包络（details → R.data）。
>
> **约束 ID 约定**：`V-ANA-NNN` 全域唯一连续编号（V-ANA-001 ~ V-ANA-003，无重号）；`STEP-ANA-NN` 在端点内编号，全局引用名为 `<operationId>.STEP-ANA-NN`，与 identity/trading 样板同风格。

## 0. 全局横切（所有端点适用）

- **R 包络**：成功 `R{code:0, message:"ok", data:<payload>}`；失败 `R{code:<6位码>, message, data:<details>}`。本域为聚合视图端点，无分页。
- **鉴权**：AdminJwtFilter + RBAC 菜单权限 key——`GET /api/admin/dashboard` → **`/dashboard`**；`GET /api/admin/analytics/*` → **`/analytics`**（BE-DIM-6 新增权限点，identity permission 字典种子已含 `/dashboard`，`/analytics` 幂等补登见 analytics-data-detail §7）。未认证 401 `40100`；缺权限 403 `40300`。本域无消费端端点、不经 CDN（管理端带鉴权数据）。
- **只读跨域 SQL 例外口径（决策 3，与 marketing/catalog 设计对齐声明）**：七域常规跨域读必须走领域服务端口；**本域是 L1 授权的唯一例外**——允许自有只读 Mapper 以 SELECT 聚合直查 trading（orders/order_line/refund）、review（review）、catalog（category）表。边界硬约束：①仅 SELECT（Mapper 接口不含任何 DML）；②不依赖他域 Java 实体（聚合结果映射到本域只读行 DTO）；③列引用以他域 data-detail DDL 为权威（列名变更由他域设计文档驱动联动，见 analytics-data-detail §1 依赖核对表）；④声明式只读事务（readOnly=true），不持有锁。
- **审计（BE-DIM-7）**：本域 3 端点全部为只读，**不写 OperationLog**（审计枚举无 analytics 条目，与 error-strategy 清单一致）。
- **金额口径（契约 Kpis 描述）**：USD 基准——各订单金额按锁汇 `exchange_rate` 折回 USD：`usd = amount / COALESCE(exchange_rate, 1)`（决策 14：rate 为 USD→订单币种汇率，USD 行恒 1）；聚合后 HALF_UP 保留 2 位。
- **时间口径（DEC-ANA-2）**：全部按 UTC——「本月」= UTC 当月 1 日 00:00:00.000 至当前；range 窗口 = `[今日(UTC)−(N−1)天 00:00, 现在]`（7d/30d/90d 含今日共 N 个日桶）；趋势 label 格式 `M-D`（如 `5-29`，与原型 gmvLabels 一致）。
- **支付口径（DEC-ANA-3）**：GMV/订单数/趋势/品类/Top 商品统一口径 = `paid_at` 落入窗口且 `status IN ('paid','shipped','completed','refunding','refunded')` 的订单（已支付即计入，后续退款不回刷 GMV——退款影响由 refund_rate 单独表达；cancelled/pending 不计）。
- **缓存（BE-DIM-8，缓存矩阵）**：`analytics:dashboard`（TTL 60s）/ `analytics:overview:{range}`（TTL 60s）/ `analytics:traffic:{range}`（TTL 300s）+ GA4 stale 快照与降级体短缓存（DEC-ANA-5）。失效触发者：TTL 自然过期（聚合数据无主动失效，决策 10/19）。key 无 locale 维度（admin 端固定中文、纯数值载荷）。
- **错误码全集（域段 0）**：422001 INVALID_RANGE / 502001 GA4_UNAVAILABLE / 504001 GA4_TIMEOUT；复用 identity：40100 / 40300 / 50000 / 50001。admin 端 message 固定中文。

### 设计决策（DEC-ANA）

| ID | 决策 | 理由 |
|---|---|---|
| DEC-ANA-1 | AnalyticsDashboard 为**虚拟聚合视图，不落库、无表、无 DDL**（er-diagram desc「不落库不可写」的显式落地）；其字段全部由本文 3 端点出参承载 | 决策 10 实时聚合不建预聚合表；TASK-009 的「数据模型」交付物 = 聚合 DTO + 只读查询，非建表 |
| DEC-ANA-2 | 时间统一 UTC、趋势 label `M-D`（见上） | 与 DB DATETIME(3) UTC 基线（CP-014）一致；label 与原型 SparkArea 视觉一致 |
| DEC-ANA-3 | 支付口径定义（见上）；refund_rate = 窗口内 `approved` 退款工单数 ÷ 窗口内支付订单数 × 100（单数比，保留 1 位小数，分母 0 → 0） | 千单量级单数比直观稳定；金额比受部分退款/汇率噪声影响大；原型展示 2.3% 即单数比量级 |
| DEC-ANA-4 | dashboard 的 `gmv_trend` 固定返回**近 30 天**按日序列（无 range 参数）；前端 14 天/30 天按钮做客户端切片（labels/values 取尾 14 或全量） | 契约 dashboard 无 range 参数；30 天序列同时满足原型两个按钮，零契约扩张 |
| DEC-ANA-5 | GA4 三级降级链（决策 19 + error-strategy 降级矩阵的确定性展开）：①fresh 缓存（300s）命中 → 200 ok；②未命中调 GA4，成功 → 200 ok + 写 fresh + 写 stale 快照（24h，remote-only）；③GA4 失败/超时 → 读 stale 快照，有 → 200 `source_status=ok` + `fetched_at=快照生成时刻`（旧数据兜底，用户无感）；④stale 亦缺失（缓存彻底缺失且拉取失败）→ 200 `source_status=unavailable` + 流量字段 null，并以 60s 短 TTL 缓存降级体（GA4 故障期不反复打 GA4）；⑤**502001/504001 仅当降级兜底链自身失效**——读 stale/构造降级体过程中缓存基础设施（Redis）再抛异常，Ga4UnavailableException→502001、Ga4TimeoutException→504001 由 GlobalExceptionHandler 兜底映射 | 同时满足契约两处表述：「常规降级走 200 + source_status=unavailable」与「仅缓存兜底亦失效时 502001/504001」；交易指标端点不受任何 GA4 故障影响 |
| DEC-ANA-6 | GA4 维度选型：traffic_sources 用 `sessionSource + sessionMedium` 双维度 + 后端归一化桶（§3 映射表）；device_share 用 `deviceCategory`；funnel 用 `eventName` 过滤五事件计 `eventCount` | 契约声明 sessionSource 维度；medium 辅助维度是区分 organic/paid/social 的必要信息；归一化后 source 取值与契约示例（organic/direct/social/referral/paid）对齐 |
| DEC-ANA-7 | GA4 客户端 stub 开关 `dreamy.ga4.mode=stub|real`（沿用 identity oidc/smtp stub 模式，tech-profile assumptions）：stub 返回确定样本（原型 mock 数值），dev/CI 联调与 UI 回归可复现 | 凭证仅生产配置；无凭证环境流量 tab 不应空白阻塞验收 |
| DEC-ANA-8 | top_products 的 name/image_url 取 `order_line` 快照列（product_name/img），不回查 catalog product 表 | 商品删除/改名不破坏看板；少一次跨域读；快照即「售出时刻事实」 |

---

## 1. E-ANA-01 getAdminDashboard — GET /api/admin/dashboard

**入参验证**：无业务入参。
- V-ANA-001 仅鉴权前置：AdminJwtFilter（40100）→ RBAC `/dashboard`（40300）。无 query/path/body。

**业务逻辑**（FLOW-P16 上半场；AnalyticsQueryService，readOnly 事务）:
- STEP-ANA-01 `JC.get("analytics:dashboard")`（CACHE-ANA-001，TTL 60s）命中 → 直接返回
- STEP-ANA-02 KPI 聚合（窗口=本月 UTC，DEC-ANA-2/3 口径）：
  - `gmv_month` ← RM-ANA-001 `sumPaidGmvUsd(monthStart, now)`（SUM(total_amount/COALESCE(exchange_rate,1))，HALF_UP 2 位）
  - `order_count` ← RM-ANA-002 `countPaidOrders(monthStart, now)`
  - `avg_order_value` ← 派生 `order_count==0 ? 0 : gmv_month/order_count`（HALF_UP 2 位）
  - `refund_rate` ← RM-ANA-003 `countApprovedRefunds(monthStart, now)` ÷ order_count × 100（DEC-ANA-3，1 位小数，分母 0 → 0）
- STEP-ANA-03 待办计数（全量现态，非窗口）：
  - `pending_refund_count` ← RM-ANA-005（refund status=pending）
  - `pending_review_count` ← RM-ANA-006（review status=pending）
  - `unshipped_order_count` ← RM-ANA-007（orders status=paid）
- STEP-ANA-04 `gmv_trend` ← RM-ANA-004 `gmvTrendDaily(now−29d, now)` 按日 GROUP BY → MAP-ANA-002 补零齐 30 桶（DEC-ANA-4），labels `M-D`
- STEP-ANA-05 组装 DashboardResponse → `JC.put("analytics:dashboard", TTL 60s)` → 返回

**出参**: 200 `DashboardResponse{ kpis{gmv_month, order_count, avg_order_value, refund_rate}, todos{pending_refund_count, pending_review_count, unshipped_order_count}, gmv_trend{labels[30], values[30]} }`
**错误映射**: 401 `40100` / 403 `40300` / 500 `50000`·`50001`

## 2. E-ANA-02 getAdminAnalyticsOverview — GET /api/admin/analytics/overview

**入参**: query `range?`
- V-ANA-002 range ∈ {7d, 30d, 90d}，缺省 `30d`；枚举外取值（含空串/大小写变体 `30D`）→ 422 `422001 INVALID_RANGE`（details: {field:"range", allowed:["7d","30d","90d"]}）

**业务逻辑**（FLOW-P16；决策 3 只读跨域 SQL 例外 + 决策 10 实时聚合）:
- STEP-ANA-01 `JC.get("analytics:overview:{range}")`（CACHE-ANA-002，TTL 60s）命中 → 返回
- STEP-ANA-02 `kpis` ← 复用 getAdminDashboard.STEP-ANA-02 同口径（**固定本月，与 range 无关**——契约「KPI 同 dashboard 口径」）
- STEP-ANA-03 `gmv_trend` ← RM-ANA-004 按 range 窗口（7/30/90 日桶，补零，labels `M-D`）
- STEP-ANA-04 `category_sales` ← RM-ANA-008（窗口内支付订单 order_line × catalog category 三层溯根聚合，金额折 USD）→ MAP-ANA-003：share = amount/总额×100（1 位小数，尾差并入最大项使 Σ=100；窗口无单 → 空数组）；按 amount DESC
- STEP-ANA-05 `top_products` ← RM-ANA-009（窗口内按 SUM(qty) DESC 取前 6；name/image_url 取 order_line 快照，DEC-ANA-8）→ MAP-ANA-004
- STEP-ANA-06 组装 AnalyticsOverviewResponse → `JC.put(TTL 60s)` → 返回

**出参**: 200 `AnalyticsOverviewResponse{ kpis, gmv_trend, category_sales[{category_id, category_name, amount, share}], top_products[≤6:{product_id, name, image_url, sales, amount}] }`
**错误映射**: 401 / 403 / 422 `422001` / 500 `50000`·`50001`

## 3. E-ANA-03 getAdminAnalyticsTraffic — GET /api/admin/analytics/traffic

**入参**: query `range?`
- V-ANA-003 同 V-ANA-002（range 枚举，缺省 30d）→ 422 `422001`

**业务逻辑**（FLOW-P16 下半场；决策 19 + DEC-ANA-5 三级降级链；SVC-ANA-02 GA4 客户端见 analytics-data-detail §5）:
- STEP-ANA-01 `JC.get("analytics:traffic:{range}")`（CACHE-ANA-003，TTL 300s）命中 → 返回（fetched_at=缓存生成时刻，契约语义）
- STEP-ANA-02 未命中 → `Ga4TrafficPort.fetch(range)`：单次 `batchRunReports`（property=配置 GA4_PROPERTY_ID）并行三报表——
  - ①sessions by [sessionSource, sessionMedium]（→ traffic_sources）
  - ②sessions by [deviceCategory]（→ device_share）
  - ③eventCount by [eventName] filter IN (page_view, view_item, add_to_cart, begin_checkout, purchase)（→ funnel）
  - dateRange：`{range}` → startDate=`(N−1)daysAgo`、endDate=`today`；超时预算：connect 2s + read 8s，不重试（读路径由缓存兜底）
- STEP-ANA-03 成功 → 归一化组装（MAP-ANA-005~007）：
  - traffic_sources：medium ∈ {cpc,ppc,paid*} → `paid`；medium=organic → `organic`；medium ∈ {social, sm} 或 source ∈ {instagram,facebook,pinterest,tiktok,twitter} → `social`；source=(direct) 且 medium ∈ {(none),(not set)} → `direct`；medium=email → `email`；其余 → `referral`；同桶 sessions 求和，share=桶/总×100（1 位小数，尾差并最大桶），按 sessions DESC
  - device_share：deviceCategory → 枚举 {mobile, desktop, tablet}（其他值并入 desktop），share 同法
  - funnel：固定顺序 [page_view, view_item, add_to_cart, begin_checkout, purchase]，缺事件补 value=0
  - 出参 `source_status=ok, fetched_at=now`；写 `analytics:traffic:{range}`（300s）+ stale 快照 `analytics:traffic:stale:{range}`（24h，CACHE-ANA-004）
- STEP-ANA-04 GA4 失败/超时（DEC-ANA-5 ③）→ 读 stale 快照：存在 → 200 `source_status=ok` + 快照数据 + `fetched_at=快照生成时刻`（不回写 fresh——下次请求再试 GA4）
- STEP-ANA-05 stale 亦缺失（DEC-ANA-5 ④）→ 200 `{source_status:"unavailable", fetched_at:null, traffic_sources:null, device_share:null, funnel:null}`（前端「数据暂不可用」；交易指标端点不受影响）；降级体写 `analytics:traffic:{range}` 短 TTL 60s（CACHE-ANA-005，故障期防穿透打 GA4）
- STEP-ANA-06 降级兜底链自身异常（DEC-ANA-5 ⑤：步骤 04/05 中 Redis/序列化再抛）→ 按失败形态抛 `Ga4UnavailableException` / `Ga4TimeoutException` → GlobalExceptionHandler 映射 502 `502001` / 504 `504001`

**出参**: 200 `AnalyticsTrafficResponse{ source_status: ok|unavailable, fetched_at?, traffic_sources?[{source, sessions, share}], device_share?[{device, share}], funnel?[{stage, value}] }`
**错误映射**: 401 / 403 / 422 `422001` / 502 `502001` / 504 `504001` / 500 `50000`

---

## 4. 端点 × 权限 × 缓存 × 错误码总表（自检）

| 端点 | operationId | 权限 key | 缓存 key（TTL） | 错误码 |
|---|---|---|---|---|
| E-ANA-01 GET /api/admin/dashboard | getAdminDashboard | /dashboard | analytics:dashboard（60s） | 40100/40300/50000/50001 |
| E-ANA-02 GET /api/admin/analytics/overview | getAdminAnalyticsOverview | /analytics | analytics:overview:{range}（60s） | +422001 |
| E-ANA-03 GET /api/admin/analytics/traffic | getAdminAnalyticsTraffic | /analytics | analytics:traffic:{range}（300s）+ stale（24h） | +422001/502001/504001 |

3/3 端点四 Part 齐全；域 3 码（422001/502001/504001）全部有触发路径；V-ANA-001~003 无重号；GA4 凭证/响应原文不落日志（脱敏规则，analytics-data-detail §5.4）。
