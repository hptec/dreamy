# Trading L2 API + 数据详设（admin-prototype-alignment 后端配合）

> 支撑缺口：ALIGN-012（订单导出）、ALIGN-013（地区/商品数列）、ALIGN-015（搜索范围）、ALIGN-025（退款/退货单号——契约已有字段，无后端改动）
> 决策依据：BE-DIM-8（DTO 扩展 country/itemCount；CSV 导出 ≤10000 行流式；PII 导出审计）、决策 10（非 breaking）
> 契约基线：`trading-api.openapi.yml`（listAdminOrders 已有 search/currency/from/to 参数；AdminOrderListItem 无 country/item_count；无导出端点）

## API-TRD-01 AdminOrderListItem 字段扩展 — country / item_count

```
AdminOrderListItem properties 追加（非 breaking）：
  country:
    type: string
    description: 收货地址快照国家（address_snapshot.country 提取；历史快照均含国家字段——前提假设④，无迁移）
  item_count:
    type: integer
    description: 订单行数量合计（SUM(order_line.qty)，派生）
```

### 数据查询方案（RM-TRD-01）

```
RM-TRD-01a 列表主查询不变（分页 + Customer 联表派生 name/email）
RM-TRD-01b country：
   - 若 address_snapshot 为 order 表 JSON 列：主查询直接取 JSON 提取（country 键），无额外 join
   - 若为独立 order_address 表：LEFT JOIN 取 country 列
   （以现有 portal-api-integration 落地结构为准，L3 按实际表结构二选一）
RM-TRD-01c item_count 批量聚合（避免 N+1 与相关子查询逐行执行）：
   SELECT order_id, SUM(qty) AS item_count FROM order_line
   WHERE order_id IN (:pageOrderIds) GROUP BY order_id
   内存合并；缺失 → 0
索引核验（IDX-TRD-01）：order_line(order_id) 必须命中索引（BE-DIM-8 约束「行计数子查询需命中 order_id 索引」）——order_line 外键列既有索引预期成立，L3 EXPLAIN 核验
```

## API-TRD-02 GET /api/admin/orders/export — 订单 CSV 导出

operationId: `exportAdminOrders`；权限：AdminBearerAuth [/orders]

### 入参验证（V-NNN）

| 编号 | 规则 |
|---|---|
| V-101 | query 与 listAdminOrders 完全一致：status/search/currency/from/to（不含分页） |
| V-102 | status 枚举外值 → 400；from > to → 400 |

### 业务流程（STEP-NN）

```
STEP-01 组装列表同款查询条件
STEP-02 keyset 游标流式读取（id ASC，批 500），每批做 RM-TRD-01b/01c 派生
STEP-03 行数达 10000 → 截断；响应头 X-Export-Truncated: true + CSV 末行 "# TRUNCATED AT 10000 ROWS"
STEP-04 写 OperationLog（action=导出订单，detail 含筛选条件、行数；PII 审计要求——BE-DIM-8）
```

### 出参

`200 text/csv`（UTF-8 BOM），`Content-Disposition: attachment; filename="orders-{yyyyMMdd}.csv"`
列：`order_no,customer_name,customer_email,country,item_count,total_amount,currency,payment_method,status,created_at`

### 错误码映射

| 场景 | 响应 |
|---|---|
| 未授权 / 无权限 | 401 / 403 |
| 参数非法 | 400 |
| 其余 | 500（既有全局处理器） |

## API-TRD-03 listAdminOrders search 语义扩展

```
现：search 模糊匹配 order_no / customer_email
改：search 模糊匹配 order_no / customer_name / customer_email   // ALIGN-015：原型搜索域为 订单号/客户名；邮箱保留为超集
契约 description 同步：『订单号 / 客户名 / 客户邮箱模糊搜索』；参数形状不变，非 breaking
RM-TRD-02：customer_name 匹配走既有 Customer 联表的 name 列 LIKE；
  性能注记（QP-TRD-01）：LIKE '%kw%' 不走索引，与现 email 搜索同量级，管理端低频可接受，不引入额外索引
```

## ALIGN-025 说明（无后端改动）

`AdminRefund.stripe_refund_id`（审核通过写入）与 `return_tracking_no`（登记）契约与实现均已存在，本缺口纯前端展示（见 refunds-frontend-detail.md COMP-TRD-R01）。

## 契约同步说明

API-TRD-01/02/03 为 trading-api.openapi.yml 增量（1 端点 + 2 字段 + 1 参数语义），随本变更产出契约 patch，归档流程合入基线。
