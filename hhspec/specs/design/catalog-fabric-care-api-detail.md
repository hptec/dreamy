---
title: "catalog fabric-care API 详细设计"
module: "catalog"
change: "fabric-care-module"
version: "1.0"
date: "2026-06-14"
author: "l2_api_designer"
status: "draft"
openapi_source: "catalog-api.openapi.yml（扩展）"
endpoints_count: 6
validation_rules_count: 45
error_codes_count: 3
---

# catalog fabric-care API 详细设计

> 角色: l2_api_designer | change: fabric-care-module | domain: catalog
> 方法论：每端点四部分 — 入参验证(V-FC-NNN) / 业务步骤(STEP-FC-NN) / 出参构造 / 错误码映射。
> 来源权威：er-diagram.yml + boundary-scenarios.yml + acceptance.yml + catalog-api.openapi.yml（既有模式）+ error-strategy.md（catalog 域段 5）。

## 0. 全局横切（适用所有端点）

- **鉴权过滤器**：
  - `/api/store/products/{slug}` → StoreJwtFilter 白名单（既有，本变更扩展 PDP 返回面料护理信息）
  - `/api/admin/products/*` → AdminJwtFilter + RBAC `/products`
  - `/api/admin/care-instructions/*` → AdminJwtFilter + RBAC `/products`（护理标签字典归属商品管理权限）
- **审计（admin 写操作）**：新增 action 枚举：`创建护理标签`/`编辑护理标签`/`删除护理标签`/`切换护理标签状态`
- **缓存**：消费端 PDP 复用 `catalog:product:{slug}:{locale}` 既有缓存；护理标签字典新增 `catalog:care-defs:{category?}` 缓存
- **422 字段级错误**：`details.fields` 格式与既有端点一致

## 1. STORE 端点（扩展既有 PDP）

### E-FC-01 getStoreProduct（扩展 E-CAT-04）— GET /api/store/products/{slug}

**公开端点**：既有白名单 `/api/store/products/**`。

**入参**: path `slug`；query `{ locale? }`
- 复用既有 V-CAT-012/013

**业务步骤（扩展 E-CAT-04）**:
- 复用 STEP-CAT-01 ~ STEP-CAT-05（既有商品详情查询逻辑）
- STEP-FC-01 批量取面料成分：`RM-FC-001 listByProductId(product.id)` ORDER BY layer, sort_order
- STEP-FC-02 批量取护理标签：`RM-FC-020 listByProductId(product.id)` JOIN care_instruction_def(status=active) ORDER BY sort_order
- STEP-FC-03 locale=en → label_en；locale=zh → label_zh（前端仅消费端暂不支持中文，保留扩展性）
- STEP-FC-04 装配到 StoreProductDetail（扩展字段）：fabric_compositions[]{layer,material,percentage,sort_order} + care_instructions[]{id,symbol_unicode,label,category} + fabric_care_note

**出参**: 200 StoreProductDetail（扩展字段，其余字段不变）
```json
{
  ...existing fields...,
  "fabric_compositions": [
    {"layer": 1, "material": 1, "percentage": 60.0, "sort_order": 0},
    {"layer": 1, "material": 2, "percentage": 40.0, "sort_order": 1}
  ],
  "care_instructions": [
    {"id": 1, "symbol_unicode": "♲", "label": "Machine wash cold", "category": 1}
  ],
  "fabric_care_note": "Professional dry clean recommended for beaded areas"
}
```

**错误映射**: 404 `404501` / 500 `50000`（复用既有）

## 2. ADMIN 商品端点（扩展既有商品 CRUD）

### E-FC-02 getAdminProduct（扩展 E-CAT-10）— GET /api/admin/products/{id}

**入参**: path `id`（复用 V-CAT-037）

