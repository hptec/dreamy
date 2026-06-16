---
phase: L1_decisions_done
challenger_rounds: 2
session_id: ''
change_name: admin-prototype-alignment
codebase_path: /Volumes/MAC/workspace/dreamy
change_type: alignment
key_decisions:
- id: D1
  question: PAGE-CAT-A04 信息架构裁决
  answer: 回对原型：属性字典+品类×属性矩阵合并回 Categories 作为第二 Tab（3-Tab），废弃独立 AttributeSets 页（菜单移除+路由重定向），Tab 文案恢复「品类×属性矩阵」
  rationale: 用户验收以原型为基准
  source: user_adjudication
- id: D2
  question: Categories 属性集徽章交互
  answer: 恢复原型交互：点击徽章直开属性三态配置抽屉
  rationale: 原型交互快捷
  source: user_adjudication
- id: D3
  question: 子品类属性覆盖卡片区
  answer: 矩阵 Tab 内补齐只读概览卡片区（按根品类分组展示 delta）
  rationale: 原型有、实现缺失
  source: user_adjudication
- id: D4
  question: Products 缺口修复
  answer: 补齐勾选框列+批量操作栏、导出按钮、销量列、商品类型/标签筛选
  rationale: 原型功能缺失
  source: user_adjudication
- id: D5
  question: Dashboard 缺口修复
  answer: 补「发布站点」按钮+快捷入口恢复 5 项
  rationale: 原型功能缺失
  source: user_adjudication
- id: D6
  question: Orders 表格列裁决
  answer: 严格回对原型列（地区/商品数替换币种/承运）+导出订单按钮
  rationale: 用户选择严格对齐
  source: user_adjudication
- id: D7
  question: Refunds 审批交互裁决
  answer: 回对行内同意/拒绝；拒绝行内展开填原因（后端必填不变）
  rationale: 兼顾原型观感与后端约束
  source: user_adjudication
- id: D8
  question: 低严重度显式偏离处置
  answer: 全部接受现状（DEC-ANA-FE-2/3/7、FORM-REV-A01、DEC-SHP-FE-1/2）
  rationale: 用户裁决未勾选回对
  source: user_adjudication
- id: D9
  question: 反向缺口处置
  answer: 实现端增强能力全部保留（三语/CRUD 抽屉/确认弹窗/骨架屏等）
  rationale: 真实业务能力不回退
  source: user_adjudication
- id: D10
  question: 批量操作 API 形态
  answer: 后端新增批量端点，逐条容错返回成功/失败清单（删除复用 409509），记录操作日志
  rationale: 避免 N 次请求
  source: backend_inference
  dimension: BE-DIM-4
  inference_tag: 事务
- id: D11
  question: 导出实现方式
  answer: 后端 CSV 导出端点，按筛选条件全量导出，上限 10000 行，记录操作日志
  rationale: 数据完整可控、PII 可审计
  source: backend_inference
  dimension: BE-DIM-8
  inference_tag: 限流降级
- id: D12
  question: Orders 列表数据扩展
  answer: AdminOrderListItem 扩展 country+itemCount，列表 SQL join/子查询提供
  rationale: 避免前端 N+1
  source: backend_inference
  dimension: BE-DIM-8
  inference_tag: 限流降级
project_tech_stack:
  frontend: pnpm-vue3-headless
  backend:
  - java-gradle
  prototype: pnpm-vue3-headless
  mirrors:
    npm: https://registry.npmmirror.com
    maven: https://maven.aliyun.com/repository/public
    gradle_plugins: https://maven.aliyun.com/repository/gradle-plugin
detected_tech_specs:
- id: java-gradle
  path: backend/build.gradle
  trigger: build.gradle exists
- id: frontend-vue3-vite
  path: frontend/portal-admin/vite.config.js
  trigger: vite.config.js exists (monorepo fallback)
