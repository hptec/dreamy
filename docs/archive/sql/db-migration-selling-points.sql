-- 移除 subtitle 字段，添加 selling_points 卖点字段
-- 执行时间：2026-06-14

-- 1. product 表：删除 subtitle，添加 selling_points (JSON 数组)
ALTER TABLE product DROP COLUMN subtitle;
ALTER TABLE product ADD COLUMN selling_points JSON NULL COMMENT '商品卖点（EN 基准，数组，最多5个）' AFTER designer_note;

-- 2. product_translation 表：删除 subtitle，添加 selling_points
ALTER TABLE product_translation DROP COLUMN subtitle;
ALTER TABLE product_translation ADD COLUMN selling_points JSON NULL COMMENT '翻译卖点（数组，与主表对应）' AFTER description;

-- 3. banner 表：保留 subtitle（banner 需要副标题）
-- banner 的 subtitle 保持不变

-- 4. banner_translation 表：保留 subtitle
-- banner_translation 的 subtitle 保持不变

-- 注意：selling_points 示例格式
-- product.selling_points (EN): ["Free Custom Sizing", "14-Day Production Time", "Free Worldwide Shipping"]
-- product_translation.selling_points (ES): ["Tallas Personalizadas Gratis", "Producción en 14 Días", "Envío Gratis Mundial"]
