# analytics 前端详细设计（L2）

> 角色: l2_frontend_designer ｜ change: portal-api-integration ｜ domain: analytics
> 端：仅 portal-admin（Vue3 + Pinia + Vite + Tailwind/Headless-UI，port 5174，中文）。消费端 gtag 埋点 + Cookie consent（决策 19）为 portal-store 横切组件，归 trading/catalog 接入文档引用的公共层，不在本域展开。
> 编号：页面路由(PAGE-ANA) / 状态管理(STORE-ANA) / 组件树(COMP-ANA) / 表单交互(FORM-ANA，本域无表单——只读看板，显式声明)。伪代码级 diff 设计——**以真实工程现状为基线，仅替换数据源（mock → API），不改设计 token 与布局结构**；无契约数据源的区块按 DEC-ANA-FE 显式决策处置（原型强对照约束 1~4）。
> API 契约消费：analytics-api-detail.md E-ANA-01~03；错误处理按 error-strategy portal-admin 呈现约定（502001 GA4 → 流量卡片「数据暂不可用」占位态，交易卡片正常）。

## 0. 真实工程现状基线（设计前提，已核对 /Volumes/MAC/workspace/dreamy/frontend）

| 文件 | 现状 | 改造策略 |
|---|---|---|
| portal-admin/src/views/Dashboard.vue（162 行） | 与原型同源：KPI 4 卡（今日口径+delta）、GMV 趋势（14/30 天按钮）、品类占比 donut、待办 8 瓦片、快捷入口、商品/用户总览、最近发布；全 `@/data/mock` | KPI/趋势/待办接 E-ANA-01；donut 接 E-ANA-02；非契约区块按 DEC-ANA-FE-2/3/4 处置 |
| portal-admin/src/views/Analytics.vue（93 行） | 四 tab（销售/流量/漏斗/商品热度），全 mock | 销售/热度接 E-ANA-02；流量/漏斗接 E-ANA-03 + 降级 UI |
| portal-admin/src/api/ | client.ts（axios + R 解包 + snake↔camel + 401/403 处理）已有 | **新增 `src/api/analytics.ts`** |
| portal-admin/src/stores/ | auth/menu/toast 已有 | **新增 `src/stores/analytics.ts`** |
| portal-admin/src/router/index.ts | `/` route meta.permission=`'/'`；`/analytics` meta.permission=`'/analytics'` | `/` 路由 meta.permission 改 `'/dashboard'`（DEC-ANA-FE-1）；/analytics 不动 |
| 既有组件 | PageHeader / SparkArea / StatusBadge / EmptyState | 全部复用；降级占位复用 EmptyState 风格 |

### 前端设计决策（DEC-ANA-FE）

