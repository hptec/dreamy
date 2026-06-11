# Dashboard / Analytics L2 前端详设（admin-prototype-alignment）

> 覆盖缺口：ALIGN-009 ~ ALIGN-011（Dashboard）、ALIGN-016 ~ ALIGN-017（Analytics）
> 原型 ground truth：`hhspec/prototype/portal-admin/src/views/Dashboard.vue`（162 行）、`Analytics.vue`（93 行）、`hhspec/prototype/portal-admin/src/data/mock.js`（funnel L61-66 / trafficSources L68-74）
> 实现现状：`frontend/portal-admin/src/views/Dashboard.vue`（192 行，快捷入口 3 项、无发布按钮——DEC-ANA-FE-4 已被决策 5 推翻）、`Analytics.vue`（228 行，trafficSources 渲染 `s.source` 原始 key、funnel 有 STAGE_LABEL 映射）
> 决策依据：决策 5（补发布入口）、决策 8（DEC-ANA-FE-2/3/7 维持现状）

## A. Dashboard

### 0. 缺口处置总表

| 缺口 | 处置 | 说明 |
|---|---|---|
| ALIGN-009 | CHANGE | PageHeader 恢复「发布站点」按钮；快捷入口 3 项 → 5 项（补 编辑首页 / 发布站点） |
| ALIGN-010 | EXEMPT(DEC-ANA-FE-2) | KPI 卡无 delta 行，维持现状；L4 豁免 |
| ALIGN-011 | EXEMPT(DEC-ANA-FE-3) | 待办瓦片 3 列，维持现状；L4 豁免 |

### 1. PageHeader actions（COMP-ANA-D01）

对照原型 L40-43：

```
<template #actions>
  RouterLink /analytics  class=btn-outline  「查看完整看板」   // impl 已有
+ RouterLink /publish    class=btn-gold + RocketLaunchIcon  「发布站点」   // 恢复
</template>
// 路由 /publish 已存在（Publish.vue）；权限随现有菜单权限点，不新增
```

### 2. 快捷入口（COMP-ANA-D02）

恢复原型 5 项（原型 L13-19 顺序逐字）：

```
quickActions = [
  { label: '新增商品',   icon: ShoppingBagIcon, to: '/products/new' },
  { label: '编辑首页',   icon: SwatchIcon,      to: '/site/home' },     // 恢复（路由 home-builder 已存在）
  { label: '新建优惠券', icon: TicketIcon,      to: '/promotions' },    // impl 路由 /promotions（原型 /marketing/promotions 为原型旧路径，以 impl 路由为准）
  { label: '写一篇文章', icon: DocumentPlusIcon,to: '/content/blog' },
  { label: '发布站点',   icon: RocketLaunchIcon,to: '/publish' },       // 恢复
]
// 移除 impl 注释「DEC-ANA-FE-4 ②」——该决策已被决策 5 推翻，注释同步更新避免误导
```

### 3. 豁免项断言（ALIGN-010/011）

- KPI 卡仅 label+value 两行，无趋势箭头/delta（impl kpiCards 现状）
- 待办瓦片容器 `sm:grid-cols-3`（impl 现状），瓦片点击直达带 query 的列表页

## B. Analytics

### 0. 缺口处置总表

| 缺口 | 处置 | 说明 |
|---|---|---|
| ALIGN-016 | EXEMPT(DEC-ANA-FE-7) | 商品热度列用「销售额」替代原型「库存/状态」，维持现状；L4 豁免 |
| ALIGN-017 | CHANGE | 流量来源 / 漏斗标签中文化 |

### 1. 标签中文化（COMP-ANA-A01）

现状：访客来源渲染 `{{ s.source }}` 原始 key（GA4 取数）；漏斗已有 `STAGE_LABEL[f.stage] || f.stage` 兜底。

```
// 新增前端映射常量（utils 或组件内）：
SOURCE_LABEL = {
  organic: '自然搜索', instagram: 'Instagram', pinterest: 'Pinterest',
  direct: '直接访问', email: '邮件', referral: '外链引荐', paid: '付费广告',
}
渲染：{{ SOURCE_LABEL[s.source] ?? s.source }}    // 未知来源回退原始值，不报错

STAGE_LABEL（补全四阶段，文案对照原型 mock.js L61-66 逐字）：
  view → '商品浏览'；add_to_cart → '加入购物车'；checkout → '进入结算'；paid → '完成支付'
// 后端 stage key 以 analytics-api 契约枚举为准；若契约 key 与上表不一致，以契约 key 为映射键、中文文案不变
```

CSV 导出（impl exportCsv）首行表头维持英文 key（机器可读），不在本缺口范围。

### 2. 豁免项断言（ALIGN-016）

- 商品热度表头：`排名 | 商品 | 销量 | 销售额`（impl 现状，原型为 销量|库存|状态）→ L4 对照时按豁免清单跳过该列差异
