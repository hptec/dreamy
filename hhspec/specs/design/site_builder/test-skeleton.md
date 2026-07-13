# L2 测试骨架 - site_builder 域

> 来源：acceptance.yml（338 场景）+ api-detail.md + data-detail.md + error-mapping.yml
> 覆盖：七层后端测试 + 韧性测试层 + 业务流 DAG + flow_groups
> 原则：分层 Mock 策略 — 单元测试允许 I/O 边界 stub，集成测试及以上严格零 Mock

## 测试分层

| 层 | 类型 | 范围 | Mock 策略 | 覆盖来源 |
|----|------|------|----------|---------|
| L1 | 单元测试 | 单个 Service 方法 | I/O 边界 stub（Repository/Mapper） | acceptance 338 场景的关键路径 |
| L2 | 模块内集成 | site_builder 域内多 Service 协作 | 零 Mock，真实 DB（Testcontainers MySQL） | 业务流 DAG |
| L3 | 模块间集成 | site_builder + 跨域（marketing/catalog/showroom/identity） | 零 Mock，真实服务 | FLOW-SB05~SB09 跨域 |
| L4 | 契约测试 | OpenAPI schema 校验 | 零 Mock | site-builder-api.openapi.yml |
| L5 | API 测试 | Controller 端到端 | 零 Mock，真实 HTTP | 端点 20 个 |
| L6 | 快照测试 | 响应结构快照 | 零 Mock | 关键端点响应 |
| L7 | 异步集成测试 | 缓存失效链 | 零 Mock，真实 Redis pub/sub | FLOW-SB10 |
| L8 | 韧性测试 | 跨域降级 + 缓存降级 | 故障注入 | EDGE-017/019/020 |

## flow_groups

| group_id | 描述 | 包含流程 | 测试覆盖 |
|---------|------|---------|---------|
| FG-01 | 后台首页区块管理 | FLOW-SB01 + acceptance s-001~s-002 + bs-001~bs-080 | L1+L2+L5 |
| FG-02 | 后台导航管理 | FLOW-SB02 + acceptance s-003~s-004 + bs-081~bs-160 | L1+L2+L5 |
| FG-03 | 后台页脚管理 | FLOW-SB03 + bs-161~bs-200 | L1+L2+L5 |
| FG-04 | 后台公告管理 | FLOW-SB04 + acceptance s-005~s-006 + bs-201~bs-280 | L1+L2+L5 |
| FG-05 | 消费端首页渲染 | FLOW-SB05 + acceptance FLOW-006/009 + bs-281~bs-300 | L3+L5+L6+L8 |
| FG-06 | 消费端 header/footer/公告 | FLOW-SB06~SB08 + acceptance FLOW-007 | L3+L5 |
| FG-07 | Newsletter 订阅 | FLOW-SB09 + acceptance FLOW-008 | L3+L5 |
| FG-08 | 缓存失效链 | FLOW-SB10 + acceptance FLOW-005 | L7+L8 |

## 业务流 DAG

```
[Admin UI] → [AdminAPI] → [Service] → [Repository] → [DB]
                              ↓
                       [cache.invalidateFamily] (事务内)
                              ↓
                       [publisher.publish] (事务外)
                              ↓
                       [Redis pub/sub]
                              ↓
                       [各节点 L1 Caffeine 失效]

[Store UI] → [StoreAPI] → [StoreContentService]
                              ↓
                    [cache.get] → miss → [DB] + 跨域调用
                              ↓                     ↓
                              ↓            [BannerSvc/TaxonomySvc/ProductSvc/WeddingSvc]
                              ↓                     ↓ (失败降级空数据)
                              ↓            [SubscriberSvc] (FLOW-SB09)
                              ↓
                    [cache.set]
                              ↓
                       [R.ok(response)]
```

## L1 单元测试 AAA 骨架

### HomePageSectionService

#### TC-U001: createHomeSection 正常路径
- **A**（Arrange）: 构造 HomePageSectionUpsert（section_type=product_rail, enabled=true, sort_order=1, data_json={source:recommend, product_ids:[1,2,3], limit:6}）
- **A**（Act）: 调用 createHomeSection(upsert)
- **A**（Assert）: 验证 repository.insert 被调用 1 次，cache.invalidateFamily 被调用 1 次，publisher.publish 被调用 1 次，OperationLog 被写入，返回 dto.id 非空

