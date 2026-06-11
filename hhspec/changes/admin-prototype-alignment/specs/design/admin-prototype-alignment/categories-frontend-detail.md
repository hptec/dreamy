# Categories / AttributeSets L2 前端详设（admin-prototype-alignment）

> 覆盖缺口：ALIGN-001 ~ ALIGN-006
> 原型 ground truth：`hhspec/prototype/portal-admin/src/views/Categories.vue`（618 行，3-Tab）+ `hhspec/prototype/portal-admin/src/views/AttributeSets.vue`（209 行，dict/matrix 双 Tab + 子品类覆盖卡片区 L184-206）
> 实现现状：`frontend/portal-admin/src/views/Categories.vue`（677 行，仅 taxonomy/tags 双 Tab）+ `frontend/portal-admin/src/views/AttributeSets.vue`（536 行，独立页 PAGE-CAT-A04）
> 决策依据：decision.md 决策 1 / 2 / 3 / 8 / 9；E-CAT-21、E-CAT-30 显式偏离保留

## 0. 缺口处置总表

| 缺口 | 处置 | 说明 |
|---|---|---|
| ALIGN-001 | CHANGE | 决策 1+3：矩阵合并回 Categories 第二 Tab，恢复 3-Tab；补子品类覆盖只读卡片区 |
| ALIGN-002 | EXEMPT(决策 9) | 编辑抽屉三语 name tab（LocaleTabs）为实现增强，保留；列入 L4 豁免清单 |
| ALIGN-003 | EXEMPT(E-CAT-30) | 维度删除 409506 引导已实现（FORM-CAT-A05，impl Categories.vue L251），保留；列入豁免清单 |
| ALIGN-004 | CHANGE | 决策 1：废弃独立 AttributeSets 页（路由重定向 + 侧边栏菜单移除） |
| ALIGN-005 | CHANGE | 决策 1：矩阵 sub-tab 文案恢复「品类×属性矩阵」（实现现为「属性集×属性矩阵」语义） |
| ALIGN-006 | EXEMPT(E-CAT-21) | 矩阵 hasUnsavedChanges + 整单保存保留（不回对原型即点即改）；合并后沿用 |

## 1. 页面信息架构（COMP-CAT-M01）

目标结构（对照原型 Categories.vue L250-257 主 Tab 条）：

```
Categories 页（/categories）
├── Tab 1「标准品类」    ← 现 impl Tab 1 保持（品类卡片网格 + 编辑抽屉 + 三语增强）
├── Tab 2「属性集与字典」 ← 新增：整体迁入现 AttributeSets.vue 内容
│     ├── sub-tab ①「属性字典」      （现 AttributeSets impl tab='dict' 区块原样迁移）
│     └── sub-tab ②「品类×属性矩阵」  （现 tab='matrix' 区块迁移，文案改回原型 ALIGN-005）
│           ├── 矩阵表（列=属性集聚合，保留 hasUnsavedChanges+整单保存 E-CAT-21）
│           └── 子品类覆盖只读卡片区（新增，ALIGN-001/决策 3）
└── Tab 3「自定义标签」  ← 现 impl Tab 2 保持
```

伪代码（主 Tab 状态）：

```
mainTab: 'taxonomy' | 'attributes' | 'tags'   // 由现有 2 值扩为 3 值
attrSubTab: 'dict' | 'matrix'                  // Tab 2 内层，默认 'dict'
// 主 Tab 文案与顺序严格对照原型：标准品类 / 属性集与字典 / 自定义标签
// 深链支持：/categories?tab=attributes[&sub=matrix] → onMounted 解析 query 设置 mainTab/attrSubTab
```

## 2. 组件迁移方案（COMP-CAT-M02）

按「复制 + 适配」原则（原型强对照约束 3）：

| 编号 | 改动 | 来源 → 去处 |
|---|---|---|
| COMP-CAT-M02-1 | AttributeSets.vue 的 script 状态（dict 列表、matrix 副本、hasUnsavedChanges、savingMatrix、LocaleTabs defLocale）整体迁入 Categories.vue（或抽为 `composables/useAttributeMatrix.ts` 以控制单文件体积，推荐后者） | AttributeSets.vue L1-330 → Categories.vue / composable |
| COMP-CAT-M02-2 | dict/matrix 两块 template 迁入 Tab 2，外层加 sub-tab 切换条（样式同主 Tab 条：`border-b-2 px-4 py-2.5 text-[13px]`，激活态 `border-gold font-medium text-ink`） | AttributeSets.vue L342-470 |
| COMP-CAT-M02-3 | 「保存配置」按钮从 PageHeader actions 迁至矩阵 sub-tab 区块右上（仅 attrSubTab==='matrix' 时显示；disabled=!hasUnsavedChanges，文案保持「保存配置 *」未保存星标语义） | AttributeSets.vue L325-326 |
| COMP-CAT-M02-4 | 新增子品类覆盖只读卡片区，置于矩阵表下方 mt-6（结构 1:1 复制原型 AttributeSets.vue L184-206） | 原型 L184-206 → 新代码 |
| COMP-CAT-M02-5 | 矩阵 sub-tab 文案：「品类×属性矩阵」（ALIGN-005，决策 1 明确恢复原型文案） | — |

