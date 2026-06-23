# 后端流程推断

> 本文件为 Phase 2.3 LLM 推断产物 + Phase 2.3.0 代码接地验证（iteration 5 补充）。
> 作为 Phase 2.3.1 维度激活的信号源，不作为下游直接消费。
> 推断结论需经 Phase 2.3.1 用户确认后，沉淀到 decision.md 的「后端关键决策」章节。

## 识别到的业务模式

本次 change 涉及 4 个原型页面，核心业务模式：

1. **可视化编排**（HomeBuilder）：拖拽排序 + 显示开关 + 多类型区块属性编辑（Hero / Announcement / ShopByColor / ThemeCards / ProductRail / EditorialFeature / Newsletter）
2. **结构化配置**（NavigationConfig）：主导航 + Mega Menu 列（引用品类/主题派生子链接）+ 页脚四栏 + 顶部公告条
3. **CRUD + 时间窗 + 上下线**（Banners）：Banner 已接入 AdminBannerController + BannerTranslation i18n，状态机 1=草稿/2=已发布/3=已归档
4. **缓存失效发布**（Publish）：原型描述 SSG 发布中心，但 KD-1 决策仅保持当前缓存失效能力（POST /api/admin/cache/invalidate + GET /api/admin/cache/invalidation-logs）

**新增 KD-5~8（用户在 iteration 4 补充）**：
- KD-5：消费端改造范围 — 首页动态渲染 + header/footer/公告条全量改造（portal-store/app/[locale]/page.tsx + layout.tsx + 公告条组件）
- KD-6：Mega Menu 列存储 — JSON 字段 mega_menu_json（NavigationItem 内嵌，不建独立表）
- KD-7：公告条数据源 — 独立 announcement 域（不依赖 navigation）
- KD-8：ThemeCards 区块数据源 — 引用分类 type=theme

跨页面共性：
- 保存即发布（KD-4）：HomeBuilder / NavigationConfig / Banners 保存后立即触发缓存失效，无草稿状态机
- i18n 多语言：HomePageSection 用单独翻译表（KD-3），Banner 已有 BannerTranslation，NavigationConfig 公告条/页脚文案也需多语言
- 引用关系：HomeBuilder Hero 复用 Banner position=HERO（KD-2，BannerPosition 枚举已含 HERO=1），NavigationConfig 引用品类/主题，ThemeCards 引用分类 type=theme（KD-8）

## 推断的后端机制（含代码接地验证）

> 代码接地状态：✅ 已实现 / ⚠️ 部分实现 / ❌ 未实现 / 🆕 需新增
> 验证依据：AdminBannerController / AdminBannerService / AdminCacheController / AdminCacheService / MarketingContentInvalidatedPublisher / MarketingAfterCommitRunner / MarketingCacheService / CdnInvalidationService / InvalidateEventConsumer / 枚举与实体扫描

### [事务] — ✅ Banner 已实现，🆕 HomeSection/Navigation/Announcement 需新增

- **Banner 现状**：AdminBannerService 所有写操作（create/update/delete/toggleStatus）均标注 `@Transactional`，双表写入（banner + banner_translation）原子性已保证
- **HomeSection 需新增**：home_sections + home_section_translations 双表写入（KD-3），需在 AdminHomeSectionService 加 `@Transactional`
- **Navigation 需新增**：NavigationItem 主表 + mega_menu_json（KD-6 内嵌）+ 翻译表，保存涉及多栏目（主导航+页脚+公告条 KD-7 独立域），需 `@Transactional`
- **Announcement 需新增**（KD-7）：独立 announcement 域，多语言文案写入需 `@Transactional`

### [异步] — ✅ Banner 已实现 after-commit，⚠️ 手动失效未实现

- **Banner 现状**：`MarketingAfterCommitRunner.run()` 注册 `TransactionSynchronization.afterCommit()`，事务提交后异步执行缓存失效 + MQ publish（无活动事务时立即执行，幂等短路场景兼容）
- **手动失效缺口**：AdminCacheController.manualInvalidate 是未实现占位（返回"手动失效功能开发中"），KD-1 明确不扩展 SSG 发布中心，但 Publish.vue 的"一键发布"按钮若要可用，需实现该端点（调用 ContentInvalidatedPublisher 或直接 CdnInvalidationService）

### [权限] — ✅ Banner 已实现，🆕 新 Controller 需加权限注解

- **Banner 现状**：`@RequirePermission("/banners")` 注解在 AdminBannerController 所有端点；AdminCacheController 用 `@RequirePermission("/cache")`
- **新增 Controller 需加权限**：
  - AdminHomeSectionController → `/home-sections`（或复用 `/site-builder`）
  - AdminNavigationController → `/navigation`
  - AdminAnnouncementController → `/announcements`（KD-7）
