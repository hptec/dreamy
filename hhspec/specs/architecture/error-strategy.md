# 错误处理策略 - site-decoration-fullstack（增量）

本文档定义 site-decoration-fullstack 变更新增的 site_builder 限界上下文（KD-15）的分层错误处理、6 位错误码号段体系、跨域调用降级与前端呈现约定。与 baseline 各域 error-strategy.md 并列（baseline 无 site_builder 域，本变更新增）。

## 错误响应格式（与 baseline 一致）

**契约层**（site-builder-api.openapi.yml 的 ErrorResponse）：
```json
{ "code": 404801, "message": "首页区块不存在", "details": { "section_id": 123 } }
```

**线上传输层**（huihao R 包络）：
```json
{ "code": 404801, "message": "首页区块不存在", "data": { "section_id": 123 } }
```

- 成功：`{ code: 0, message: "ok", data: <payload> }`；失败：`code` 为业务错误码，**details 内容装入 R 的 data 字段**。
- `message`：admin 端固定中文（后台不翻译，与 baseline 约定一致）；消费端按 locale 本地化（从 i18n_json 读取）。
- 数字码为契约稳定锚点，文案变更不影响前端 code 映射。

## 错误码号段总表（site_builder 新增）

| 体系 | 格式 | 号段 | 归属 |
|------|------|------|------|
| identity 既有 | 5 位：HTTP(3)+序号(2) | 40000~50401 | baseline（不改动） |
| 新七域（portal-api-integration） | 6 位：HTTP(3)+域段(1)+序号(2) | catalog=5/trading=6/marketing=7/review=8/shipping=9/analytics=0/showroom=1 | baseline（不改动） |
| i18n 活跃域 | 6 位：HTTP(3)+域段(1)+序号(2) | gateway=2、ai_translation=3 | glossary=4 已退役，历史错误码仅供迁移追溯 |
| **site_builder 新增** | **6 位：HTTP(3)+域段(1)+序号(2)** | **site_builder=8** | **本 change** |

高 3 位恒对应 HTTP 状态；同域同 HTTP 状态下序递增。集中码表维护在 site-builder-api.openapi.yml info 码表，本表为权威汇总。

## 错误分类与码表

### site_builder 域（域段 8）

