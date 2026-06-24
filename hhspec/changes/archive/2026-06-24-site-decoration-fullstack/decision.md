# 关键决策：site-decoration-fullstack

> 生成时间：2026-06-23
> L0 模式：incremental
> Challenger 审查：第 3 轮通过（10/10 MUST_FIX 已修复）
> 代码接地复核：第 1 轮发现 10 个遗漏，已全部纳入并修复；第 2 轮发现 5 个新 MUST_FIX（GRD2-001~005），已全部纳入并修复

## 业务决策

### KD-1 Publish.vue 范围
- **选择**：仅保持当前缓存失效能力，不扩展 SSG 发布中心
- **理由**：原型描述的 SSG 发布中心（diff+build+受影响页面+历史+回滚）范围过大；当前缓存失效已能满足"保存即生效"的诉求。portal-store 首页为 force-dynamic 无 SSG，本变更不引入 SSG，保持 force-dynamic + 缓存失效
- **影响**：Publish.vue 不改造，HomeBuilder/NavigationConfig 保存后通过 in-process cache.invalidateFamily + publisher.publish 触发缓存失效（非 HTTP 自调）

### KD-2 Hero 区块数据源
- **选择**：复用 Banner position=HERO
- **理由**：Banner 已有完整 i18n（BannerTranslation）+ 时间窗 + 上下线机制；避免数据重复
- **影响**：HomePageSection 的 Hero 类型只存 enabled/sort_order，文案/图片/CTA 从 Banner position=HERO 读取；管理端 Hero 区块属性编辑表单联动 Banner 编辑

### KD-3 HomePageSection i18n 策略（被 KD-16 覆盖）
- **选择**：~~单独 home_section_translations 表~~ → 合并到主表 JSON 列（见 KD-16）
- **理由**：L1 决策阶段调整为合并策略，减少表数量
- **影响**：以 KD-16 为准

### KD-4 HomeBuilder/NavigationConfig 发布机制
- **选择**：保存即发布（与 Banners 一致）
- **理由**：交互简单，与现有 Banners 模式一致；保存后立即触发缓存失效
- **影响**：无草稿状态机；保存 API 直接写入 DB + 触发缓存失效；UI 无"发布"按钮，只有"保存"按钮

### KD-5 消费端改造范围
- **选择**：首页动态渲染 + header/footer/公告条 全量改造
- **理由**：用户明确要求"包括消费端相关前后端代码"；导航配置必须在消费端 header/footer 生效才有意义
- **影响**：消费端改造点：(1)portal-store/app/[locale]/page.tsx 动态渲染区块；(2)portal-store/app/[locale]/layout.tsx 读取导航配置渲染 header/footer；(3)顶部公告条组件动态渲染

### KD-6 Mega Menu 列存储
- **选择**：JSON 字段 mega_menu_json（NavigationItem 内嵌）
- **理由**：Mega Menu 列是导航项的从属数据，整体保存更方便；避免表数过多
- **影响**：NavigationItem 表增加 mega_menu_json 字段；Repository 不需要 NavigationMegaMenuColumn 表

### KD-7 公告条数据源
- **选择**：独立 announcement 域
- **理由**：公告条可复用于其他位置（如分类页、活动页）；不依赖导航
- **影响**：新建 announcements 表 + AnnouncementController（admin）+ StoreContentController 扩展（store）；与 navigation 域解耦；BannerPosition.TOPBAR 废弃公告语义（见 KD-17）

### KD-8 ThemeCards 区块数据源
- **选择**：引用分类 type=theme
- **理由**：与项目现有分类体系一致；避免数据重复；分类管理已上线
- **影响**：HomePageSection 的 ThemeCards 类型只存 enabled/sort_order + 显示数量；实际主题从 taxonomy type=theme 读取

### KD-9 ProductRail source=recommend 人工推荐
- **选择**：支持 recommend + 商品选择器
- **理由**：人工推荐是运营核心能力，允许运营精选爆款上首页
- **影响**：HomePageSection 的 ProductRail 类型 data_json 增加 product_ids 数组（source=recommend 时启用）；管理端需要商品选择器 UI 组件

