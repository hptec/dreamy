# L2 后端 API 详设 - site_builder 域

> 来源：site-builder-api.openapi.yml（L1.2 产出，权威）+ data-flow.md + error-strategy.md
> 覆盖：20 个端点（15 admin + 5 store）
> 决策依据：KD-1~KD-17

## acceptance.yml 路径映射（L1 challenger SHOULD_FIX-1）

| acceptance.yml 旧路径 | L1.2 契约新路径 | 说明 |
|----------------------|----------------|------|
| `PUT /api/admin/home-sections`（批量） | `PUT /api/admin/site-builder/home-sections/{id}`（单条）+ `PUT /api/admin/site-builder/home-sections/sort`（批量排序） | KD-15 新建 site_builder 限界上下文，路径加 `/site-builder/` 子前缀；批量保存拆为单条更新 + 批量排序 |
| `PUT /api/admin/navigation` | `PUT /api/admin/site-builder/navigation` | 整体保存语义不变 |
| `PUT /api/admin/announcements` | `PUT /api/admin/site-builder/announcements/{id}` + `POST` + `DELETE` + `PATCH/{id}/toggle` | 公告 CRUD 拆分 |
| `GET /api/store/content/home/sections` | `GET /api/store/content/home` | 聚合响应，不分 sections 子路径 |
| `GET /api/store/content/navigation`（合并 main+footer+announcements） | `GET /api/store/content/navigation` + `GET /api/store/content/footer` + `GET /api/store/content/announcements` | 拆为 3 个独立端点，消费端并发请求 |

L3 implementer 和 test_engineer 在 traceability map 中按新路径建立约束映射。

---

## 端点详设

### 1. POST /api/admin/site-builder/home-sections — 创建首页区块

**operationId**: createAdminHomeSection
**鉴权**: AdminBearerAuth + @RequirePermission("/site/home")

#### V-NNN 入参验证

| ID | 字段 | 规则 | 错误码 |
|----|------|------|--------|
| V-001 | section_type | 必填，枚举 [hero, theme_cards, product_rail, editorial_feature, newsletter, custom] | 422802 |
| V-002 | enabled | 必填，boolean | 422808 |
| V-003 | sort_order | 必填，>= 0 | 422808 |
| V-004 | data_json | 可选，JSON 对象，按 section_type 校验 schema（见 js_guard） | 422801 |
| V-005 | i18n_json | 可选，结构 `{en:{}, es:{}, fr:{}}`，locale 键合法 | 422806/422807 |

**js_guard**（section_type 与 data_json/i18n_json 组合校验，422808）：
- section_type=hero → data_json 应为空且 i18n_json 为 null（文案从 Banner position=HERO 派生，KD-2/KD-14）
- section_type=theme_cards → data_json 应含 `count` ∈ [1, 8]
- section_type=product_rail → data_json 应含 `source` ∈ [recommend, new_arrival] + `limit` ∈ [1, 12]；source=recommend 时 `product_ids` 非空数组（KD-9）
- section_type=editorial_feature → data_json 应含 `limit` ∈ [1, 6]，可选 `wedding_ids`
- section_type=newsletter → data_json 应为空且 i18n_json 非空（title/subtitle/cta_text，KD-11/KD-16）
- section_type=custom → data_json 可自定义，i18n_json 应非空

#### STEP-NN 业务逻辑

| ID | 步骤 | 异常 |
|----|------|------|
| STEP-01 | 校验入参（V-001~V-005 + js_guard） | 422801/802/806/807/808 |
| STEP-02 | 事务开启（TX-001） | — |
| STEP-03 | 构造 HomePageSection Entity（id=null, created_at=now, updated_at=now, version=0） | — |
| STEP-04 | INSERT home_sections | 50001（DB 异常） |
| STEP-05 | cache.invalidateFamily(Family.HOME_SECTION) | 500802（不阻断） |
| STEP-06 | publisher.publish(TYPE_HOME_SECTION_CHANGED) | 500802（不阻断） |
| STEP-07 | 写 OperationLog（action=create_home_section, entity_id, entity_type=home_section） | — |
| STEP-08 | 事务提交 | — |
| STEP-09 | 返回 R.ok(sectionDto) | — |