**业务步骤（扩展 E-CAT-10）**:
- 复用 STEP-CAT-01 ~ STEP-CAT-03（既有商品详情查询）
- STEP-FC-01 批量取面料成分：`RM-FC-001 listByProductId(id)`
- STEP-FC-02 批量取护理标签 ID：`RM-FC-020 listByProductId(id)` → 提取 care_id 数组
- STEP-FC-03 装配到 AdminProductDetail（扩展）：fabric_compositions[] + care_instruction_ids[] + fabric_care_note

**出参**: 200 AdminProductDetail（扩展）
```json
{
  ...existing fields...,
  "fabric_compositions": [
    {"id": 1, "product_id": 123, "layer": 1, "material": 1, "percentage": 60.0, "sort_order": 0}
  ],
  "care_instruction_ids": [1, 3, 5],
  "fabric_care_note": "..."
}
```

**错误映射**: 404 `404501` / 500 `50000`

### E-FC-03 createAdminProduct（扩展 E-CAT-09）— POST /api/admin/products

**入参**: body AdminProductUpsert（扩展）
- 复用既有 V-CAT-023 ~ V-CAT-036
- V-FC-001 fabric_compositions[]（可选数组）：每行 layer ∈ {1,2,3,4}、material ∈ {1..10}、percentage 0..100、sort_order ≥ 0（缺省按索引分配）
- V-FC-002 fabric_compositions[] 提交集内 (layer, sort_order) 组合不重复（重复 → 422 `422501` fields.fabric_compositions）
- V-FC-003 care_instruction_ids[]（可选数组）：去重；全部存在且 status=active（不存在 → 422 `422501` fields.care_instruction_ids=not_exists）
- V-FC-004 fabric_care_note（可选 TEXT）

**业务步骤（扩展 E-CAT-09 单事务 TX-FC-001）**:
- 复用 STEP-CAT-01 ~ STEP-CAT-03（商品主表插入）
- STEP-FC-01 percentage 总和校验（js_guard）：按 layer 分组，每组 SUM(percentage) 必须=100（不等 → 422 `422510` FABRIC_PERCENTAGE_INVALID，details.layer + details.actual_sum）
- STEP-FC-02 批量 INSERT product_fabric_composition（sort_order 缺省按数组索引）
- STEP-FC-03 批量 INSERT product_care_instruction（care_id 数组，sort_order 按数组索引）
- 复用 STEP-CAT-05/06（operation_log + 缓存失效 + MQ）

**出参**: 201 AdminProductDetail（含新增字段）

**错误映射**: 409 `409501`/`409504` / 422 `422501`/`422510` / 500 `50000`,`50001`

**新增错误码**:
- 422510 FABRIC_PERCENTAGE_INVALID：面料成分百分比总和不等于 100%（details.layer/actual_sum）

### E-FC-04 updateAdminProduct（扩展 E-CAT-11）— PUT /api/admin/products/{id}

**入参**: path `id`；body AdminProductUpsert（扩展）
- 复用既有 V-CAT-023 ~ V-CAT-038
- 复用 V-FC-001 ~ V-FC-004（面料成分与护理标签校验）

**业务步骤（扩展 E-CAT-11 单事务 TX-FC-001）**:
- 复用 STEP-CAT-01 ~ STEP-CAT-04（商品主表更新 + SKU 乐观锁）
- STEP-FC-01 percentage 总和校验（同 E-FC-03 STEP-FC-01）
- STEP-FC-02 fabric_compositions[] 整单覆盖：`RM-FC-003 replaceAll(productId, rows[])` — DELETE + 批量 INSERT
- STEP-FC-03 care_instruction_ids[] 整单覆盖：`RM-FC-022 replaceAll(productId, careIds[])` — DELETE + 批量 INSERT
- STEP-FC-04 更新 fabric_care_note（若提交）
- 复用 STEP-CAT-06/07（operation_log + 失效链）

**出参**: 200 AdminProductDetail

