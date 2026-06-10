---
phase: L1_decisions_done
challenger_rounds: 4
session_id: ""
change_name: "portal-api-integration"
codebase_path: "/Volumes/MAC/workspace/dreamy"
change_type: "alignment"
key_decisions:
  - id: D13
    topic: 内容数据多语言（EN/ES/FR 三语，translation 附表，缺翻译回退 EN）
    source: keyword
  - id: D14
    topic: 多币种结算（USD 基准+后端汇率表+下单锁汇+Stripe 原币种收款+补 EUR）
    source: keyword
  - id: D15
    topic: 税费立场（含税价+DDU，结算无 Tax 行）
    source: keyword
  - id: D16
    topic: 交易性邮件（MQ 消费者+MailRecord 幂等重试+SMTP 抽象）
    source: keyword
  - id: D17
    topic: 商品搜索（MySQL FULLTEXT ngram；Doris 方案经确认撤回，全量保留 MySQL）
    source: keyword
  - id: D18
    topic: Wishlist 后端持久化（wishlist_item 表 + /api/store/wishlists，归 trading 域）
    source: keyword
  - id: D19
    topic: Analytics 流量数据接 GA4（gtag 标准电商事件 + Data API 后端拉取 + JetCache 缓存 + Cookie consent banner）
    source: keyword
  - id: D20
    topic: 消费端原型迭代4全纳入（Showroom 协作域 + 邀请 token/guest JWT + 尺码表规则推荐 + PDP 定制表单/婚期交期 + 提醒真发邮件）
    source: keyword
  - id: D21
    topic: 种子数据（mock 转种子脚本，含三语翻译表，建表后灌入）
    source: keyword
  - id: D22
    topic: portal-store 改 Node standalone 部署（去 output:export；CDN 前置 + MQ→revalidate+purge 秒级失效 + serve-stale 兜底）
    source: code_feature
  - id: D23
    topic: Recently Viewed 后端持久化（BrowseHistory，每用户保留 50 条，trading 域）
    source: keyword
  - id: D24
    topic: 退款政策（定制款支付后 24h 宽限期，超时视为投产不可退；按 OrderLine.customSizeData 判定）
    source: keyword
  - id: D25
    topic: 支付方式 Stripe 全家桶（Payment Element 承载卡/Apple Pay/Google Pay/Klarna/Afterpay，PayPal 置灰 coming soon）
    source: keyword
  - id: D26
    topic: Newsletter 订阅仅落表收集（NewsletterSubscriber，不发码不发邮件，弹窗文案改纯订阅确认）
    source: keyword
  - id: D27
    topic: 消费端 UI 文案 i18n（next-intl 三语 + 路径前缀 /es /fr + hreflang + 按 locale revalidate/purge）
    source: keyword
  - id: D28
    topic: 礼品包装订单级开关 + 固定费（Order.giftWrap/giftWrapFee 快照，计入总额与退款上限）
    source: keyword
  - id: D29
    topic: 首页/推荐位规则化（Best Sellers 30天销量聚合冷启动回退 recommend；Complete the Look 同类目降级不建关联表）
    source: keyword
  - id: D30
    topic: 联系表单落表（ContactMessage + /api/store/contact，管理端本期不做查看页）
    source: keyword
  - id: D31
    topic: 退货寄回审核制 + 登记字段（不建 RMA 节点，Refund.returnTrackingNo 选填）
    source: keyword
project_tech_stack:
  frontend: pnpm-vue3-headless
  backend:
    - java-gradle
  prototype: pnpm-vue3-headless
detected_tech_specs:
  - "已验证：声明技术栈（Vue3 后台 / Next.js 消费端 / Java Gradle 后端）与实际代码一致；消费端 Next.js 为 project.yml frontends[] 扩展声明，PD 原生 frontend 枚举不含"
has_ui: true
prototype_dir: "hhspec/prototype"
feature_map_path: "hhspec/prototype/feature-map-portal-admin.md"
linked_features: []
requirement_ids:
  - REQ-PORTAL-API-001