#### 出参构造（MAP-001）

HomePageSectionDto：
```json
{
  "id": 123,
  "section_type": "product_rail",
  "enabled": true,
  "sort_order": 1,
  "data_json": {"source": "recommend", "product_ids": [1,2,3], "limit": 6},
  "i18n_json": null,
  "version": 0,
  "created_at": "2026-06-23T15:30:00Z",
  "updated_at": "2026-06-23T15:30:00Z"
}
```

#### 错误码映射

| 错误码 | HTTP | 触发条件 |
|--------|------|----------|
| 422801 | 422 | data_json 不符合 section_type schema |
| 422802 | 422 | section_type 不在枚举 |
| 422806 | 422 | mega_menu_json 配置非法（不适用于 home-sections） |
| 422807 | 422 | i18n_json locale 键非法 |
| 422808 | 422 | section_type 与 data_json/i18n_json 组合不满足 js_guard |
| 500801 | 500 | 数据库异常 |
| 500802 | 500 | 缓存失效失败（降级记录） |

---

### 2. GET /api/admin/site-builder/home-sections — 区块列表

**operationId**: listAdminHomeSections
**鉴权**: AdminBearerAuth + @RequirePermission("/site/home")

#### V-NNN

| ID | 字段 | 规则 | 错误码 |
|----|------|------|--------|
| V-006 | enabled_only | 可选，boolean，默认 false | — |

#### STEP-NN

| ID | 步骤 |
|----|------|
| STEP-01 | 查 DB：`SELECT * FROM home_sections ORDER BY sort_order, id`（enabled_only=true 时加 WHERE enabled=true） |
| STEP-02 | 转 DTO 列表（MAP-001） |
| STEP-03 | 返回 R.ok(list) |

#### 出参

`[HomePageSectionDto, ...]`（不缓存，admin 端实时读 DB）

#### 错误码

| 错误码 | HTTP | 触发 |
|--------|------|------|
| 500801 | 500 | DB 异常 |

---

### 3. GET /api/admin/site-builder/home-sections/{id} — 区块详情

**鉴权**: AdminBearerAuth + @RequirePermission("/site/home")

#### STEP-NN

| ID | 步骤 | 异常 |
|----|------|------|
| STEP-01 | SELECT BY id | — |
| STEP-02 | 不存在 → 404801 | 404801 |
| STEP-03 | 转 DTO（MAP-001） | — |
| STEP-04 | 返回 R.ok(dto) | — |

---

### 4. PUT /api/admin/site-builder/home-sections/{id} — 更新区块

**鉴权**: AdminBearerAuth + @RequirePermission("/site/home")

#### V-NNN

同 V-001~V-005 + js_guard，但所有字段可选（部分更新）。

#### STEP-NN

| ID | 步骤 | 异常 |
|----|------|------|
| STEP-01 | 校验入参 + js_guard | 422xxx |
| STEP-02 | 事务（TX-001） | — |
| STEP-03 | SELECT BY id 检查存在 + 读取 version | 404801 |
| STEP-04 | 乐观锁校验：version 匹配 | 409801 |
| STEP-05 | UPDATE home_sections SET ... WHERE id=? AND version=? | — |
| STEP-06 | version+1 | — |
| STEP-07 | cache.invalidateFamily + publisher.publish | 500802（不阻断） |
| STEP-08 | OperationLog（action=update_home_section） | — |
| STEP-09 | 事务提交 | — |
| STEP-10 | 返回 R.ok(updatedDto) | — |

---

### 5. DELETE /api/admin/site-builder/home-sections/{id} — 删除区块

**鉴权**: AdminBearerAuth + @RequirePermission("/site/home")

#### STEP-NN

