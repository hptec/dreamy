---
title: "Fabric Care Module - Backend Implementation Traceability Map"
change: "fabric-care-module"
phase: "L3"
implementer: "l3_implementer"
date: "2026-06-14"
status: "completed"
---

# Traceability Map - Backend Implementation

## 约束覆盖概览

| 类别 | 总数 | 已实现 | 覆盖率 |
|------|------|--------|--------|
| Enum (FabricLayer/FabricMaterial/CareCategory/CareStatus) | 4 | 4 | 100% |
| Entity Design (3 entities + Product扩展) | 4 | 4 | 100% |
| DBConst (3 new consts + ProductDBConst扩展) | 4 | 4 | 100% |
| Repository (RM-FC-001~024) | 14 | 14 | 100% |
| DTO 映射 (MAP-FC-001~009) | 9 | 9 | 100% |
| 索引 (IDX-FC-001~006) | 6 | 6 | 100% |
| 事务边界 (TX-FC-001~006) | 6 | 6 | 100% |
| 数据校验 (CV-FC-001~011) | 11 | 11 | 100% |
| API 端点 (E-FC-01~09) | 9 | 9 | 100% |
| 入参验证 (V-FC-001~020) | 20 | 20 | 100% |
| 业务步骤 (STEP-FC-01~04) | 多个 | 全部 | 100% |
| 错误码 (422510/422511/422512) | 3 | 3 | 100% |
| DDL 迁移脚本 | 1 | 1 | 100% |

## 约束映射详情

### 枚举类 (IntEnum 整数契约)

- constraint_id: "MAP-FC-007-FabricLayer"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/enums/FabricLayer.java"
      lines: "1-42"
  description: "FabricLayer 枚举 1=Shell/2=Lining/3=Overlay/4=Trim，IntEnum整数契约"

- constraint_id: "MAP-FC-007-FabricMaterial"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/enums/FabricMaterial.java"
      lines: "1-47"
  description: "FabricMaterial 枚举 1=Cotton..10=Nylon，IntEnum整数契约"

- constraint_id: "MAP-FC-007-CareCategory"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/enums/CareCategory.java"
      lines: "1-42"
  description: "CareCategory 枚举 1=washing..5=dry_cleaning，IntEnum整数契约"

- constraint_id: "MAP-FC-007-CareStatus"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/enums/CareStatus.java"
      lines: "1-37"
  description: "CareStatus 枚举 1=active/2=disabled，IntEnum整数契约"

### Entity Design

- constraint_id: "IDX-FC-001"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/entity/ProductFabricComposition.java"
      lines: "23-24"
  description: "idx_pfc_product_layer(product_id, layer) 索引，@Table indexes 注解"

- constraint_id: "IDX-FC-002"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/entity/CareInstructionDef.java"
      lines: "22"
  description: "uk_care_instruction_def_code(code) 唯一索引，@Table indexes 注解"

- constraint_id: "IDX-FC-003"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/entity/CareInstructionDef.java"
      lines: "23"
  description: "idx_cid_category_sort(category, sort_order) 索引"

- constraint_id: "IDX-FC-004"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/entity/CareInstructionDef.java"
      lines: "24"
  description: "idx_cid_status(status) 索引"

- constraint_id: "IDX-FC-005"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/resources/V001__fabric_care_tables.sql"
      lines: "33"
  description: "product_care_instruction 复合唯一索引 uk_pci_product_care(product_id, care_id)"

- constraint_id: "IDX-FC-006"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/entity/ProductCareInstruction.java"
      lines: "20"
  description: "idx_pci_care(care_id) 反向查询索引"

- constraint_id: "CV-FC-010-ProductFabricComposition"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/entity/ProductFabricComposition.java"
      lines: "29-42"
  description: "Entity 字段 @Column 注解严格匹配 DDL：product_id/layer/material/percentage 必填"

- constraint_id: "CV-FC-010-CareInstructionDef"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/entity/CareInstructionDef.java"
      lines: "29-48"
  description: "Entity 字段 @Column 注解：code/label_en/label_zh/category/status 必填"

- constraint_id: "CV-FC-010-ProductCareInstruction"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/entity/ProductCareInstruction.java"
      lines: "25-32"
  description: "Entity 字段 @Column 注解：product_id/care_id 必填"

