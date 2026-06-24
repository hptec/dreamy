# 验收基准：site-decoration-fullstack

生成时间：2026-06-23T08:50:00Z
L0 模式：incremental
change_type: alignment

## FUNC — 功能验收

来源：acceptance.yml 的 10 个 FLOW 场景（s-329~s-338）

| 编号 | 场景标题 | 页面 | 来源 |
|------|---------|------|------|
| FUNC-001 | 管理员配置首页区块 | HomeBuilder | acceptance.yml #s-329 |
| FUNC-002 | 管理员配置导航与页脚 | NavigationConfig | acceptance.yml #s-330 |
| FUNC-003 | 管理员配置公告条 | NavigationConfig | acceptance.yml #s-331 |
| FUNC-004 | 管理员管理 Banner | Banners | acceptance.yml #s-332 |
| FUNC-005 | 管理员手动失效缓存 | Publish | acceptance.yml #s-333 |
| FUNC-006 | 消费者访问首页 | portal-store 首页 | acceptance.yml #s-334 |
| FUNC-007 | 消费者访问任意页面（header/footer 渲染） | portal-store layout | acceptance.yml #s-335 |
| FUNC-008 | 消费者订阅 Newsletter | portal-store 首页 Newsletter | acceptance.yml #s-336 |
| FUNC-009 | HomeBuilder Hero 区块跨域读取 Banner | HomeBuilder（内部调用） | acceptance.yml #s-337 |
| FUNC-010 | NavigationConfig 引用 Taxonomy 跨域校验 | NavigationConfig（内部调用） | acceptance.yml #s-338 |

## EDGE — 边界/异常验收

来源：boundary-scenarios.yml（304 条，8 类覆盖）

| 类别 | 场景数 | 说明 |
|------|--------|------|
| null | 112 | 空值/缺失字段场景 |
| extreme | 54 | 极值/边界值场景 |
| concurrent | 14 | 并发/竞态场景 |
| auth | 26 | 权限/认证场景 |
| network | 27 | 网络/超时场景 |
| integrity | 16 | 数据完整性场景 |
| state | 21 | 状态机转换场景 |
| callsite-compat | 34 | 调用方兼容性场景 |

**注意**：boundary-scenarios.yml 部分场景含 stale 残留（subscriber 旧状态机字段 status/unsubscribed_at），L2 详设阶段须同步裁剪。

## UI — UI 验收检查点

### 页面清单

| page_id | 原型文件 | 路由 | 改造状态 |
|---------|---------|------|---------|
| HomeBuilder | prototype/portal-admin/src/views/HomeBuilder.vue | /site/home | 全栈新建（后端 API + 前端接入 + 消费端首页改造） |
| NavigationConfig | prototype/portal-admin/src/views/NavigationConfig.vue | /site/navigation | 全栈新建（后端 API + 前端接入 + 消费端 layout 改造） |
| Banners | prototype/portal-admin/src/views/Banners.vue | /banners | 已接入（AdminBannerController），本期扩展 cta_text_secondary 字段 |
| Publish | prototype/portal-admin/src/views/Publish.vue | /publish | 已接入（AdminCacheController），本期不改造 |

### 核心约束

→ 见 decision.md 的"原型强对照约束"章节

- 样式 token：复用项目现有 tailwind.config.js + CSS 变量（gold/canvas/ink/sage）
- CDN → Vite：原型为 Vue SFC，apply 直接复制 .vue 文件
- 视觉还原度：L4 验收 ≥ 95%

### 详细字段/交互规格

> 详细页面交互规格将在 L2 前端详设阶段产出（由 ui_test_designer 生成 ui-test-spec.yml），届时请更新此引用。

## PERF — 性能基线

| 指标 | 目标 | 来源 |
|------|------|------|
| 消费端首页 API 响应时间 P95 | < 200ms（JetCache 缓存命中）/ < 500ms（缓存未命中） | KD-BE-4 |
| 消费端导航 API 响应时间 P95 | < 200ms（JetCache 缓存命中） | KD-BE-4 |
| JetCache TTL | 300s（Caffeine 本地 + Redis 远程） | KD-BE-4 + GRD-W02 |
| 管理端保存 API 响应时间 P95 | < 500ms（含 in-process 缓存失效） | KD-BE-4 |
| 并发保存冲突 | 乐观锁检测（MyBatis-Plus @Version 或 updated_at 比对） | BE-DIM-4 |

## SEC — 安全要求

| 维度 | 要求 | 来源 |
|------|------|------|
| 认证 | 管理端写入 API 需 admin JWT；消费端读取 API 匿名可读 | KD-BE-2 |
| 授权 | RBAC 权限码：/site/home, /site/navigation, /site/announcement, /banners, /publish | KD-BE-2 + GRD-007 |
| 数据隔离 | 多租户不适用（单租户管理后台） | - |
| 输入校验 | 所有写入 API 须校验 i18n_json 结构、section_type 枚举、taxonomy_id 存在性 | BE-DIM-4 |
| 错误处理 | 沿用 MarketingException + MarketingErrorCode（6 位码 + R 包络） | KD-BE-3 + GRD-003 |
| Controller 登记 | 新 Controller 须加入 MarketingExceptionHandler assignableTypes | GRD-006 |

## 影响域

| 域 | 实体 | 操作 |
|----|------|------|
| site_builder（新建） | HomePageSection, NavigationItem, FooterColumn, FooterLink, Announcement | 新建 |
| marketing/content（已有） | Banner, BannerTranslation | 修改（add cta_text_secondary + cta_link_secondary） |
| subscriber（已有） | NewsletterSubscriber, NewsletterSource | 修改（add HOME_BLOCK(4)） |
| cache（已有） | CacheInvalidationLog, MarketingCacheService.Family, MarketingContentInvalidatedPublisher | 修改（add HOME_SECTION/NAVIGATION/ANNOUNCEMENT 族 + TYPE_*_CHANGED 常量） |
| identity（已有） | DataInitializer | 修改（add /site/announcement 权限种子） |