- **消费端读取 API**（KD-5）：StoreHomeSectionController / StoreNavigationController / StoreAnnouncementController 匿名可读，无需权限注解

### [消息队列] — ✅ Banner 已实现 RabbitMQ 链路，🆕 新域需扩展 event type

- **Banner 现状**：`MarketingContentInvalidatedPublisher.publish(TYPE_BANNER_CHANGED)` 通过 `DomainEventPublisher` 发 `content.invalidated` 事件；消费端 `InvalidateEventConsumer` 消费后调 `CdnInvalidationService.invalidatePaths()` 做 CDN purge
- **事件类型需扩展**：新增 `home_section_changed` / `navigation_changed` / `announcement_changed` 三种 type（参照 TYPE_BANNER_CHANGED 模式）
- **payload locales 已固定**：`ALL_LOCALES = ["en","es","fr"]`，新事件复用即可（KD-5 消费端多语言渲染需全部失效）

### [外部集成] — ⚠️ Hero 跨域引用 Banner，Navigation 引用 Categories

- **Hero 复用 Banner（KD-2）**：BannerPosition 枚举已含 HERO=1，AdminHomeSectionService 读取 Hero 区块时需按 `position=HERO + status=PUBLISHED + 时间窗` 查询 Banner。**推荐通过 Banner domain 暴露的 Service 接口调用，不直接查 Banner 表**（保持 domain 边界）
- **Navigation 引用 Categories**：NavigationConfig 原型显示导航项可链接到品类/主题（`linkType: 'taxonomy'` + `taxonomyId`），Mega Menu 列引用 `category-children` 或 `taxonomy-type`。需调用 Categories domain 查询接口校验存在性 + 派生子链接
- **ThemeCards 引用分类（KD-8）**：HomeBuilder 的 ThemeCards 区块只存 enabled/sort_order + 显示数量，实际主题从 `taxonomy type=theme` 读取，需跨域调用 Categories

### [定时任务] — ❌ Banner 时间窗自动上下线未实现

- **Banner 现状**：项目有 `@EnableScheduling` + 8 个 @Scheduled 任务（SalesWindowRefreshJob / OrderTimeoutScheduler / MarketingPromoScheduler 等），**但无 Banner 时间窗自动上下线任务**。Banner 的 start_time/end_time 仅在查询时按当前时间过滤（DEC-MKT-2「已过窗」前端派生），不自动改 status
- **HomeBuilder/Navigation 无时间窗**：无需定时任务
- **Announcement（KD-7）**：若公告条有时间窗需求（如限时活动公告），需新增 scheduled task

### [审计日志] — ✅ Banner 已实现，🆕 新域需复用或新建

- **Banner 现状**：`MarketingAuditRecorder.record(action, target, changes)` 在 create/update/delete/toggleStatus 中调用，记录操作人+动作+目标+变更摘要（toggleStatus 记 `{"status":{"before":"...","after":"..."}}`）
- **新域审计**：
  - HomeSection/Navigation/Announcement 的写操作均需调用审计（复用 MarketingAuditRecorder 或新建 SiteBuilderAuditRecorder）
  - 缓存失效日志已有 `AdminCacheService.logInvalidation()`，由 publisher 自动调用，新事件 type 会自动入日志

### [状态机并发] — ⚠️ Banner 有状态 guard 但无乐观锁，🆕 新实体建议加 @Version

- **Banner 现状**：`ContentStateGuards.transitionAllowed()` 校验状态迁移合法性（draft→published→archived），非法迁移抛 409703。**但 Banner 实体无 @Version 字段**，并发 update 可能后保存者覆盖前保存者
- **项目已有乐观锁基建**：MyBatisPlusConfig 注册了乐观锁插件，User/Role/AdminUser/OtpCode/UserSession 均用 @Version
- **新实体建议**：HomeSection / NavigationItem / Announcement 实体加 `@Version` 字段，利用已有插件防并发覆盖
- **Banner 乐观锁缺口**：是否给 Banner 补 @Version 需用户决策（属于 scope 外，可记为独立 change）

### [幂等] — ✅ Banner 已实现 toggle 幂等，🆕 新域保存幂等

- **Banner 现状**：toggleStatus 幂等短路（目标态=当前态直接返回，不写审计不发事件）；缓存失效操作幂等（重复 invalidate 无副作用）
- **新域幂等**：
  - HomeSection/Navigation/Announcement 保存幂等：相同配置重复保存产生相同结果，避免重复触发缓存失效（可通过 after-commit + 事件去重实现）
  - 缓存失效幂等：复用已有 CDN purge 幂等性