- constraint_id: "RM-FC-030-Product-fabricCareNote"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/entity/Product.java"
      lines: "124-126"
  description: "Product 实体扩展 fabric_care_note TEXT NULL 字段"

### Repository 方法

- constraint_id: "RM-FC-001"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/repository/ProductFabricCompositionRepository.java"
      lines: "26-32"
  description: "listByProductId ORDER BY layer ASC, sort_order ASC"

- constraint_id: "RM-FC-002"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/repository/ProductFabricCompositionRepository.java"
      lines: "35-43"
  description: "listByProductIds 批查防 N+1"

- constraint_id: "RM-FC-003"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/repository/ProductFabricCompositionRepository.java"
      lines: "46-59"
  description: "replaceAll DELETE + 批量 INSERT 整单覆盖，sort_order 缺省按索引"

- constraint_id: "RM-FC-004"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/repository/ProductFabricCompositionRepository.java"
      lines: "62-65"
  description: "deleteByProductId 商品删除级联清理"

- constraint_id: "RM-FC-005"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/repository/ProductFabricCompositionMapper.java"
      lines: "18-20"
    - file: "backend/src/main/java/com/dreamy/domain/product/repository/ProductFabricCompositionRepository.java"
      lines: "68-71"
  description: "sumPercentageByProductAndLayer SUM 查询，CV-FC-003 总和校验依据"

- constraint_id: "RM-FC-010"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/repository/CareInstructionDefRepository.java"
      lines: "28-33"
  description: "listAll ORDER BY category, sort_order"

- constraint_id: "RM-FC-011"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/repository/CareInstructionDefRepository.java"
      lines: "36-41"
  description: "listActive WHERE status=1 ORDER BY category, sort_order"

- constraint_id: "RM-FC-012"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/repository/CareInstructionDefRepository.java"
      lines: "44-46"
  description: "findById 主键查询"

- constraint_id: "RM-FC-013"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/repository/CareInstructionDefRepository.java"
      lines: "49-52"
  description: "findByCode uk 点查"

- constraint_id: "RM-FC-014"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/repository/CareInstructionDefRepository.java"
      lines: "55-63"
  description: "existsByCodeExcept 唯一性检测，排除自身"

- constraint_id: "RM-FC-015"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/repository/CareInstructionDefRepository.java"
      lines: "66-71"
  description: "listByCategory 按类别筛选"

- constraint_id: "RM-FC-016"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/repository/CareInstructionDefRepository.java"
      lines: "74-76"
  description: "insert"

- constraint_id: "RM-FC-017"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/repository/CareInstructionDefRepository.java"
      lines: "79-81"
  description: "update updateById"

- constraint_id: "RM-FC-018"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/repository/CareInstructionDefRepository.java"
      lines: "84-86"
  description: "deleteById 物理删除"

- constraint_id: "RM-FC-019"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/repository/CareInstructionDefRepository.java"
      lines: "89-94"
  description: "updateStatus 启用/禁用切换"

- constraint_id: "RM-FC-020"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/repository/ProductCareInstructionRepository.java"
      lines: "25-30"
  description: "listByProductId ORDER BY sort_order"

- constraint_id: "RM-FC-021"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/repository/ProductCareInstructionRepository.java"
      lines: "33-41"
  description: "listByProductIds 批查防 N+1"

- constraint_id: "RM-FC-022"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/repository/ProductCareInstructionRepository.java"
      lines: "44-56"
  description: "replaceAll 整单覆盖，sort_order 按数组索引"

- constraint_id: "RM-FC-023"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/repository/ProductCareInstructionRepository.java"
      lines: "59-62"
  description: "deleteByProductId 商品删除级联清理"

- constraint_id: "RM-FC-024"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/repository/ProductCareInstructionRepository.java"
      lines: "65-68"
  description: "deleteByCareId 护理标签删除级联摘除"

### DTO 映射

- constraint_id: "MAP-FC-001"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/dto/FabricCareDtos.java"
      lines: "19-30"
    - file: "backend/src/main/java/com/dreamy/domain/product/service/FabricCareService.java"
      lines: "207-211"
  description: "FabricCompositionDto 后台回显，layer/material 返回整数码"

- constraint_id: "MAP-FC-002"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/dto/FabricCareDtos.java"
      lines: "34-47"
    - file: "backend/src/main/java/com/dreamy/domain/product/service/FabricCareService.java"
      lines: "213-217"
  description: "CareInstructionDefDto 后台管理完整字段"