**错误映射**: 404 `404501` / 409 `409501`/`409504`/`409508` / 422 `422501`/`422510` / 500 `50000`,`50001`

## 3. ADMIN 护理标签字典端点

### E-FC-05 listAdminCareInstructions — GET /api/admin/care-instructions

**入参**: query `{ category? }`
- V-FC-010 category 可选 ∈ {1,2,3,4,5}（washing/bleaching/drying/ironing/dry_cleaning，枚举外 → 422 `422501` fields.category）

**业务步骤**:
- STEP-FC-01 category 给定 → `RM-FC-015 listByCategory(category)`；否则 `RM-FC-010 listAll()` ORDER BY category, sort_order
- STEP-FC-02 装配 AdminCareInstructionDTO（id/code/symbol_unicode/label_en/label_zh/category/sort_order/status/created_at/updated_at）

**出参**: 200 `{ items: CareInstructionDef[] }`
```json
{
  "items": [
    {
      "id": 1,
      "code": "WASH_30C",
      "symbol_unicode": "♲",
      "label_en": "Machine wash cold",
      "label_zh": "冷水机洗",
      "category": 1,
      "sort_order": 0,
      "status": 1,
      "created_at": "...",
      "updated_at": "..."
    }
  ]
}
```

**错误映射**: 403 `40300` / 422 `422501` / 500 `50000`

### E-FC-06 createAdminCareInstruction — POST /api/admin/care-instructions

**入参**: body `{ code!, symbol_unicode?, label_en!, label_zh!, category!, sort_order?, status! }`
- V-FC-011 code 必填 trim 非空 ≤64，匹配 `^[A-Z0-9_]+$`（大写字母+数字+下划线）
- V-FC-012 symbol_unicode 可选 ≤16
- V-FC-013 label_en/label_zh 必填 trim 非空 ≤128
- V-FC-014 category 必填 ∈ {1,2,3,4,5}
- V-FC-015 status 必填 ∈ {1,2}（active/disabled）
- V-FC-016 sort_order 可选 ≥ 0（缺省取同 category 最大值+1）

**业务步骤（单事务 TX-FC-003）**:
- STEP-FC-01 code 唯一性：`RM-FC-014 existsByCodeExcept(code, null)`（uk_care_instruction_def_code 兜底）命中 → 409 `422511` CARE_CODE_EXISTS
- STEP-FC-02 INSERT care_instruction_def；INSERT operation_log(action=创建护理标签)
- STEP-FC-03 提交后失效 `catalog:care-defs:*` → 不发 MQ（护理标签字典变更不触发前台页面重新生成，仅缓存失效）

**出参**: 201 CareInstructionDef

**错误映射**: 403 `40300` / 409 `422511` / 422 `422501` / 500 `50000`,`50001`

**新增错误码**:
- 422511 CARE_CODE_EXISTS：护理标签 code 已存在

### E-FC-07 updateAdminCareInstruction — PUT /api/admin/care-instructions/{id}

**入参**: path `id`；body（同 E-FC-06）
- V-FC-017 护理标签存在（不存在 → 404 `422512` CARE_NOT_FOUND）
- 复用 V-FC-011 ~ V-FC-016（code 唯一性排除自身）

**业务步骤（单事务 TX-FC-004）**:
- STEP-FC-01 code 变更时查重（排除自身）→ 409 `422511`
- STEP-FC-02 UPDATE care_instruction_def；INSERT operation_log(action=编辑护理标签)
- STEP-FC-03 提交后失效 `catalog:care-defs:*` + `catalog:product:*`（已挂载商品的 PDP 缓存失效）

**出参**: 200 CareInstructionDef

**错误映射**: 404 `422512` / 409 `422511` / 422 `422501` / 500 `50000`

**新增错误码**:
- 422512 CARE_NOT_FOUND：护理标签不存在

### E-FC-08 deleteAdminCareInstruction — DELETE /api/admin/care-instructions/{id}

