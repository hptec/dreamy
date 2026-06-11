# Catalog L2 数据详设（admin-prototype-alignment 后端配合）

> 支撑：API-CAT-01/02/03（批量操作、CSV 导出、sales_total 派生字段）
> 约束：决策 10 —— **DB schema 零变更**，全部查询层解决；伪代码级，不含 ORM 语法

## RM-CAT-01 商品累计销量派生（sales_total）

口径：`sales_total(product_id) = SUM(order_line.qty) WHERE order.status IN (paid, shipped, completed, refunding, refunded)`（已支付后状态；cancelled/pending 不计）。

列表查询方案（避免 N+1）：

```
RM-CAT-01a listAdminProducts 主查询不变（分页）
RM-CAT-01b 取本页 product_ids 后一次聚合：
   SELECT ol.product_id, SUM(ol.qty) AS sales_total
   FROM order_line ol JOIN `order` o ON o.id = ol.order_id
   WHERE ol.product_id IN (:pageIds) AND o.status IN ('paid','shipped','completed','refunding','refunded')
   GROUP BY ol.product_id
RM-CAT-01c 内存合并到 DTO；缺失 product_id → sales_total = 0
```

索引核验（IDX-CAT-01）：`order_line(product_id)` 需有索引（若仅有 `order_line(order_id)` 复合主键，L3 实施时核验执行计划；本变更不加 DDL，若缺索引记录为风险上报，不擅自变更 schema）。

缓存（QP-CAT-01）：列表页可接受弱一致；若性能不达标，sales_total 聚合结果 JetCache 缓存 5 分钟（key=product_id 批量），订单状态变更不主动失效。

## RM-CAT-02 批量操作行级执行

```
RM-CAT-02a 行级操作复用既有单品 Repository 方法（statusUpdate / recommendUpdate / deleteById）
RM-CAT-02b 事务边界（TX-CAT-01）：每行独立提交（REQUIRES_NEW 或循环外无事务）——
            整体不回滚语义（BE-DIM-4）；禁止把整个循环包进单事务
RM-CAT-02c delete 前置校验：status='published' → 抛 409509（行级捕获进 failures）
RM-CAT-02d 幂等（EC-CAT-01）：delete 目标不存在 → 视为成功；publish 已 published → 视为成功
```

## RM-CAT-03 导出流式读取

```
RM-CAT-03a 条件同 listAdminProducts（search/category_id/status）
RM-CAT-03b 游标分页：ORDER BY id ASC, WHERE id > :lastId LIMIT 500（keyset 分页，避免深翻页）
RM-CAT-03c 每批联查 category_name（既有 join）+ RM-CAT-01b 同款 sales_total 批量聚合
RM-CAT-03d 累计行数 >= 10000 → break，置 truncated 标记
```

## CV-CAT-01 数据校验

- 批量 ids 中不存在的 id：publish/unpublish/recommend → failures（404 语义行级码）；delete → 幂等成功
- CSV 字段含逗号/引号/换行 → 标准 CSV 转义（双引号包裹 + 引号翻倍）
