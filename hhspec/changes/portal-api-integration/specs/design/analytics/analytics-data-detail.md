# analytics 数据层详细设计（L2）

> 角色: l2_data_designer ｜ change: portal-api-integration ｜ domain: analytics
> 方法论：实体处置 / 只读跨域聚合查询(RM-ANA) / 聚合行↔DTO 映射(MAP-ANA) / 依赖索引核对(IDX-ANA) / 事务口径(TX-ANA) / 缓存设计(CACHE-ANA) / GA4 客户端(SVC-ANA)。
> 来源权威：er-diagram.yml（AnalyticsDashboard 聚合只读视图）+ analytics-api.openapi.yml v1.1.0 + analytics-api-detail.md（E-ANA-01~03 / DEC-ANA-1~8）+ data-flow.md FLOW-P16 + tech-profile.yml integrations.analytics + 决策 3/10/19。
> **包结构**：`com.dreamy.analytics/`（单模块多 domain，与 identity 平级）：`domain/dashboard/{service,readmodel}` + `infra/ga4/`（Ga4TrafficPort/Ga4Client/Ga4StubClient）+ `repository/`（只读 Mapper）+ `controller/` + `dto/` + `config/`。无 entity 包、无 mq 包、无定时任务。

## 0. 实体处置（DEC-ANA-1 显式声明）

**AnalyticsDashboard 为虚拟聚合只读视图：不建表、无 Entity、无 DDL、无迁移。**

- er-diagram desc 原文「数据看板聚合只读视图（不落库不可写）」的 L2 落地 = 本文只读查询（§1）+ DTO（§2）+ 缓存（§4）+ GA4 客户端（§5）。
- TASK-009（entity:AnalyticsDashboard 数据模型）的交付物据此重定义为：`readmodel/` 只读行 DTO + `dto/` 响应 DTO + RM-ANA 查询集（task-allocation.yml 同步声明，防「建表」误读）。
- er 字段 → 端点承载映射：gmv_month/order_count/avg_order_value/refund_rate/gmv_trend → E-ANA-01/02 kpis+gmv_trend；category_sales/top_products → E-ANA-02；traffic_sources/device_share/funnel → E-ANA-03（GA4 源）。
- 本域无自有表 ⇒ 无种子数据；看板数值由 trading/review 域种子订单/评价自然聚合产生（决策 21 订单类样例 dev/staging 灌入）。

## 1. 只读跨域聚合查询（RM-ANA，决策 3 例外口径）

> 落点 `com.dreamy.analytics.repository.AnalyticsReadMapper`（MyBatis XML/注解 SQL，**仅 SELECT**）。结果映射到本域 readmodel 行 DTO，不依赖他域 Java 实体。列引用权威：trading-data-detail（orders/order_line/refund）/ review 域设计（review）/ catalog-data-detail（category）。

### 1.1 KPI 与趋势（源表 orders / refund，trading 域）

- RM-ANA-001 `sumPaidGmvUsd(from, to) -> BigDecimal`
  ```sql
  SELECT COALESCE(SUM(o.total_amount / COALESCE(o.exchange_rate, 1)), 0)
  FROM orders o
  WHERE o.paid_at >= :from AND o.paid_at < :to
    AND o.status IN ('paid','shipped','completed','refunding','refunded')   -- DEC-ANA-3 支付口径
  ```
- RM-ANA-002 `countPaidOrders(from, to) -> long` —— 同 WHERE，COUNT(*)
- RM-ANA-003 `countApprovedRefunds(from, to) -> long`
  ```sql
  SELECT COUNT(*) FROM refund r WHERE r.status='approved' AND r.applied_at >= :from AND r.applied_at < :to
  ```