- 已验证：所有声明技术栈与实际代码一致
has_ui: true
prototype_dir: /Volumes/MAC/workspace/dreamy/hhspec/prototype/portal-admin
feature_map_path: hhspec/prototype/feature-map.md
linked_features: []
requirement_ids:
- ALIGN-001
- ALIGN-002
- ALIGN-003
- ALIGN-004
- ALIGN-005
- ALIGN-006
- ALIGN-007
- ALIGN-008
- ALIGN-009
- ALIGN-010
- ALIGN-011
- ALIGN-012
- ALIGN-013
- ALIGN-014
- ALIGN-015
- ALIGN-016
- ALIGN-017
- ALIGN-018
- ALIGN-019
- ALIGN-020
- ALIGN-021
- ALIGN-022
- ALIGN-023
- ALIGN-024
- ALIGN-025
- ALIGN-026
- ALIGN-027
- ALIGN-028
- ALIGN-029
- ALIGN-030
- ALIGN-031
- ALIGN-032
- ALIGN-033
- ALIGN-034
- ALIGN-035
- ALIGN-036
- ALIGN-037
- ALIGN-038
- ALIGN-039
linked_prototype_snapshots:
- page_id: portal-admin/Dashboard
  file: prototype/portal-admin/src/views/Dashboard.vue
  hash_at_link: sha256:49f3339120e5e943d5a703be6f38c6494a268bff602c98e1cfd84ec57af98ac4
  linked_at: '2026-06-11T04:13:39Z'
- page_id: portal-admin/Analytics
  file: prototype/portal-admin/src/views/Analytics.vue
  hash_at_link: sha256:c02f088cf89355c4a8c6fdd5736202b00d0eb9e934756e582b9bc9985c298cc0
  linked_at: '2026-06-11T04:13:39Z'
- page_id: portal-admin/Products
  file: prototype/portal-admin/src/views/Products.vue
  hash_at_link: sha256:e1bb77083f6dd8cdafefaa83ab1ca08403e8fda205c7a558dac8970648181ab8
  linked_at: '2026-06-11T04:13:39Z'
- page_id: portal-admin/ProductEdit
  file: prototype/portal-admin/src/views/ProductEdit.vue
  hash_at_link: sha256:124d14a9b3508364a23a0fbb20b4d36569987737c9a657824e59e9ec03966e14
  linked_at: '2026-06-11T04:13:39Z'
- page_id: portal-admin/Categories
  file: prototype/portal-admin/src/views/Categories.vue
  hash_at_link: sha256:9cd49bffb471e6602776666456e1cb65be361149e830e324f94966cdf4bd2e09
  linked_at: '2026-06-11T04:13:39Z'
- page_id: portal-admin/AttributeSets
  file: prototype/portal-admin/src/views/AttributeSets.vue
  hash_at_link: sha256:28e68e51c9dad5b2e99d02daaabea24b1a22f8a05220c9b43b8045e4d8db69df
  linked_at: '2026-06-11T04:13:39Z'
- page_id: portal-admin/Orders
  file: prototype/portal-admin/src/views/Orders.vue
  hash_at_link: sha256:73107e9a4d154841f76e21b7f834a6f9f6618de809694a126f539afe9102a521
  linked_at: '2026-06-11T04:13:39Z'
- page_id: portal-admin/OrderDetail
  file: prototype/portal-admin/src/views/OrderDetail.vue
  hash_at_link: sha256:4d4f02137afa1158e4bdd105258b53cd1862922cabed774c19aafe7c82e57ba3
  linked_at: '2026-06-11T04:13:39Z'
- page_id: portal-admin/Refunds
  file: prototype/portal-admin/src/views/Refunds.vue
  hash_at_link: sha256:4330543f08c571d21296a30d8b9c2c4c12045c3ff996266b3099d535bbfe83cc
  linked_at: '2026-06-11T04:13:39Z'
- page_id: portal-admin/Reviews
  file: prototype/portal-admin/src/views/Reviews.vue
  hash_at_link: sha256:08c456ab8b4cb61d31bec60cac32afc24ffe4b7c5602245d2a6f68c636c3cb21
  linked_at: '2026-06-11T04:13:39Z'
