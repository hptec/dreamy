# Catalog L2 API 详设（admin-prototype-alignment 后端配合）

> 支撑缺口：ALIGN-007（Products 批量操作 / CSV 导出 / 销量列）
> 决策依据：BE-DIM-4（批量端点逐条容错）、BE-DIM-8（CSV 导出上限 10000 行）、决策 10（3 个新增端点，非 breaking；DB 零变更）
> 契约基线：`hhspec/specs/architecture/api-contracts/catalog-api.openapi.yml`（基线无 batch/export 端点，需随本变更补录契约增量）
> 错误码：沿用 409501~409509 体系（409509 PRODUCT_NOT_DELETABLE），不新增错误码

## API-CAT-01 POST /api/admin/products/batch — 商品批量操作

operationId: `batchAdminProducts`；权限：AdminBearerAuth [/products]

### 入参验证（V-NNN）

| 编号 | 规则 | 失败响应 |
|---|---|---|
| V-001 | `action` ∈ {publish, unpublish, recommend, unrecommend, delete}，必填 | 400 参数错误 |
| V-002 | `ids`: int64[]，必填，1 ≤ size ≤ 200，元素去重 | 400（size 超限文案「单次最多 200 件」） |
| V-003 | 鉴权 + 权限点 /products | 401 / 403 |

请求体：`{ "action": "publish", "ids": [1,2,3] }`

### 业务流程（STEP-NN，逐条容错，整体不回滚）

```
STEP-01 解析 action → 单品操作函数（复用既有单品 service：上架/下架/推荐置位/删除）
STEP-02 for id in ids（循环内独立事务或无事务单语句）：
          try: 执行单品操作
            - publish：draft→published（published 幂等成功）
            - unpublish：published→draft（draft 幂等成功）
            - recommend/unrecommend：recommend 置位（幂等）
            - delete：仅 draft 可删；published → 409509；不存在 → 幂等成功（容忍已删除，BE-DIM-4）
          catch BizException e: failures.add({id, error_code: e.code, message: e.msg})
          else: success_ids.add(id)
STEP-03 写 OperationLog 一条（action=批量{操作名}，detail 含 ids 总数/成功数/失败数）
STEP-04 返回 200
```

### 出参

```
R<{ "success_ids": int64[], "failures": [{ "id": int64, "error_code": int, "message": string }] }>
// 部分失败仍 200（逐条容错语义）；全部失败也 200，由调用方按 failures 展示
```

### 错误码映射

| 场景 | 码 | HTTP |
|---|---|---|
| delete 命中已发布 | 409509 PRODUCT_NOT_DELETABLE | 行级（包内 failures） |
| 行级未知异常 | 500500（行级包内，message 脱敏） | 包内 |
| action/ids 非法 | 400 | 400 |

## API-CAT-02 GET /api/admin/products/export — 商品 CSV 导出

operationId: `exportAdminProducts`；权限：AdminBearerAuth [/products]

### 入参验证

| 编号 | 规则 |
|---|---|
| V-011 | query 与 listAdminProducts 服务端筛选参数对齐：`search` / `category_id` / `status`（不含分页参数） |
| V-012 | 参数非法 → 400 |

### 业务流程

```
STEP-01 组装与列表一致的查询条件（不分页）
STEP-02 分页流式读取（pageSize=500 游标循环，BE-DIM-8 内存约束）逐批写 CSV
STEP-03 行数达 10000 → 停止，响应头 X-Export-Truncated: true，CSV 末行追加 "# TRUNCATED AT 10000 ROWS"
STEP-04 写 OperationLog（action=导出商品，detail 含筛选条件与导出行数）
```

### 出参

`200 text/csv; charset=UTF-8`（带 BOM 便于 Excel），`Content-Disposition: attachment; filename="products-{yyyyMMdd}.csv"`
列：`id,name,slug,style_no,category_name,price,compare_at,status,is_new,recommend,sort,stock_total,sales_total`

## API-CAT-03 AdminProductListItem 字段扩展 — sales_total

`AdminProductListItem` properties 追加（非 breaking）：

```
sales_total:
  type: integer
  description: 累计销量（已支付及之后状态订单的订单行 qty 合计，派生；见 catalog-data-detail.md RM-CAT-01）
```

listAdminProducts / 导出 CSV 共用同一派生逻辑。

## 契约同步说明

以上三项为 catalog-api.openapi.yml 的增量（新增 2 端点 + 1 字段），随本变更产出契约 patch；基线 specs 只读，由归档流程合入。