- RM-ANA-004 `gmvTrendDaily(from, to) -> List<DailyGmvRow{day, gmv_usd}>`
  ```sql
  SELECT DATE(o.paid_at) AS day, SUM(o.total_amount / COALESCE(o.exchange_rate, 1)) AS gmv_usd
  FROM orders o
  WHERE o.paid_at >= :from AND o.paid_at < :to AND o.status IN (…支付口径…)
  GROUP BY DATE(o.paid_at) ORDER BY day ASC
  ```
  （缺日补零在 MAP-ANA-002 完成，SQL 不造日历表）

### 1.2 待办计数（源表 refund / review / orders）

- RM-ANA-005 `countPendingRefunds() -> long` —— `WHERE status='pending'`（idx_refund_order_status 前缀可用）
- RM-ANA-006 `countPendingReviews() -> long` —— `WHERE status='pending'`（review 域 idx_review_status）
- RM-ANA-007 `countUnshippedOrders() -> long` —— `WHERE status='paid'`（trading idx_order_status_created 前缀）

### 1.3 品类占比与 Top 商品（源表 order_line × orders × category）

- RM-ANA-008 `categorySales(from, to) -> List<CategorySalesRow{root_category_id, root_category_name, amount_usd}>`
  ```sql
  SELECT root.id AS root_category_id, root.name AS root_category_name,
         SUM(ol.unit_price * ol.qty / COALESCE(o.exchange_rate, 1)) AS amount_usd
  FROM order_line ol
  JOIN orders o   ON o.id = ol.order_id
                 AND o.paid_at >= :from AND o.paid_at < :to AND o.status IN (…支付口径…)
  JOIN product p  ON p.id = ol.product_id                       -- catalog 只读：取归属品类
  JOIN category c1 ON c1.id = p.category_id
  LEFT JOIN category c2 ON c2.id = c1.parent_id                 -- 三层树溯根（level ≤ 3，er Category）
  LEFT JOIN category c3 ON c3.id = c2.parent_id
  JOIN category root ON root.id = COALESCE(c3.id, c2.id, c1.id)
  GROUP BY root.id, root.name ORDER BY amount_usd DESC
  ```
  （商品已删除的行 `JOIN product` 不命中 → 归并入 `category_id=0, category_name='Other'` 不可行——改用 LEFT JOIN product/category 链 + root 缺失行落 `Other` 桶，见 MAP-ANA-003 兜底规则）
- RM-ANA-009 `topProducts(from, to, limit=6) -> List<TopProductRow{product_id, product_name, img, sales, amount_usd}>`
  ```sql
  SELECT ol.product_id,
         MAX(ol.product_name) AS product_name,        -- 快照列（DEC-ANA-8，不回查 product）
         MAX(ol.img)          AS img,
         SUM(ol.qty)          AS sales,
         SUM(ol.unit_price * ol.qty / COALESCE(o.exchange_rate, 1)) AS amount_usd
  FROM order_line ol
  JOIN orders o ON o.id = ol.order_id AND o.paid_at >= :from AND o.paid_at < :to AND o.status IN (…支付口径…)
  GROUP BY ol.product_id ORDER BY sales DESC, amount_usd DESC LIMIT :limit
  ```

## 2. 聚合行 ↔ DTO 映射（MAP-ANA）

