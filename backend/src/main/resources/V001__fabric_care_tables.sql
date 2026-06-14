-- V001__fabric_care_tables.sql
-- 面料护理模块：3张新表 + Product表扩展
-- L2 TRACE: catalog-fabric-care-data-detail §9 完整DDL

-- 1. product_fabric_composition 商品面料成分
CREATE TABLE IF NOT EXISTS product_fabric_composition (
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
CREATE TABLE IF NOT EXISTS care_instruction_def (
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
CREATE TABLE IF NOT EXISTS product_care_instruction (
  id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  product_id BIGINT      NOT NULL COMMENT '逻辑外键 product.id',
  care_id    BIGINT      NOT NULL COMMENT '逻辑外键 care_instruction_def.id',
  sort_order INT         NULL COMMENT 'PDP 展示顺序',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_pci_product_care (product_id, care_id),
  KEY idx_pci_care (care_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品-护理标签关联（多对多）';

-- 4. product 表扩展（新增 fabric_care_note 字段）
ALTER TABLE product ADD COLUMN fabric_care_note TEXT NULL COMMENT '面料护理说明（自由文本补充）' AFTER style_no;