### KD-10 EditorialFeature 区块数据源
- **选择**：引用 weddings 数据 + limit（方案 B）
- **理由**：婚纱是高客单价情感决策商品，真实婚礼故事是核心转化要素（社交证明）；数据已就绪（fetchStoreWeddings）；与 Hero 区块差异化；竞品（David's Bridal、BHLDN）首页都有 Real Weddings 精选
- **影响**：HomePageSection 的 EditorialFeature 类型存储 title + limit + 可选 wedding_ids；消费端调用 fetchStoreWeddings 渲染卡片列表

### KD-11 Newsletter 区块范围
- **选择**：完整订阅表单（邮箱输入 + API）
- **理由**：首页是订阅转化主入口；复用 EmailMarketing 模块的订阅者存储
- **影响**：扩展基线 POST /api/store/newsletter（见 KD-13）；Newsletter 区块存储 title + 副文案 + CTA 文案；消费端渲染邮箱输入 + 提交按钮

### KD-12 NewsletterSubscriber 退订状态机（GRD-001 修复）
- **选择**：本期不扩展退订状态机，保留基线模式
- **理由**：基线 NewsletterSubscriber 仅 subscribed_at 单字段；本期订阅即生效、无退订
- **影响**：subscriber_status 状态机简化为单态 subscribed；er-diagram Subscriber 实体移除 status/unsubscribed_at 字段；不新建退订 API；acceptance s-017~s-022 退订/重订阅场景裁剪

### KD-13 订阅 API 路径（GRD-009 修复）
- **选择**：扩展基线 POST /api/store/newsletter
- **理由**：避免 API 重复；与基线一致
- **影响**：不新建 /api/store/subscribers/subscribe；扩展基线 /api/store/newsletter 接收 source=4(HOME_BLOCK)；NewsletterSource IntEnum 新增 HOME_BLOCK(4)

### KD-14 Hero 第二 CTA 数据源（GRD-010 修复）
- **选择**：Banner 实体新增 cta_text_secondary + cta_link_secondary 字段（含 BannerTranslation）
- **理由**：电商行业 Banner 通常支持多 CTA；与 KD-2 纯派生原则一致；运营在 Banner 编辑页统一管理两个 CTA
- **影响**：Banner entity + BannerTranslation 新增 cta_text_secondary + cta_link_secondary；AdminBannerController 的 BannerUpsert DTO 扩展

### KD-15 限界上下文归属（L1 决策）
- **选择**：新建 site_builder 限界上下文（domain_code: site_builder）
- **理由**：HomeBuilder/NavigationConfig/Announcement 职责边界清晰，与 marketing/content（Banner）解耦；便于独立演进
- **影响**：domains.yml 新增 site_builder domain；新增 Controller 须登记 MarketingExceptionHandler assignableTypes；Subscriber 保留在 subscriber 域

### KD-16 翻译表策略修正（L1 决策，覆盖 KD-3）
- **选择**：合并翻译表到主表 JSON 列（不建独立 *_translations 表）
- **理由**：减少表数量、简化 DDL 和 Repository；接受与 BannerTranslation 独立表模式的偏离作为技术债
- **影响**：
  - 覆盖 KD-3 的"单独 home_section_translations 表"决策
  - home_sections / navigation_items / footer_columns / footer_links / announcements 表均增加 i18n_json JSON 列
  - 不建 *_translations 表（包括 GRD-W08 推荐的 FooterColumnTranslation/FooterLinkTranslation）
  - i18n_json 结构：{"en": {"title": "...", "subtitle": "..."}, "zh": {...}, "es": {...}}