- page_id: portal-admin/Shipping
  file: prototype/portal-admin/src/views/Shipping.vue
  hash_at_link: sha256:bcbb05da6ad99f6fd4824cc0e55dc5a307af21f505891711bb5cbec661af39f4
  linked_at: '2026-06-11T04:13:39Z'
- page_id: portal-admin/Promotions
  file: prototype/portal-admin/src/views/Promotions.vue
  hash_at_link: sha256:7f6e833a82018653c0e8ea6104cad42853ba4f64cc9b6002a1f264b811d0052b
  linked_at: '2026-06-11T04:13:39Z'
- page_id: portal-admin/Banners
  file: prototype/portal-admin/src/views/Banners.vue
  hash_at_link: sha256:531260e03d17f95e090de91ffe3166e64766133069b701f3506e21aff3e292b8
  linked_at: '2026-06-11T04:13:39Z'
- page_id: portal-admin/ContentBlog
  file: prototype/portal-admin/src/views/ContentBlog.vue
  hash_at_link: sha256:5922ace236f3d6a0f1dff07a317f2c5f2fa7e8d02d244f3512d8541ead90d847
  linked_at: '2026-06-11T04:13:39Z'
- page_id: portal-admin/ContentWeddings
  file: prototype/portal-admin/src/views/ContentWeddings.vue
  hash_at_link: sha256:1ffe21f155e4ed85f1f74f77bed6caa6162bf0f647a538a2de889183af96ea70
  linked_at: '2026-06-11T04:13:39Z'
- page_id: portal-admin/ContentLookbook
  file: prototype/portal-admin/src/views/ContentLookbook.vue
  hash_at_link: sha256:424fe52c8f356508be2d42967758688434857fb478ed513ce083485623790471
  linked_at: '2026-06-11T04:13:39Z'
operation_paths_file: hhspec/changes/admin-prototype-alignment/operation-paths.yml
operation_paths_files: {}
proto_is_frontend: true
field_inventory_path: hhspec/changes/admin-prototype-alignment/field-inventory.yml
domain_data_model_path: ''
global_field_dict_path: ''
task_manifest_path: hhspec/changes/admin-prototype-alignment/task-manifest.yml
backend_inference_path: hhspec/changes/admin-prototype-alignment/backend-flow-inference.md
backend_requirements_resolved: true
l0_mode: incremental
baseline_context_path: hhspec/changes/admin-prototype-alignment/.baseline-context.yml
l0_breaking_changes_count: 0
content_index_path: hhspec/prototype/portal-admin/content-index.json
content_index_paths: {}
content_index_missing_portals: []
domain_code: catalog
specs_dir: hhspec/specs
open_questions: []
created_at: '2026-06-11T02:49:22Z'
updated_at: '2026-06-11T04:13:39Z'
page_diff_summary:
- page: Categories
  prototype_file: hhspec/prototype/portal-admin/src/views/Categories.vue
  impl_file: frontend/portal-admin/src/views/Categories.vue
  gap_ids:
  - ALIGN-001
  - ALIGN-002
  - ALIGN-003
  severity: high
- page: AttributeSets
  prototype_file: hhspec/prototype/portal-admin/src/views/AttributeSets.vue
  impl_file: frontend/portal-admin/src/views/AttributeSets.vue
  gap_ids:
  - ALIGN-004
  - ALIGN-005
  - ALIGN-006
  severity: high
- page: Products
  prototype_file: hhspec/prototype/portal-admin/src/views/Products.vue
  impl_file: frontend/portal-admin/src/views/Products.vue
  gap_ids:
  - ALIGN-007
  - ALIGN-008
  severity: high
- page: Dashboard
  prototype_file: hhspec/prototype/portal-admin/src/views/Dashboard.vue
  impl_file: frontend/portal-admin/src/views/Dashboard.vue
  gap_ids:
  - ALIGN-009
  - ALIGN-010
  - ALIGN-011
  severity: high
