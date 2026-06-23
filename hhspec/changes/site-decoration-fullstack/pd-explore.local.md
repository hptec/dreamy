---
phase: L1_decisions_done
challenger_rounds: 2
session_id: b08fd9ee-5377-4af7-8fbd-73d4a29dae41
change_name: site-decoration-fullstack
codebase_path: /Volumes/MAC/workspace/dreamy
change_type: alignment
domain_code: site_builder
specs_dir: hhspec/specs
key_decisions:
- id: KD-1
  topic: Publish.vue 范围
  decision: 仅保持当前缓存失效能力，不扩展 SSG 发布中心
  rationale: 原型描述的 SSG 发布中心（diff+build+受影响页面+历史+回滚）范围过大；当前缓存失效已能满足"保存即生效"的诉求。portal-store
    首页为 force-dynamic 无 SSG，本变更不引入 SSG，保持 force-dynamic + JetCache 两级缓存 + in-process
    失效链（GRD-W04 修正：澄清架构实际）
  impact: Publish.vue 不改造，HomeBuilder/NavigationConfig 保存后通过 in-process cache.invalidateFamily
    + publisher.publish 触发缓存失效（非 HTTP 自调，GRD-W01 修正）
- id: KD-2
  topic: Hero 区块数据源
  decision: 复用 Banner position=HERO
  rationale: Banner 已有完整 i18n（BannerTranslation）+ 时间窗 + 上下线机制；避免数据重复
  impact: HomePageSection 的 Hero 类型只存 enabled/sort_order，文案/图片/CTA 从 Banner position=HERO
    读取；管理端 Hero 区块属性编辑表单要联动 Banner 编辑
- id: KD-3
  topic: HomePageSection i18n 策略
  decision: 单独 home_section_translations 表（与 Banner 翻译表对齐）
  rationale: 与项目现有 i18n 模式一致（BannerTranslation 已实现）；查询效率高；支持多语言扩展
  impact: 表设计为 home_sections + home_section_translations 双表；API 契约要支持多语言读写
- id: KD-4
  topic: HomeBuilder/NavigationConfig 发布机制
  decision: 保存即发布（与 Banners 一致）
  rationale: 交互简单，与现有 Banners 模式一致；保存后立即触发缓存失效
  impact: 无草稿状态机；保存 API 直接写入 DB + 触发缓存失效；UI 无"发布"按钮，只有"保存"按钮
- id: KD-5
  topic: 消费端改造范围
  decision: 首页动态渲染 + header/footer/公告条 全量改造
  rationale: 用户明确要求"包括消费端相关前后端代码"；导航配置必须在消费端 header/footer 生效才有意义
  impact: 消费端改造点：(1)portal-store/app/[locale]/page.tsx 动态渲染区块；(2)portal-store/app/[locale]/layout.tsx
    读取导航配置渲染 header/footer；(3)顶部公告条组件动态渲染
- id: KD-6
  topic: Mega Menu 列存储
  decision: JSON 字段 mega_menu_json（NavigationItem 内嵌）
  rationale: Mega Menu 列是导航项的从属数据，整体保存更方便；避免表数过多
  impact: NavigationItem 表增加 mega_menu_json 字段；Repository 不需要 NavigationMegaMenuColumn
    表
- id: KD-7
  topic: 公告条数据源
  decision: 独立 announcement 域
  rationale: 公告条可复用于其他位置（如分类页、活动页）；不依赖导航
  impact: 新建 announcements 表 + AnnouncementController（admin）+ StoreContentController
    扩展（store）；与 navigation 域解耦
- id: KD-8
  topic: ThemeCards 区块数据源
  decision: 引用分类 type=theme
  rationale: 与项目现有分类体系一致；避免数据重复；分类管理已上线
  impact: HomePageSection 的 ThemeCards 类型只存 enabled/sort_order + 显示数量；实际主题从 taxonomy
    type=theme 读取
- id: KD-9
  topic: ProductRail source=recommend 人工推荐
  decision: 支持 recommend + 商品选择器
  rationale: 人工推荐是运营核心能力，允许运营精选爆款上首页
  impact: HomePageSection 的 ProductRail 类型 data_json 增加 product_ids 数组（source=recommend
    时启用）；管理端需要商品选择器 UI 组件（可复用现有商品选择器）
- id: KD-10
  topic: EditorialFeature 区块数据源
  decision: 引用 weddings 数据 + limit（方案 B）
  rationale: 婚纱是高客单价情感决策商品，真实婚礼故事是核心转化要素（社交证明）；数据已就绪（fetchStoreWeddings）；与 Hero 区块差异化；竞品（David's
    Bridal、BHLDN）首页都有 Real Weddings 精选
  impact: HomePageSection 的 EditorialFeature 类型存储 title + limit + 可选 wedding_ids（人工精选，空则按
    sort_order 取前 N 条）；消费端调用 fetchStoreWeddings 渲染卡片列表