#### TC-U002: createHomeSection js_guard 失败（section_type=hero 但 data_json 非空）
- **A**: 构造 upsert（section_type=hero, data_json={foo:bar}）
- **A**: 调用 createHomeSection
- **A**: 抛 SectionTypeDataMismatchException（422808），repository.insert 未调用

#### TC-U003: updateHomeSection 乐观锁冲突
- **A**: mock repository.findById 返回 version=5 的 entity，upsert.version=4
- **A**: 调用 updateHomeSection(1, upsert)
- **A**: 抛 HomeSectionSortConflictException（409801）

#### TC-U004: toggleHomeSection 正常
- **A**: entity enabled=true, version=0
- **A**: toggle(1, false)
- **A**: repository.updateEnabled 被调用，参数 enabled=false

#### TC-U005~U020: 其他 home-sections 单元测试
- sort_order 负数 → 422808
- section_type 非法 → 422802
- i18n_json locale 键非法 → 422807
- i18n_json 结构非法 → 422807
- ...

### NavigationService

#### TC-U021: saveNavigation 循环依赖检测
- **A**: items = [{id:1, parent_id:2}, {id:2, parent_id:1}]
- **A**: saveNavigation
- **A**: 抛 NavigationItemCycleException（409802），details.chain=[1,2,1]

#### TC-U022: saveNavigation taxonomy_id 跨域校验失败
- **A**: items=[{taxonomy_id:999}], mock TaxonomyService.findById 返回 empty
- **A**: saveNavigation
- **A**: 抛 TaxonomyNotFoundException（404805）

#### TC-U023: saveNavigation 整体替换
- **A**: 现有 items=[1,2,3]，upsert items=[2,4]（保留 2，新增 4，删除 1,3）
- **A**: saveNavigation
- **A**: repository.deleteByIdsNotIn([2,4]) 被调用，upsert(2) + upsert(4) 被调用

#### TC-U024~U035: 其他 navigation 单元测试

### FooterService / AnnouncementService / StoreContentService

#### TC-U036~U060: 其他单元测试

---

## L2 模块内集成测试 AAA 骨架

### TC-I001: 首页区块 CRUD 端到端
- **A**: Testcontainers MySQL 启动，初始化 home_sections 表
- **A**: POST 创建 → GET 列表 → PUT 更新 → PATCH toggle → DELETE → GET 确认删除
- **A**: 每步响应正确，DB 状态正确，cache 失效被触发（验证 Redis L2 被清除）

### TC-I002: 导航整体替换端到端
- **A**: 初始化 navigation_items=[1,2,3]
- **A**: PUT /api/admin/site-builder/navigation body={items:[{id:2,...},{label:"new"}]}
- **A**: DB 中 id=1,3 被删除，id=2 更新，新增 id=4，version+1

### TC-I003: 公告时间窗冲突
- **A**: 初始化 announcement(priority=1, start=2026-06-23, end=2026-06-30)
- **A**: POST 新公告(priority=1, start=2026-06-25, end=2026-07-05)
- **A**: 抛 AnnouncementTimeWindowConflictException（409804）

### TC-I004~I015: 其他模块内集成

---

## L3 模块间集成测试 AAA 骨架

### TC-M001: 消费端首页聚合（跨域正常）
- **A**: 初始化 home_sections=[hero, theme_cards, product_rail, editorial_feature, newsletter]，mock BannerSvc/TaxonomySvc/ProductSvc/WeddingSvc 返回正常数据
- **A**: GET /api/store/content/home?locale=es
- **A**: 响应包含 5 个 section，各 section.data 按 locale 扁平化

### TC-M002: 消费端首页聚合（跨域全失败降级）
- **A**: 初始化同上，mock 所有跨域 Service 抛异常
- **A**: GET /api/store/content/home
- **A**: 响应包含 5 个 section，hero/themes/products/weddings 为空数据，custom 类型 section 正常返回，WARN 日志记录

### TC-M003: Newsletter 订阅跨域
- **A**: 初始化空 subscriber 表
- **A**: POST /api/store/newsletter body={email:user@example.com, source:HOME_BLOCK, locale:en}
- **A**: subscriber 表新增记录，source=4

### TC-M004: Newsletter 重复订阅幂等
- **A**: 初始化 subscriber(email=user@example.com) 已存在
- **A**: POST /api/store/newsletter body={email:user@example.com, source:HOME_BLOCK}
- **A**: 返回成功（不抛 409704），subscriber 表无新增

