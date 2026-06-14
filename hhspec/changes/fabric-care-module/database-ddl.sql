-- ========================================
-- DDL Script for fabric-care-module
-- ========================================
-- Database: dreamy
-- Generated: 2026-06-14T08:12:00Z
-- Description: 面料成分与护理标签存储结构
-- ========================================

-- 1. 面料层级表
-- 存储商品的多层面料信息（Shell/Lining/Overlay/Trim）
CREATE TABLE IF NOT EXISTS fabric_layers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    layer_type VARCHAR(20) NOT NULL COMMENT '层级类型: shell/lining/overlay/trim',
    display_order INT DEFAULT 0 COMMENT '显示顺序',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    CONSTRAINT fk_fabric_layer_product FOREIGN KEY (product_id)
        REFERENCES products(id) ON DELETE CASCADE,

    INDEX idx_product_layer (product_id, layer_type),
    INDEX idx_product_order (product_id, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='面料层级表';

-- 2. 面料成分表
-- 存储每层面料的具体材料和百分比
CREATE TABLE IF NOT EXISTS fabric_compositions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    layer_id BIGINT NOT NULL COMMENT '面料层级ID',
    material VARCHAR(50) NOT NULL COMMENT '材料枚举值: POLYESTER/SILK/COTTON等',
    percentage DECIMAL(5,2) NOT NULL COMMENT '百分比: 0.00-100.00',
    display_order INT DEFAULT 0 COMMENT '显示顺序',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    CONSTRAINT fk_composition_layer FOREIGN KEY (layer_id)
        REFERENCES fabric_layers(id) ON DELETE CASCADE,

    CONSTRAINT chk_percentage_range CHECK (percentage >= 0 AND percentage <= 100),

    INDEX idx_layer_material (layer_id, material),
    INDEX idx_layer_order (layer_id, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='面料成分表';

-- 3. 商品护理标签表
-- 存储商品关联的 ISO 3758 护理符号
CREATE TABLE IF NOT EXISTS product_care_symbols (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    symbol_id VARCHAR(50) NOT NULL COMMENT '护理符号ID: washing_machine_30等',
    display_order INT DEFAULT 0 COMMENT '显示顺序',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    CONSTRAINT fk_care_symbol_product FOREIGN KEY (product_id)
        REFERENCES products(id) ON DELETE CASCADE,

    UNIQUE KEY uk_product_symbol (product_id, symbol_id),
    INDEX idx_product_order (product_id, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='商品护理标签表';

-- ========================================
-- 示例数据插入（可选）
-- ========================================

-- 假设商品 ID=1 存在
-- 示例：一件婚纱，外层 80% 涤纶 + 20% 氨纶，内衬 100% 真丝

-- 插入外层（Shell）
INSERT INTO fabric_layers (product_id, layer_type, display_order)
VALUES (1, 'shell', 1);

SET @shell_layer_id = LAST_INSERT_ID();

INSERT INTO fabric_compositions (layer_id, material, percentage, display_order) VALUES
(@shell_layer_id, 'POLYESTER', 80.00, 1),
(@shell_layer_id, 'ELASTANE', 20.00, 2);

-- 插入内衬（Lining）
INSERT INTO fabric_layers (product_id, layer_type, display_order)
VALUES (1, 'lining', 2);

SET @lining_layer_id = LAST_INSERT_ID();

INSERT INTO fabric_compositions (layer_id, material, percentage, display_order) VALUES
(@lining_layer_id, 'SILK', 100.00, 1);

-- 插入护理标签
INSERT INTO product_care_symbols (product_id, symbol_id, display_order) VALUES
(1, 'washing_machine_30', 1),
(1, 'bleach_non_chlorine', 2),
(1, 'tumble_dry_low', 3),
(1, 'iron_low', 4),
(1, 'dry_clean_any', 5);

-- ========================================
-- 查询示例
-- ========================================

-- 查询商品的完整面料信息
SELECT
    fl.layer_type,
    fc.material,
    fc.percentage,
    fc.display_order
FROM fabric_layers fl
INNER JOIN fabric_compositions fc ON fl.id = fc.layer_id
WHERE fl.product_id = 1
ORDER BY fl.display_order, fc.display_order;

-- 查询商品的护理标签
SELECT symbol_id, display_order
FROM product_care_symbols
WHERE product_id = 1
ORDER BY display_order;

-- 验证某层面料的百分比总和是否为 100%
SELECT
    layer_id,
    SUM(percentage) AS total_percentage
FROM fabric_compositions
GROUP BY layer_id
HAVING total_percentage <> 100.00;

-- ========================================
-- 清理脚本（开发环境用）
-- ========================================

-- DROP TABLE IF EXISTS product_care_symbols;
-- DROP TABLE IF EXISTS fabric_compositions;
-- DROP TABLE IF EXISTS fabric_layers;

-- ========================================
-- 索引优化说明
-- ========================================
-- 1. fabric_layers.idx_product_layer: 支持按商品+层级快速查询
-- 2. fabric_compositions.idx_layer_material: 支持按层级+材料去重检查
-- 3. product_care_symbols.uk_product_symbol: 防止重复关联相同符号
-- 4. 所有 display_order 索引：支持排序查询优化

-- ========================================
-- 数据完整性约束说明
-- ========================================
-- 1. 级联删除：删除商品时自动删除关联的面料层级和护理标签
-- 2. 级联删除：删除面料层级时自动删除关联的成分记录
-- 3. 百分比范围检查：确保单个成分百分比在 0-100 之间
-- 4. 业务层校验：需在应用层保证每层面料的总百分比等于 100%

-- ========================================
-- 迁移建议
-- ========================================
-- 1. 现有商品允许面料信息为空（向后兼容）
-- 2. 分阶段迁移：先迁移核心品类（Wedding Dresses），再扩展到其他品类
-- 3. 提供批量导入工具或管理界面辅助数据录入
-- 4. 护理符号枚举需预先在代码层定义完整（见 care-symbols-catalog.yml）