| code | 标识 | HTTP | message_zh | 触发 | EDGE 场景 |
|------|------|------|-----------|------|-----------|
| 404801 | HOME_SECTION_NOT_FOUND | 404 | 首页区块不存在 | GET/PUT/DELETE `/home-sections/{id}` 不存在 | — |
| 404802 | NAVIGATION_NOT_FOUND | 404 | 导航配置不存在 | GET `/navigation` 无数据（首次访问应自动初始化空配置） | — |
| 404803 | FOOTER_NOT_FOUND | 404 | 页脚配置不存在 | GET `/footer` 无数据 | — |
| 404804 | ANNOUNCEMENT_NOT_FOUND | 404 | 公告不存在 | GET/PUT/DELETE `/announcements/{id}` 不存在 | — |
| 404805 | TAXONOMY_NOT_FOUND | 404 | 导航引用的分类不存在 | NavigationItem.taxonomy_id 跨域校验失败 | EDGE-008 |
| 409801 | HOME_SECTION_SORT_CONFLICT | 409 | 首页区块排序冲突（并发更新） | 乐观锁 version 不匹配 | EDGE-005 |
| 409802 | NAVIGATION_ITEM_CYCLE_DETECTED | 409 | 导航项形成循环引用 | parent_id 链检测到环 | EDGE-006 |
| 409803 | FOOTER_COLUMN_SORT_CONFLICT | 409 | 页脚栏目排序冲突 | 乐观锁 version 不匹配 | — |
| 409804 | ANNOUNCEMENT_TIME_WINDOW_CONFLICT | 409 | 公告同优先级时间窗重叠 | priority + 时间窗唯一约束冲突 | EDGE-014 |
| 409805 | NAVIGATION_VERSION_CONFLICT | 409 | 导航配置版本冲突（并发保存） | 乐观锁 version 不匹配 | EDGE-010 |
| 422801 | HOME_SECTION_DATA_JSON_INVALID | 422 | 首页区块 data_json 结构不合法 | data_json 不符合 section_type 对应 schema | EDGE-002 |
| 422802 | HOME_SECTION_TYPE_INVALID | 422 | 首页区块类型非法 | section_type 不在枚举 | EDGE-003 |
| 422803 | FOOTER_COLUMN_REF_INVALID | 422 | 页脚链接引用栏目不存在 | links.column_id 不在 columns 中 | EDGE-011 |
| 422804 | FOOTER_LINK_URL_INVALID | 422 | 页脚链接 URL 格式非法 | url 不符合 HTTP(S) 协议 | EDGE-012 |
| 422805 | ANNOUNCEMENT_TIME_WINDOW_INVALID | 422 | 公告时间窗非法（start_at >= end_at） | start_at >= end_at | EDGE-013 |
| 422806 | I18N_JSON_LOCALE_INVALID | 422 | i18n_json locale 键非法 | 非 en/es/fr 之一 | — |
| 422807 | I18N_JSON_STRUCTURE_INVALID | 422 | i18n_json 结构不合法 | 不是 {locale: {field: value}} 结构 | — |
| 422808 | FIELD_VALIDATION_FAILED | 422 | 字段校验失败 | 其他字段级校验（如 sort_order 负数、enabled 非 boolean） | — |
| 500801 | SITE_BUILDER_INTERNAL_ERROR | 500 | 站点装修内部错误 | 数据库异常、未捕获异常 | — |
| 500802 | CACHE_INVALIDATION_FAILED | 500 | 缓存失效失败（降级记录，不阻断主流程） | publisher.publish 失败 | — |
| 502801 | BANNER_SERVICE_UNAVAILABLE | 502 | Banner 服务不可用（跨域调用失败） | StoreBannerService.list(HERO, locale) 异常 | EDGE-019 |
| 502802 | TAXONOMY_SERVICE_UNAVAILABLE | 502 | Taxonomy 服务不可用 | TaxonomySvc.findByType 异常 | — |
| 502803 | WEDDING_SERVICE_UNAVAILABLE | 502 | Wedding 服务不可用 | WeddingSvc.fetchStoreWeddings 异常 | — |
| 502804 | PRODUCT_SERVICE_UNAVAILABLE | 502 | Product 服务不可用 | ProductSvc.findByIds 异常 | — |

**复用基线码**：
- 401 未认证 → `40100`（AdminJwtFilter / StoreJwtFilter）
- RBAC 无权限 → `40300`（@RequirePermission 校验失败，site_builder 新增权限点：`/site/home`、`/site/navigation`、`/site/announcement`）
- 通用 404 → `40400`
- 500 内部错误 → `50000`
- DB 异常 → `50001`
- newsletter 订阅相关错误沿用 marketing 域（409704 重复、422704 email 格式、500705 订阅失败），KD-13

**完整性核对**：site_builder 域共 23 个新码（404×5 + 409×5 + 422×8 + 500×2 + 502×4 - 重复复用），无重复；高 3 位与 HTTP 状态一致；号段无交叉；与 site-builder-api.openapi.yml info 码表逐一一致。

## 分层错误处理

| 层级 | 职责 | 错误类型 | 处理方式 |
|------|------|----------|----------|
| **表示层（AdminHomePageSectionController / AdminNavigationController / AdminFooterController / AdminAnnouncementController + StoreContentController + MarketingExceptionHandler assignableTypes，KD-15）** | 捕获一切异常 → R.fail(code, message, data=details)；admin 中文；消费端按 locale 本地化；公开路径白名单放行匿名 | MethodArgumentNotValidException→422808、DomainException、AccessDenied、跨域调用异常 | 映射 HTTP + 6 位码；访问日志按 family 分组 |
| **应用层（HomePageSectionService / NavigationService / FooterService / AnnouncementService / StoreContentService）** | 业务规则与跨域集成校验（区块类型/data_json 结构/导航循环/页脚引用/公告时间窗/Hero 派生） | HomeSectionNotFound, NavigationItemCycleDetected, AnnouncementTimeWindowConflict, TaxonomyNotFound | 抛领域异常，表示层映射 |
| **领域层（HomePageSection / NavigationItem / FooterColumn / FooterLink / Announcement 实体）** | 不变量与状态约束 | I18nJsonStructureInvalid, FieldValidationFailed | 抛领域异常向上传播 |
| **基础设施层（跨域调用端口 + 缓存失效端口）** | 跨域调用错误、缓存失效错误 | BannerServiceUnavailable, TaxonomyServiceUnavailable, WeddingServiceUnavailable, ProductServiceUnavailable, CacheInvalidationFailed | 转基础设施异常；按降级矩阵处理（消费端降级空数据，admin 端直接报错） |

