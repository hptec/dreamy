-- 删除 product 表的 fabric_composition 固定列（已全面迁移至 EAV 系统）
-- 执行前确保：
-- 1. 已在 attribute_def 中创建 'fabric_composition' 属性定义
-- 2. 已将现有数据迁移至 product_attribute_value 表
-- 3. 已部署新版本代码（不再引用此列）

USE dreamy;

-- 删除固定列
ALTER TABLE product DROP COLUMN fabric_composition;

-- 验证
SHOW COLUMNS FROM product LIKE 'fabric_composition';
-- 预期：Empty set（无结果）