| ID | 决策 | 理由 |
|---|---|---|
| DEC-ANA-FE-1 | Dashboard 路由 meta.permission 由 `'/'` 改为 `'/dashboard'` | 对齐契约权限点与 identity permission 字典种子（'/dashboard' 已在种子中）；现状 `'/'` 对非超管是无字典对应的悬空 key（潜在守卫缺陷），一行 meta 修正属数据层改动 |
| DEC-ANA-FE-2 | Dashboard KPI 4 卡映射契约 kpis：label 改为「本月 GMV / 订单数 / 客单价 / 退款率」（与 Analytics 销售 tab 同口径），delta「较昨日」行移除 | 契约 Kpis 无环比字段；卡片栅格/字号/token 不变，仅文案与数值为数据驱动差异（同 marketing 决策 26 显式降级口径） |
| DEC-ANA-FE-3 | Dashboard 待办瓦片由 8 收敛为 3：待发货订单(unshipped_order_count→/orders?status=paid)、退款待审批(pending_refund_count→/refunds)、待审核评价(pending_review_count→/reviews)；其余 5 瓦片（待付款/已发货收货中/低库存/内容草稿/Banner 到期）无契约数据源，移除 | 契约 todos 仅三计数；瓦片为同构重复单元，数量为数据驱动（grid-cols 自适应不动 token） |
| DEC-ANA-FE-4 | Dashboard 非契约面板处置：①品类销售占比 donut → 数据源 E-ANA-02（range=30d）的 category_sales，无 /analytics 权限（403 40300）时卡片渲染 EmptyState「暂无权限查看」；②快捷入口=纯静态路由链接保留（移除 /site/home、/publish 两个指向被推迟 CMS 范围的入口，保留 新增商品/新建优惠券/写一篇文章）；③商品总览/用户总览、最近发布两卡片渲染 EmptyState（数据归属被推迟的站点 CMS/Publish 与运营统计范围——决策 1 备选范围，由后续独立 change 接入）；④PageHeader actions 的「发布站点」按钮移除（Publish 范围外），保留「查看完整看板」 | 看板不得展示 mock 假数（真实数据目标）；布局栅格与卡片壳保留，内容为空态（强对照约束 2 的 empty 态条款） |
| DEC-ANA-FE-5 | Dashboard GMV 趋势 14 天/30 天按钮 = 客户端切片（E-ANA-01 返回 30 桶，14 天取尾 14；DEC-ANA-4 后端约定），无二次请求 | 契约 dashboard 无 range 参数；交互保留零契约扩张 |
| DEC-ANA-FE-6 | Analytics 页 range 固定 `30d`（契约默认值），不新增 range 选择器 | 原型无该控件，强对照不加 UI；7d/90d 能力由 API 保留给后续迭代 |
| DEC-ANA-FE-7 | Analytics 商品热度表列调整：排名/商品/销量/销售额（amount）；原型 库存/状态 两列移除 | 契约 top_products 无 stock/status（快照口径无实时库存语义，DEC-ANA-8）；销售额列为契约既有字段补位 |
| DEC-ANA-FE-8 | GA4 降级 UI：`source_status==='unavailable'` 时 流量/漏斗 两 tab 的图表区渲染「数据暂不可用」占位（EmptyState 风格 + 重试按钮触发 refetch）；`fetched_at` 在流量 tab 角标显示「数据时间 HH:mm」（stale 兜底时用户可感知数据时点）；502001/504001 → 同占位 + toast | error-strategy portal-admin 行「502001 GA4 → 流量卡片数据暂不可用占位态（交易卡片正常）」的组件级落地 |

## 1. 页面路由（PAGE-ANA）

| 编号 | 路由 | 视图 | 权限 key（meta.permission） | 消费端点 |
|---|---|---|---|---|
| PAGE-ANA-A01 | / | Dashboard.vue | /dashboard（DEC-ANA-FE-1 修正） | E-ANA-01 主体 + E-ANA-02（donut，403 容忍） |
| PAGE-ANA-A02 | /analytics | Analytics.vue | /analytics | E-ANA-02（销售/热度 tab）+ E-ANA-03（流量/漏斗 tab） |

路由守卫沿用 identity GUARD-01~04。

## 2. API 模块（src/api/analytics.ts，复用 client.ts）

```
getDashboard()              GET /admin/dashboard                       -> DashboardResponse
getAnalyticsOverview(range) GET /admin/analytics/overview?range=30d    -> AnalyticsOverviewResponse
getAnalyticsTraffic(range)  GET /admin/analytics/traffic?range=30d     -> AnalyticsTrafficResponse
```

拦截器自动 camelize（gmv_month→gmvMonth / source_status→sourceStatus / fetched_at→fetchedAt）；错误统一 ApiError{code,message,details}。

## 3. 状态管理（STORE-ANA，Pinia）

