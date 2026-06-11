# shipping 前端详细设计（L2）

> 角色: l2_frontend_designer ｜ change: portal-api-integration ｜ domain: shipping
> 端：仅 portal-admin（Vue3 + Pinia + Vite + Tailwind/Headless-UI，port 5174，中文）。消费端无本域页面（运费仅在 trading 结算报价中透出，归 trading-frontend-detail）。
> 编号：页面路由(PAGE-SHP) / 状态管理(STORE-SHP) / 组件树(COMP-SHP) / 表单交互(FORM-SHP)。伪代码级 diff 设计——**以真实工程现状为基线，仅替换数据源（mock → API）并补齐 CRUD 数据行为，不改设计 token 与布局结构**（原型强对照约束 1~4）。
> API 契约消费：shipping-api-detail.md E-SHP-01~09；错误处理按 error-strategy portal-admin 呈现约定（422 字段 inline / 409 toast / 401 跳登录 / 403 toast）。

## 0. 真实工程现状基线（设计前提，已核对 /Volumes/MAC/workspace/dreamy/frontend）

| 文件 | 现状 | 改造策略 |
|---|---|---|
| portal-admin/src/views/Shipping.vue（43 行） | 与原型同源：左右双 panel（承运方列表 + 国际邮费表），`@/data/mock` carriers/shippingRates，Toggle 仅本地态，「添加承运方」「保存配置」按钮无行为，邮费表纯展示 | 保留双 panel 栅格与 token，数据层换 API；补齐承运方/规则行 CRUD 交互（FormDrawer + 行内操作，复用既有组件风格）；「保存配置」按钮移除（DEC-SHP-FE-1） |
| portal-admin/src/api/ | client.ts（axios + R 解包 + snake↔camel + 401 清 token 跳 /login + 403 toast）已有 | **新增 `src/api/shipping.ts`**，复用 client 全部拦截器 |
| portal-admin/src/stores/ | auth/menu/toast 等已有 | **新增 `src/stores/shipping.ts`** |
| portal-admin/src/router/index.ts | `/shipping` 路由已存在，meta.permission='/shipping'（与契约权限点一致，零改动） | 不动 |
| 既有组件 | PageHeader / Toggle / StatusBadge / EmptyState / FormDrawer 风格（marketing/catalog 设计已沿用） | 全部复用，不新建样式 |

**DEC-SHP-FE-1**：顶部「保存配置」按钮移除——契约为逐资源即时持久化（9 端点），无批量保存语义；行内操作成功即落库 + toast。属数据驱动的交互差异显式决策（同 marketing 决策 26 话术降级口径），不改 token/栅格。
**DEC-SHP-FE-2**：原型未提供承运方编辑/删除与邮费行操作入口——按「数据行为补齐」补充行内 hover 操作（PencilSquare/Trash ghost 图标按钮）与表格「操作」列，复用 catalog/marketing 已定稿的行内操作形态，不引入新 token。

## 1. 页面路由（PAGE-SHP）

| 编号 | 路由 | 视图 | 权限 key（meta.permission） | 消费端点 |
|---|---|---|---|---|
| PAGE-SHP-01 | /shipping | Shipping.vue | /shipping | E-SHP-01~09 全部 |

路由守卫沿用 identity GUARD-01~04（meta.permission ∉ permissionKeys 且非超管 → /403；菜单按权限过滤）。

## 2. API 模块（src/api/shipping.ts，复用 client.ts）

```
listCarriers()                      GET    /admin/shipping/carriers          -> { items: Carrier[] }
createCarrier(body)                 POST   /admin/shipping/carriers          -> Carrier
updateCarrier(id, body)             PUT    /admin/shipping/carriers/{id}     -> Carrier
deleteCarrier(id)                   DELETE /admin/shipping/carriers/{id}
toggleCarrierStatus(id, status)     PATCH  /admin/shipping/carriers/{id}/status -> Carrier
listRates()                         GET    /admin/shipping/rates             -> { items: ShippingRate[] }
createRate(body)                    POST   /admin/shipping/rates             -> ShippingRate
updateRate(id, body)                PUT    /admin/shipping/rates/{id}        -> ShippingRate
deleteRate(id)                      DELETE /admin/shipping/rates/{id}
```

拦截器自动 camelize（lead_time→leadTime / fee_under→feeUnder）；错误统一 ApiError{code,message,details}。

## 3. 状态管理（STORE-SHP，Pinia）

- STORE-SHP-01 `useShippingStore`：
  - state：`carriers[]`、`rates[]`、`loadingCarriers`、`loadingRates`、`saving`
  - actions：
    - `fetchAll()`：listCarriers + listRates 并行（Promise.all），页面 onMounted 调用
    - `toggleCarrier(row, status)`：**乐观更新** Toggle → PATCH；失败回滚本地态 + 按 code toast（409902「至少保留一个启用的承运方」/404901 后整列表 refetch）
    - `saveCarrier(body, id?)`：id 有→PUT 无→POST；成功后局部替换/追加行 + toast「已保存」；name 变更成功时追加 toast 提示「承运商名称已变更，请同步检查邮费规则行后缀」（DEC-SHP-2 运营提示）
    - `removeCarrier(id)` / `saveRate(body, id?)` / `removeRate(id)`：同型；409901 → 表单 zone 字段 inline「同名规则行已存在」
  - getters：`enabledCount`（前端预判：仅剩 1 个 enabled 时其 Toggle/删除按钮置灰 + tooltip「至少保留一个启用的承运方」——后端 409902 为兜底防线）