| ID | 步骤 | 异常 |
|----|------|------|
| STEP-01 | 事务（TX-001） | — |
| STEP-02 | SELECT BY id | 404801 |
| STEP-03 | DELETE home_sections WHERE id=? | — |
| STEP-04 | cache.invalidateFamily + publisher.publish | — |
| STEP-05 | OperationLog（action=delete_home_section） | — |
| STEP-06 | 事务提交 | — |
| STEP-07 | 返回 R.ok(null) | — |

---

### 6. PUT /api/admin/site-builder/home-sections/sort — 批量调整排序

**鉴权**: AdminBearerAuth + @RequirePermission("/site/home")

#### V-NNN

| ID | 字段 | 规则 | 错误码 |
|----|------|------|--------|
| V-007 | items | 必填，数组，每项含 id + sort_order | 422808 |
| V-008 | items[].id | 必填，存在 | 404801 |
| V-009 | items[].sort_order | >= 0 | 422808 |

#### STEP-NN

| ID | 步骤 |
|----|------|
| STEP-01 | 校验 items 非空 + 所有 id 存在 |
| STEP-02 | 事务（TX-001） |
| STEP-03 | 批量 UPDATE：`UPDATE home_sections SET sort_order=?, version=version+1 WHERE id=?` |
| STEP-04 | cache.invalidateFamily + publisher.publish |
| STEP-05 | OperationLog（action=sort_home_sections） |
| STEP-06 | 事务提交 |
| STEP-07 | 返回 R.ok(null) |

---

### 7. PATCH /api/admin/site-builder/home-sections/{id}/toggle — 启停切换

**鉴权**: AdminBearerAuth + @RequirePermission("/site/home")

#### V-NNN

| ID | 字段 | 规则 |
|----|------|------|
| V-010 | enabled | 必填，boolean |

#### STEP-NN

| ID | 步骤 |
|----|------|
| STEP-01 | 事务（TX-001） |
| STEP-02 | SELECT BY id + 乐观锁 version | 404801/409801 |
| STEP-03 | UPDATE enabled + version+1 |
| STEP-04 | cache.invalidateFamily + publisher.publish |
| STEP-05 | OperationLog（action=toggle_home_section） |
| STEP-06 | 事务提交 |
| STEP-07 | 返回 R.ok(updatedDto) |

---

### 8. GET /api/admin/site-builder/navigation — 导航配置

**鉴权**: AdminBearerAuth + @RequirePermission("/site/navigation")

#### STEP-NN

| ID | 步骤 |
|----|------|
| STEP-01 | SELECT * FROM navigation_items ORDER BY sort_order, id |
| STEP-02 | 组装树结构（parent_id 关联） |
| STEP-03 | 转 NavigationItemDto（MAP-002，含 mega_menu_json） |
| STEP-04 | 返回 R.ok({items: [...]}) |

---

### 9. PUT /api/admin/site-builder/navigation — 保存导航配置（整体替换）

**鉴权**: AdminBearerAuth + @RequirePermission("/site/navigation")

#### V-NNN

| ID | 字段 | 规则 | 错误码 |
|----|------|------|--------|
| V-011 | items | 必填，数组 | 422808 |
| V-012 | items[].parent_id | 可选，存在或 null | 404802 |
| V-013 | items[].label | 必填（i18n_json.en.label 非空） | 422807 |
| V-014 | items[].url | 可选，HTTP(S) 格式 | 422804（footer 用） |
| V-015 | items[].target | 枚举 [self, blank] | 422808 |
| V-016 | items[].mega_menu_json | 可选，结构 `{columns:[{title, links:[{label,url,target}]}]}` | 422806 |
| V-017 | items[].taxonomy_id | 可选，存在 | 404805 |

#### STEP-NN