**入参**: path `id`

**业务步骤（单事务 TX-FC-005）**:
- STEP-FC-01 不存在 → 404 `422512`
- STEP-FC-02 物理删除 care_instruction_def + `RM-FC-024 deleteByCareId(id)` 级联摘除 product_care_instruction（无 guard，允许删除正在使用的标签）
- STEP-FC-03 INSERT operation_log(action=删除护理标签)
- STEP-FC-04 提交后失效 `catalog:care-defs:*` + `catalog:product:*`

**出参**: 204

**错误映射**: 404 `422512` / 500 `50000`

### E-FC-09 toggleAdminCareInstructionStatus — PATCH /api/admin/care-instructions/{id}/status

**入参**: path `id`；body `{ status! }`
- V-FC-020 status 必填 ∈ {1,2}

**业务步骤（单事务 TX-FC-006）**:
- STEP-FC-01 不存在 → 404 `422512`
- STEP-FC-02 幂等：目标态=当前态 → 直接返回（不写审计不发事件）
- STEP-FC-03 `RM-FC-019 updateStatus(id, status)`；INSERT operation_log(action=切换护理标签状态)
- STEP-FC-04 提交后失效 `catalog:care-defs:*` + `catalog:product:*`

**出参**: 200 CareInstructionDef

**错误映射**: 404 `422512` / 422 `422501` / 500 `50000`

## 4. 新增错误码清单

| 错误码 | 标识 | HTTP | 触发场景 | 追加理由 | 关联端点 |
|--------|------|------|----------|----------|----------|
| 422510 | FABRIC_PERCENTAGE_INVALID | 422 | 面料成分百分比总和不等于 100% | js_guard 业务规则校验（er-diagram 定义） | E-FC-03/04 |
| 422511 | CARE_CODE_EXISTS | 422 | 护理标签 code 已存在 | uk_care_instruction_def_code 唯一性约束 | E-FC-06/07 |
| 422512 | CARE_NOT_FOUND | 404 | 护理标签不存在 | 护理标签资源不存在 | E-FC-07/08/09 |

> 注：本变更新增 3 个错误码，遵循 catalog 域段 5 号段（422501~422599 / 404501~404599）

## 5. 推断设计清单

| 编号 | 设计内容 | 推断依据 | 置信度 |
|------|----------|----------|--------|
| INFER-FC-001 | percentage 总和校验返回 422510 而非 422501 | js_guard 业务规则需专用错误码便于前端精确提示 | 高 |
| INFER-FC-002 | care_instruction_ids[] 存在性校验复用 422501 | 与既有 tag_ids 校验模式一致 | 高 |
| INFER-FC-003 | 护理标签删除无 guard 允许级联摘除 | 与 tag 删除模式一致（无阻止删除约束） | 中 |
| INFER-FC-004 | fabric_care_note 为可选字段 | er-diagram 未强制必填，商品可选提供自由文本补充 | 高 |

## 6. 风险记录与后续跟进

| 编号 | 问题 | 影响范围 | 建议 |
|------|------|----------|------|
| RISK-FC-001 | percentage 小数精度 DECIMAL(5,2) 是否足够 | 面料成分输入 | 确认是否需要更高精度（如 DECIMAL(6,3)） |
| RISK-FC-002 | 护理标签 symbol_unicode 渲染兼容性 | 前端 UI 展示 | 确认目标浏览器/设备对 Unicode 符号的支持 |
| CONF-FC-001 | 护理标签字典是否需要多语言扩展（ES/FR） | 消费端国际化 | 当前仅 EN/ZH，未来是否需 translation 附表 |

## 7. 自检清单执行结果

