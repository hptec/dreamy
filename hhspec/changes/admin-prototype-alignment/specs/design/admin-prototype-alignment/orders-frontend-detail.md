# Orders / OrderDetail L2 前端详设（admin-prototype-alignment）

> 覆盖缺口：ALIGN-012 ~ ALIGN-015（Orders）、ALIGN-021 ~ ALIGN-023（OrderDetail）
> 原型 ground truth：`hhspec/prototype/portal-admin/src/views/Orders.vue`（68 行）、`OrderDetail.vue`（105 行）
> 实现现状：`frontend/portal-admin/src/views/Orders.vue`（141 行，8 Tab、币种/承运列、邮箱搜索、无导出）、`OrderDetail.vue`（375 行，条件按钮/API 承运枚举/金额拆分已实现）
> 决策依据：决策 6（严格回对原型列 + 导出）、决策 9（8 Tab/币种时间筛选保留）、BE-DIM-8（DTO 扩展 + CSV 导出）

## A. Orders 列表

### 0. 缺口处置总表

| 缺口 | 处置 | 说明 |
|---|---|---|
| ALIGN-012 | CHANGE | PageHeader 补「导出订单」按钮（后端 CSV 端点，trading-api-detail.md API-TRD-02） |
| ALIGN-013 | CHANGE | 表格列 币种/承运 → 地区/商品数（依赖 AdminOrderListItem 扩展 country/item_count，API-TRD-01） |
| ALIGN-014 | EXEMPT(决策 9) | 8 状态 Tab（含 cancelled/refunded）为实现超集，保留；L4 豁免 |
| ALIGN-015 | CHANGE | 搜索回对原型「订单号 / 客户名」；服务端 search 扩展匹配 customer_name（邮箱匹配保留为超集） |

### 1. 表头与列映射（COMP-TRD-O01）

目标列（原型 L50 逐字）：

```
| 订单号 | 客户 | 地区 | 商品数(右) | 金额(右) | 支付方式 | 状态 | 下单时间 | 操作(右) |
```

| 列 | 数据源 | 渲染 |
|---|---|---|
| 地区 | `o.country`（**新增字段**，地址快照国家） | `<td>{{ o.country || '—' }}</td>` |
| 商品数 | `o.itemCount`（**新增字段**，订单行 qty 合计） | `text-right` |
| ~~币种~~ | 删除列；币种信息保留在金额符号（currencySymbol）与币种筛选下拉 | — |
| ~~承运~~ | 删除列；承运信息保留在详情页（impl OrderDetail L309 已有） | — |

### 2. 导出订单（COMP-TRD-O02，FORM-TRD-O01）

PageHeader actions（原型 L28）：`btn-outline + ArrowDownTrayIcon + 导出订单`。

```
onExport():
  GET /api/admin/orders/export?{status,search,currency,from,to}   // 与列表当前筛选完全一致
  → blob 下载 orders-{yyyyMMdd}.csv；X-Export-Truncated: true → toast.warn('已达 10000 行上限，结果已截断')
  按钮 loading 防重复；PII（客户邮箱）导出由后端审计（OperationLog），前端无额外处理
```

### 3. 搜索（FORM-TRD-O02）

```
placeholder：'搜索订单号 / 客户名…'    // 原型 L43 逐字（替换现「搜索订单号 / 客户邮箱…」）
行为：v-model store.search，300ms 防抖（沿用现 onSearchInput）
服务端语义：search 同时模糊匹配 order_no / customer_name / customer_email（API-TRD-03 扩展）
  // 邮箱匹配为超集保留（决策 9），placeholder 文案以原型为准
```

币种下拉 + 时间范围筛选：实现超集，保留（决策 9），L4 豁免清单登记。

## B. OrderDetail 详情

### 0. 缺口处置总表（全部 VERIFY——impl 已实现，设计固化目标规格 + 测试断言）

| 缺口 | 处置 | impl 证据 |
|---|---|---|
| ALIGN-021 | VERIFY | L186-190：取消订单(pending)/发起退款(paid,shipped)/确认完成(shipped)/标记发货(paid) 条件渲染 |
| ALIGN-022 | VERIFY | L50-63：carriers = listCarriers().filter(status==='enabled')，默认值=order.carrier ?? carriers[0] |
| ALIGN-023 | VERIFY | L253-259：小计/运费/Gift Wrapping(v-if o.giftWrap)/优惠(v-if discountAmount>0)/合计 |

### 1. 操作按钮状态矩阵（FORM-TRD-D01，目标规格固化）

| 订单状态 | 返回 | 取消订单 | 发起退款 | 确认完成 | 标记发货 |
|---|---|---|---|---|---|
| pending | ✓ | ✓ | — | — | — |
| paid | ✓ | —(提示走退款) | ✓ | — | ✓ |
| shipped | ✓ | — | ✓ | ✓ | — |
| completed / cancelled / refunding / refunded | ✓ | — | — | — | — |

前端预判 + 后端 409602 兜底（既有错误码体系，不新增）。

### 2. 金额拆分行序（COMP-TRD-D01）

`小计 → 运费(0 显示「免邮」) → Gift Wrapping(条件) → 优惠(条件，负号) → 合计(border-t 加重)`——对照原型 L60-64 结构，Gift Wrapping/优惠为决策 28 既有扩展。

### 3. 承运方枚举（COMP-TRD-D02）

- 选项 = API 启用承运方 name 列表；**禁止照抄原型 mock 三个硬编码选项**（impl 注释 F-036 已标注）
- 空列表兜底：select 显示空 + 提交校验「请先在物流配置启用承运方」（关联 s-879 前置条件）