| ID | 步骤 | 异常 |
|----|------|------|
| STEP-01 | 校验 items + 循环依赖检测（CV-001，DFS） | 409802 |
| STEP-02 | 跨域校验 taxonomy_id 存在性（调用 TaxonomyService.findById） | 404805 |
| STEP-03 | 事务（TX-002） | — |
| STEP-04 | SELECT 现有所有 navigation_items 的 id 列表 + version | — |
| STEP-05 | 计算差集：upserted_ids - existing_ids = 新增；existing_ids - upserted_ids = 删除 | — |
| STEP-06 | DELETE 已移除的 navigation_items | — |
| STEP-07 | UPSERT 保留的和新增的（含 mega_menu_json） | — |
| STEP-08 | version+1（整体 version，存 site_builder_config 单例） | 409805 |
| STEP-09 | cache.invalidateFamily(Family.NAVIGATION) + publisher.publish(TYPE_NAVIGATION_CHANGED) | 500802 |
| STEP-10 | OperationLog（action=save_navigation） | — |
| STEP-11 | 事务提交 | — |
| STEP-12 | 返回 R.ok({items: [...]}) | — |

---

### 10. GET /api/admin/site-builder/footer — 页脚配置

**鉴权**: AdminBearerAuth + @RequirePermission("/site/navigation")

#### STEP-NN

| ID | 步骤 |
|----|------|
| STEP-01 | SELECT * FROM footer_columns + footer_links |
| STEP-02 | 组装 columns + links 结构 |
| STEP-03 | 转 FooterColumnDto（MAP-003，含 links） |
| STEP-04 | 返回 R.ok({columns: [...]}) |

---

### 11. PUT /api/admin/site-builder/footer — 保存页脚配置（整体替换）

**鉴权**: AdminBearerAuth + @RequirePermission("/site/navigation")

#### V-NNN

| ID | 字段 | 规则 | 错误码 |
|----|------|------|--------|
| V-018 | columns | 必填，数组 | 422808 |
| V-019 | columns[].id | 可选（新增时 null） | — |
| V-020 | columns[].sort_order | >= 0 | 422808 |
| V-021 | columns[].links[].column_id | 必须在 columns 中存在 | 422803 |
| V-022 | columns[].links[].url | HTTP(S) 格式 | 422804 |

#### STEP-NN

| ID | 步骤 | 异常 |
|----|------|------|
| STEP-01 | 校验 columns + links 结构 + column_id 引用完整性 | 422803 |
| STEP-02 | 事务（TX-003） | — |
| STEP-03 | DELETE footer_columns + footer_links 全量 | — |
| STEP-04 | INSERT columns + links（含 i18n_json） | — |
| STEP-05 | cache.invalidateFamily(Family.FOOTER) + publisher.publish(TYPE_FOOTER_CHANGED) | 500802 |
| STEP-06 | OperationLog | — |
| STEP-07 | 事务提交 | — |
| STEP-08 | 返回 R.ok({columns: [...]}) | — |

---

### 12. GET /api/admin/site-builder/announcements — 公告列表

**鉴权**: AdminBearerAuth + @RequirePermission("/site/announcement")

#### V-NNN

| ID | 字段 | 规则 |
|----|------|------|
| V-023 | enabled_only | 可选，boolean |
| V-024 | page | 可选，默认 1 |
| V-025 | page_size | 可选，默认 20，max 100 |

#### STEP-NN

| ID | 步骤 |
|----|------|
| STEP-01 | SELECT FROM announcements ORDER BY priority DESC, id（分页） |
| STEP-02 | 转 AnnouncementDto（MAP-004） |
| STEP-03 | 返回 R.ok(Paginated) |

---

### 13. POST /api/admin/site-builder/announcements — 创建公告

**鉴权**: AdminBearerAuth + @RequirePermission("/site/announcement")

#### V-NNN

| ID | 字段 | 规则 | 错误码 |
|----|------|------|--------|
| V-026 | enabled | 必填 | 422808 |
| V-027 | priority | 必填，>= 0 | 422808 |
| V-028 | start_at | 可选，ISO 8601 | — |
| V-029 | end_at | 可选，ISO 8601，> start_at | 422805 |
| V-030 | content_i18n_json | 必填，结构 `{en:{content}, es:{}, fr:{}}` | 422807 |
| V-031 | i18n_json | 可选 | — |