- STORE-ANA-01 `useDashboardStore`：
  - state：`dashboard`（kpis/todos/gmvTrend）、`categorySales`、`loading`、`donutState: 'ok'|'forbidden'|'error'`、`trendWindow: 14|30`
  - actions：`fetch()` —— getDashboard 与 getAnalyticsOverview('30d') **并行且互不阻塞**（Promise.allSettled）：dashboard 失败 → 页面级错误态；overview 403（40300）→ donutState='forbidden'（DEC-ANA-FE-4 ①），其他失败 → donutState='error'
  - getters：`trendSlice` —— trendWindow===14 ? labels/values 尾 14 : 全量 30（DEC-ANA-FE-5）；`todoTiles` —— 三瓦片数组（count/tone/to 映射：unshipped→danger、refund→danger、review→info，tone 沿用原型映射）
- STORE-ANA-02 `useAnalyticsStore`：
  - state：`overview`、`traffic`、`loadingOverview`、`loadingTraffic`、`range:'30d'`（DEC-ANA-FE-6 常量）
  - actions：`fetchOverview()`、`fetchTraffic()`（流量/漏斗 tab 首次激活时懒加载，60s/300s 后端缓存使重复切 tab 零代价；`retryTraffic()` 供降级占位重试按钮）
  - getters：`trafficUnavailable`（sourceStatus==='unavailable' || traffic 请求 502/504 失败）、`maxFunnel`（funnel[0].value，除零保护：0→1）、`donutSegments`（categorySales→start/end 角度，复刻原型 arc 纯函数）

## 4. 组件树（COMP-ANA）

### Dashboard.vue（PAGE-ANA-A01，布局栅格零改动）

- COMP-ANA-A01 KPI 卡 ×4：label/数值映射 `kpis`（gmvMonth `$X,XXX.XX` 千分位、orderCount 千分位、avgOrderValue `$X.XX`、refundRate `X.X%`）；delta 行移除（DEC-ANA-FE-2）；loading 骨架数值条
- COMP-ANA-A02 GMV 趋势卡：SparkArea(:data=trendSlice.values, :labels=trendSlice.labels)（组件不动）；14/30 天按钮切 `trendWindow`，选中态样式沿用原型（bg-ink/border-line 互换）
- COMP-ANA-A03 品类占比卡：donutSegments 驱动 SVG path（arc 函数复刻原型）+ 图例（categoryName/share%）；色板按序取既有 token 色数组（#C19A6B/#8B9D83/#D8A7A0 + 追加 token 内灰阶，**不引入新色值**）；donutState forbidden/error → EmptyState 占位（DEC-ANA-FE-4 ①）
- COMP-ANA-A04 待办瓦片 ×3：RouterLink 结构/hover token 不变，count/tone/to 取 `todoTiles`（DEC-ANA-FE-3）
- COMP-ANA-A05 快捷入口：静态链接 3 项（DEC-ANA-FE-4 ②）
- COMP-ANA-A06 商品/用户总览卡 + 最近发布卡：卡片壳与标题保留，主体 EmptyState（DEC-ANA-FE-4 ③）
- 页面级：PageHeader subtitle 改为当前日期动态拼接（替换 mock 写死日期，数据行为）；actions 仅「查看完整看板」（DEC-ANA-FE-4 ④）

### Analytics.vue（PAGE-ANA-A02，四 tab 结构零改动）

