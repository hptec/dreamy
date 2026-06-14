---
title: "catalog fabric-care 数据层详细设计"
module: "catalog"
change: "fabric-care-module"
version: "1.0"
date: "2026-06-14"
author: "l2_data_designer"
status: "draft"
---

# catalog fabric-care 数据层详细设计

> 角色: l2_data_designer | change: fabric-care-module | domain: catalog
> 方法论：Entity Design / Repository 方法(RM-FC) / DTO 映射(MAP-FC) / 索引(IDX-FC) / 事务边界(TX-FC) / 数据校验(CV-FC) / 缓存设计(CACHE-FC) / 完整 DDL。
> 来源权威：er-diagram.yml（3 新实体：ProductFabricComposition / CareInstructionDef / ProductCareInstruction）+ boundary-scenarios.yml（72 边界场景）+ acceptance.yml（84 验收场景）+ catalog 既有设计模式（catalog-data-detail.md）。

## 1. Entity Design（基类选型 / 逻辑删除 / 审计字段）

### 1.1 基类与通用约定

- **基类**：全部实体继承 `huihao.mysql.auditable.LongAuditableEntity`（与 catalog 既有实体一致）——提供 `id BIGINT AUTO_INCREMENT 主键` + `created_at` / `updated_at DATETIME(3)` 审计列。
- **注解范式**（CP-015）：`@Table(indexes=...)` + `@TableName(value, autoResultMap=true)` + `@Column(name=<EntityDBConst 常量>, definition=...)`；每实体配 `{Entity}DBConst extends CommonDBConst`（置于 `com.dreamy.catalog.domain.fabriccare/consts/`）。
- **逻辑删除**：不启用（与 catalog 既有模式一致）。CareInstructionDef 的 status 枚举（active/disabled）为业务状态而非逻辑删除标记。
- **枚举落地**（决策：本变更采用 IntEnum 整数契约，与项目整体 IntEnum 重构对齐）：layer/material/category/status 等枚举列用 `TINYINT + Java enum` 映射（存储整数码值，与前端整数契约一致）。
- **时间**：DATETIME(3) UTC ↔ LocalDateTime ↔ ISO8601（CP-014）。
- **包结构**：`com.dreamy.catalog/domain/fabriccare/{entity,repository,service,consts}` + `controller/` + `dto/`（与既有 product/category/attribute/tag 平级）。

### 1.2 实体清单（3 张新表 + Product 表扩展）

| 实体 | 表名 | 要点 |
|---|---|---|
| ProductFabricComposition | product_fabric_composition | product_id 外键；layer enum 1..4；material enum 1..10；percentage 0..100；sort_order |
| CareInstructionDef | care_instruction_def | code 唯一键；symbol_unicode；label_en/label_zh；category enum（5 类）；status enum（active/disabled）；sort_order |
| ProductCareInstruction | product_care_instruction | 复合主键(product_id, care_id)；sort_order |
| Product（扩展） | product | 新增字段 fabric_care_note TEXT（面料护理说明，可空） |

### 1.3 枚举值定义（IntEnum 整数契约）

**Layer（层次）**：
- 1: Shell（主料）
- 2: Lining（内衬）
- 3: Overlay（装饰层）
- 4: Trim（边饰）

**Material（材质）**：
- 1: Cotton（棉）
- 2: Polyester（聚酯纤维）
- 3: Lace（蕾丝）
- 4: Satin（缎面）
- 5: Chiffon（雪纺）
- 6: Tulle（薄纱）
- 7: Silk（丝绸）
- 8: Organza（欧根纱）
- 9: Spandex（氨纶）
- 10: Nylon（尼龙）

**CareCategory（护理类别）**：
- 1: washing（水洗）
- 2: bleaching（漂白）
- 3: drying（干燥）
- 4: ironing（熨烫）
- 5: dry_cleaning（干洗）

**CareStatus（状态）**：
- 1: active（启用）
- 2: disabled（禁用）

## 2. Repository 方法（RM-FC-001 ~ RM-FC-050）

