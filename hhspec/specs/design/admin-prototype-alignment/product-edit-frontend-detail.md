# ProductEdit L2 前端详设（admin-prototype-alignment）

> 覆盖缺口：ALIGN-018 ~ ALIGN-020
> 原型 ground truth：`hhspec/prototype/portal-admin/src/views/ProductEdit.vue`（699 行；SKU 颜色=预设 swatch 按钮 L515-523；多币种 4 列 L605-611；保存并生成静态页→router.push('/publish') L155）
> 实现现状：`frontend/portal-admin/src/views/ProductEdit.vue`（1170 行；颜色=自由文本输入 L910-918；多币种 5 列含 EUR L130-132；保存走 API save('published') L589）
> 决策依据：决策 9（实现增强保留）、原型强对照约束 3（回对模板结构与交互，保留数据层增强）

## 0. 缺口处置总表

| 缺口 | 处置 | 说明 |
|---|---|---|
| ALIGN-018 | CHANGE | SKU 颜色恢复预设 swatch 列表选择（带色点），自由文本输入保留为补充 |
| ALIGN-019 | EXEMPT(决策 9) | 多币种 5 列（EUR/CAD/AUD/GBP + USD 主价）为后端 5 币种能力超集，保留；L4 豁免 |
| ALIGN-020 | CHANGE(合并方案) | 保存并生成静态页 = API 保存触发（保留）+ 成功后跳转 /publish 查看进度（回对原型导航语义） |

## 1. SKU 颜色预设 swatch（COMP-CAT-E01，ALIGN-018 核心）

前端常量（无后端字典，名称+hex 镜像原型 `data/mock.js productColors`，新建 `frontend/portal-admin/src/constants/productColors.ts`）：

```
PRODUCT_COLORS: { name: string; hex: string }[]
  = [Ivory#FFFFF0 系、White、Champagne、Blush、Dusty Blue、Sage、Burgundy、Black …]
  // L3 实施时从 hhspec/prototype/portal-admin/src/data/mock.js 的 productColors 全量抄录（名称/hex 逐项一致）
```

交互（对照原型 L515-523 结构 + 保留自由输入为超集）：

```
颜色区块（替换现纯输入框区）：
  <p class="field-label">颜色 swatch（可选多个）</p>          // 原型 L515 文案
  按钮组：v-for c in PRODUCT_COLORS
    @click → skuColors.includes(c.name) ? remove : push
    class：rounded-full border px-2.5 py-1 text-[12px]；选中 border-gold
    内含色点：<span class="h-3.5 w-3.5 rounded-full border border-line" :style="{background:c.hex}" />
  尾部追加（实现增强保留）：
    <input v-model=newColorInput placeholder='自定义颜色' @keyup.enter=addColor /> [添加]
    // 自定义颜色 chips 展示在按钮组末尾（无 hex 时灰点占位）；删除沿用现 removeColor
约束：
  - skuColors 数据结构不变（string[]，提交 payload 不受影响——颜色仍为自由字符串，预设列表仅是输入捷径）
  - 编辑已有商品时，SKU 中已存在但不在预设表的颜色名 → 渲染为自定义 chip，不丢失
  - SKU 矩阵行色点：productColors.find(name)?.hex ?? 灰色占位（对照原型 L548-549）
```

## 2. 保存并生成静态页（FORM-CAT-E01，ALIGN-020）

```
save('published') 现有流程保持：buildPayload → API 保存（含 SKU 并发版本控制）→ 成功
+ 成功分支追加：
    toast.success('已保存，静态页失效链已触发')
    router.push('/publish')          // 回对原型 OP-006 navigation 语义，发布进度可见
「保存草稿」不跳转（维持现状）。
失败分支（409/422）：停留当前页展示错误，不跳转。
```

## 3. 豁免项断言（ALIGN-019）

- 多币种区块 5 输入位：USD 位 disabled、placeholder=主价格联动；EUR/CAD/AUD/GBP placeholder='auto'（空=自动换算，decision 14 语义）
- L4 对照时该区块与原型 4 列差异列入豁免清单