#### STEP-NN

| ID | 步骤 | 异常 |
|----|------|------|
| STEP-01 | 校验入参 + 时间窗 | 422805/807/808 |
| STEP-02 | 唯一性校验：同 priority + 时间窗重叠 | 409804 |
| STEP-03 | 事务（TX-004） | — |
| STEP-04 | INSERT announcements | — |
| STEP-05 | cache.invalidateFamily(Family.ANNOUNCEMENT) + publisher.publish | — |
| STEP-06 | OperationLog | — |
| STEP-07 | 事务提交 | — |
| STEP-08 | 返回 R.ok(dto) | — |

---

### 14. PUT /api/admin/site-builder/announcements/{id} — 更新公告

同 POST，但需 SELECT BY id + 乐观锁 version。

---

### 15. DELETE /api/admin/site-builder/announcements/{id} — 删除公告

事务（TX-004）→ SELECT → DELETE → cache invalidate → OperationLog → 提交。

---

### 16. PATCH /api/admin/site-builder/announcements/{id}/toggle — 启停切换

同 home-sections toggle。

---

### 17. GET /api/store/content/home — 消费端首页聚合

**鉴权**: 匿名（security: []）

#### V-NNN

| ID | 字段 | 规则 |
|----|------|------|
| V-032 | locale | 可选，从 URL 路径或 Accept-Language 提取，默认 en，枚举 [en, es, fr] | 422807 |

#### STEP-NN

| ID | 步骤 | 异常 |
|----|------|------|
| STEP-01 | cache.get(`home:{locale}`) | — |
| STEP-02 | miss → SELECT home_sections WHERE enabled=true ORDER BY sort_order, id | — |
| STEP-03 | 按 section_type 派生数据（跨域调用）： | — |
| STEP-03a | hero → BannerService.findByPosition(HERO, locale)，失败降级空 Hero + WARN，502801 | 502801 |
| STEP-03b | theme_cards → TaxonomyService.findByType(theme, locale, limit)，失败降级空，502802 | 502802 |
| STEP-03c | product_rail → ProductService.findByIds 或 findNewArrivals，失败降级空，502804 | 502804 |
| STEP-03d | editorial_feature → WeddingService.fetchStoreWeddings(limit, locale)，失败降级空，502803 | 502803 |
| STEP-03e | newsletter / custom → 按 locale 扁平化 i18n_json | — |
| STEP-04 | 按 locale 扁平化 i18n_json（三层回退：locale→en→主表字段） | — |
| STEP-05 | cache.set(`home:{locale}`, home_data, ttl=30min) | — |
| STEP-06 | 返回 R.ok({sections: [...]}) | — |

#### 出参（StoreHomePageDto）

```json
{
  "sections": [
    {
      "section_type": "hero",
      "data": {"title": "...", "subtitle": "...", "cta_text": "...", "cta_link": "...", "cta_text_secondary": "...", "cta_link_secondary": "...", "image_url": "..."}
    },
    {
      "section_type": "theme_cards",
      "data": {"themes": [{"id": 1, "name": "...", "image_url": "..."}]}
    },
    {
      "section_type": "product_rail",
      "data": {"products": [{"id": 1, "name": "...", "price": 99.99, "image_url": "..."}]}
    },
    {
      "section_type": "editorial_feature",
      "data": {"title": "...", "weddings": [{"id": 1, "title": "...", "image_url": "..."}]}
    },
    {
      "section_type": "newsletter",
      "data": {"title": "...", "subtitle": "...", "cta_text": "..."}
    }
  ]
}
```

---

### 18. GET /api/store/content/navigation — 消费端导航

**鉴权**: 匿名

#### STEP-NN

| ID | 步骤 |
|----|------|
| STEP-01 | cache.get(`navigation:{locale}`) |
| STEP-02 | miss → SELECT navigation_items WHERE enabled=true ORDER BY sort_order, id |
| STEP-03 | 组装树结构 |
| STEP-04 | 按 locale 扁平化 i18n_json（label, mega_menu_json.columns[].title, links[].label） |
| STEP-05 | cache.set |
| STEP-06 | 返回 R.ok({items: [...]}) |