### TC-M005~M010: 其他模块间集成

---

## L4 契约测试

### TC-C001: OpenAPI schema 校验
- **A**: 加载 site-builder-api.openapi.yml
- **A**: 对每个端点的请求和响应 schema 校验
- **A**: 所有 schema 符合 OpenAPI 3.0.3 规范

### TC-C002: R 包络校验
- **A**: 调用每个端点
- **A**: 响应体结构 {code, message, data}
- **A**: 成功 code=0，失败 code 为 6 位错误码

---

## L5 API 测试

### TC-A001~A020: 20 个端点各一个 happy path
### TC-A021~A040: 20 个端点各一个 error path（4xx）
### TC-A041~A050: 鉴权测试（未认证 401 + 无权限 403）

---

## L6 快照测试

### TC-S001: GET /api/store/content/home 响应快照
### TC-S002: GET /api/store/content/navigation 响应快照
### TC-S003: GET /api/store/content/footer 响应快照
### TC-S004: GET /api/store/content/announcements 响应快照

快照按 locale 分别保存（en/es/fr 三份）。

---

## L7 异步集成测试

### TC-AS001: 缓存失效广播
- **A**: 启动两个应用节点（模拟集群），都缓存了 home:en
- **A**: 节点1 调用 PUT /api/admin/site-builder/home-sections/{id}
- **A**: 节点1 的 L1+L2 失效，publisher.publish 广播；节点2 收到事件后 L1 失效；节点2 下次读触发 DB 重建

### TC-AS002: publisher.publish 失败降级
- **A**: mock publisher.publish 抛异常
- **A**: 调用 PUT home-sections
- **A**: 事务仍提交，ERROR 日志记录，其他节点 L1 可能脏（最终一致）

### TC-AS003: 跨域缓存失效联动
- **A**: 启动两个节点，都缓存了 home:en
- **A**: 调用 marketing 域 PUT /api/admin/banners/{id}（修改 HERO Banner）
- **A**: publisher.publish TYPE_HOME_SECTION_CHANGED；site_builder 节点订阅后失效 home family

---

## L8 韧性测试

### TC-R001: Banner 跨域调用超时降级
- **A**: mock BannerSvc.findByPosition 延迟 5s（超时 3s）
- **A**: GET /api/store/content/home
- **A**: Hero 区块被省略，其他 section 正常，WARN 日志记录

### TC-R002: 缓存击穿 singleflight
- **A**: 让 cache miss，并发 100 个 GET /api/store/content/home 请求
- **A**: 验证 DB 只被查询 1 次（singleflight 合并）

### TC-R003: 缓存雪崩 TTL 抖动
- **A**: 批量预热 1000 个 cache key
- **A**: 验证 TTL 在 30min ± 60s 范围内随机分布

### TC-R004: 跨域服务熔断
- **A**: mock BannerSvc 连续失败 5 次
- **A**: 第 6 次调用直接走熔断逻辑（不实际调用 BannerSvc），省略 Hero 区块

---

## acceptance 场景覆盖映射

| acceptance 场景 | 测试层 | 测试用例 ID |
|----------------|--------|------------|
| s-001~s-006（状态机） | L1 单元 + L2 集成 | TC-U001~U060 + TC-I001~I015 |
| bs-001~bs-280（边界场景） | L1 单元 | TC-U001~U060（按 cat 分组） |
| bs-281~bs-304（消费端边界） | L3 模块间 + L8 韧性 | TC-M001~M010 + TC-R001~R004 |
| FLOW-001~FLOW-010（功能） | L5 API + L3 模块间 | TC-A001~A050 + TC-M001~M010 |

**覆盖率目标**：
- 338 个 acceptance 场景 100% 覆盖（关键路径 + 边界 + 状态机）
- 20 个端点 100% 覆盖（happy + error path）
- 24 个 EDGE 场景 100% 覆盖（韧性测试层）

## 测试工具链

| 工具 | 用途 |
|------|------|
| JUnit 5 + Mockito | L1 单元测试 |
| Spring Boot Test + Testcontainers MySQL | L2/L3 集成测试 |
| Spring Cloud Contract | L4 契约测试 |
| MockMvc | L5 API 测试 |
| JsonSnapshot | L6 快照测试 |
| Awaitility + Testcontainers Redis | L7 异步集成测试 |
| Chaos Toolkit | L8 韧性测试（故障注入） |