- id: KD-11
  topic: Newsletter 区块范围
  decision: 完整订阅表单（邮箱输入 + API）
  rationale: 首页是订阅转化主入口；复用 EmailMarketing 模块的订阅者存储
  impact: 新建订阅 API（POST /api/store/subscribers/subscribe）；Newsletter 区块存储 title +
    副文案 + CTA 文案；消费端渲染邮箱输入 + 提交按钮
- id: KD-BE-1
  topic: 后端维度 - 事务/并发/幂等（BE-DIM-4）
  decision: 沿用项目现有模式
  rationale: HomePageSection + home_section_translations 双表写入用 @Transactional；保存即发布无状态机；保存幂等（相同配置重复保存产生相同结果）
  source: code_feature
  dimension: BE-DIM-4
- id: KD-BE-2
  topic: 后端维度 - 安全与权限（BE-DIM-6）
  decision: 沿用 RBAC + 新增权限码
  rationale: 新增 AdminHomeSectionController 用 @RequirePermission("/site/home")；AdminNavigationController
    用 @RequirePermission("/site/navigation")；AdminAnnouncementController 用 @RequirePermission("/site/announcement")；消费端读取
    API 匿名可读
  source: code_feature
  dimension: BE-DIM-6
- id: KD-BE-3
  topic: 后端维度 - 可观测性/审计（BE-DIM-7）
  decision: 复用 OperationLogs 模块 + 缓存失效日志
  rationale: 配置变更日志走 OperationLogs（已有）；缓存失效日志走 AdminCacheController.invalidation-logs（已有）；不新建独立审计模块
  source: code_feature
  dimension: BE-DIM-7
- id: KD-BE-4
  topic: 后端维度 - 性能/缓存/限流（BE-DIM-8）
  decision: JetCache 两级缓存 + 保存触发同步失效
  rationale: 消费端读取 API 走 JetCache（Caffeine + Redis，与项目现有 consumer-read-apis 配置一致）；HomeBuilder/NavigationConfig
    保存后同步调用 POST /api/admin/cache/invalidate；缓存 key 按 portal+locale 维度；不做防抖（保存频率低）
  source: code_feature
  dimension: BE-DIM-8
- id: KD-BE-5
  topic: 后端维度 - 外部集成（BE-DIM-5）
  decision: 跨 domain 通过 Service 接口调用，不跨表查询
  rationale: HomeBuilder Hero 区块读取 Banner 通过 BannerService 查询（不直接查 Banner 表）；NavigationConfig
    引用 taxonomy 通过 TaxonomyService 校验和派生；保持 domain 边界
  source: code_feature
  dimension: BE-DIM-5
- id: KD-12
  topic: NewsletterSubscriber 退订状态机（GRD-001 修复）
  decision: 本期不扩展退订状态机，保留基线模式
  rationale: 基线 NewsletterSubscriber 仅 subscribed_at 单字段；本期订阅即生效、无退订；acceptance s-017~s-022
    退订/重订阅场景裁剪
  impact: subscriber_status 状态机简化为单态 subscribed；er-diagram Subscriber 实体移除 status/unsubscribed_at
    字段；不新建退订 API
- id: KD-13
  topic: 订阅 API 路径（GRD-009 修复）
  decision: 扩展基线 POST /api/store/newsletter，新增 source=4(HOME_BLOCK)
  rationale: 基线已有 /api/store/newsletter（E-MKT-11）；不新建 /api/store/subscribers/subscribe；避免
    API 重复
  impact: NewsletterSource IntEnum 新增 HOME_BLOCK(4)；er-diagram Subscriber.source 改为
    IntEnum [1=FOOTER,2=MODAL,3=EXIT_INTENT,4=HOME_BLOCK]；KD-11 的"新建订阅 API"修正为"扩展基线
    API"
- id: KD-14
  topic: Hero 第二 CTA 数据源（GRD-010 修复）
  decision: Banner 实体新增 cta_text_secondary + cta_link_secondary 字段（含 BannerTranslation）
  rationale: 电商行业 Banner 通常支持多 CTA；与 KD-2 纯派生原则一致；运营在 Banner 编辑页统一管理两个 CTA
  impact: Banner entity + BannerTranslation 新增 cta_text_secondary + cta_link_secondary；er-diagram
    modified_entities 声明 Banner added_fields；AdminBannerController 的 BannerUpsert
    DTO 扩展
- id: KD-15
  topic: 限界上下文归属（L1 决策）
  decision: 新建 site_builder 限界上下文（domain_code=site_builder）
  rationale: HomeBuilder/NavigationConfig/Announcement 职责边界清晰，与 marketing/content（Banner）解耦；便于独立演进
  impact: domains.yml 新增 site_builder domain（domain_code=site_builder）；新增 AdminHomeSectionController/AdminNavigationController/AdminAnnouncementController
    须登记 MarketingExceptionHandler assignableTypes；Subscriber 保留在 subscriber 域
