-- V20260617_rollback_tag_to_collection.sql
-- 反向回滚脚本（仅用于部署失败紧急回退；DDL rename 不可逆，需先执行此脚本再回滚 backend 镜像）
-- 注意：执行前必须确认 backend 已停服；执行后旧 backend 镜像方可启动

-- 1. collection_group → tag_dimension
RENAME TABLE collection_group TO tag_dimension;

-- 2. collection_group_translation → tag_dimension_translation
ALTER TABLE collection_group_translation
  DROP INDEX uk_cgt,
  ADD UNIQUE KEY uk_tdt (tag_dimension_id, locale);
ALTER TABLE collection_group_translation
  CHANGE COLUMN collection_group_id tag_dimension_id BIGINT NOT NULL;
RENAME TABLE collection_group_translation TO tag_dimension_translation;

-- 3. collection → tag
ALTER TABLE collection
  DROP INDEX idx_collection_group,
  DROP INDEX idx_collection_status,
  ADD KEY idx_tag_dimension (dimension_id),
  ADD KEY idx_tag_status (status);
ALTER TABLE collection
  CHANGE COLUMN collection_group_id dimension_id BIGINT NOT NULL COMMENT '逻辑外键 tag_dimension.id';
RENAME TABLE collection TO tag;

-- 4. collection_translation → tag_translation
ALTER TABLE collection_translation
  DROP INDEX uk_ct,
  ADD UNIQUE KEY uk_tt (tag_id, locale);
ALTER TABLE collection_translation
  CHANGE COLUMN collection_id tag_id BIGINT NOT NULL;
RENAME TABLE collection_translation TO tag_translation;

-- 5. product_collection → product_tag
ALTER TABLE product_collection
  DROP INDEX uk_pcol,
  DROP INDEX idx_pcol_collection,
  ADD UNIQUE KEY uk_ptag (product_id, tag_id),
  ADD KEY idx_ptag_tag (tag_id);
ALTER TABLE product_collection
  CHANGE COLUMN collection_id tag_id BIGINT NOT NULL;
RENAME TABLE product_collection TO product_tag;