### KD-17 Breaking change 处理（L1 决策）
- **选择**：接受 breaking change（BannerPosition.TOPBAR 废弃公告语义 + 消费端 layout.tsx 改造）
- **理由**：KD-7 已决定独立 announcement 域；TOPBAR Banner 公告语义必须废弃以避免双数据源
- **影响**：
  - (1) BannerPosition.TOPBAR 标记 @Deprecated，存量 TOPBAR Banner 公告数据脚本迁入 announcements 表
  - (2) 消费端 layout.tsx 改调 GET /api/store/content/announcements 读取公告（原 fetchStoreBanners(TOPBAR) 废弃）
  - (3) /api/store/newsletter 扩展 source=4(HOME_BLOCK) 向后兼容（非 breaking）

## 后端关键决策

> 来源：Phase 2.3.1 后端实现深度探索
> 下游消费：L0 flow_modeler（映射到 business-flow.yml 节点）、L1 architect（映射到 error-strategy.md / data-flow.md）

### BE-DIM-4 状态机/并发/事务
- **决策**：HomePageSection + 翻译字段（JSON 内嵌）单表写入用 @Transactional；保存即发布无状态机；保存幂等
- **触发信号**：code_feature
- **理由**：双表写入事务保证原子性；保存即发布避免草稿态；相同配置重复保存产生相同结果
- **约束**：L1 架构须提供 @Transactional 注解；L2 详设须明确事务边界

### BE-DIM-5 外部集成与第三方依赖
- **决策**：跨 domain 通过 Service 接口调用，不跨表查询
- **触发信号**：code_feature
- **理由**：保持 domain 边界；HomeBuilder Hero 读取 Banner 通过 BannerService；NavigationConfig 引用 taxonomy 通过 TaxonomyService
- **约束**：L1 架构须明确 domain 间 Service 接口契约；禁止跨 domain 直接查表

### BE-DIM-6 安全与权限
- **决策**：沿用 RBAC + 新增权限码
- **触发信号**：code_feature
- **理由**：新增 AdminHomeSectionController 用 @RequirePermission("/site/home")；AdminNavigationController 用 @RequirePermission("/site/navigation")；AdminAnnouncementController 用 @RequirePermission("/site/announcement")；消费端读取 API 匿名可读
- **约束**：L1 须声明权限码；DataInitializer 须种子 /site/announcement 权限

### BE-DIM-7 可观测性与运维
- **决策**：复用 OperationLogs 模块 + 缓存失效日志
- **触发信号**：code_feature
- **理由**：配置变更日志走 OperationLogs（已有）；缓存失效日志走 AdminCacheController.invalidation-logs（已有）
- **约束**：不新建独立审计模块；L2 详设须明确日志切面

### BE-DIM-8 性能与可扩展性
- **决策**：JetCache 两级缓存 + in-process 失效链
- **触发信号**：code_feature
- **理由**：消费端读取 API 走 JetCache（Caffeine + Redis，TTL=300s）；HomeBuilder/NavigationConfig 保存后通过 in-process cache.invalidateFamily + publisher.publish 触发失效（非 HTTP 自调）；缓存 key 按 portal+locale 维度；不做防抖（保存频率低）
- **约束**：L1 须声明 MarketingCacheService.Family 新增 HOME_SECTION/NAVIGATION/ANNOUNCEMENT；L2 详设须明确缓存 key 设计

## 原型强对照约束

> 触发条件：linked_prototype_snapshots 不为空 且 prototype_dir 有值（均满足）

### 样式 token 迁移
- 原型 portal-admin 使用 Tailwind + 自定义 CSS 变量（gold/canvas/ink/sage 等设计系统）
- 实现须复用项目现有 `frontend/portal-admin/tailwind.config.js` + `frontend/portal-admin/src/assets/` 下的样式
- 禁止引入新的设计系统或覆盖现有 CSS 变量

### CDN → Vite 转换规则
- 原型为 Vue SFC 工程（proto_is_frontend=true），apply 时直接复制 .vue 文件
- 禁止引入 CDN 链接；所有依赖通过 pnpm 安装

### L2/L3/L4 约束
- L2 前端详设：组件拆解须对齐原型 HomeBuilder/NavigationConfig/Banners/Publish 的结构
- L3 实现：Vue SFC 组件须保持原型交互（拖拽排序/Toggle/属性编辑/Tab 切换）
- L4 验收：UI 验收以原型为 ground truth，视觉还原度 ≥ 95%