- id: KD-16
  topic: 翻译表策略修正（L1 决策，覆盖 KD-3）
  decision: 合并翻译表到主表 JSON 列（不建独立 *_translations 表）
  rationale: 减少表数量、简化 DDL 和 Repository；接受与 BannerTranslation 独立表模式的偏离作为技术债
  impact: '覆盖 KD-3 的"单独 home_section_translations 表"决策：

    - home_sections 表增加 i18n_json JSON 列（存多语言文案）

    - navigation_items 表增加 i18n_json JSON 列

    - footer_columns/footer_links 表增加 i18n_json JSON 列

    - announcements 表增加 i18n_json JSON 列

    - 不建 home_section_translations / navigation_translations / footer_column_translations
    / footer_link_translations / announcement_translations 表

    - GRD-W08 推荐的 FooterColumnTranslation/FooterLinkTranslation 不采纳

    - i18n_json 结构：{"en": {"title": "...", "subtitle": "..."}, "zh": {...}, "es":
    {...}}

    '
- id: KD-17
  topic: Breaking change 处理（L1 决策）
  decision: 接受 breaking change（BannerPosition.TOPBAR 废弃公告语义 + 消费端 layout.tsx 改造）
  rationale: KD-7 已决定独立 announcement 域；TOPBAR Banner 公告语义必须废弃以避免双数据源；存量数据脚本迁移
  impact: '(1) BannerPosition.TOPBAR 标记 @Deprecated，存量 TOPBAR Banner 公告数据脚本迁入 announcements
    表

    (2) 消费端 layout.tsx 改调 GET /api/store/content/announcements 读取公告（原 fetchStoreBanners(TOPBAR)
    废弃）

    (3) /api/store/newsletter 扩展 source=4(HOME_BLOCK) 向后兼容（非 breaking）

    '
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
  path: frontend/portal-admin/vite.config.ts
  trigger: vite.config.ts exists (monorepo fallback)
- id: frontend-vue3-vite
  path: frontend/portal-admin/vite.config.js
  trigger: vite.config.js exists (monorepo fallback)
has_ui: true
prototype_dir: /Volumes/MAC/workspace/dreamy/hhspec/prototype/portal-admin
feature_map_path: hhspec/prototype/feature-map.md
linked_features:
- id: CFG-HOME
  name: 首页装修
  priority: Must
  page_id: HomeBuilder
- id: CFG-NAV
  name: 导航与页脚配置
  priority: Must
  page_id: NavigationConfig
- id: A-027
  name: 首页 Banner 配置
  priority: Must
  page_id: Banners
- id: SSG-PUBLISH
  name: 发布中心
  priority: Must
  page_id: Publish
requirement_ids: [FUNC-001, FUNC-002, FUNC-003, FUNC-004, FUNC-005, FUNC-006, FUNC-007, FUNC-008, FUNC-009, FUNC-010, ALIGN-001, ALIGN-002, ALIGN-006]
linked_prototype_snapshots:
- page_id: HomeBuilder
  file: prototype/portal-admin/src/views/HomeBuilder.vue
  hash_at_link: sha256:14189e2cd9c9b819a1e7355ba7491ffd8995b7520d30d3fb5f1f31a19e58b2af
  linked_at: '2026-06-23T07:43:00Z'
- page_id: NavigationConfig
  file: prototype/portal-admin/src/views/NavigationConfig.vue
  hash_at_link: sha256:8cb5f9a1d9aaac16c11b4cb0e3ac584330f401b780e476a7e507b9d4486b2c3a
  linked_at: '2026-06-23T07:43:00Z'
- page_id: Banners
  file: prototype/portal-admin/src/views/Banners.vue
  hash_at_link: sha256:531260e03d17f95e090de91ffe3166e64766133069b701f3506e21aff3e292b8
  linked_at: '2026-06-23T07:43:00Z'
- page_id: Publish
  file: prototype/portal-admin/src/views/Publish.vue
  hash_at_link: sha256:f7891b67cba0f895e89f51d6d8f040dd14d03a8f00c488cc28d94a16197a9f3c
  linked_at: '2026-06-23T07:43:00Z'
operation_paths_file: hhspec/changes/site-decoration-fullstack/operation-paths.yml
operation_paths_files: {}
proto_is_frontend: true
field_inventory_path: hhspec/changes/site-decoration-fullstack/field-inventory.yml
domain_data_model_path: hhspec/changes/site-decoration-fullstack/domain-data-model.yml
global_field_dict_path: hhspec/changes/site-decoration-fullstack/.global-field-dict.json
task_manifest_path: hhspec/changes/site-decoration-fullstack/task-manifest.yml
backend_inference_path: hhspec/changes/site-decoration-fullstack/backend-flow-inference.md
backend_requirements_resolved: true
l0_mode: incremental
baseline_context_path: hhspec/changes/site-decoration-fullstack/.baseline-context.yml
l0_breaking_changes_count: 0
content_index_path: hhspec/prototype/portal-admin/content-index.json
content_index_paths:
  portal-admin: hhspec/prototype/portal-admin/content-index.json