linked_prototype_snapshots:
  - page_id: portal-admin/Dashboard
    file: portal-admin/src/views/Dashboard.vue
    hash_at_link: "sha256:49f3339120e5e943d5a703be6f38c6494a268bff602c98e1cfd84ec57af98ac4"
  - page_id: portal-admin/Analytics
    file: portal-admin/src/views/Analytics.vue
    hash_at_link: "sha256:c02f088cf89355c4a8c6fdd5736202b00d0eb9e934756e582b9bc9985c298cc0"
  - page_id: portal-admin/Products
    file: portal-admin/src/views/Products.vue
    hash_at_link: "sha256:e1bb77083f6dd8cdafefaa83ab1ca08403e8fda205c7a558dac8970648181ab8"
  - page_id: portal-admin/ProductEdit
    file: portal-admin/src/views/ProductEdit.vue
    hash_at_link: "sha256:124d14a9b3508364a23a0fbb20b4d36569987737c9a657824e59e9ec03966e14"
  - page_id: portal-admin/Categories
    file: portal-admin/src/views/Categories.vue
    hash_at_link: "sha256:9cd49bffb471e6602776666456e1cb65be361149e830e324f94966cdf4bd2e09"
  - page_id: portal-admin/AttributeSets
    file: portal-admin/src/views/AttributeSets.vue
    hash_at_link: "sha256:28e68e51c9dad5b2e99d02daaabea24b1a22f8a05220c9b43b8045e4d8db69df"
  - page_id: portal-admin/Orders
    file: portal-admin/src/views/Orders.vue
    hash_at_link: "sha256:73107e9a4d154841f76e21b7f834a6f9f6618de809694a126f539afe9102a521"
  - page_id: portal-admin/OrderDetail
    file: portal-admin/src/views/OrderDetail.vue
    hash_at_link: "sha256:4d4f02137afa1158e4bdd105258b53cd1862922cabed774c19aafe7c82e57ba3"
  - page_id: portal-admin/Refunds
    file: portal-admin/src/views/Refunds.vue
    hash_at_link: "sha256:4330543f08c571d21296a30d8b9c2c4c12045c3ff996266b3099d535bbfe83cc"
  - page_id: portal-admin/Reviews
    file: portal-admin/src/views/Reviews.vue
    hash_at_link: "sha256:08c456ab8b4cb61d31bec60cac32afc24ffe4b7c5602245d2a6f68c636c3cb21"
  - page_id: portal-admin/Shipping
    file: portal-admin/src/views/Shipping.vue
    hash_at_link: "sha256:bcbb05da6ad99f6fd4824cc0e55dc5a307af21f505891711bb5cbec661af39f4"
  - page_id: portal-admin/Promotions
    file: portal-admin/src/views/Promotions.vue
    hash_at_link: "sha256:7f6e833a82018653c0e8ea6104cad42853ba4f64cc9b6002a1f264b811d0052b"
  - page_id: portal-admin/Banners
    file: portal-admin/src/views/Banners.vue
    hash_at_link: "sha256:531260e03d17f95e090de91ffe3166e64766133069b701f3506e21aff3e292b8"
  - page_id: portal-admin/ContentBlog
    file: portal-admin/src/views/ContentBlog.vue
    hash_at_link: "sha256:5922ace236f3d6a0f1dff07a317f2c5f2fa7e8d02d244f3512d8541ead90d847"
  - page_id: portal-admin/ContentWeddings
    file: portal-admin/src/views/ContentWeddings.vue
    hash_at_link: "sha256:1ffe21f155e4ed85f1f74f77bed6caa6162bf0f647a538a2de889183af96ea70"
  - page_id: portal-admin/ContentLookbook
    file: portal-admin/src/views/ContentLookbook.vue
    hash_at_link: "sha256:424fe52c8f356508be2d42967758688434857fb478ed513ce083485623790471"
  - page_id: portal-store/ShowroomList
    file: app/showroom/page.tsx
    hash_at_link: "sha256:fe29f1b1a260aacb3b7bfc0b28b5f5033b987f64daa2ac1c72109d1f467beb07"
  - page_id: portal-store/ShowroomDetail
    file: app/showroom/[id]/page.tsx
    hash_at_link: "sha256:bcdf10be927ad4f8a2523e5a407178a6a52cca551df30ef62b4cbcdd6f7beee0"
  - page_id: portal-store/ShowroomDetailComponent
    file: components/showroom/showroom-detail.tsx
    hash_at_link: "sha256:d99273621659d4dae0882ca2e1e6257323c6cdda7b6e8b4b5eb77ad24057cc41"
  - page_id: portal-store/AddToShowroomModal
    file: components/showroom/add-to-showroom-modal.tsx
    hash_at_link: "sha256:062896b3fce85c06df96156d72d8595fc4b2a770bb913c10be4eb775da00278e"
  - page_id: portal-store/FindMySizeModal
    file: components/product/find-my-size-modal.tsx
    hash_at_link: "sha256:f3b8706b6338ca6546f8920d98184562b2a81da9d6ec8dd853aa221e3e04330a"
  - page_id: portal-store/AccountWishlist
    file: app/account/wishlist/page.tsx
    hash_at_link: "sha256:00510f570a7bf24f06c67c921971c95d075ea38235901861ba2e84c9ea08a423"
  - page_id: portal-store/ProductDetail
    file: app/product/[slug]/page.tsx
    hash_at_link: "sha256:482dddccb6272bcbf198be73c4b62cafa369e43938c0bfb8dabf3502fec6bb39"
  - page_id: portal-store/Checkout
    file: app/checkout/page.tsx
    hash_at_link: "sha256:4ca7b1287066024c4054a3e110c48e9e34b389d39f257cdb17d5f34b236a7dfb"
