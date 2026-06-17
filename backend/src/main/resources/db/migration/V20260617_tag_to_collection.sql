-- V20260617_tag_to_collection.sql
-- 自定义标签 → 集合（Collection）重命名迁移
-- 仅 rename 表/列/索引，不重置数据；历史 operation_log.action / cache_invalidation_log.event_type 行保留旧值

-- 1. tag_dimension → collection_group
RENAME TABLE tag_dimension TO collection_group;

-- 2. tag_dimension_translation → collection_group_translation
RENAME TABLE tag_dimension_translation TO collection_group_translation;
ALTER TABLE collection_group_translation
  CHANGE COLUMN tag_dimension_id collection_group_id BIGINT NOT NULL;
ALTER TABLE collection_group_translation
  DROP INDEX uk_tdt,
  ADD UNIQUE KEY uk_cgt (collection_group_id, locale);

-- 3. tag → collection
RENAME TABLE tag TO collection;
ALTER TABLE collection
  CHANGE COLUMN dimension_id collection_group_id BIGINT NOT NULL COMMENT '逻辑外键 collection_group.id';
ALTER TABLE collection
  DROP INDEX idx_tag_dimension,
  DROP INDEX idx_tag_status,
  ADD KEY idx_collection_group (collection_group_id),
  ADD KEY idx_collection_status (status);

-- 4. tag_translation → collection_translation
RENAME TABLE tag_translation TO collection_translation;
ALTER TABLE collection_translation
  CHANGE COLUMN tag_id collection_id BIGINT NOT NULL;
ALTER TABLE collection_translation
  DROP INDEX uk_tt,
  ADD UNIQUE KEY uk_ct (collection_id, locale);

-- 5. product_tag → product_collection
RENAME TABLE product_tag TO product_collection;
ALTER TABLE product_collection
  CHANGE COLUMN tag_id collection_id BIGINT NOT NULL;
ALTER TABLE product_collection
  DROP INDEX uk_ptag,
  DROP INDEX idx_ptag_tag,
  ADD UNIQUE KEY uk_pcol (product_id, collection_id),
  ADD KEY idx_pcol_collection (collection_id);