content_index_missing_portals: []
page_diff_summary:
- page: HomeBuilder
  prototype_file: prototype/portal-admin/src/views/HomeBuilder.vue
  impl_file: frontend/portal-admin/src/views/HomeBuilder.vue
  prototype_features:
  - 区块列表（拖拽排序 + 显示开关）
  - 中央实时预览（desktop/mobile 切换）
  - 右侧属性编辑（Hero/Announcement/ProductRail 等多类型）
  - 保存并生成首页按钮
  - 前台预览按钮
  missing_in_impl:
  - 后端 API（首页区块配置 CRUD）完全缺失
  - 保存按钮无实际逻辑（仅前端 mock）
  - 消费端首页未读取区块配置（硬编码顺序）
  misaligned: []
- page: NavigationConfig
  prototype_file: prototype/portal-admin/src/views/NavigationConfig.vue
  impl_file: frontend/portal-admin/src/views/NavigationConfig.vue
  prototype_features:
  - 主导航 + Mega Menu 列编辑（引用品类/主题）
  - 页脚四栏配置
  - 顶部公告条配置
  - Tab 切换
  - 保存并重新生成 header 按钮
  missing_in_impl:
  - 后端 API（导航配置 CRUD）完全缺失
  - 保存按钮无实际逻辑（仅前端 mock）
  - 消费端 layout.tsx 未读取导航配置（硬编码）
  misaligned: []
- page: Banners
  prototype_file: prototype/portal-admin/src/views/Banners.vue
  impl_file: frontend/portal-admin/src/views/Banners.vue
  prototype_features:
  - Banner 列表（图片 + 位置 + 投放时间 + 上下线 + 点击 + 排序）
  - 新增/编辑/删除 Banner
  - 保存并发布
  missing_in_impl: []
  misaligned:
  - 原型用 mock 数据，实际已接入 AdminBannerController（E-MKT-21~25）+ BannerTranslation i18n
  - 原型无 i18n 字段，实际已实现 BannerTranslation 多语言表
  - '原型无状态枚举，实际 status: 1=草稿/2=已发布/3=已归档'
- page: Publish
  prototype_file: prototype/portal-admin/src/views/Publish.vue
  impl_file: frontend/portal-admin/src/views/Publish.vue
  prototype_features:
  - 待发布改动 diff（勾选纳入）
  - 模拟 next build 构建日志（进度条 + 6 步）
  - 受影响页面清单
  - 发布历史时间线
  - 一键回滚
  missing_in_impl:
  - 待发布改动 diff（哪些区块/导航/Banner 被修改未发布）
  - 模拟 next build 构建日志（进度条 + 6 步）
  - 受影响页面清单（哪些消费端页面会被重新生成）
  - 发布历史时间线
  - 一键回滚
  misaligned:
  - 原型描述 SSG 发布中心，实际仅缓存失效日志（GET /api/admin/cache/invalidation-logs + POST /api/admin/cache/invalidate）
  - 当前实现是 Publish.vue + AdminCacheController，与原型 SSG-PUBLISH 差距大
open_questions: []
created_at: '2026-06-23T07:42:16Z'
updated_at: '2026-06-23T11:00:00Z'
cp_1_1: ec8d54cae727684d
cp_1_3: 6c2afcdc95de3a3a
cp_1_4: ''
cp_1_5: ''
context_docs_available:
- hhspec/domains.yml
- hhspec/specs/architecture/domain-model.md
- hhspec/prototype/sync-status.yml
- hhspec/prototype/requirements-brief.md
- hhspec/prototype/feature-map-portal-admin-iteration-2.md
- hhspec/prototype/feature-map-portal-admin.md
- hhspec/prototype/feature-map-portal-store.md
- hhspec/prototype/feature-map.md
feature_map_candidates:
- name: feature-map-portal-admin-iteration-2.md
  path: hhspec/prototype/feature-map-portal-admin-iteration-2.md
- name: feature-map-portal-admin.md
  path: hhspec/prototype/feature-map-portal-admin.md
- name: feature-map-portal-store.md
  path: hhspec/prototype/feature-map-portal-store.md
- name: feature-map.md
  path: hhspec/prototype/feature-map.md