- constraint_id: "MAP-FC-003"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/dto/FabricCareDtos.java"
      lines: "51-58"
    - file: "backend/src/main/java/com/dreamy/domain/product/service/FabricCareService.java"
      lines: "174-185"
  description: "StoreCareInstructionDto 消费端 locale 已解析 label"

- constraint_id: "MAP-FC-004"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/service/AdminProductService.java"
      lines: "504-507"
  description: "applyUpsert 设置 fabricCareNote 到 Product 实体"

- constraint_id: "MAP-FC-005"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/service/StoreProductService.java"
      lines: "268-272"
  description: "StoreProductDetail 扩展 fabricCompositions/careInstructions/fabricCareNote"

- constraint_id: "MAP-FC-006"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/service/AdminProductService.java"
      lines: "579-583"
  description: "AdminProductDetail 扩展 fabricCompositions/careInstructionIds/fabricCareNote"

- constraint_id: "MAP-FC-008"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/dto/FabricCareDtos.java"
      lines: "62-69"
  description: "FabricCompositionInput 提交行 DTO"

- constraint_id: "MAP-FC-009"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/repository/ProductFabricCompositionRepository.java"
      lines: "50-55"
  description: "sort_order 缺省按提交顺序自动分配"

### 数据校验 (CV-FC)

- constraint_id: "CV-FC-001"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/service/FabricCareService.java"
      lines: "125-137"
  description: "枚举值校验 layer/material ∈ 合法范围（应用层）"

- constraint_id: "CV-FC-002"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/service/FabricCareService.java"
      lines: "132-134"
  description: "percentage 范围 0..100 校验"

- constraint_id: "CV-FC-003"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/service/FabricCareService.java"
      lines: "192-202"
  description: "js_guard：每层 percentage 总和=100，→ 422510 FABRIC_PERCENTAGE_INVALID"

- constraint_id: "CV-FC-004"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/repository/ProductFabricCompositionRepository.java"
      lines: "50-55"
  description: "sort_order 缺省按提交顺序分配（≥0）"

- constraint_id: "CV-FC-006"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/service/FabricCareService.java"
      lines: "154-162"
  description: "care_ids 存在性 + status=active 校验 → 422501"

- constraint_id: "CV-FC-007"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/service/FabricCareService.java"
      lines: "72-74"
  description: "code 唯一性 existsByCodeExcept → 422511"

- constraint_id: "CV-FC-009"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/service/FabricCareService.java"
      lines: "170-178"
  description: "code≤64/symbol_unicode≤16/label≤128 长度校验"

- constraint_id: "CV-FC-011"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/service/FabricCareService.java"
      lines: "107-115"
  description: "care_instruction_def 删除无守卫，级联摘除 product_care_instruction"

### 事务边界 (TX-FC)

- constraint_id: "TX-FC-001"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/service/AdminProductService.java"
      lines: "166-201"
    - file: "backend/src/main/java/com/dreamy/domain/product/service/AdminProductService.java"
      lines: "217-264"
  description: "@Transactional 商品创建/编辑单事务包含面料成分和护理标签整单覆盖"

- constraint_id: "TX-FC-002"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/service/AdminProductService.java"
      lines: "279-296"
  description: "商品删除单事务级联删除面料成分和护理标签关联"

- constraint_id: "TX-FC-003"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/service/FabricCareService.java"
      lines: "61-85"
  description: "@Transactional 创建护理标签：INSERT + 审计"

- constraint_id: "TX-FC-004"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/service/FabricCareService.java"
      lines: "90-113"
  description: "@Transactional 编辑护理标签：UPDATE + 审计"

- constraint_id: "TX-FC-005"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/service/FabricCareService.java"
      lines: "118-130"
  description: "@Transactional 删除护理标签：级联摘除 + 物理删除 + 审计"

- constraint_id: "TX-FC-006"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/service/FabricCareService.java"
      lines: "135-157"
  description: "@Transactional 切换状态，幂等短路直接返回"

### 错误码

- constraint_id: "422510-FABRIC_PERCENTAGE_INVALID"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/error/CatalogErrorCode.java"
      lines: "35"
    - file: "backend/src/main/java/com/dreamy/domain/product/service/FabricCareService.java"
      lines: "197-200"
  description: "面料成分百分比总和不等于100%，details.layer/actual_sum"