## 3. 子品类覆盖只读卡片区（COMP-CAT-M03，ALIGN-001 核心新增）

数据派生（伪代码，对照原型 AttributeSets.vue L46-54）：

```
subcategoryOverrides = categoriesTree
  .map(root => ({
    rootName: root.name(当前 locale),
    children: root.children
      .map(c => ({ name, overrides: c.attrOverrides }))   // 来源：现有分类树接口 attr_overrides 字段
      .filter(c => keys(c.overrides).length > 0)
  }))
  .filter(g => g.children.length > 0)
// 若 subcategoryOverrides 为空 → 整个卡片区不渲染（原型 v-if 同款）
```

渲染规则：
- 区块标题：`子品类属性覆盖（相对父级基础属性集的 delta）`（原型 L186 文案逐字）
- 按根品类分组：组名 `text-[11px] font-semibold uppercase tracking-widest text-ink-faint`
- 卡片：`rounded-luxe border border-line bg-canvas-warm/40 px-4 py-3 min-w-[180px]`，每行 = 属性 label + 三态徽章（必/选/—，STATE_CLASSES 同矩阵）
- **只读**：卡片不提供编辑入口；编辑路径 = Tab 1 品类卡片子类目 chip 上的「N覆盖/继承」按钮（原型 L303-307 注释同款，impl 已有该入口）

## 4. 品类卡片徽章交互（ALIGN-001 附属，决策 2 验证项）

impl Categories.vue L384-391 已实现徽章点击 `openDrawer(cat, null)` 打开属性三态抽屉。设计要求验证语义与原型一致：
- 点击徽章 → 直接进入该品类属性集三态配置（抽屉滚动定位到属性区，无需先点「编辑」）
- 徽章 hover 态 `hover:bg-gold/25`（原型 L283）
- 判定：已对齐则仅补测试断言，不改代码

## 5. AttributeSets 页废弃（ALIGN-004，FORM-CAT-M01）

```
router/index.ts:
- 删除：{ path: '/attribute-sets', component: AttributeSets.vue, ... }
+ 新增：{ path: '/attribute-sets', redirect: to => ({ path: '/categories', query: { tab: 'attributes' } }) }
  // 保留 redirect 以兼容书签/操作日志旧链接；permission 校验随 /categories

AdminShell.vue（侧边栏）:
- 移除「属性集」菜单项（group 商品管理）
  // 注意：若菜单由权限点驱动，仅删前端菜单项，不动后端权限点（决策 10：不新增/删除权限点）

views/AttributeSets.vue:
- 文件删除（内容已迁入 Categories）；其 API 调用层（attribute stores/api）保留复用
```

## 6. 状态管理（STORE-CAT-M01）

- 现 `stores/` 中属性集/字典/矩阵相关 store **不改接口**，仅消费方从 AttributeSets.vue 变为 Categories.vue（或 composable）
- 矩阵保存流程沿用 E-CAT-21：`saveMatrix() → PUT 整单 → 成功后重置 hasUnsavedChanges 基线`
- Tab 切换防丢失：`mainTab/attrSubTab` 切换且 hasUnsavedChanges=true 时弹确认（沿用现有未保存提示文案 L443，新增 beforeTabSwitch guard）：

```
function switchTab(next):
  if mainTab==='attributes' && attrSubTab==='matrix' && hasUnsavedChanges:
    confirm('有未保存的矩阵变更，离开将丢失，确认切换？') ? proceed : abort
```

## 7. 三语 / 409506 豁免项验证清单（ALIGN-002 / 003 / 006）

| 项 | 现状证据 | 验证断言 |
|---|---|---|
| 三语 name tab | impl L576 LocaleTabs | 编辑抽屉含 EN/ES/FR tab，切换可独立编辑 name |
| 维度删除 409506 引导 | impl L251 FORM-CAT-A05 | 删除含标签的维度 → toast/对话框提示先清空标签，维度不被删除 |
| 矩阵整单保存 | AttributeSets impl L65/325 | 改格子后出现「保存配置 *」，刷新前未保存变更丢弃告警 |

## 8. 风险与依赖

- R1：Categories.vue 合并后体积 >1100 行 → 建议抽 `AttributeDictPanel.vue` / `AttributeMatrixPanel.vue` 两个子组件（与原型逐区块对照不受影响）
- R2：旧路由 `/attribute-sets` 在操作日志/收藏中的引用 → redirect 已兜底
- 依赖：无后端变更（决策 10：catalog 域 DB 零变更）
