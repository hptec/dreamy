# analytics 测试设计（L2）

> 角色: l2_test_designer ｜ change: portal-api-integration ｜ domain: analytics
> 多层骨架：单元(UT) / 集成(IT，DB+缓存+GA4 防腐层) / 契约(CT) / API 端到端(AT) / 前端组件(FCT) / 韧性(RST)，统一编号 **TC-ANA-NNN**，AAA 伪代码骨架 + 数据工厂 + P0~P3。
> 覆盖来源：①field-constraint-test-matrix.yml——**本域无行**（矩阵仅含 FLD-CUSTOMERS 系列，显式声明无遗漏）；②boundary-scenarios.yml bs-048~057（analytics_dashboard 可选字段 null 容忍，§6 逐条映射）；③state-machine.yml——本域无状态机（显式声明）；④analytics-api-detail E-ANA-01~03 与域 3 码 + DEC-ANA-1~8 降级链；⑤RM-ANA 聚合 SQL 正确性（决策 3 只读例外）。
> 数据工厂：`paidOrderFactory(totalAmount, currency='USD', exchangeRate=1, paidAt, status='paid')`、`orderLineFactory(orderId, productId, qty, unitPrice, productName, img)`、`refundFactory(status, appliedAt)`、`reviewFactory(status='pending')`、`ga4RawFactory(...)`（stub 样本基线 DEC-ANA-7）。

## 1. 单元测试（聚合纯逻辑 + GA4 归一化）

| TC | 内容（AAA） | 溯源 | P |
|---|---|---|---|
| TC-ANA-001 | USD 折算口径：USD 单(rate=1, 100)+GBP 单(rate=0.78, 78)→ gmv=200.00；exchange_rate null → 按 1 折算；HALF_UP 2 位 | DEC-ANA-2 金额口径 / RM-ANA-001 | P0 |
| TC-ANA-002 | 支付口径过滤：pending/cancelled 单不计；refunding/refunded 单计入 GMV（不回刷）；paid_at 窗口边界（=monthStart 计入、<monthStart 不计） | DEC-ANA-3 | P0 |
| TC-ANA-003 | KPI 派生：order_count=0 → avg_order_value=0、refund_rate=0（除零）；refund_rate=approved 3/支付 130 → 2.3（1 位小数） | MAP-ANA-001 / DEC-ANA-3 | P0 |
| TC-ANA-004 | 趋势补零：30 天窗口仅 3 天有单 → labels/values 各 30 项、缺日 0、label 格式 `M-D`（UTC 日界） | MAP-ANA-002 / DEC-ANA-4 | P0 |
| TC-ANA-005 | category_sales share 归一：三品类 52.34/31.33/16.33 → 1 位小数且 Σ=100.0（尾差并最大项）；总额 0 → 空数组；溯根断链行落 Other 桶 | MAP-ANA-003 | P0 |
| TC-ANA-006 | top_products 组装：按 sales DESC 取 6、并列按 amount DESC；name/image_url 取快照列；img 空串 → image_url=null | MAP-ANA-004 / DEC-ANA-8 | P1 |
| TC-ANA-007 | GA4 来源归一化矩阵：google/organic→organic；(direct)/(none)→direct；instagram/social→social；newsletter/email→email；google/cpc→paid；partner-site/unknown→referral；同桶 sessions 求和 + share Σ=100 | MAP-ANA-005 / DEC-ANA-6 | P0 |
| TC-ANA-008 | device_share 归一：Mobile/desktop/TABLET 大小写归位三桶；枚举外 'smarttv' 并入 desktop；GA4 空行集 → 三桶 share=0 不报错 | MAP-ANA-006 | P1 |
| TC-ANA-009 | funnel 对位：GA4 返回乱序/缺 begin_checkout → 固定五 stage 顺序、缺位补 0、value 取整 | MAP-ANA-007 | P0 |
| TC-ANA-010 | range 解析：7d/30d/90d → 窗口 [今日−(N−1)d 00:00 UTC, now]；'30D'/'7'/''/null→缺省或 422001 异常类型（null→30d，非法→异常） | V-ANA-002 / DEC-ANA-2 | P0 |

## 2. 集成测试（DB 聚合 + 缓存 + GA4 降级链）