### [限流降级] — ⚠️ 缓存已有但无防抖，消费端读取需走 JetCache

- **Banner 现状**：`MarketingCacheService.invalidateFamily(Family.BANNERS)` 失效缓存族；消费端 `StoreBannerService` 走 JetCache Redis 远端层（DTO 实现 Serializable）
- **防抖缺口**：管理员频繁保存（连续点击）每次都触发全量缓存失效 + CDN purge，**无防抖合并**。建议后端合并（5-10s 窗口内多次保存合并为一次失效事件）
- **消费端缓存（KD-5）**：
  - StoreHomeSectionController 读取区块配置 → JetCache 本地+远程两级，key 按 `portal + locale`，TTL 600s（参照 store:authconfig 模式）
  - StoreNavigationController 读取导航配置 → 同上
  - StoreAnnouncementController 读取公告条 → 同上
  - 写即失效：`@CacheInvalidate` 在 Service 层落地（参照 AuthConfigService 模式）

## 确认事项（代码接地后精炼）

需要在 Phase 2.3.1 与用户对齐的不确定点，每条含相关 tag：

1. **[事务]** HomeSection/Navigation/Announcement Service 层用 `@Transactional` 即可（与 Banner 一致），是否需要分布式事务？→ **推荐不需要**（单库本地事务足够，与项目现有模式一致）
2. **[异步]** AdminCacheController.manualInvalidate 是否实现？KD-1 说"不扩展 SSG 发布中心"，但 Publish.vue 的"一键发布"按钮若要可用需实现该端点 → **需用户确认**：Publish.vue 保留 mock 还是实现手动失效？
3. **[权限]** 新增 Controller 权限粒度：复用现有 admin 角色校验（`/home-sections` / `/navigation` / `/announcements`），还是新增 `site_builder` 细粒度权限？→ **推荐复用**（与 Banner 的 `/banners` 模式一致）
4. **[外部集成]** HomeBuilder Hero 读取 Banner 的方式：通过 Banner domain 暴露的 Service 接口（推荐，保持 domain 边界），还是直接查 Banner 表？→ **推荐 Service 接口**
5. **[外部集成]** NavigationConfig 引用分类的校验时机：保存时强校验（taxonomyId 不存在则报错），还是软引用（保存不校验，读取时 join）？→ **推荐强校验**（避免悬挂引用）
6. **[定时任务]** Banner 时间窗自动上下线是否纳入本次 scope？当前未实现，仅查询时按时间过滤 → **推荐不纳入**（DEC-MKT-2 已约定前端派生，scope 外）
7. **[审计日志]** 新域审计复用 MarketingAuditRecorder 还是新建 SiteBuilderAuditRecorder？→ **推荐复用**（避免审计表分散）
8. **[状态机并发]** HomeSection/NavigationItem/Announcement 实体是否加 @Version？→ **推荐加**（项目已有乐观锁插件，防并发覆盖）
9. **[限流降级]** 缓存失效防抖策略：后端合并（5-10s 窗口），还是前端节流？→ **推荐后端合并**（更可靠，参照已有 after-commit 模式扩展）
10. **[限流降级]** 消费端读取 API 的 JetCache 策略：本地+远程两级（TTL 600s，参照 store:authconfig），还是仅远程？→ **推荐两级**（低频变更数据，本地缓存加速）

## operation-paths.yml 覆盖缺口（iteration 5 发现）

operation-paths.yml 仅捕获 6 个操作，**4 个目标页面（HomeBuilder/NavigationConfig/Banners/Publish）有 0 个操作被捕获**。原因：提取脚本只抓 `button.btn-primary` / `button.btn-ghost` 选择器，漏了：
- `button.btn-gold`（保存并发布 / 保存并生成首页 / 保存并重新生成 header）
- `button.btn-outline`（前台预览 / 新增 Banner）
- `button.btn-danger-ghost`（删除）
- `Toggle` 组件切换（Banner 上下线 / 区块显示开关）
- 拖拽排序（HomeBuilder 区块排序 / NavigationConfig 导航项排序）
- Tab 切换（NavigationConfig 主导航/页脚/公告条）

**影响**：L0 语义提取（Phase 3.1）依赖 operation-paths.yml 推断状态机，4 个目标页面的操作路径缺失会导致 er-diagram/state-machine 对这些页面建模不完整。

**建议**：Phase 3.1 前手动补充 4 个目标页面的操作路径，或重跑提取脚本（需调整选择器规则）。本次 change 不阻断，但需在 Phase 3.1 启动前确认。