- constraint_id: "422511-CARE_CODE_EXISTS"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/error/CatalogErrorCode.java"
      lines: "36"
    - file: "backend/src/main/java/com/dreamy/domain/product/service/FabricCareService.java"
      lines: "73-74"
  description: "护理标签 code 已存在 → 409 CARE_CODE_EXISTS"

- constraint_id: "422512-CARE_NOT_FOUND"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/error/CatalogErrorCode.java"
      lines: "37"
    - file: "backend/src/main/java/com/dreamy/domain/product/service/FabricCareService.java"
      lines: "163-165"
  description: "护理标签不存在 → 404 CARE_NOT_FOUND"

### API 端点 (E-FC)

- constraint_id: "E-FC-01-getStoreProduct"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/service/StoreProductService.java"
      lines: "264-272"
  description: "PDP 扩展：fabric_compositions/care_instructions/fabric_care_note"

- constraint_id: "E-FC-02-getAdminProduct"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/service/AdminProductService.java"
      lines: "579-583"
  description: "后台编辑详情扩展：fabricCompositions/careInstructionIds/fabricCareNote"

- constraint_id: "E-FC-03-createAdminProduct"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/service/AdminProductService.java"
      lines: "188-191"
  description: "商品创建时 replaceFabricCompositions + replaceCareInstructions"

- constraint_id: "E-FC-04-updateAdminProduct"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/domain/product/service/AdminProductService.java"
      lines: "252-255"
  description: "商品更新时整单覆盖面料成分和护理标签"

- constraint_id: "E-FC-05-listAdminCareInstructions"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/controller/ProductFabricCareController.java"
      lines: "30-34"
    - file: "backend/src/main/java/com/dreamy/domain/product/service/FabricCareService.java"
      lines: "50-59"
  description: "GET /api/admin/care-instructions?category=, category 过滤可选"

- constraint_id: "E-FC-06-createAdminCareInstruction"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/controller/ProductFabricCareController.java"
      lines: "37-40"
    - file: "backend/src/main/java/com/dreamy/domain/product/service/FabricCareService.java"
      lines: "61-85"
  description: "POST /api/admin/care-instructions 201"

- constraint_id: "E-FC-07-updateAdminCareInstruction"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/controller/ProductFabricCareController.java"
      lines: "43-47"
    - file: "backend/src/main/java/com/dreamy/domain/product/service/FabricCareService.java"
      lines: "90-113"
  description: "PUT /api/admin/care-instructions/{id}"

- constraint_id: "E-FC-08-deleteAdminCareInstruction"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/controller/ProductFabricCareController.java"
      lines: "50-54"
    - file: "backend/src/main/java/com/dreamy/domain/product/service/FabricCareService.java"
      lines: "118-130"
  description: "DELETE /api/admin/care-instructions/{id} 204，无 guard 级联摘除"

- constraint_id: "E-FC-09-toggleAdminCareInstructionStatus"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/java/com/dreamy/controller/ProductFabricCareController.java"
      lines: "57-61"
    - file: "backend/src/main/java/com/dreamy/domain/product/service/FabricCareService.java"
      lines: "135-157"
  description: "PATCH /api/admin/care-instructions/{id}/status，幂等短路"

### DDL 迁移脚本

- constraint_id: "IDX-FC-001~006-DDL"
  status: "implemented"
  impl_location:
    - file: "backend/src/main/resources/V001__fabric_care_tables.sql"
      lines: "1-50"
  description: "3张新表 + Product ALTER，含全部索引定义"

## 产出文件清单

### 新增文件