prototype_diff_result:
  modified: []
  added:
  - page_id: App
    file: prototype/portal-admin/src/App.vue
    current_hash: 9804ef7cbb907ab60066a3ac7771e42680497cf09770e803021426cc76bb75e7
  - page_id: AdminShell
    file: prototype/portal-admin/src/components/AdminShell.vue
    current_hash: 37837e279146f31e8e26a64f15fab8f245bc72f1a4cae3ea4cd4e2b52bc8427a
  - page_id: EmptyState
    file: prototype/portal-admin/src/components/EmptyState.vue
    current_hash: dd91e62e1027fb401a25f21e41c3dbba1d7695aeeefc9b3eafb4f70010a432cd
  - page_id: PageHeader
    file: prototype/portal-admin/src/components/PageHeader.vue
    current_hash: adb0b2b019348b3eebe5d03bdc28f2e279e5f4ff72e0a8e267dbf7d85746004d
  - page_id: Pagination
    file: prototype/portal-admin/src/components/Pagination.vue
    current_hash: 57d7d8787f57a88dd26f3579ea336b8606d25bc2658f604a356668d1d9b93593
  - page_id: SparkArea
    file: prototype/portal-admin/src/components/SparkArea.vue
    current_hash: 7269b13d9a2c1f75e6aeae848d17de3580cf3447590a58cdc6ffb1fba8562cdd
  - page_id: StatusBadge
    file: prototype/portal-admin/src/components/StatusBadge.vue
    current_hash: d31187c26e89a30eb7a0eb3472bca1f0d5b0952a55010c2247ad28a0267572a0
  - page_id: Toggle
    file: prototype/portal-admin/src/components/Toggle.vue
    current_hash: 762d7bb986cb7728ebf6933cfb39993dfda6dc56d41dff1968a85ecdabd125ce
  - page_id: AdminList
    file: prototype/portal-admin/src/views/AdminList.vue
    current_hash: 666707d6eaa49604a6229279ef817d1f89ab4b678c17ef7bb21d72967f6adc8a
  - page_id: AuthSettings
    file: prototype/portal-admin/src/views/AuthSettings.vue
    current_hash: 2de9e7cab916f20fca1d983b032aa7b42e5f48cbf7df6653b8b2fb48273456aa
  - page_id: CustomerDetail
    file: prototype/portal-admin/src/views/CustomerDetail.vue
    current_hash: 0c97f5350461416f65e6a969452c438b96e989140ded823c6faf9db060f1f9cb
  - page_id: Customers
    file: prototype/portal-admin/src/views/Customers.vue
    current_hash: 666c2d676700460c07d634b6721a38234f86c0ba12d9db4a425e00d8f80311f2
  - page_id: EmailMarketing
    file: prototype/portal-admin/src/views/EmailMarketing.vue
    current_hash: 1056800e03c6bc0d93b4c0430843c342012c6661e9c105256fd56fae21d52392
  - page_id: HomeBuilder
    file: prototype/portal-admin/src/views/HomeBuilder.vue
    current_hash: 14189e2cd9c9b819a1e7355ba7491ffd8995b7520d30d3fb5f1f31a19e58b2af
  - page_id: Login
    file: prototype/portal-admin/src/views/Login.vue
    current_hash: 143dfb5c60e62a3de343ac37c98ddfbc00bf0eec91a99969273239c34cabb80d
  - page_id: NavigationConfig
    file: prototype/portal-admin/src/views/NavigationConfig.vue
    current_hash: 8cb5f9a1d9aaac16c11b4cb0e3ac584330f401b780e476a7e507b9d4486b2c3a
  - page_id: OperationLogs
    file: prototype/portal-admin/src/views/OperationLogs.vue
    current_hash: a43ec4c9a3566400e96de500d6ec96c2af8d6a2eb7ca29a165f0f0225d8133c4
  - page_id: Publish
    file: prototype/portal-admin/src/views/Publish.vue
    current_hash: f7891b67cba0f895e89f51d6d8f040dd14d03a8f00c488cc28d94a16197a9f3c
  - page_id: RoleManagement
    file: prototype/portal-admin/src/views/RoleManagement.vue
    current_hash: 5d8effb5be5e894fd26dd0676c5aebdf8b0b1f8b5e7945661e5e5458e973aed9
  - page_id: Settings
    file: prototype/portal-admin/src/views/Settings.vue
    current_hash: d6c66cd93d3cd2513cdaf80fcac7b9f0b01ece1d9e78046f3b6dbb657c0cb549
  unchanged: []
  auto_aligned:
  - baseline_file: portal-admin/src/views/Dashboard.vue
    current_file: prototype/portal-admin/src/views/Dashboard.vue
    confidence: 0.875
    note: 'auto-aligned: portal-admin/src/views/ -> prototype/portal-admin/src/views/'
  - baseline_file: portal-admin/src/views/Analytics.vue
    current_file: prototype/portal-admin/src/views/Analytics.vue
    confidence: 0.875
    note: 'auto-aligned: portal-admin/src/views/ -> prototype/portal-admin/src/views/'
  - baseline_file: portal-admin/src/views/Products.vue
    current_file: prototype/portal-admin/src/views/Products.vue
    confidence: 0.875
    note: 'auto-aligned: portal-admin/src/views/ -> prototype/portal-admin/src/views/'
  - baseline_file: portal-admin/src/views/ProductEdit.vue
    current_file: prototype/portal-admin/src/views/ProductEdit.vue
    confidence: 0.875
    note: 'auto-aligned: portal-admin/src/views/ -> prototype/portal-admin/src/views/'
  - baseline_file: portal-admin/src/views/Categories.vue
    current_file: prototype/portal-admin/src/views/Categories.vue
    confidence: 0.875
    note: 'auto-aligned: portal-admin/src/views/ -> prototype/portal-admin/src/views/'
  - baseline_file: portal-admin/src/views/AttributeSets.vue
    current_file: prototype/portal-admin/src/views/AttributeSets.vue
    confidence: 0.875
    note: 'auto-aligned: portal-admin/src/views/ -> prototype/portal-admin/src/views/'
  - baseline_file: portal-admin/src/views/Orders.vue
    current_file: prototype/portal-admin/src/views/Orders.vue
    confidence: 0.875
    note: 'auto-aligned: portal-admin/src/views/ -> prototype/portal-admin/src/views/'
  - baseline_file: portal-admin/src/views/OrderDetail.vue
    current_file: prototype/portal-admin/src/views/OrderDetail.vue
    confidence: 0.875
    note: 'auto-aligned: portal-admin/src/views/ -> prototype/portal-admin/src/views/'
  - baseline_file: portal-admin/src/views/Refunds.vue
    current_file: prototype/portal-admin/src/views/Refunds.vue
    confidence: 0.875
    note: 'auto-aligned: portal-admin/src/views/ -> prototype/portal-admin/src/views/'
  - baseline_file: portal-admin/src/views/Reviews.vue
    current_file: prototype/portal-admin/src/views/Reviews.vue
    confidence: 0.875
    note: 'auto-aligned: portal-admin/src/views/ -> prototype/portal-admin/src/views/'
  - baseline_file: portal-admin/src/views/Shipping.vue
    current_file: prototype/portal-admin/src/views/Shipping.vue
    confidence: 0.875
    note: 'auto-aligned: portal-admin/src/views/ -> prototype/portal-admin/src/views/'
  - baseline_file: portal-admin/src/views/Promotions.vue
    current_file: prototype/portal-admin/src/views/Promotions.vue
    confidence: 0.875
    note: 'auto-aligned: portal-admin/src/views/ -> prototype/portal-admin/src/views/'
  - baseline_file: portal-admin/src/views/Banners.vue
    current_file: prototype/portal-admin/src/views/Banners.vue
    confidence: 0.875
    note: 'auto-aligned: portal-admin/src/views/ -> prototype/portal-admin/src/views/'
  - baseline_file: portal-admin/src/views/ContentBlog.vue
    current_file: prototype/portal-admin/src/views/ContentBlog.vue
    confidence: 0.875
    note: 'auto-aligned: portal-admin/src/views/ -> prototype/portal-admin/src/views/'
  - baseline_file: portal-admin/src/views/ContentWeddings.vue
    current_file: prototype/portal-admin/src/views/ContentWeddings.vue
    confidence: 0.875
    note: 'auto-aligned: portal-admin/src/views/ -> prototype/portal-admin/src/views/'
  - baseline_file: portal-admin/src/views/ContentLookbook.vue
    current_file: prototype/portal-admin/src/views/ContentLookbook.vue
    confidence: 0.875
    note: 'auto-aligned: portal-admin/src/views/ -> prototype/portal-admin/src/views/'
  pending_alignments:
  - baseline_file: app/showroom/page.tsx
    candidates:
    - file: prototype/portal-admin/src/components/SparkArea.vue
      confidence: 0.249
  - baseline_file: app/showroom/[id]/page.tsx
    candidates:
    - file: prototype/portal-admin/src/components/SparkArea.vue
      confidence: 0.239
  - baseline_file: components/showroom/showroom-detail.tsx
    candidates:
    - file: prototype/portal-admin/src/views/CustomerDetail.vue
      confidence: 0.284
  - baseline_file: components/showroom/add-to-showroom-modal.tsx
    candidates:
    - file: prototype/portal-admin/src/views/CustomerDetail.vue
      confidence: 0.182
  - baseline_file: components/product/find-my-size-modal.tsx
    candidates:
    - file: prototype/portal-admin/src/components/AdminShell.vue
      confidence: 0.223
  - baseline_file: app/account/wishlist/page.tsx
    candidates:
    - file: prototype/portal-admin/src/components/SparkArea.vue
      confidence: 0.249
  - baseline_file: app/product/[slug]/page.tsx
    candidates:
    - file: prototype/portal-admin/src/components/SparkArea.vue
      confidence: 0.249
  - baseline_file: app/checkout/page.tsx
    candidates:
    - file: prototype/portal-admin/src/components/SparkArea.vue
      confidence: 0.249