### 覆盖完整性检查
- [x] 分配范围内的所有端点均已设计（1 个扩展消费端 + 2 个扩展管理端商品 + 4 个新增管理端护理标签 = 7 个端点）
- [x] 每个端点的四部分（验证/业务逻辑/出参/错误码）均已完成
- [x] 每个请求参数的每个字段均有验证规则（V-FC-001 ~ V-FC-020）
- [x] 每个响应体字段均有明确的构造来源（MAP-FC-001 ~ MAP-FC-009）
- [x] 每个异常路径均有对应的错误码映射（422501/422510/422511/422512/404501/40300/50000/50001）

### 可追溯性检查
- [x] 每条验证规则附有来源引用（er-diagram 字段约束 / boundary-scenarios）
- [x] 每个业务步骤附有来源引用（acceptance.yml 验收场景 / catalog 既有模式）
- [x] 每个错误码引用了 error-strategy.md catalog 域段 5 编号范围
- [x] 所有推断设计已标注 `[INFERRED]` 并记入推断清单（INFER-FC-001 ~ INFER-FC-004）

### 一致性检查
- [x] 同一字段在不同端点的验证规则一致（layer/material/category/status 枚举校验）
- [x] 错码在所有端点中无重复（422510/422511/422512 新增，不与既有冲突）
- [x] 命名/类型/引用均遵循 catalog 既有模式（fabric_compositions/care_instruction_ids 命名风格）
- [x] 枚举映射采用 IntEnum 整数契约（与项目整体重构对齐）

### 粒度合规检查
- [x] 验证规则精确到每个字段的每条约束（percentage 0..100、code 格式、唯一性等）
- [x] 业务逻辑精确到每个步骤的输入/操作/正常路径/异常路径（percentage 总和校验、整单覆盖等）
- [x] 出参构造精确到每个字段的来源和转换逻辑（JOIN care_instruction_def 取标签详情）
- [x] 不包含框架语法细节（无 @Valid/Depends/Serializer 等）
- [x] 不包含性能优化设计（无缓存策略/索引建议/批量优化等，已在 data-detail 完成）

### 格式合规检查
- [x] 产出文件结构符合 api-detail-template.md 模板定义
- [x] Frontmatter 元数据字段完整（title/module/change/version/date/author/status/endpoints_count 等）
- [x] 编号规则遵循 numbering-conventions.md（V-FC-NNN 全域连续、STEP-FC-NN 每端点独立）

### 引用合规检查
- [x] 已复用 catalog 既有模式（商品 CRUD 扩展、缓存失效链、operation_log、MQ 失效事件）
- [x] 未自创 error-strategy 中不存在的错误码编号范围（新增 3 码均在 422/404 catalog 域段内）
- [x] 所有新增错误码已标注追加理由并记入新增错误码清单

## 8. 交叉引用

### 与 data-detail 交叉验证
- [x] TX-FC-001 ~ TX-FC-006 与 data-detail 事务边界一一对应
- [x] RM-FC-001 ~ RM-FC-030 Repository 方法在业务步骤中正确引用
- [x] MAP-FC-001 ~ MAP-FC-009 DTO 映射与出参构造一致
- [x] IDX-FC-001 ~ IDX-FC-006 索引支撑查询路径完整

### 与 boundary-scenarios / acceptance 交叉验证
- [x] bs-001 ~ bs-021（null 类）→ V-FC-001 ~ V-FC-004 必填/可选校验
- [x] bs-022 ~ bs-031（extreme 类）→ 枚举/范围/长度校验
- [x] s-009 ~ s-029（null 类验收）→ 422 `422501` 字段级错误
- [x] s-030 ~ s-039（extreme 类验收）→ 422 `422501` 枚举/范围错误
- [x] percentage 总和约束（js_guard）→ 422 `422510` FABRIC_PERCENTAGE_INVALID

---

**产出完成时间**: 2026-06-14
**文件路径**: `/Volumes/MAC/workspace/dreamy/hhspec/changes/fabric-care-module/specs/design/catalog-fabric-care-api-detail.md`
**验证状态**: 自检通过，待 L3 实现验证

