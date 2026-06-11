# REQ-CATALOG-001：portal-admin 原型对齐修复（admin-prototype-alignment）

- **change**：admin-prototype-alignment
- **类型**：alignment（原型 = 验收基准；portal-api-integration 验收发现实现未对齐原型，本次回对）
- **领域**：catalog（主）/ trading / analytics / showroom（仅前端入口）
- **权威产物**：er-diagram.yml（17 实体）、state-machine.yml（35 状态机）、business-flow.yml（23 流程，新增 5 条）、acceptance.yml（899 场景）、feature-gap-report.yml（ALIGN-001~039）
- **生成日期**：2026-06-11

## 背景

portal-api-integration 完成两端 API 对接后，验收发现 16 个 portal-admin 页面存在与原型未对齐的功能缺口（用户点名：属性定义、商品分类）。经逐页对比审计（page_diff_summary）与用户逐项裁决（decision.md 决策 1~9），确定修复范围：高+中严重度缺口回对原型，低严重度显式偏离接受现状，实现端增强能力（三语/CRUD 抽屉/确认弹窗等）全部保留。

核心修复：
1. **Categories 回对原型 3-Tab**（推翻 PAGE-CAT-A04）：属性字典 + 品类×属性矩阵合并回 Categories；废弃独立 AttributeSets 页（菜单移除 + 路由重定向）；品类卡片属性集徽章恢复点击直开配置抽屉；矩阵 Tab 补「子品类属性覆盖」只读概览卡片区
2. **Products**：勾选框列 + 批量操作栏（批量上架/下架/推荐/删除，后端新增批量端点逐条容错）、导出按钮（后端 CSV 端点）、销量列、商品类型/标签筛选
3. **Dashboard**：「发布站点」按钮 + 快捷入口恢复 5 项
4. **Orders**：表格列严格回对原型（地区/商品数 替换 币种/承运，AdminOrderListItem 扩展 country/itemCount）+ 导出订单按钮
5. **Refunds**：回对行内同意/拒绝按钮，拒绝行内展开填原因（后端必填约束不变）

后端变更：3 个新增端点（商品批量操作 / 商品 CSV 导出 / 订单 CSV 导出）+ 1 个 DTO 扩展，零 DB schema 变更，沿用既有错误码与 RBAC（decision.md 决策 10 与「后端关键决策」章节）。

## 需求清单（requirement_ids）