### ProductFabricCompositionRepository
- RM-FC-001 `listByProductId(productId) -> List<ProductFabricComposition>` — ORDER BY layer ASC, sort_order ASC（层次优先，同层按排序）
- RM-FC-002 `listByProductIds(productIds) -> List` — 批查防 N+1
- RM-FC-003 `replaceAll(productId, rows[])` — DELETE WHERE product_id=? + 批量 INSERT（整单覆盖，TX-FC-001 中使用）
- RM-FC-004 `deleteByProductId(productId)` — 商品删除级联清理
- RM-FC-005 `validatePercentageSum(productId, layer) -> int` — 返回指定 product_id + layer 的 percentage 总和（业务规则校验：每层总和必须=100%）

### CareInstructionDefRepository
- RM-FC-010 `listAll() -> List<CareInstructionDef>` — ORDER BY category ASC, sort_order ASC（按类别分组排序）
- RM-FC-011 `listActive() -> List` — WHERE status=1 ORDER BY category, sort_order（消费端仅取启用）
- RM-FC-012 `findById(id) -> CareInstructionDef?`
- RM-FC-013 `findByCode(code) -> CareInstructionDef?` — uk_care_instruction_def_code 点查
- RM-FC-014 `existsByCodeExcept(code, exceptId?) -> bool` — 409 冲突检测（code 唯一性）
- RM-FC-015 `listByCategory(category) -> List` — 按类别筛选（管理端分类展示）
- RM-FC-016 `insert(CareInstructionDef)`
- RM-FC-017 `update(CareInstructionDef)`
- RM-FC-018 `deleteById(id)`
- RM-FC-019 `updateStatus(id, status)` — 启用/禁用切换

### ProductCareInstructionRepository
- RM-FC-020 `listByProductId(productId) -> List<ProductCareInstruction>` — ORDER BY sort_order ASC
- RM-FC-021 `listByProductIds(productIds) -> List` — 批查防 N+1
- RM-FC-022 `replaceAll(productId, careIds[])` — DELETE WHERE product_id=? + 批量 INSERT（整单覆盖，TX-FC-001 中使用）
- RM-FC-023 `deleteByProductId(productId)` — 商品删除级联清理
- RM-FC-024 `deleteByCareId(careId)` — 护理标签删除级联摘除

### ProductRepository（扩展既有方法）
- RM-FC-030 `updateFabricCareNote(productId, fabricCareNote)` — 单独更新 fabric_care_note 字段（若需要）
- 复用既有 RM-CAT-086/087（insert/update）支持新增字段

## 3. DTO ↔ Entity 映射（MAP-FC-001 ~ MAP-FC-020）

- MAP-FC-001 ProductFabricComposition→DTO：id/product_id/layer（整数→前端按枚举映射）/material（整数）/percentage/sort_order；created_at/updated_at
- MAP-FC-002 CareInstructionDef→AdminDTO：id/code/symbol_unicode/label_en/label_zh/category（整数）/sort_order/status（整数）/created_at/updated_at
- MAP-FC-003 CareInstructionDef→StoreDTO：仅 id/symbol_unicode/label_en/label_zh/category（按 locale 选择 label，前端多语言渲染）
- MAP-FC-004 ProductCareInstruction→DTO：product_id/care_id/sort_order（关联查询时 JOIN care_instruction_def 取标签详情）
- MAP-FC-005 Product→StoreProductDetail（扩展）：新增 fabric_compositions[]{layer,material,percentage,sort_order} + care_instructions[]{id,symbol_unicode,label,category} + fabric_care_note
- MAP-FC-006 Product→AdminProductDetail（扩展）：新增 fabric_compositions[] + care_instruction_ids[] + fabric_care_note
- MAP-FC-007 枚举映射：Java IntEnum ↔ TINYINT 数据库值 ↔ 前端整数契约（layer 1..4、material 1..10、category 1..5、status 1..2）
- MAP-FC-008 percentage 校验：前端提交 decimal，后端存 DECIMAL(5,2)，范围 0..100
- MAP-FC-009 sort_order 缺省值：按提交顺序自动分配（0, 1, 2...）

## 4. 索引设计（IDX-FC-001 ~ IDX-FC-010）