| TC | 内容 | 溯源 | P |
|---|---|---|---|
| TC-ANA-011 | RM-ANA-001~004 真库聚合：工厂灌 5 单（跨币种/跨状态/跨日）→ KPI 与趋势逐值断言；只读事务内多 SQL 同快照（事务中途插单不影响本次结果） | RM-ANA / TX-ANA-002 | P0 |
| TC-ANA-012 | RM-ANA-005~007 待办计数：pending refund×2 + pending review×3 + paid 单×4 → todos {2,3,4}；approved/rejected 不计 | RM-ANA-005~007 | P0 |
| TC-ANA-013 | RM-ANA-008 三层溯根：三级品类（根→子→孙）下商品的订单行归并到根品类；一级品类商品直接归根；删除商品的行落 Other 桶 | RM-ANA-008 / MAP-ANA-003 | P0 |
| TC-ANA-014 | RM-ANA-009 Top6：7 个商品有销量 → 仅 6 行、LIMIT 与排序正确；商品改名后 name 仍为下单时快照 | RM-ANA-009 | P1 |
| TC-ANA-015 | 缓存 60s：E-ANA-01 首读落库写缓存 → 60s 内二读零 DB（SQL 计数断言）→ TTL 过期后回源新值；overview key 含 range 维度（7d/30d 互不串） | CACHE-ANA-001/002 | P0 |
| TC-ANA-016 | GA4 成功链（DEC-ANA-5 ②）：stub 模式首调 → fresh(300s)+stale(24h) 双写；300s 内二调零 GA4 调用（port mock 计数） | CACHE-ANA-003/004 | P0 |
| TC-ANA-017 | stale 兜底（③）：fresh 过期 + GA4 mock 抛 unavailable → 200 source_status=ok + 数据=stale 快照 + fetched_at=快照时刻；不回写 fresh（下次仍试 GA4） | DEC-ANA-5 ③ | P0 |
| TC-ANA-018 | 彻底降级（④）：清空双缓存 + GA4 失败 → 200 source_status=unavailable + 三字段 null + fetched_at null；降级体 60s 短缓存内重复请求零 GA4 调用 | DEC-ANA-5 ④ / CACHE-ANA-005 | P0 |
| TC-ANA-019 | 兜底链失效（⑤）：GA4 失败 + Redis 读 stale 抛异常 → Ga4Unavailable→HTTP 502 `502001`；超时形态同构 → 504 `504001` | DEC-ANA-5 ⑤ | P0 |
| TC-ANA-020 | 单飞防并发：清缓存后并发 10 请求 traffic → GA4 仅 1 次往返（penetrationProtect） | SVC-ANA §5.3 | P1 |
| TC-ANA-021 | 凭证脱敏：real 模式失败日志不含 credentials 路径/内容/GA4 响应原文（日志捕获断言）；降级计数指标 ga4.degrade.* 递增 | SVC-ANA §5.4 | P1 |
| TC-ANA-022 | 聚合不锁写：开启 E-ANA-02 只读事务执行聚合期间并发 trading 下单写 orders → 写不阻塞（锁等待=0） | TX-ANA-001 | P2 |

## 3. 契约/API 端到端（AT，3 端点全覆盖）

| TC | 内容 | 溯源 | P |
|---|---|---|---|
| TC-ANA-023 | E-ANA-01：200 R 包络 + snake_case（gmv_month/pending_refund_count）；kpis/todos/gmv_trend 必备键齐全；gmv_trend 30 桶 | 契约 DashboardResponse | P0 |
| TC-ANA-024 | E-ANA-02：缺省 range=30d 与显式 30d 同响应；range=7d 趋势 7 桶；range=99d → 422 `422001` + details.allowed；top_products ≤6 | V-ANA-002 | P0 |
| TC-ANA-025 | E-ANA-03：stub 模式 200 source_status=ok 三字段结构（source/sessions/share、device 枚举、stage 枚举五值序）；unavailable 形态三字段为 null 且 source_status 必备（契约 required 仅 source_status） | 契约 AnalyticsTrafficResponse | P0 |
| TC-ANA-026 | 鉴权矩阵：无 token → 401 40100；store JWT 误用 → 401 40100；有 /dashboard 无 /analytics 的角色 → E-ANA-01 200、E-ANA-02/03 403 40300（权限点隔离） | §0 鉴权 | P0 |
| TC-ANA-027 | 只读断言：3 端点调用前后 operation_log 行数不变（无审计）；DB 全表行数不变（决策 3 例外仅 SELECT） | §0 审计/只读口径 | P1 |