## 24 个 EDGE 场景的错误处理矩阵

| EDGE | 触发流程 | 错误码 | 处理策略 |
|------|---------|--------|----------|
| EDGE-001 i18n_json 部分缺失 | FLOW-SB01 | — | 接受（EN 为基准，消费端三层回退） |
| EDGE-002 data_json 字段缺失 | FLOW-SB01 | 422801 | 拒绝，返回 details.field 指示缺失字段 |
| EDGE-003 section_type 非法 | FLOW-SB01 | 422802 | 拒绝，details.allowed 列出合法枚举 |
| EDGE-004 sort_order 重复 | FLOW-SB01 | — | 接受（按 sort_order, id 排序） |
| EDGE-005 并发更新首页区块 | FLOW-SB01 | 409801 | 乐观锁拒绝，前端提示刷新后重试 |
| EDGE-006 导航 parent_id 环 | FLOW-SB02 | 409802 | DFS 检测后拒绝，details.chain 指出环节点 |
| EDGE-007 mega_menu_json null | FLOW-SB02 | — | 接受（非 mega menu 项允许 null） |
| EDGE-008 taxonomy_id 不存在 | FLOW-SB02 | 404805 | 跨域校验失败，拒绝保存 |
| EDGE-009 整体替换 id 缺失 | FLOW-SB02 | — | 接受（视为新增，生成新 id） |
| EDGE-010 导航并发保存 | FLOW-SB02 | 409805 | 乐观锁拒绝 |
| EDGE-011 column_id 引用缺失 | FLOW-SB03 | 422803 | 拒绝，details.link_id 指示问题链接 |
| EDGE-012 link.url 格式非法 | FLOW-SB03 | 422804 | 拒绝，details.url 指示非法值 |
| EDGE-013 公告时间窗颠倒 | FLOW-SB04 | 422805 | 拒绝，details.start_at/end_at 指示问题值 |
| EDGE-014 公告 priority+时间窗冲突 | FLOW-SB04 | 409804 | 拒绝，details.conflict_id 指示冲突公告 |
| EDGE-015 公告启停不影响时间窗 | FLOW-SB04 | — | 接受（启停独立于时间窗） |
| EDGE-016 所有 section disabled | FLOW-SB05 | — | 返回空数组，不报错 |
| EDGE-017 无有效 Hero Banner | FLOW-SB05 | — | 正常省略 Hero 区块，不记录错误、不阻断聚合 |
| EDGE-018 i18n locale 缺失 | FLOW-SB05 | — | 三层回退（locale→en→主表字段） |
| EDGE-019 跨域全失败 | FLOW-SB05 | 502801/802/803/804 | 各自降级空数据，仍返回 custom 类型 section |
| EDGE-020 缓存击穿 | FLOW-SB05 | — | singleflight 合并并发重建 |
| EDGE-021 所有导航 disabled | FLOW-SB06 | — | 返回空树，不报错 |
| EDGE-022 footer 为空 | FLOW-SB07 | — | 返回空 columns，不报错 |
| EDGE-023 无有效公告 | FLOW-SB08 | — | 返回空数组，不报错 |
| EDGE-024 重复 Newsletter 订阅 | FLOW-SB09 | — | 幂等成功（避免泄露订阅状态） |

## 韧性策略

### 跨域调用降级矩阵