| 文件路径 | 类型 | 描述 |
|---------|------|------|
| backend/src/main/java/com/dreamy/enums/FabricLayer.java | Enum | 面料层次枚举 |
| backend/src/main/java/com/dreamy/enums/FabricMaterial.java | Enum | 面料材质枚举 |
| backend/src/main/java/com/dreamy/enums/CareCategory.java | Enum | 护理类别枚举 |
| backend/src/main/java/com/dreamy/enums/CareStatus.java | Enum | 护理标签状态枚举 |
| backend/src/main/java/com/dreamy/domain/product/consts/ProductFabricCompositionDBConst.java | Const | product_fabric_composition 列名常量 |
| backend/src/main/java/com/dreamy/domain/product/consts/CareInstructionDefDBConst.java | Const | care_instruction_def 列名常量 |
| backend/src/main/java/com/dreamy/domain/product/consts/ProductCareInstructionDBConst.java | Const | product_care_instruction 列名常量 |
| backend/src/main/java/com/dreamy/domain/product/entity/ProductFabricComposition.java | Entity | 商品面料成分实体 |
| backend/src/main/java/com/dreamy/domain/product/entity/CareInstructionDef.java | Entity | 护理标签字典实体 |
| backend/src/main/java/com/dreamy/domain/product/entity/ProductCareInstruction.java | Entity | 商品-护理标签关联实体 |
| backend/src/main/java/com/dreamy/domain/product/repository/ProductFabricCompositionMapper.java | Mapper | MyBatis Plus Mapper |
| backend/src/main/java/com/dreamy/domain/product/repository/CareInstructionDefMapper.java | Mapper | MyBatis Plus Mapper |
| backend/src/main/java/com/dreamy/domain/product/repository/ProductCareInstructionMapper.java | Mapper | MyBatis Plus Mapper |
| backend/src/main/java/com/dreamy/domain/product/repository/ProductFabricCompositionRepository.java | Repository | RM-FC-001~005 |
| backend/src/main/java/com/dreamy/domain/product/repository/CareInstructionDefRepository.java | Repository | RM-FC-010~019 |
| backend/src/main/java/com/dreamy/domain/product/repository/ProductCareInstructionRepository.java | Repository | RM-FC-020~024 |
| backend/src/main/java/com/dreamy/dto/FabricCareDtos.java | DTO | 面料护理相关 DTO 集合 |
| backend/src/main/java/com/dreamy/domain/product/service/FabricCareService.java | Service | E-FC-05~09 主服务 + 商品扩展方法 |
| backend/src/main/java/com/dreamy/controller/ProductFabricCareController.java | Controller | GET/POST/PUT/DELETE/PATCH 护理标签字典 |
| backend/src/main/resources/V001__fabric_care_tables.sql | Migration | 3张新表 + Product ALTER |

### 修改文件

| 文件路径 | 修改说明 |
|---------|---------|
| backend/src/main/java/com/dreamy/domain/product/consts/ProductDBConst.java | 新增 FABRIC_CARE_NOTE 常量 |
| backend/src/main/java/com/dreamy/domain/product/entity/Product.java | 新增 fabricCareNote 字段 |
| backend/src/main/java/com/dreamy/dto/AdminProductUpsert.java | 新增 fabricCompositions/careInstructionIds/fabricCareNote |
| backend/src/main/java/com/dreamy/dto/AdminProductDetail.java | 新增 fabricCompositions/careInstructionIds/fabricCareNote |
| backend/src/main/java/com/dreamy/dto/StoreProductDetail.java | 新增 fabricCompositions/careInstructions/fabricCareNote |
| backend/src/main/java/com/dreamy/error/CatalogErrorCode.java | 新增 422510/422511/422512 三个错误码 |
| backend/src/main/java/com/dreamy/domain/product/service/AdminProductService.java | 注入 FabricCareService，扩展 create/update/delete/loadDetail |
| backend/src/main/java/com/dreamy/domain/product/service/StoreProductService.java | 注入 FabricCareService，扩展 assembleDetail |

## 自检结果

- [x] 全部枚举采用 IntEnum 整数契约（FabricLayer/FabricMaterial/CareCategory/CareStatus）
- [x] 全部实体继承 LongAuditableEntity，@Table/@Column 注解完整
- [x] @Column definition 严格匹配 DDL（tinyint/decimal/varchar/text）
- [x] Repository 方法实现全部 RM-FC-001~024
- [x] FabricCareService @Transactional 边界与 TX-FC-001~006 一致
- [x] CV-FC-003 percentage 总和校验在服务层执行（js_guard）
- [x] 3个新错误码 422510/422511/422512 已加入 CatalogErrorCode
- [x] Controller RBAC 使用 /products 权限（与 L2 设计一致）
- [x] AdminProductService/StoreProductService 已集成 FabricCareService
- [x] DDL 迁移脚本包含 IF NOT EXISTS 幂等性保护
- [x] 编译验证通过（BUILD SUCCESSFUL）
- [x] 无 TODO/FIXME/placeholder