## 4. 组件树（COMP-SHP）

- COMP-SHP-01 `Shipping.vue`（页面壳，布局零改动）：
  - PageHeader（eyebrow=Settings / title=物流配置 / subtitle 不变；actions 槽移除「保存配置」按钮，DEC-SHP-FE-1）
  - 左 panel = COMP-SHP-02；右 panel = COMP-SHP-03
- COMP-SHP-02 `承运方面板`（页面内区块）：
  - 行渲染保持原型结构（name + zones·时效 副行 + Toggle）；`c.enabled` 本地映射 `status==='enabled'`（er-diagram UI toggle ↔ status 枚举映射注记）
  - 行 hover 显示 编辑/删除 ghost 图标按钮（DEC-SHP-FE-2）；删除走二次确认弹窗（CP-071 风格）
  - 「添加承运方」btn-ghost → 打开 FORM-SHP-01（新建态）
  - loading：骨架行 ×3（复用既有骨架风格）；空列表：EmptyState「尚未配置承运方」
- COMP-SHP-03 `国际邮费表面板`：
  - data-table 列保持（区域/基础邮费/满额包邮/门槛）+ 追加「操作」列（编辑/删除，DEC-SHP-FE-2）+ 表头右侧「添加规则」btn-ghost → FORM-SHP-02
  - 金额渲染：`feeUnder==null ? '—' : $X.XX`；`feeOver===0 ? '免邮'(text-ok) : $X.XX`；`threshold==null ? '—' : $X`（DEC-SHP-3 语义可视化，沿用原型 免邮=text-ok 色）
  - loading 骨架行；空表 EmptyState
- COMP-SHP-04 `CarrierFormDrawer`（FORM-SHP-01 载体）：Headless-UI Dialog（**根组件配 as，CP-072**），字段 name*/zones/lead_time/status(enabled|disabled 单选)
- COMP-SHP-05 `RateFormDrawer`（FORM-SHP-02 载体）：字段 zone*/fee_under/fee_over/threshold（number 输入，min=0 step=0.01）+ 表单内灰字说明「区域 / 承运商名 = 该承运商专属价；仅填区域 = 该区域全部启用承运商兜底价」（F-036 约定运营可见化）

## 5. 表单交互（FORM-SHP）

- FORM-SHP-01 承运方表单：
  - 前端校验镜像 V-SHP-003~006：name trim 非空 ≤64、zones ≤255、lead_time ≤64、status 必选；提交置 `saving` 防双击
  - 422901 → details.field 分发 inline；409902（编辑中改 disabled）→ status 字段 inline「至少保留一个启用的承运方」
- FORM-SHP-02 规则行表单：
  - 镜像 V-SHP-009/010：zone trim 非空 ≤128、费用三字段空或 ≥0 且两位小数
  - 409901 → zone inline「同名规则行已存在」；422901 → 字段分发
- FORM-SHP-03 删除确认：
  - 承运方：文案「删除后该承运商不再出现在结算选项；历史订单不受影响。确认删除？」；enabled 且 enabledCount===1 时按钮置灰（预判）
  - 规则行：通用确认文案；当目标行 zone 不含「 / 」且为 `Rest of World` 时追加警示「这是全局兜底行，删除后未配置区域将无运费报价」（CV-SHP-006）
- FORM-SHP-04 Toggle 行内切换：无表单，乐观更新 + 失败回滚（STORE-SHP-01.toggleCarrier）；置灰预判同上

## 6. 原型对照表（强对照自检）

| 原型元素（prototype/portal-admin/src/views/Shipping.vue） | 本设计处置 | 偏离声明 |
|---|---|---|
| PageHeader（Settings/物流配置/副标题） | 原样保留 | 无 |
| actions 槽「保存配置」btn-gold | 移除 | DEC-SHP-FE-1（无批量契约，行内即时持久化） |
| 双 panel lg:grid-cols-2 栅格 | 原样保留 | 无 |
| 承运方行（name/zones·时效/Toggle） | 数据源 mock→E-SHP-01；Toggle 接 E-SHP-05 | 行 hover 增编辑/删除按钮（DEC-SHP-FE-2 数据行为补齐） |
| 「添加承运方」btn-ghost | 接 FORM-SHP-01 → E-SHP-02 | 无 |
| 邮费表 data-table 四列 | 数据源 mock→E-SHP-06；免邮 text-ok 渲染保留 | 增「操作」列与「添加规则」入口（DEC-SHP-FE-2） |
| USPS Priority disabled 行视觉 | 由种子数据驱动复现（DEC-SHP-6） | 无 |
| token（panel/btn-gold/btn-ghost/data-table/text-ok） | 全部复用，零新增样式 | 无 |

## 7. 错误呈现汇总（error-strategy portal-admin 约定落点）

| code | 呈现 |
|---|---|
| 422901 | 表单字段 inline（details.field 分发） |
| 409901 | zone 字段 inline「同名规则行已存在」 |
| 409902 | Toggle 回滚 + toast / 表单 status inline「至少保留一个启用的承运方」 |
| 404901/404902 | toast「数据已变更，列表已刷新」+ 整列表 refetch（他端并发删除场景） |
| 401 `40100` | client.ts 拦截器清 token 跳 /login（既有） |
| 403 `40300` | toast「无权限」；菜单/路由由 permissions 预隐藏 |
| 5xx | toast「操作失败」+ 保留表单现场 |