| 调用方 | 被调方 | 失败场景 | 降级策略 | 错误码 |
|--------|--------|---------|---------|--------|
| StoreContentService (FLOW-SB05 Hero) | StoreBannerService.list(HERO, locale) | Banner 查询异常 | 省略 Hero 区块 + WARN 日志，不阻断首页聚合 | 502801 |
| StoreContentService (FLOW-SB05 ThemeCards) | TaxonomySvc.findByType | Taxonomy 表异常 | 降级空 themes 数组 + WARN 日志 | 502802 |
| StoreContentService (FLOW-SB05 ProductRail) | ProductSvc.findByIds/findNewArrivals | Product 表异常 | 降级空 products 数组 + WARN 日志 | 502804 |
| StoreContentService (FLOW-SB05 EditorialFeature) | WeddingSvc.fetchStoreWeddings | Wedding 表异常 | 降级空 weddings 数组 + WARN 日志 | 502803 |
| NavigationService (FLOW-SB02 校验) | TaxonomySvc.findById | Taxonomy 不存在 | **不降级，拒绝保存**（admin 端数据完整性优先） | 404805 |
| SubscriberService (FLOW-SB09 订阅) | （内部调用） | DB 异常 | 返回 500705，前端 toast 提示稍后重试 | 500705 |

**降级原则**：
- **消费端（store）**：跨域失败优先降级空数据，保证首页可用性
- **后台（admin）**：跨域校验失败直接拒绝，保证数据完整性
- **缓存失效失败**：记录 ERROR 日志但不阻断主流程（最终一致性）

### 缓存失效韧性

| 失败场景 | 处理 |
|---------|------|
| cache.invalidateFamily 失败 | 记录 ERROR，事务仍提交；下次读触发刷新 |
| publisher.publish 失败 | 记录 ERROR，不重试；其他节点 L1 可能脏，但 L2 已失效，最终一致 |
| 订阅节点事件丢失 | 该节点 L1 脏，但 L2 已失效，下次读从 DB 重建 |
| singleflight 防击穿 | 同 key 并发重建合并为单次 DB 查询 |

### 数据库异常兜底

| 异常类型 | 处理 |
|---------|------|
| DuplicateKeyException | 映射到对应 409xx 冲突码 |
| DataIntegrityViolationException | 映射到 422808 FIELD_VALIDATION_FAILED |
| 乐观锁（version 不匹配） | 映射到对应 409xx 冲突码 |
| 其他 SQLException | 50001 DB 异常（baseline 既有码） |

## 传播路径

```
Controller (admin/store)
    ↓ 抛出 DomainException 或未捕获异常
@RestControllerAdvice (MarketingExceptionHandler, KD-15 assignableTypes)
    ↓ 捕获 + 映射 6 位码 + 构造 details
R.fail(code, message, data=details)
    ↓ HTTP 响应
{ code, message, data }  // huihao R 包络
```

**MarketingExceptionHandler assignableTypes**（KD-15 要求，须在 advice 配置中登记）：
- AdminHomePageSectionController
- AdminNavigationController
- AdminFooterController
- AdminAnnouncementController
- StoreContentController（扩展部分）

**未登记的 Controller** 走 GlobalExceptionHandler 兜底（baseline 既有），错误码可能不准。

## i18n_json locale 回退策略（EDGE-018）

消费端按 locale 解析 i18n_json 时的三层回退：

```
resolveI18n(i18n_json, locale, mainField):
  1. 若 i18n_json[locale] 存在且非空 → 返回 i18n_json[locale]
  2. 若 i18n_json.en 存在且非空 → 返回 i18n_json.en
  3. 若主表 mainField 非空 → 返回 mainField
  4. 返回空字符串 ""（不返回 null，避免前端崩溃）
```

**应用范围**：所有含 i18n_json 的实体（HomePageSection / NavigationItem / FooterColumn / FooterLink / Announcement）。

**例外**：admin 端直接透传 i18n_json 整块对象，不做 locale 解析（运营需要看到所有语言的文案）。

## 缓存与 DB 不一致处理

| 场景 | 处理 |
|------|------|
| admin 写 DB 成功但缓存失效失败 | L1/L2 可能脏，但 publisher.publish 最终广播；最坏情况 5 分钟后 L1 TTL 过期 |
| 消费端读到脏数据 | 接受（最终一致性），不主动校验 |
| 缓存穿透（查不存在的 key） | 空结果也缓存（短 TTL 60s），避免反复查 DB |
| 缓存击穿（热 key 过期） | singleflight 合并重建 |
| 缓存雪崩（多 key 同时过期） | TTL 加随机抖动（±60s） |