- MAP-ANA-001 KPI 组装：gmv_month HALF_UP 2 位；avg_order_value 派生（分母 0→0.00）；refund_rate 1 位小数（DEC-ANA-3）；全部 JSON number
- MAP-ANA-002 趋势补零：以窗口日历（UTC，N 桶）左连接 RM-ANA-004 结果，缺日 value=0；labels `M-D` 格式（DEC-ANA-2）；labels.length === values.length === N
- MAP-ANA-003 category_sales：share = amount/Σamount×100（1 位小数，四舍五入尾差并入最大项使 Σ=100.0）；Σamount==0 → 空数组；溯根链断裂行（商品/品类已删除）并入 `{category_id:0, category_name:"Other"}` 桶（兜底规则，排序按 amount DESC 自然落位）
- MAP-ANA-004 top_products：`{product_id, name←product_name, image_url←img, sales, amount}`；maxItems=6（SQL LIMIT 保证）；快照 img 为空串 → image_url=null
- MAP-ANA-005 GA4 traffic_sources 归一化：桶映射表见 api-detail E-ANA-03 STEP-ANA-03；`{source, sessions, share}` 按 sessions DESC；share 计算同 MAP-ANA-003 法
- MAP-ANA-006 GA4 device_share：deviceCategory 小写化 → {mobile, desktop, tablet}（枚举外并入 desktop）；share 同法；GA4 返回空行集 → 三桶 share=0 不视为失败
- MAP-ANA-007 GA4 funnel：固定 stage 顺序 [page_view, view_item, add_to_cart, begin_checkout, purchase]；按 eventName 对位填 value，缺事件补 0；value 取整
- MAP-ANA-008 通用：BigDecimal ↔ JSON number；时刻 → ISO8601 UTC（fetched_at）；readmodel 行 DTO 均为不可变 record

## 3. 依赖索引核对表（IDX-ANA，他域表索引依赖声明）

> 本域无自有表，不新建索引；以下为聚合 SQL 的索引依赖核对，列出归属域与协同要求。

| ID | 依赖 | 归属 | 状态/协同 |
|---|---|---|---|
| IDX-ANA-001 | orders `(status, paid_at)` 复合索引（RM-ANA-001/002/004/008/009 的 WHERE 主驱动） | trading | trading-data-detail 已有 idx_order_status_created/idx_order_status_expires，未含 paid_at 组合——由本域 TASK-058 在 L3 向 trading 表**协同增列** `idx_order_status_paid`(status, paid_at)；千单量级缺失仅退化为小表扫描，不阻塞功能 |
| IDX-ANA-002 | refund `(status)` 前缀（RM-ANA-003/005） | trading | idx_refund_order_status 已含 status 维度，复用 |
| IDX-ANA-003 | review `(status)`（RM-ANA-006） | review | review 域审核列表同款索引，复用 |
| IDX-ANA-004 | order_line `(order_id)`（RM-ANA-008/009 JOIN 驱动） | trading | trading 行表既有逻辑外键索引，复用 |

## 4. 缓存设计（CACHE-ANA，BE-DIM-8）

| ID | key | 载荷 | 策略 | TTL | 失效触发者 |
|---|---|---|---|---|---|
| CACHE-ANA-001 | `analytics:dashboard` | DashboardResponse | JetCache 两级 | 60s | TTL 自然过期（决策 10） |
| CACHE-ANA-002 | `analytics:overview:{range}` | AnalyticsOverviewResponse | JetCache 两级 | 60s | TTL 自然过期 |
| CACHE-ANA-003 | `analytics:traffic:{range}` | AnalyticsTrafficResponse（ok 形态） | JetCache 两级 | 300s | TTL 自然过期（决策 19） |
| CACHE-ANA-004 | `analytics:traffic:stale:{range}` | 最近一次成功的 traffic 载荷 + 生成时刻 | **remote-only（Redis 单级）** | 24h | 每次 GA4 成功拉取覆盖写（DEC-ANA-5 ②） |
| CACHE-ANA-005 | `analytics:traffic:{range}`（降级体） | unavailable 形态响应 | JetCache 两级 | 60s | TTL（故障期防反复打 GA4，DEC-ANA-5 ④） |

- key 维度：range ∈ {7d,30d,90d} 枚举（V-ANA-002 先行校验，**非法值不会进入缓存层**——key 空间有界，无穿透面）；无 locale/currency 维度。
- stale 快照取 remote-only：跨实例共享同一「最近成功」副本，避免本地级各实例新旧不一。
- 不经 CDN（契约声明，管理端带鉴权）。

## 5. GA4 客户端设计（SVC-ANA，决策 19 / BE-DIM-5 防腐层）

### 5.1 端口与实现