- page: Orders
  prototype_file: hhspec/prototype/portal-admin/src/views/Orders.vue
  impl_file: frontend/portal-admin/src/views/Orders.vue
  gap_ids:
  - ALIGN-012
  - ALIGN-013
  - ALIGN-014
  - ALIGN-015
  severity: high
- page: Analytics
  prototype_file: hhspec/prototype/portal-admin/src/views/Analytics.vue
  impl_file: frontend/portal-admin/src/views/Analytics.vue
  gap_ids:
  - ALIGN-016
  - ALIGN-017
  severity: medium
- page: ProductEdit
  prototype_file: hhspec/prototype/portal-admin/src/views/ProductEdit.vue
  impl_file: frontend/portal-admin/src/views/ProductEdit.vue
  gap_ids:
  - ALIGN-018
  - ALIGN-019
  - ALIGN-020
  severity: medium
- page: OrderDetail
  prototype_file: hhspec/prototype/portal-admin/src/views/OrderDetail.vue
  impl_file: frontend/portal-admin/src/views/OrderDetail.vue
  gap_ids:
  - ALIGN-021
  - ALIGN-022
  - ALIGN-023
  severity: medium
- page: Refunds
  prototype_file: hhspec/prototype/portal-admin/src/views/Refunds.vue
  impl_file: frontend/portal-admin/src/views/Refunds.vue
  gap_ids:
  - ALIGN-024
  - ALIGN-025
  severity: medium
- page: Reviews
  prototype_file: hhspec/prototype/portal-admin/src/views/Reviews.vue
  impl_file: frontend/portal-admin/src/views/Reviews.vue
  gap_ids:
  - ALIGN-026
  - ALIGN-027
  - ALIGN-028
  - ALIGN-029
  severity: high
- page: Shipping
  prototype_file: hhspec/prototype/portal-admin/src/views/Shipping.vue
  impl_file: frontend/portal-admin/src/views/Shipping.vue
  gap_ids:
  - ALIGN-030
  - ALIGN-031
  - ALIGN-032
  severity: high
- page: Promotions
  prototype_file: hhspec/prototype/portal-admin/src/views/Promotions.vue
  impl_file: frontend/portal-admin/src/views/Promotions.vue
  gap_ids:
  - ALIGN-033
  - ALIGN-034
  severity: medium
- page: Banners
  prototype_file: hhspec/prototype/portal-admin/src/views/Banners.vue
  impl_file: frontend/portal-admin/src/views/Banners.vue
  gap_ids:
  - ALIGN-035
  severity: medium
- page: ContentBlog
  prototype_file: hhspec/prototype/portal-admin/src/views/ContentBlog.vue
  impl_file: frontend/portal-admin/src/views/ContentBlog.vue
  gap_ids:
  - ALIGN-036
  - ALIGN-037
  severity: medium
- page: ContentWeddings
  prototype_file: hhspec/prototype/portal-admin/src/views/ContentWeddings.vue
  impl_file: frontend/portal-admin/src/views/ContentWeddings.vue
  gap_ids:
  - ALIGN-038
  severity: medium
- page: ContentLookbook
  prototype_file: hhspec/prototype/portal-admin/src/views/ContentLookbook.vue
  impl_file: frontend/portal-admin/src/views/ContentLookbook.vue
  gap_ids:
  - ALIGN-039
  severity: medium
---

<!-- 探索进展摘要 -->
- Phase 1~5 全部完成（exit-check PASS，teardown 已执行）
- 16 页原型 vs 实现逐页对比完成，39 条缺口（ALIGN-001~039）= requirement_ids，明细见 feature-gap-report.yml
- 12 项关键决策（D1~D12）已沉淀到 decision.md（含后端关键决策章节与原型强对照约束）
- Challenger 第 2 轮 PASS；验收基准 acceptance-baseline.md 已生成
- REQ 文档：hhspec/specs/requirements/catalog/REQ-CATALOG-001-admin-prototype-alignment.md
- 注：本文件曾被 teardown 误删后重建，linked_at 时间戳为重建时间