| 编号 | 页面 | 类型 | 缺口内容 | 严重度 |
|------|------|------|---------|--------|
| ALIGN-001 | Categories | missing_module | [auto] | high |
| ALIGN-002 | Categories | wrong_purpose | 编辑抽屉增加 EN/ES/FR 三语 name tab（原型无） | medium |
| ALIGN-003 | Categories | wrong_purpose | 维度删除收紧为 409506 引导（E-CAT-30 显式偏离） | medium |
| ALIGN-004 | AttributeSets | missing_module | [auto] | high |
| ALIGN-005 | AttributeSets | wrong_purpose | Tab 名「品类×属性矩阵」→「属性集×属性矩阵」（矩阵数据维度本质一致，原型列也按属性集分组） | medium |
| ALIGN-006 | AttributeSets | wrong_purpose | 原型矩阵即点即改无保存；实现为 hasUnsavedChanges + 整单保存（E-CAT-21） | medium |
| ALIGN-007 | Products | missing_module | [auto] | high |
| ALIGN-008 | Products | wrong_purpose | 新增排序列（原型无） | medium |
| ALIGN-009 | Dashboard | missing_module | [auto] | high |
| ALIGN-010 | Dashboard | wrong_purpose | KPI 卡 delta 行删除（DEC-ANA-FE-2） | medium |
| ALIGN-011 | Dashboard | wrong_purpose | 待办瓦片 4 列→3 列（DEC-ANA-FE-3） | medium |
| ALIGN-012 | Orders | missing_module | [auto] | high |
| ALIGN-013 | Orders | wrong_purpose | 表格列：地区→币种、商品数→承运 | medium |
| ALIGN-014 | Orders | wrong_purpose | 状态 Tab 6→8（补 cancelled/refunded） | medium |
| ALIGN-015 | Orders | wrong_purpose | 搜索范围：客户名→客户邮箱 | medium |
| ALIGN-016 | Analytics | wrong_purpose | 商品热度列：库存/状态→销售额（DEC-ANA-FE-7） | medium |
| ALIGN-017 | Analytics | wrong_purpose | 流量来源/漏斗标签中文化 | medium |
| ALIGN-018 | ProductEdit | wrong_purpose | SKU 颜色：预设列表选择→自由文本输入 | medium |
| ALIGN-019 | ProductEdit | wrong_purpose | 多币种价格 4 列→5 列（USD disabled + auto placeholder） | medium |
| ALIGN-020 | ProductEdit | wrong_purpose | 保存并生成静态页：跳转 /publish→API 触发 | medium |
| ALIGN-021 | OrderDetail | wrong_purpose | 操作按钮固定→按状态条件渲染 | medium |
| ALIGN-022 | OrderDetail | wrong_purpose | 承运方 mock→API 枚举 | medium |
| ALIGN-023 | OrderDetail | wrong_purpose | 金额拆分补 Gift Wrapping/优惠行 | medium |
| ALIGN-024 | Refunds | wrong_purpose | 同意/拒绝行内按钮→弹窗二次确认 | medium |
| ALIGN-025 | Refunds | wrong_purpose | 已处理状态增加退款/退货单号展示 | medium |
| ALIGN-026 | Reviews | missing_module | [auto] | high |
| ALIGN-027 | Reviews | wrong_purpose | Q&A 搜索全局→当前页内存过滤（FORM-REV-A01 显式偏离②） | medium |
| ALIGN-028 | Reviews | wrong_purpose | 状态 chips 计数仅保留待审核角标（显式偏离①） | medium |
| ALIGN-029 | Reviews | wrong_purpose | 删除回复直接点击→二次确认（CP-071） | medium |
| ALIGN-030 | Shipping | missing_module | [auto] | high |
| ALIGN-031 | Shipping | wrong_purpose | 承运方/邮费表只读→行内编辑/删除（DEC-SHP-FE-2） | medium |
| ALIGN-032 | Shipping | wrong_purpose | 满额包邮 0 显示「免邮」 | medium |
| ALIGN-033 | Promotions | wrong_purpose | 状态枚举补 expired/ended | medium |
| ALIGN-034 | Promotions | wrong_purpose | 已结束活动禁止编辑 | medium |
| ALIGN-035 | Banners | wrong_purpose | Toggle 简单开关→published/archived 三态映射 + 409703 回滚 | medium |
| ALIGN-036 | ContentBlog | wrong_purpose | 发布按钮增加下线/重新发布流转 | medium |
| ALIGN-037 | ContentBlog | wrong_purpose | 预览增加 slug 校验+新窗口 | medium |
| ALIGN-038 | ContentWeddings | wrong_purpose | 操作按钮增加发布/下线流转 | medium |
| ALIGN-039 | ContentLookbook | wrong_purpose | Lookbook/Guide 卡片增加发布/下线按钮 | medium |

> 注：低严重度条目若对应 decision.md 决策 8 的豁免清单（DEC-ANA-FE-2/3/7、FORM-REV-A01、DEC-SHP-FE-1/2），验收时按「接受现状」处理，不要求回对。

## 验收基准

- 功能验收：acceptance.yml（899 场景，其中 func 23 条对应 business-flow 23 流程）
- 边界验收：boundary-scenarios.yml（616 条，7 类全覆盖），重点：批量操作部分失败清单（含 409509）、导出 10000 行截断、退款拒绝原因必填、矩阵未保存变更提示、地址快照缺国家显示「—」
- UI 验收：原型 16 页全量对照（decision.md「原型强对照约束」，豁免清单除外）
- 详见 acceptance-baseline.md
