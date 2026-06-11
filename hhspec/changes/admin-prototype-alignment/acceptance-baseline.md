# 验收基准：admin-prototype-alignment

生成时间：2026-06-11T04:07:52Z

> 变更类型：alignment（原型 = 验收基准）。差异豁免清单见 decision.md 决策 8/9。

## FUNC — 功能验收

（来源：acceptance.yml func 场景，由 business-flow.yml 23 个流程派生）

| 编号 | 场景标题 | 页面 | 来源 |
|------|---------|------|------|
| FUNC-001 | checkout_order | - | acceptance.yml #s-877 |
| FUNC-002 | order_timeout_cancel | - | acceptance.yml #s-878 |
| FUNC-003 | admin_ship_complete | - | acceptance.yml #s-879 |
| FUNC-004 | refund_flow | - | acceptance.yml #s-880 |
| FUNC-005 | review_moderation_flow | - | acceptance.yml #s-881 |
| FUNC-006 | content_publish_invalidate | - | acceptance.yml #s-882 |
| FUNC-007 | order_event_dispatch | - | acceptance.yml #s-883 |
| FUNC-008 | dashboard_analytics_read | - | acceptance.yml #s-884 |
| FUNC-009 | coupon_redeem | - | acceptance.yml #s-885 |
| FUNC-010 | flash_sale_auto_offline | - | acceptance.yml #s-886 |
| FUNC-011 | transactional_email | - | acceptance.yml #s-887 |
| FUNC-012 | wishlist_manage | - | acceptance.yml #s-888 |
| FUNC-013 | showroom_collaboration | - | acceptance.yml #s-889 |
| FUNC-014 | size_recommendation | - | acceptance.yml #s-890 |
| FUNC-015 | ga4_traffic_fetch | - | acceptance.yml #s-891 |
| FUNC-016 | browse_history_track | - | acceptance.yml #s-892 |
| FUNC-017 | newsletter_subscribe | - | acceptance.yml #s-893 |
| FUNC-018 | contact_submit | - | acceptance.yml #s-894 |
| FUNC-019 | category_attribute_config | - | acceptance.yml #s-895 |
| FUNC-020 | product_batch_ops | - | acceptance.yml #s-896 |
| FUNC-021 | admin_export_csv | - | acceptance.yml #s-897 |
| FUNC-022 | order_list_query | - | acceptance.yml #s-898 |
| FUNC-023 | dashboard_publish_entry | - | acceptance.yml #s-899 |

## ALIGN — 对齐缺口验收（本次核心）

（来源：feature-gap-report.yml，39 条缺口 = requirement_ids；验收时逐条对照原型确认已修复或已豁免）

| 编号 | 页面 | 类型 | 缺口描述 | 严重度 |
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

## EDGE — 边界/异常验收

boundary-scenarios.yml 共 616 条（null/extreme/concurrent/auth/network/integrity/state 7 类全覆盖），
对应 acceptance.yml 中 source=bs 的 616 条场景。重点边界：

- 批量操作部分失败（含 409509 被订单引用不可删）返回成功/失败清单
- 导出超 10000 行截断并提示
- 退款行内拒绝原因为空时阻断提交（后端 422 必填校验）
- 品类×属性矩阵未保存变更离开提示
- 订单地址快照缺国家字段时地区列显示「—」

## UI — UI 验收检查点

### 页面清单
| page_id | 原型文件 | 路由 |
|---------|---------|------|
| portal-admin/Dashboard | prototype/portal-admin/src/views/Dashboard.vue | /dashboard |
| portal-admin/Analytics | prototype/portal-admin/src/views/Analytics.vue | /analytics |
| portal-admin/Products | prototype/portal-admin/src/views/Products.vue | /products |
| portal-admin/ProductEdit | prototype/portal-admin/src/views/ProductEdit.vue | /products/:id/edit |
| portal-admin/Categories | prototype/portal-admin/src/views/Categories.vue | /categories |
| portal-admin/AttributeSets | prototype/portal-admin/src/views/AttributeSets.vue | /categories（合并后重定向） |
| portal-admin/Orders | prototype/portal-admin/src/views/Orders.vue | /orders |
| portal-admin/OrderDetail | prototype/portal-admin/src/views/OrderDetail.vue | /orders/:id |
| portal-admin/Refunds | prototype/portal-admin/src/views/Refunds.vue | /refunds |
| portal-admin/Reviews | prototype/portal-admin/src/views/Reviews.vue | /reviews |
| portal-admin/Shipping | prototype/portal-admin/src/views/Shipping.vue | /shipping |
| portal-admin/Promotions | prototype/portal-admin/src/views/Promotions.vue | /promotions |
| portal-admin/Banners | prototype/portal-admin/src/views/Banners.vue | /banners |
| portal-admin/ContentBlog | prototype/portal-admin/src/views/ContentBlog.vue | /content/blog |
| portal-admin/ContentWeddings | prototype/portal-admin/src/views/ContentWeddings.vue | /content/weddings |
| portal-admin/ContentLookbook | prototype/portal-admin/src/views/ContentLookbook.vue | /content/lookbook |

### 核心约束
→ 见 decision.md 的「原型强对照约束」章节（决策 8 豁免项不计差异）

### 详细字段/交互规格
> 详细页面交互规格将在 L2 前端详设阶段产出（由 ui_test_designer 生成 ui-test-spec.yml），届时请更新此引用。

## PERF — 性能基线

- 订单列表扩展 country/itemCount 后，列表接口 P95 不劣化超过 20%（行计数子查询须命中索引）
- 导出端点采用流式/分页读取，单次导出 ≤ 10000 行

## SEC — 安全要求

- 批量操作/导出沿用 admin JWT + RBAC 既有权限点（前提假设②）
- 导出含客户邮箱 PII：操作记录审计日志（OperationLog）
- 批量端点入参 id 列表须校验归属与上限（防超大请求）