operation_paths_file: "hhspec/changes/portal-api-integration/operation-paths.yml"
operation_paths_files: {}
proto_is_frontend: true
field_inventory_path: "hhspec/changes/portal-api-integration/field-inventory.yml"
domain_data_model_path: "hhspec/changes/portal-api-integration/domain-data-model.yml"
global_field_dict_path: ""
task_manifest_path: "hhspec/changes/portal-api-integration/task-manifest.yml"
backend_inference_path: "hhspec/changes/portal-api-integration/backend-flow-inference.md"
backend_requirements_resolved: true
l0_mode: "bootstrap"
baseline_context_path: ""
l0_breaking_changes_count: 0
content_index_path: ""
content_index_paths:
  portal-admin: "hhspec/prototype/portal-admin/content-index.json"
content_index_missing_portals: []
page_diff_summary: []
open_questions: []
created_at: "2026-06-10T07:53:52Z"
updated_at: "2026-06-10T15:45:00Z"
---

<!-- 探索进展摘要（每轮更新） -->

## 2026-06-10 细化轮（遗漏审查）

原 explore 已完成（phase=L1_decisions_done，Challenger r1 PASS，762 验收场景），原 state file 被 teardown 删除，本轮重建并回填。

本轮发现并修复的产出质量问题：
1. feature-gap-report.yml：15 条 impl_has=NOT_FOUND 误报已修正为 visual_only（UI 已实现，缺口为 mock→API 数据对接）
2. er-diagram.yml：Category 实体 parent_id/parentId 重复字段已合并
3. acceptance-baseline.md：EDGE 表 656 条按 8 类补全分类统计
4. state file 回填完成（16 页原型快照即时重算 SHA256）

待讨论需求级遗漏（见 open_questions）：内容多语言 / 税费货币 / 交易邮件 / 商品搜索。

## 2026-06-10 细化轮（A 类决策落地，全部完成）

5 项新决策（decision.md 决策 13-17）：内容三语 translation 附表、多币种锁汇结算（补 EUR）、含税价+DDU、交易性邮件（MailRecord 幂等）、MySQL FULLTEXT 搜索（Doris 经确认撤回）。

产物增量：er-diagram 31→46 实体（13 translation + ExchangeRate + MailRecord，Order 加 exchangeRate）、state-machine 19→20（mail_delivery）、business-flow 10→11（transactional_email）、boundary 656→674、acceptance 762→774、REQ 补 ALIGN-016~020。Mermaid 重新生成，check-completeness PASS（0 issues）。open_questions 已清空，可运行 /pd:apply。

## 2026-06-10 细化轮三（范围遗漏补全，全部完成）

遗漏审查发现 4 项并经用户决策（decision.md 决策 18-21）：
1. D18 Wishlist 后端持久化（实现端页面已存在但全套产出 0 提及）→ 纳入 trading 域
2. D19 Analytics 流量数据源（埋点悬空）→ GA4 + Data API 拉取 + Cookie consent
3. D20 消费端原型迭代4（Showroom/尺码推荐/PDP 定制表单+婚期交期）→ 全部纳入，新增 showroom 域（第7域）；访客=邀请 token+guest JWT；尺码=尺码表规则匹配；提醒升级真发邮件
4. D21 种子数据 → mock 转种子脚本（含三语翻译表）