## 开放问题（Open Questions）

以下问题在 L0 阶段识别，未在 L1 敲定，留待 L2 详设阶段处理：

1. **OQ-7 TOPBAR Banner 迁移**：废弃 TOPBAR 公告语义后，BannerPosition.TOPBAR 保留给非公告用途还是完全废弃？推荐方案 D（@Deprecated + 数据迁移 + 消费端切流）
2. **OQ-9 消费端首页 4 类区块去留**：现有首页含 FlashSaleRail/Lookbook Editorial/ColorPalette Moodboard/ValueProps 四类区块，均不在 section_type 7 种枚举内。建议在 L2 详设阶段决定：保留硬编码 / 新增枚举 / 合并到现有区块类型
3. **OQ-11 KD-1 措辞修正**：已记录"不引入 SSG，保持 force-dynamic + 缓存失效"
4. **派生产物 stale 残留**：acceptance s-015~s-022（subscriber 旧状态机场景）、boundary-scenarios.yml 多处、business-flow.yml newsletter_subscribe_flow step 4-5 须在 L2 详设阶段同步裁剪/修正

## 第 2 轮代码接地复核修复记录（GRD2-001~005）

> 来源：reviews/grounding-r2.yml（phase=L1_decisions_done 后重入复盘）
> 处理方式：5 个 MUST_FIX 全部"纳入"——回退 phase 到 requirements_confirmed 修复后恢复 L1_decisions_done

### GRD2-001+002 er-diagram.yml 翻译表 vs KD-16 i18n_json 列矛盾
- **问题**：KD-16 决定合并翻译表到主表 i18n_json JSON 列，但 er-diagram.yml 仍保留 5 个翻译表实体（HomeSectionTranslation/NavigationTranslation/FooterColumnTranslation/FooterLinkTranslation/AnnouncementTranslation）；HomePageSection.data_json 描述声明 Newsletter 类型为空，与 KD-11 Newsletter 文案存储需求矛盾
- **修复**：
  - 移除 5 个翻译表实体
  - HomePageSection/NavigationItem/FooterColumn/FooterLink/Announcement 5 个主实体新增 i18n_json JSON 字段（使用 JacksonTypeHandler + @TableName(autoResultMap=true)，对齐 Category.attrOverrides 模式）
  - 修正 HomePageSection.data_json 描述：Hero/Announcement 数据从其他域派生，Newsletter 文案存 i18n_json，ShopByColor/ThemeCards/ProductRail/EditorialFeature 存区块配置
  - review_fixes 移除 GRD-W08，新增 GRD2-001/GRD2-002
  - CONSTRAINT-004 更新：移除翻译表实体列表，新增 i18n_json TypeHandler 约束
  - OQ-3 更新：方案 C（KD-16 已采纳 i18n_json JSON 列）
  - OQ-5 更新：mega_menu_titles 作为 i18n_json JSON 列子字段
  - business-flow.yml 5 处翻译表引用同步修正
  - scope-manifest.yml database.new_tables 无变化（翻译表本就不在列表中）

### GRD2-003 Subscriber 唯一约束内部矛盾
- **问题**：er-diagram.yml entities 段声明 (email, source) 复合唯一，modified_entities 段声明"不修改 unique 约束"（保留 email 单字段唯一），内部矛盾；state-machine guard 已写 (email, source)；若保留 email 单字段唯一，KD-13 source=HOME_BLOCK(4) 的语义失效（同邮箱多入口订阅只保留首写 source）
- **方案选择**：方案 A（复合唯一 (email, source)）——通用做法最合理最长远
- **理由**：
  1. 电商 Newsletter 订阅入口分析是核心运营能力（首页/页脚/弹窗/退出意图转化率差异大）
  2. KD-13 扩展 source=HOME_BLOCK(4) 本身隐含 source 数据完整性诉求
  3. Mailchimp/Klaviyo 等行业标杆普遍采用 (email, source) 复合唯一或 list_membership 关联表
  4. 幂等语义更清晰（同邮箱同来源短路 vs 同邮箱任意来源都被短路）
  5. 未来扩展性（订阅偏好管理的基础）