---

### 19. GET /api/store/content/footer — 消费端页脚

**鉴权**: 匿名

类似 navigation，读 footer_columns + footer_links，组装 + locale 扁平化。

---

### 20. GET /api/store/content/announcements — 消费端公告

**鉴权**: 匿名

#### STEP-NN

| ID | 步骤 |
|----|------|
| STEP-01 | cache.get(`announcement:{locale}`) |
| STEP-02 | miss → SELECT FROM announcements WHERE enabled=true AND (start_at IS NULL OR start_at<=NOW()) AND (end_at IS NULL OR end_at>=NOW()) ORDER BY priority DESC, id |
| STEP-03 | 按 locale 扁平化 content_i18n_json + i18n_json |
| STEP-04 | cache.set |
| STEP-05 | 返回 R.ok({announcements: [...]}) |

---

### 21. POST /api/store/newsletter — Newsletter 订阅（扩展基线 marketing-api）

**鉴权**: 匿名

#### V-NNN

| ID | 字段 | 规则 | 错误码 |
|----|------|------|--------|
| V-033 | email | 必填，email 格式 | 422704（marketing 域） |
| V-034 | source | 必填，枚举，本变更新增 HOME_BLOCK=4 | 422704 |
| V-035 | locale | 可选，默认 en | — |

#### STEP-NN

| ID | 步骤 | 异常 |
|----|------|------|
| STEP-01 | 校验 email + source + locale | 422704 |
| STEP-02 | 调用 SubscriberService.subscribe(email, source=HOME_BLOCK, locale) | — |
| STEP-03 | SubscriberService 内部：INSERT newsletter_subscriber（如不存在） | 409704（重复，幂等成功） |
| STEP-04 | 返回 R.ok(null) | — |

---

## 端点覆盖 acceptance FUNC 场景映射

| acceptance FUNC | L1.2 端点 | 说明 |
|----------------|----------|------|
| FLOW-001（HomeBuilder 保存） | POST/PUT/DELETE/PATCH /api/admin/site-builder/home-sections/* | 路径已映射 |
| FLOW-002（NavigationConfig 保存） | PUT /api/admin/site-builder/navigation | 路径已映射 |
| FLOW-003（Announcement 保存） | POST/PUT/DELETE/PATCH /api/admin/site-builder/announcements/* | 路径已映射 |
| FLOW-004（Banner CRUD） | 基线 marketing-api /api/admin/banners | 本变更不改造（KD-14 仅扩展字段） |
| FLOW-005（缓存失效） | in-process cache.invalidateFamily + publisher.publish | KD-1/GRD-W01 非 HTTP 自调 |
| FLOW-006（消费端首页） | GET /api/store/content/home | 路径已映射 |
| FLOW-007（消费端 chrome） | GET /api/store/content/navigation + /footer + /announcements | 拆为 3 个端点 |
| FLOW-008（Newsletter 订阅） | POST /api/store/newsletter | source=HOME_BLOCK(4) |
| FLOW-009（Hero 跨域 Banner） | GET /api/store/content/home 内部 BannerService.findByPosition | 非独立端点 |
| FLOW-010（Navigation 跨域 Taxonomy） | PUT /api/admin/site-builder/navigation 内部 TaxonomyService.findById | 非独立端点 |

---

## L1 SHOULD_FIX 消化清单

| SHOULD_FIX | 消化位置 | 说明 |
|-----------|---------|------|
| 路径映射 | 本文件"acceptance.yml 路径映射"章节 + 端点覆盖映射章节 | 已完整说明旧路径到新路径的映射 |
| 权限点种子数据 | data-detail.md DataInitializer 扩展章节 | 详见 data-detail.md |
| 跨域缓存失效联动 | data-detail.md 跨域缓存失效联动章节 | 详见 data-detail.md |