| ID | 表 | 索引 | 支撑路径 |
|---|---|---|---|
| IDX-FC-001 | product_fabric_composition | `idx_pfc_product_layer(product_id, layer)` | 按商品+层次查询；percentage 总和校验 |
| IDX-FC-002 | care_instruction_def | `UNIQUE uk_care_instruction_def_code(code)` | code 唯一性约束；409 冲突检测 |
| IDX-FC-003 | care_instruction_def | `idx_cid_category_sort(category, sort_order)` | 按类别分组排序查询 |
| IDX-FC-004 | care_instruction_def | `idx_cid_status(status)` | 启用/禁用过滤 |
| IDX-FC-005 | product_care_instruction | `PRIMARY KEY (product_id, care_id)` | 复合主键（防重复挂载） |
| IDX-FC-006 | product_care_instruction | `idx_pci_care(care_id)` | 反向查询（护理标签被哪些商品使用） |

查询优化补充：
- NP-FC-001 防 N+1：商品列表/详情装配面料成分与护理标签一律 productIds IN 批查（RM-FC-002/021）
- QP-FC-001 percentage 总和校验走单条 SUM 查询（RM-FC-005），不逐行累加

## 5. 事务边界（TX-FC-001 ~ TX-FC-010）

| ID | 端点/流程 | 边界与回滚语义 |
|---|---|---|
| TX-FC-001 | 商品创建/编辑（扩展 TX-CAT-001/002） | 单事务：product + fabric_composition/care_instruction 关联表批插/整单覆盖；percentage 总和校验失败 → 422 回滚 |
| TX-FC-002 | 商品删除（扩展 TX-CAT-003） | 单事务：级联删除 product + fabric_composition/care_instruction 关联行 |
| TX-FC-003 | 创建护理标签 | 单事务：INSERT care_instruction_def + operation_log |
| TX-FC-004 | 编辑护理标签 | 单事务：UPDATE care_instruction_def + operation_log |
| TX-FC-005 | 删除护理标签 | 单事务：DELETE care_instruction_def + product_care_instruction 级联摘除 + operation_log |
| TX-FC-006 | 切换护理标签状态 | 单事务：UPDATE status + operation_log；幂等短路不开事务 |

## 6. 数据校验与引用完整性（CV-FC-001 ~ CV-FC-020）

- CV-FC-001 枚举值校验：layer ∈ {1,2,3,4}、material ∈ {1..10}、category ∈ {1..5}、status ∈ {1,2}（应用层校验，DB 层 TINYINT 不加 CHECK）
- CV-FC-002 percentage 范围：0 ≤ percentage ≤ 100（DECIMAL(5,2)，应用层校验）
- CV-FC-003 percentage 总和约束（js_guard）：每个 product_id + layer 组合的所有行 percentage 总和必须=100（V-FC-015 应用层校验，STEP 中执行）
- CV-FC-004 sort_order ≥ 0（缺省按提交顺序分配）
- CV-FC-005 逻辑外键：product_fabric_composition.product_id / product_care_instruction.product_id 引用 product.id（不存在 → 422 `422501` fields.product_id=not_exists）
- CV-FC-006 逻辑外键：product_care_instruction.care_id 引用 care_instruction_def.id（不存在 → 422 `422501` fields.care_instruction_ids=not_exists）
- CV-FC-007 code 唯一性：uk_care_instruction_def_code 保证（冲突 → 409，应用层提前检测）
- CV-FC-008 复合主键：(product_id, care_id) 防重复挂载（幂等写入，整单覆盖天然维护）
- CV-FC-009 长度上限：code ≤64、symbol_unicode ≤16、label_en/label_zh ≤128
- CV-FC-010 必填字段：product_id/layer/material/percentage（fabric_composition）；code/label_en/label_zh/category/status（care_instruction_def）；product_id/care_id（product_care_instruction）
- CV-FC-011 删除引用守卫：care_instruction_def 删除无守卫（级联摘除 product_care_instruction，不阻止删除）

## 7. 缓存设计（CACHE-FC，JetCache 两级 Caffeine+Redis）

本变更新增字段/关联表不引入新缓存 key，复用既有商品缓存失效机制：

| ID | key 模板 | TTL | 装载点 | 失效触发者 |
|---|---|---|---|---|
| CACHE-FC-001 | 复用 `catalog:product:{slug}:{locale}` | 300s | E-CAT-04 PDP（扩展返回 fabric_compositions/care_instructions） | 商品编辑（TX-FC-001）触发既有失效链 |
| CACHE-FC-002 | 新增 `catalog:care-defs:{category?}` | 600s | 护理标签字典查询（管理端/消费端） | 护理标签写操作（TX-FC-003/004/005/006）触发 @CacheInvalidate |