auto_linked_snapshots: true
cp_proto: 7af51e197fa1b1ec
selected_pages: AdminList,AdminShell,App,AuthSettings,CustomerDetail,Customers,EmailMarketing,EmptyState,HomeBuilder,Login,NavigationConfig,OperationLogs,PageHeader,Pagination,Publish,RoleManagement,Settings,SparkArea,StatusBadge,Toggle
frontend_state_machine_path: hhspec/changes/site-decoration-fullstack/frontend-state-machine.yml
frontend_state_machine_summary:
  machine_count: 0
  machines: []
pending_llm_steps:
- step: '1.9'
  name: domain-data-model inference
  trigger: field_inventory_path + operation_paths_file both non-empty
  trigger_met: true
  output: hhspec/changes/<change>/domain-data-model.yml
  type: llm
- step: 1.9.5
  name: global-field-dict load
  trigger: field_inventory_path non-empty
  trigger_met: true
  output: hhspec/changes/<change>/.global-field-dict.json
  type: script
todo_sweep_round: '0'
---

<!-- 探索进展摘要（每轮更新） -->

## Iteration 3 (2026-06-23 07:52 UTC) — Phase 2.3 后端流程推断

**本轮完成**:
- Phase 2.3 后端流程推断：读取 4 个原型 Vue SFC（HomeBuilder/NavigationConfig/Banners/Publish）+ field-inventory + operation-paths + page_diff_summary + KD-1~KD-4
- 产出 `backend-flow-inference.md`（10 维度 tag 全覆盖）
- 回填 state file `backend_inference_path` 字段