- **修复**：
  - modified_entities.NewsletterSubscriber 新增 changed_constraints 段：uk_newsletter_email(email) → uk_newsletter_email_source(email, source) 复合唯一迁移
  - DDL：ALTER TABLE newsletter_subscriber DROP INDEX uk_newsletter_email, ADD UNIQUE KEY uk_newsletter_email_source (email, source)
  - NewsletterService.insertIgnoreDuplicate 语义变为"同邮箱同来源重复订阅幂等短路，不同来源新增记录"
  - state-machine.yml subscriber_status guard 保留 (email, source) 唯一性校验，desc 补充 GRD2-003 方案 A 说明
  - scope-manifest.yml modified_tables 新增 newsletter_subscriber 约束变更
  - scope-manifest.yml migration_scripts 新增 migrate-uk-newsletter-email-to-composite.sql

### GRD2-004+005 scope-manifest.yml 遗漏消费端文件
- **问题**：KD-5 决定"首页动态渲染 + header/footer/公告条 全量改造"，但 scope-manifest.yml store.modify 仅列 3 个文件，遗漏 site-header.tsx/site-footer.tsx/data/navigation.ts（KD-5 无法落地）和 lib/api/store-types.ts（KD-13/14/17 前端类型扩展无法落地）
- **修复**：
  - store.modify 新增 4 个文件：
    - components/layout/site-header.tsx（mainNav/announcements 改为从 API 读取）
    - components/layout/site-footer.tsx（footerNav 改为从 API 读取）
    - data/navigation.ts（静态数据源改为 API 调用兜底回退或移除）
    - lib/api/store-types.ts（NewsletterSource 新增 HOME_BLOCK(4)；StoreBanner 新增 ctaTextSecondary/ctaLinkSecondary；BannerPosition.TOPBAR 标记 @Deprecated）
  - admin.modify 顺带补充（对应 WARNING GRD2-W09/W10）：
    - src/api/types.ts（Banner/BannerTranslation/BannerUpsert 接口扩展 + 新实体接口定义）
    - src/config/translatableFields.ts（按 KD-16 i18n_json 整块 JSON 模式适配）
  - scope-manifest.yml migration_scripts 新增 migrate-topbar-banner-to-announcements.sql（GRD2-W03 顺带落地）
  - constraints 段追加 GRD2-001~005 处理记录

### 遗留 WARNING（10 条，留待 L2 详设阶段处理）

| ID | 问题 | 处理时机 |
|----|------|---------|
| GRD2-W01 | er-diagram.yml modified_entities 缺失 StoreContentController 条目 | L2 详设 |
| GRD2-W02 | 消费端 API 路径模式不一致（/home/sections 嵌套 vs 基线平铺复数名词） | L2 详设 |
| GRD2-W04 | HomePageSection.data_json ProductRail.source 类型未明确 | L2 详设 |
| GRD2-W05 | KD-16 i18n_json 与 translatableFields.ts 字段级 i18n 策略兼容性 | L2 详设 |
| GRD2-W06 | 新 Controller 的 OperationLog 写入模式未明确 | L2 详设 |
| GRD2-W07 | JetCache key 后缀格式未明确 | L2 详设 |
| GRD2-W08 | acceptance.yml s-015~s-022 stale 残留未裁剪 | L2 详设 |
| GRD2-W09 | translatableFields.ts 缺失新实体字段策略（scope-manifest 已补，待 L2 实施） | L2 详设 |
| GRD2-W10 | portal-admin src/api/types.ts Banner 接口需扩展（scope-manifest 已补，待 L2 实施） | L2 详设 |
| GRD2-W03 | KD-17 TOPBAR Banner 迁移脚本未声明（scope-manifest 已补 migration_scripts，待 L2 实施） | L2 详设 |