- 穿透保护：cacheNullValue=true，null 短 TTL 60s
- 失效模式：JetCache @CacheInvalidate 按前缀批量失效；管理端端点一律不缓存

## 8. 完整 DDL（MySQL 8.0，utf8mb4_0900_ai_ci，InnoDB）

```sql
-- 1. product_fabric_composition 商品面料成分
CREATE TABLE product_fabric_composition (
  id         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  product_id BIGINT        NOT NULL COMMENT '逻辑外键 product.id',
  layer      TINYINT       NOT NULL COMMENT '层次：1=Shell/2=Lining/3=Overlay/4=Trim',
  material   TINYINT       NOT NULL COMMENT '材质：1=Cotton/2=Polyester/.../10=Nylon',
  percentage DECIMAL(5,2)  NOT NULL COMMENT '百分比 0..100（每层总和必须=100%，js_guard）',
  sort_order INT           NULL COMMENT '同层排序（可空，缺省按提交顺序）',
  created_at DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_pfc_product_layer (product_id, layer)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品面料成分（支持多层次结构）';

-- 2. care_instruction_def 护理标签字典
CREATE TABLE care_instruction_def (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  code           VARCHAR(64)  NOT NULL COMMENT '唯一标识码（如 WASH_30C）',
  symbol_unicode VARCHAR(16)  NULL COMMENT 'Unicode 符号（如 ♲）',
  label_en       VARCHAR(128) NOT NULL COMMENT '英文标签',
  label_zh       VARCHAR(128) NOT NULL COMMENT '中文标签',
  category       TINYINT      NOT NULL COMMENT '类别：1=washing/2=bleaching/3=drying/4=ironing/5=dry_cleaning',
  sort_order     INT          NULL COMMENT '同类别内排序',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=active/2=disabled',
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_care_instruction_def_code (code),
  KEY idx_cid_category_sort (category, sort_order),
  KEY idx_cid_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='护理标签字典（标准化护理说明定义）';

-- 3. product_care_instruction 商品-护理标签关联
CREATE TABLE product_care_instruction (
  product_id BIGINT      NOT NULL COMMENT '逻辑外键 product.id',
  care_id    BIGINT      NOT NULL COMMENT '逻辑外键 care_instruction_def.id',
  sort_order INT         NULL COMMENT 'PDP 展示顺序',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (product_id, care_id),
  KEY idx_pci_care (care_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品-护理标签关联（多对多）';

-- 4. product 表扩展（ALTER 语句）
ALTER TABLE product ADD COLUMN fabric_care_note TEXT NULL COMMENT '面料护理说明（自由文本补充）' AFTER care_instructions;
```

> 备注：
> ①枚举值采用 TINYINT 存储整数码值（与项目整体 IntEnum 重构对齐）
> ②percentage 采用 DECIMAL(5,2) 支持小数（如 45.5%）
> ③product 表的 ALTER 语句在商品表已存在的基础上执行
> ④未建 FULLTEXT 索引（面料/护理标签不参与全文搜索）

## 9. 自检清单

- [x] er-diagram 本变更 3 实体全部建模；Product 表扩展字段声明
- [x] 基类选型（LongAuditableEntity）/ 逻辑删除（不启用）/ 审计字段（created_at/updated_at）显式声明
- [x] RM-FC-001~030（分段编号，无重号）；MAP-FC-001~009；IDX-FC-001~006；TX-FC-001~006；CV-FC-001~011；CACHE-FC-001~002
- [x] 枚举映射采用 IntEnum 整数契约（layer 1..4、material 1..10、category 1..5、status 1..2）
- [x] percentage 总和约束（js_guard）明确在 CV-FC-003，应用层校验实现
- [x] 缓存策略复用既有商品缓存失效链；新增护理标签字典缓存
- [x] 3 张表完整 DDL + Product ALTER 语句；列定义与 er-diagram 约束一致
- [x] 事务边界与后续 api-detail TX 引用一一对应（待 API 设计完成后交叉验证）
- [x] 复合主键 (product_id, care_id) 防重复挂载；code 唯一键防冲突

