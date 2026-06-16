-- V20250124: 面料/护理内联化——删专用表,Product 加 JSON 列
-- 原因: 简化数据模型(3 张专用表 + 4 个枚举 → 2 个 JSON 列),提升查询性能(减少 JOIN),与行业通用 Unicode 符号对齐

-- 1. Product 加 3 个 JSON 列
ALTER TABLE product
  ADD COLUMN fabric_compositions JSON COMMENT '面料成分内联 JSON: [{layer:1|2|3|4, material:"Polyester", percentage:70.00}]',
  ADD COLUMN care JSON COMMENT '护理标签内联 JSON: [{symbol:"🫧", label:"Hand wash cold"}]',
  ADD COLUMN fabric_care_note VARCHAR(500) COMMENT '护理备注（可选）';

-- 2. 删除专用表(先删外键约束表)
DROP TABLE IF EXISTS product_care_instruction;
DROP TABLE IF EXISTS product_fabric_composition;
DROP TABLE IF EXISTS care_instruction_def;

-- 注: FabricMaterial/CareCategory/CareStatus/FabricLayer 枚举已在代码层删除,无需 SQL 清理