**推断要点**:
- [事务] HomePageSection+翻译双表 / NavigationConfig 多栏目 / Banner+BannerTranslation 双写需事务
- [异步][消息队列] 缓存失效建议走 RabbitMQ（项目已有 spring-rabbit 依赖）
- [权限] 新增 AdminHomeSectionController / AdminNavigationController 需权限注解
- [外部集成] Hero 复用 Banner position=HERO（KD-2）；NavigationConfig 引用品类/主题
- [定时任务] Banner 时间窗自动上下线（需确认是否已有 scheduled task）
- [审计日志] 配置变更需记录 OperationLogs
- [状态机并发] Banner 状态转换 + 并发保存需乐观锁
- [幂等] 缓存失效 / 保存 / 上下线均幂等
- [限流降级] 缓存失效防抖（5-10s 合并）+ 消费端读取走 JetCache

**确认事项**: 10 条（见 backend-flow-inference.md `## 确认事项`），需在 Phase 2.3.1 通过 AskUserQuestion 与用户对齐

**下一轮（iteration 4）应做**:
1. Phase 2.3.1 后端实现深度探索：对 10 个维度逐一检查激活条件（关键词 / 代码特征 / backend-flow-inference tag 三信号），激活维度通过 AskUserQuestion QOC 讨论后写入 `key_decisions`（带 source 和 dimension 字段）
2. Phase 2.4 退出判断与确认（AskUserQuestion）
3. Phase 2.5 前提假设显式化（AskUserQuestion）
4. 若用户确认，更新 `phase: requirements_confirmed`，进入 Phase 3 L0 分析

**当前阻塞**: Phase 2.3.1 / 2.4 / 2.5 均需用户交互（AskUserQuestion），本轮无用户输入，只能完成自动推断部分

## Iteration 5 (2026-06-23 07:58 UTC) — Phase 2.3 代码接地验证 + KD-5~8 整合

**本轮完成**:
- 检测到用户在 iteration 4 并行添加了 KD-5~KD-8（消费端改造范围 / Mega Menu JSON 存储 / 公告条独立域 / ThemeCards 引用分类）
- 代码接地验证：读取 AdminBannerController / AdminBannerService / AdminCacheController / AdminCacheService / MarketingContentInvalidatedPublisher / MarketingAfterCommitRunner / 枚举与实体扫描
- 重写 `backend-flow-inference.md`，每个维度增加代码接地状态（✅/⚠️/❌/🆕）

**代码接地关键发现**:
- [事务] ✅ Banner 已实现 @Transactional（create/update/delete/toggleStatus）
- [异步] ✅ Banner 已实现 MarketingAfterCommitRunner（after-commit 回调）；⚠️ AdminCacheController.manualInvalidate 是未实现占位
- [权限] ✅ Banner 已实现 @RequirePermission("/banners")
- [消息队列] ✅ Banner 已实现 RabbitMQ 链路（DomainEventPublisher → InvalidateEventConsumer → CdnInvalidationService）
- [外部集成] ⚠️ Hero 需跨域调 Banner Service（KD-2），Navigation 需跨域调 Categories（KD-8 ThemeCards）
- [定时任务] ❌ Banner 时间窗自动上下线未实现（项目有 8 个 @Scheduled 但无 banner 时间窗任务）
- [审计日志] ✅ Banner 已实现 MarketingAuditRecorder
- [状态机并发] ⚠️ Banner 有 ContentStateGuards 状态 guard 但无 @Version；项目已有乐观锁插件（User/Role/AdminUser 等用 @Version），新实体建议加
- [幂等] ✅ Banner toggleStatus 幂等短路
- [限流降级] ⚠️ 缓存失效无防抖；消费端读取走 JetCache（StoreBannerService 已有模式）