产物增量：er-diagram 46→52 实体（WishlistItem + Showroom 5 实体，Order 加 weddingDate，AnalyticsDashboard 流量字段标注 GA4 来源）、state-machine 20→21（showroom_member_assignment）、business-flow 11→15（wishlist_manage/showroom_collaboration/size_recommendation/ga4_traffic_fetch）、boundary 674→902、acceptance 重新生成 1042 场景（15 条 flow 级 FUNC 全部补全断言）、REQ 补 ALIGN-021~025、feature-gap-report 15→20 条、acceptance-baseline FUNC 17→21 条并修正 id 映射。
新绑定 8 个 portal-store 原型快照（showroom×4 / find-my-size / wishlist / PDP / checkout）。
Challenger r2 PASS（full 模式，0 阻断，2 条 warning 不阻断：CON-03 field:state 命名沿用、s-763~774 source_id 指向 decision 而非 flow id）。check-completeness PASS（0 issues），零残留扫描通过。可运行 /pd:apply。

## 2026-06-10 细化轮四（架构冲突修复 + 遗漏补全，全部完成）

遗漏审查发现 3 项并经用户决策（decision.md 决策 22-24）：
1. D22 **架构冲突**：portal-store 为 output:'export' 静态导出，决策 4 的 ISR+revalidatePath 失效链不可用 → 改 Node standalone（docker）+ CDN 前置 + MQ→revalidate+Cloudflare purge 秒级失效 + serve-stale 兜底（用户经三轮 QA 确认理解后选定）
2. D23 Recently Viewed（F-048 Must，0 覆盖）→ 后端持久化 BrowseHistory（trading 域，50 条滚动）
3. D24 退款政策空白 → 定制款支付后 24h 宽限期，超时视为投产不可退（OrderLine.customSizeData + paidAt+24h 判定，零管理端 UI 改动）
另直接补建模：My Reviews（F-049）场景并入 review_moderation_flow。

产物增量：er-diagram 52→53（BrowseHistory）、order_lifecycle 退款 guard 更新、business-flow 15→16（browse_history_track + refund 定制校验节点 + my reviews 节点）、boundary 902→915、acceptance 1058 场景、REQ 补 ALIGN-026~028、feature-gap 20→23。
Challenger r3 PASS（0 阻断/4 warning，其中 3 个已顺手修复：refund_flow n24→end 边、transactional_email 补 end 节点、12 条 decision-* 场景 source 字段改 decision）。check-completeness PASS。可运行 /pd:apply。

## 2026-06-10 细化轮五（消费端 feature map 全量对照遗漏审查，全部完成）

对照 feature-map-portal-store（F-001~F-077）发现 7 项遗漏并经用户决策（decision.md 决策 25-31）：
1. D25 支付方式范围悬空（F-037 六选项卡 vs 决策7仅卡）→ Stripe Payment Element 全家桶，PayPal 置灰
2. D26 Newsletter 零覆盖（F-008/F-056 Must）→ 仅落表收集，弹窗折扣码话术移除（显式降级）
3. D27 UI 静态文案 i18n 悬空（决策13仅内容数据）→ next-intl + 路径前缀 /es /fr + hreflang
4. D28 礼品包装零建模（F-036 Must）→ Order.giftWrap + giftWrapFee 固定费
5. D29 推荐位数据规则不明（F-006/F-031 Must）→ 规则化查询 + Complete the Look 同类目降级
6. D30 联系表单纯模拟（F-058）→ ContactMessage 落表
7. D31 退货寄回无节点 → 审核制 + returnTrackingNo 登记字段（不建 RMA）

产物增量：er-diagram 53→55（NewsletterSubscriber/ContactMessage，Order +giftWrap/giftWrapFee，Refund +returnTrackingNo 并顺手清洗历史 snake_case 重复字段）、business-flow 16→18（newsletter_subscribe/contact_submit，checkout n6/n12 更新）、boundary 915→944、acceptance 重新生成 1089 场景（18 条 flow 级 FUNC 补全断言）、REQ 补 ALIGN-029~035、feature-gap 23→30、acceptance-baseline FUNC 22→24 条并校正 id 漂移。
Challenger r4 PASS（0 阻断/4 warning，其中 2 个已顺手修复：refund_flow n24→n22 边、newsletter n3 重试闭环；9 条 API 直达 func 场景补 http_status）。check-completeness PASS（0 issues）。可运行 /pd:apply。