- COMP-ANA-A07 销售 tab：KPI 4 卡（overview.kpis，环比小字行移除，同 DEC-ANA-FE-2 口径）+ GMV 趋势 SparkArea（overview.gmvTrend，30d/30 桶）+ 品类占比条形列表（categorySales：name/share% + 条宽 share%，条色同 COMP-ANA-A03 色板序）
- COMP-ANA-A08 流量 tab：访客来源条形列表（traffic.trafficSources：source 中文映射表 organic→自然搜索/direct→直接访问/social→社交/referral→外链/paid→付费/email→邮件，share% 条宽）+ 设备占比三数值（device_share 按 mobile/desktop/tablet 对位，share%）；`trafficUnavailable` → 两卡主体 EmptyState「数据暂不可用」+ 重试按钮（DEC-ANA-FE-8）；fetchedAt 角标
- COMP-ANA-A09 漏斗 tab：funnel 渲染复刻原型（宽度 value/maxFunnel、转化率小字 value/funnel[0]）；stage 中文映射（page_view→商品浏览/view_item→商品详情/add_to_cart→加入购物车/begin_checkout→进入结算/purchase→完成支付）；降级同 COMP-ANA-A08
- COMP-ANA-A10 商品热度 tab：data-table 列=排名/商品/销量/销售额（DEC-ANA-FE-7）；排名前 3 金底徽章样式保留；商品列 imageUrl 缺失渲染占位底图；空数据 EmptyState
- COMP-ANA-A11 「导出报表」按钮：保留视觉，行为=当前 tab 数据导出 CSV（前端本地序列化 store 数据，无新端点）

## 5. 表单交互（FORM-ANA）

**本域为只读看板，无表单、无写操作——FORM-ANA 显式声明为空集**（四 Part 完整性声明）。唯一输入交互为 tab 切换、趋势窗口切换与降级重试按钮，已在 COMP/STORE 段覆盖。

## 6. 原型对照表（强对照自检）

| 原型元素 | 本设计处置 | 偏离声明 |
|---|---|---|
| Dashboard KPI 4 卡（今日口径 + delta） | 本月口径 4 指标，delta 移除 | DEC-ANA-FE-2 |
| Dashboard GMV 趋势 + 14/30 按钮 | E-ANA-01 30 桶 + 客户端切片 | DEC-ANA-FE-5（交互保留） |
| Dashboard 品类占比 donut | E-ANA-02 category_sales；403 空态 | DEC-ANA-FE-4 ① |
| Dashboard 待办 8 瓦片 | 3 瓦片（契约 todos） | DEC-ANA-FE-3 |
| Dashboard 快捷入口 5 项 | 3 项（剔除 CMS 范围入口） | DEC-ANA-FE-4 ② |
| Dashboard 商品/用户总览、最近发布 | 卡片壳保留 + EmptyState | DEC-ANA-FE-4 ③ |
| PageHeader「发布站点」btn-gold | 移除（Publish 范围外）；「查看完整看板」保留 | DEC-ANA-FE-4 ④ |
| Analytics 四 tab 结构/下划线高亮 | 原样保留 | 无 |
| Analytics 销售 tab KPI/趋势/品类条形 | E-ANA-02 数据源 | 环比小字移除（DEC-ANA-FE-2） |
| Analytics 流量/漏斗 tab | E-ANA-03 + 降级占位 + fetchedAt 角标 | DEC-ANA-FE-8（占位为新增 empty 态，复用 EmptyState 风格） |
| Analytics 商品热度表（库存/状态列） | 列改 销量/销售额 | DEC-ANA-FE-7 |
| 「导出报表」btn-outline | 保留，前端 CSV | 无 |
| token（panel/eyebrow/SparkArea/data-table/text-ok 等） | 全部复用，零新增样式 | 无 |

## 7. 错误呈现汇总（error-strategy portal-admin 约定落点）

| code/状态 | 呈现 |
|---|---|
| 422001 | 防御性兜底 toast（前端 range 为常量 30d，正常不触发） |
| 502001 / 504001 | 流量/漏斗图表区「数据暂不可用」占位 + toast「流量数据服务不可用/超时」；交易卡片不受影响 |
| 200 + source_status=unavailable | 同占位（无 toast——常规降级静默，DEC-ANA-FE-8） |
| 403 `40300`（donut 跨权限读） | donut 卡 EmptyState「暂无权限查看」，页面其余正常 |
| 401 `40100` | client.ts 拦截器清 token 跳 /login（既有） |
| 5xx / 50001 | 页面级错误态 + 重试按钮（复用既有错误态风格） |