**operation-paths.yml 覆盖缺口（重大发现）**:
- 4 个目标页面（HomeBuilder/NavigationConfig/Banners/Publish）在 operation-paths.yml 中有 0 个操作被捕获
- 原因：提取脚本只抓 button.btn-primary / button.btn-ghost，漏了 btn-gold/btn-outline/btn-danger-ghost/Toggle/拖拽/Tab 切换
- 影响：L0 语义提取（Phase 3.1）依赖 operation-paths 推断状态机，目标页面操作缺失会导致建模不完整
- 建议：Phase 3.1 前手动补充或重跑提取脚本

**确认事项精炼**: 10 条（见 backend-flow-inference.md `## 确认事项`），每条附推荐方案，等用户在 Phase 2.3.1 AskUserQuestion 确认

**下一轮（iteration 6）应做**:
1. 检测 state file 是否有用户新增 KD 或 phase 变化
2. 若仍 context_ready 且无用户交互：继续自动准备工作（如补充 operation-paths 缺口、验证 field-inventory 完整性）
3. 若 phase 已推进（用户在并行会话完成了 2.3.1/2.4/2.5）：读取新 phase 继续

**当前阻塞**: Phase 2.3.1 / 2.4 / 2.5 均需用户交互（AskUserQuestion），本轮无用户输入
## Iteration 7 (2026-06-23 11:00 UTC) — 第 2 轮代码接地复盘 + 5 个 MUST_FIX 修复

**本轮完成**:
- 用户问"还有什么遗漏和需要讨论的?"，触发 0.4.1 代码接地复盘（重入复盘场景）
- 调用 pd:l0_grounding_reviewer 执行第 2 轮反查（reviews/grounding-r2.yml）
- r1 的 10 个 MUST_FIX 全部落地 ✅（GRD-001~010 在 er-diagram/state-machine/acceptance/decision 中完整落地）
- 发现 5 个新 MUST_FIX + 10 个 WARNING + 3 个 INFO

**5 个新 MUST_FIX 修复**:
- GRD2-001+002：er-diagram.yml 翻译表 vs KD-16 i18n_json 列矛盾
  - 移除 5 个翻译表实体（HomeSectionTranslation/NavigationTranslation/FooterColumnTranslation/FooterLinkTranslation/AnnouncementTranslation）
  - 5 个主实体（HomePageSection/NavigationItem/FooterColumn/FooterLink/Announcement）新增 i18n_json JSON 字段（JacksonTypeHandler + autoResultMap=true）
  - 修正 HomePageSection.data_json 描述（Newsletter 文案存 i18n_json，Hero/Announcement 数据从其他域派生）
  - review_fixes 移除 GRD-W08，新增 GRD2-001/002
  - CONSTRAINT-004 更新（移除翻译表列表，新增 i18n_json TypeHandler 约束）
  - OQ-3/OQ-5 更新（KD-16 已采纳 i18n_json 方案）
  - business-flow.yml 5 处翻译表引用同步修正
- GRD2-003：Subscriber 唯一约束内部矛盾
  - 采用方案 A（复合唯一 (email, source)）——通用做法最合理最长远
  - modified_entities.NewsletterSubscriber 新增 changed_constraints 段（uk_newsletter_email → uk_newsletter_email_source 迁移）
  - state-machine.yml subscriber_status guard 保留 (email, source)，desc 补充 GRD2-003 说明
  - scope-manifest.yml modified_tables 新增 newsletter_subscriber 约束变更
  - migration_scripts 新增 migrate-uk-newsletter-email-to-composite.sql
- GRD2-004+005：scope-manifest.yml 遗漏消费端文件
  - store.modify 新增 4 个文件（site-header.tsx/site-footer.tsx/data/navigation.ts/lib/api/store-types.ts）
  - admin.modify 顺带补充 2 个文件（src/api/types.ts/src/config/translatableFields.ts，对应 WARNING GRD2-W09/W10）
  - migration_scripts 新增 migrate-topbar-banner-to-announcements.sql（对应 WARNING GRD2-W03）
  - constraints 段追加 GRD2-001~005 处理记录

**decision.md 追加**:
- 第 2 轮代码接地复核修复记录章节
- 10 个 WARNING 的处理时机说明（留待 L2 详设阶段）

**phase 变化**:
- requirements_confirmed（回退修复） → L1_decisions_done（修复完成恢复）
- open_questions: 5 项 → []

**下一步**:
- 可继续问"还有什么遗漏"触发第 3 轮复盘
- 或运行 /pd:apply 推进实施
- 或运行 /pd:archive 归档变更