## 4. 前端组件测试（FCT，Vitest + Testing Library）

| TC | 内容 | 溯源 | P |
|---|---|---|---|
| TC-ANA-028 | useDashboardStore.fetch：dashboard 与 overview 并行（allSettled）；overview 403 → donutState='forbidden' 且 KPI/待办正常渲染（互不阻塞） | STORE-ANA-01 / DEC-ANA-FE-4① | P0 |
| TC-ANA-029 | trendSlice：30 桶数据，trendWindow=14 → 尾 14 切片；切换按钮零网络请求 | DEC-ANA-FE-5 | P1 |
| TC-ANA-030 | 待办瓦片：todos 三计数映射 3 瓦片（to/tone 断言）；KPI 卡无 delta 行 | DEC-ANA-FE-2/3 | P1 |
| TC-ANA-031 | 流量降级 UI：sourceStatus=unavailable → 流量/漏斗 tab EmptyState「数据暂不可用」+ 重试按钮触发 retryTraffic；502 失败同占位 + toast | DEC-ANA-FE-8 / COMP-ANA-A08 | P0 |
| TC-ANA-032 | fetchedAt 角标：ok 形态显示「数据时间 HH:mm」；unavailable 隐藏 | DEC-ANA-FE-8 | P2 |
| TC-ANA-033 | 商品热度表：列=排名/商品/销量/销售额；imageUrl null 渲染占位图；前 3 金底徽章 | DEC-ANA-FE-7 / COMP-ANA-A10 | P1 |
| TC-ANA-034 | 漏斗渲染：maxFunnel 除零保护（全 0 数据不 NaN）；stage 中文映射五值 | STORE-ANA-02 / COMP-ANA-A09 | P1 |
| TC-ANA-035 | 导出 CSV：当前 tab 数据序列化下载（文件名含 tab 与日期），无网络请求 | COMP-ANA-A11 | P3 |

## 5. 韧性（RST）

| TC | 内容 | 溯源 | P |
|---|---|---|---|
| TC-ANA-036 | GA4 故障期交易隔离：GA4 全故障 + 双缓存空 → E-ANA-01/02 全程 200 正常（交易指标不受影响，决策 19 验收语句直译） | 降级矩阵 | P0 |
| TC-ANA-037 | DB 异常：聚合查询失败 → 500 `50001` 不暴露 SQL；缓存命中期 DB 宕机 → 命中仍 200（TTL 内可用性） | error-strategy 分层 | P1 |
| TC-ANA-038 | GA4 超时预算：mock 延迟 9s → read-timeout 8s 触发 timeout 形态走降级链（请求总耗时 < 9s，无重试放大） | SVC-ANA §5.2/5.3 | P1 |

## 6. boundary-scenarios 映射自检

bs-048~057（analytics_dashboard 十个可选字段 null 容忍）：本域为只读聚合视图（无写入路径），bs「提交 null 请求」语义落地为**出参侧 null/空容忍**——
| bs | 字段 | 落点 TC |
|---|---|---|
| bs-048~051 | gmv_month/order_count/avg_order_value/refund_rate（无数据→0 值渲染） | TC-ANA-003/023 |
| bs-052 | gmv_trend（无单→30 桶全 0） | TC-ANA-004 |
| bs-053 | category_sales（总额 0→空数组，前端空态） | TC-ANA-005 |
| bs-054~056 | traffic_sources/device_share/funnel（降级→null，前端占位） | TC-ANA-018/025/031 |
| bs-057 | top_products（无销量→空数组） | TC-ANA-014/033 |

覆盖自检：3/3 端点（§3）+ 域 3 码全触发（422001:TC-024、502001/504001:TC-019）+ DEC-ANA-5 降级链五分支（TC-016~019/036）+ 决策 3 只读例外验证（TC-022/027）+ bs-048~057 全映射 + 本域无状态机/无 field-matrix 行显式声明；TC-ANA-001~038 无重号。
