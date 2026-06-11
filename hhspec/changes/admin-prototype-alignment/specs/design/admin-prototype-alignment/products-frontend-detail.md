# Products L2 前端详设（admin-prototype-alignment）

> 覆盖缺口：ALIGN-007、ALIGN-008
> 原型 ground truth：`hhspec/prototype/portal-admin/src/views/Products.vue`（283 行）
> 实现现状：`frontend/portal-admin/src/views/Products.vue`（325 行）——已有「更多筛选」面板（COMP-CAT-A01 当前页过滤）；缺勾选列/批量操作栏/导出按钮/销量列；多出排序列
> 决策依据：决策 4（补批量/导出/销量列/筛选）、决策 9（排序列保留）、BE-DIM-4（批量端点）、BE-DIM-8（CSV 导出）

## 0. 缺口处置总表

| 缺口 | 处置 | 说明 |
|---|---|---|
| ALIGN-007 | CHANGE | 勾选框列 + 批量操作栏（上架/下架/推荐/删除）+ 导出按钮 + 销量列；更多筛选已实现仅验证 |
| ALIGN-008 | EXEMPT(决策 9) | 排序列为实现增强（行内 blur 保存），保留；列入 L4 豁免清单 |

## 1. 表头与列映射（COMP-CAT-P01）

目标列（原型 L215-226 ⊕ 决策 9 保留排序列）：

```
| ☑ | 商品 | 品类 | 价格 | 上架/新品/推荐 | 库存(右) | 销量(右) | 排序 | 状态 | 操作(右) |
```

| 列 | 数据源 | 备注 |
|---|---|---|
| ☑ 勾选 | 前端 `selected: number[]` | th 内全选 checkbox（`h-4 w-4 rounded border-line accent-gold`，原型 L217） |
| 销量 | `AdminProductListItem.sales_total`（**新增字段**，见 catalog-api-detail.md API-CAT-03） | `text-right text-ink-soft`；字段缺省显示 0 |
| 排序 | 现有 `p.sort` 行内 input | 保留现实现（blur 保存） |

全选逻辑（伪代码，对照原型 L100-104）：

```
allChecked (computed get/set):
  get: list.length > 0 && selected.length === 当前页 list.length
  set(v): selected = v ? list.map(p => p.id) : []
// 翻页/筛选变更 → selected 清空（服务端分页下不跨页保留勾选）
```

## 2. 批量操作栏（COMP-CAT-P02，FORM-CAT-P01）

结构 1:1 对照原型 L272-278：`v-if="selected.length"`，置于表格底部 `border-t bg-canvas-warm/50 px-4 py-3`：

```
已选 {n} 项 | [批量上架] [批量下架] [设为推荐] [批量删除(ml-auto, danger)]
```

交互流程（FORM-CAT-P01）：

```
onBatch(action: 'publish'|'unpublish'|'recommend'|'delete'):
  if action==='delete': ConfirmDialog(`确认批量删除 ${n} 件商品？已上架/被订单引用的商品将跳过`)
  resp = POST /api/admin/products/batch { action, ids: selected }     // API-CAT-01
  // 逐条容错语义（BE-DIM-4）：
  if resp.failures.length === 0:
    toast.success(`已${verb} ${resp.success_ids.length} 件商品`)
  else:
    toast.warn(`成功 ${resp.success_ids.length} 件，失败 ${resp.failures.length} 件`)
    打开失败明细面板：列出 {商品名(由 id 反查当前页) , errorCode→文案}    // 409509→「已发布商品需先下架」等
  selected = []; store.fetchList()   // 整页刷新保证派生字段一致
```

按钮置灰预判（js_guard，后端兜底）：无；批量语义为逐条容错，不做前端预判。

## 3. 导出按钮（COMP-CAT-P03，FORM-CAT-P02）

PageHeader actions 区，置于「新增商品」左侧（原型 L111-114）：`btn-outline + ArrowDownTrayIcon + 导出`。

```
onExport():
  exporting = true
  GET /api/admin/products/export?{当前服务端筛选参数：search/category_id/status}   // API-CAT-02
  → 响应为 text/csv 附件；前端以 blob 下载，文件名取 Content-Disposition（兜底 products-{yyyyMMdd}.csv）
  超限提示：若响应头 X-Export-Truncated: true → toast.warn('已达 10000 行上限，结果已截断')
  exporting = false（按钮 loading 态防重复点击）
```

注意：「更多筛选」中的当前页内存过滤条件（productType/库存档/flags/tagIds/价格）**不参与导出**（导出按服务端筛选口径，与 tooltip「当前页过滤」语义一致，需在导出按钮 title 标注「按搜索/品类/状态条件导出」）。

## 4. 已对齐项验证（不改码，仅断言）

- 更多筛选面板：商品类型/库存状态/标记/价格区间/主题标签 5 组（impl 已有，对照原型 L141-208）
- 上架/新品/推荐三 Toggle 列、库存色阶（0 红 / <10 警 / 正常）

## 5. 状态管理（STORE-CAT-P01）

`stores/products.ts` 新增：

```
state: 无新增（selected 留在组件层）
actions:
  batchOperate(action, ids) → POST /api/admin/products/batch → {success_ids, failures[]}
  exportCsv(filters) → GET blob
list 项类型 AdminProductListItem += sales_total?: number   // api/types.ts 同步
```

## 6. 风险

- R1：批量删除含已上架商品 → 后端逐条返回 409509，前端失败明细面板必须可读（错误码→中文映射沿用现有 bizMsg 表）
- R2：sales_total 为派生重字段 → 列表接口性能依赖 catalog-data-detail.md RM-CAT-01 的聚合方案，前端不做兜底计算