```java
// infra/ga4
public interface Ga4TrafficPort {
    /** 单次 batchRunReports 拉取三报表；失败抛 Ga4FetchException（含 timeout 标记） */
    Ga4TrafficRaw fetch(RangeWindow range);
}
// 实现选择（@ConditionalOnProperty dreamy.ga4.mode）：
//   Ga4Client      —— real：google-analytics-data SDK BetaAnalyticsDataClient.batchRunReports
//   Ga4StubClient  —— stub：返回确定样本（原型 mock 数值：自然搜索38/IG24/Pinterest16/直接14/邮件8；68/27/5；10万→2.84万→1.26万→8200），DEC-ANA-7
```

### 5.2 配置项（仅后端，环境变量/配置中心）

| 配置 | 说明 |
|---|---|
| `dreamy.ga4.mode` | `stub`（dev/CI 默认）/ `real`（staging/生产） |
| `dreamy.ga4.property-id` | GA4 媒体资源 ID（real 必填，缺失时启动校验失败快速暴露） |
| `dreamy.ga4.credentials-path` | service account JSON 路径（real 必填；文件权限 600） |
| `dreamy.ga4.connect-timeout-ms` / `read-timeout-ms` | 2000 / 8000（E-ANA-03 STEP-ANA-02 预算） |

### 5.3 调用与失败语义

- 单次 `batchRunReports`（≤5 报表限额内用 3 个）一回合往返；**不重试**（读路径，缓存与 stale 兜底；重试只放大尾延迟）。
- 失败分类：`DEADLINE_EXCEEDED`/socket timeout → timeout 形态（兜底链失效时映射 504001）；其余（UNAVAILABLE/PERMISSION_DENIED/RESOURCE_EXHAUSTED 配额/网络）→ unavailable 形态（映射 502001）。分类仅决定 ⑤ 兜底码，③④ 降级路径对两类一致（DEC-ANA-5）。
- 线程模型：同步调用于请求线程（管理端低频 + 300s 缓存，无需异步编排）；缓存未命中并发请求经 JetCache `penetrationProtect`（同 key 单飞）合并为单次 GA4 往返。
- 消费端埋点（gtag + Consent Mode v2）前端直连 GA4，不经本客户端（决策 19 连带约束，归 portal-store 横切，不在本域文档展开）。

### 5.4 脱敏与可观测（BE-DIM-7 脱敏规则）

- service account 凭证内容/路径不入日志、不出任何响应（error-strategy 脱敏表「GA4 service account 凭证…任何响应与日志不出现」）。
- 失败日志仅记：range、失败分类、耗时、GA4 错误码（不含响应原文）；降级命中（③④ 路径）记 INFO 计数指标 `ga4.degrade.{stale|unavailable}`（缓存命中率观测，决策 5）。

## 6. 事务口径（TX-ANA）

- TX-ANA-001 本域**无写事务**。RM-ANA 全部查询包裹声明式只读事务（`readOnly=true`，单连接一致性读），不持有行锁/表锁；聚合慢查询不阻塞 trading 写路径（InnoDB MVCC 快照读）。
- TX-ANA-002 E-ANA-01/02 的多条聚合 SQL 在同一只读事务内执行（同一快照，KPI 与趋势数值口径一致）；E-ANA-03 不开事务（无 DB 访问）。

## 7. DDL 与种子

- **本域无 DDL**（DEC-ANA-1 虚拟视图声明，§0）。
- 权限字典种子（identity permission 表，跨域幂等补登；`/dashboard` identity 种子已含）：

```sql
INSERT IGNORE INTO `permission` (`key`,`group`,`label`) VALUES
  ('/dashboard', '概览',     '仪表盘'),
  ('/analytics', '数据分析', '数据看板');
```

- IDX-ANA-001 协同索引（归 trading 表，L3 由本域 task 提交至 trading DDL）：

```sql
ALTER TABLE `orders` ADD INDEX `idx_order_status_paid` (`status`, `paid_at`);
```
