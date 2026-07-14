-- Gateway 发布完整性迁移：扩大 AES-GCM 密文列、增加 version CAS，并补齐权威索引。
--
-- 执行顺序：必须先执行 V20260714_01_physical_delete.sql。该迁移会清除
-- external_gateway_config 的历史 soft-deleted 行并删除 deleted_at；本脚本随后才对所有保留行建立
-- (gateway_type, name) 唯一约束。若 deleted_at 仍存在，本脚本会在任何结构变更前由 guard 中止。
--
-- 可重跑性：列和索引均按 information_schema 检查；同名但定义错误的索引、重复名称、错误列定义
-- 会中止而不是静默改写。成功后仅保留目标列/索引，不创建永久过程、触发器或辅助表。

SET @gateway_table_count = (
    SELECT COUNT(*)
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'external_gateway_config'
      AND TABLE_TYPE = 'BASE TABLE'
);

SET @gateway_deleted_at_count = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'external_gateway_config'
      AND COLUMN_NAME = 'deleted_at'
);

SET @gateway_duplicate_count = 0;
SET @gateway_duplicate_sql = IF(
    @gateway_table_count = 1,
    'SELECT COUNT(*) INTO @gateway_duplicate_count FROM (SELECT gateway_type, name FROM external_gateway_config GROUP BY gateway_type, name HAVING COUNT(*) > 1) duplicate_names',
    'DO 0'
);
PREPARE dreamy_gateway_stmt FROM @gateway_duplicate_sql;
EXECUTE dreamy_gateway_stmt;
DEALLOCATE PREPARE dreamy_gateway_stmt;

SET @gateway_api_key_column_count = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'external_gateway_config'
      AND COLUMN_NAME = 'api_key_encrypted'
);
SET @gateway_api_key_column_compatible = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'external_gateway_config'
      AND COLUMN_NAME = 'api_key_encrypted'
      AND DATA_TYPE = 'varchar'
      AND IS_NULLABLE = 'NO'
);
SET @gateway_api_key_length = COALESCE((
    SELECT CHARACTER_MAXIMUM_LENGTH
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'external_gateway_config'
      AND COLUMN_NAME = 'api_key_encrypted'
), 0);

SET @gateway_version_column_count = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'external_gateway_config'
      AND COLUMN_NAME = 'version'
);
SET @gateway_valid_version_column_count = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'external_gateway_config'
      AND COLUMN_NAME = 'version'
      AND COLUMN_TYPE IN ('int', 'int unsigned')
      AND IS_NULLABLE = 'NO'
      AND CAST(COLUMN_DEFAULT AS CHAR) = '0'
      AND EXTRA NOT LIKE '%GENERATED%'
);

SET @gateway_uk_name_count = (
    SELECT COUNT(DISTINCT INDEX_NAME)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'external_gateway_config'
      AND INDEX_NAME = 'uk_type_name'
);
SET @gateway_uk_row_count = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'external_gateway_config'
      AND INDEX_NAME = 'uk_type_name'
);
SET @gateway_uk_valid_row_count = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'external_gateway_config'
      AND INDEX_NAME = 'uk_type_name'
      AND NON_UNIQUE = 0
      AND SUB_PART IS NULL
      AND INDEX_TYPE = 'BTREE'
      AND ((SEQ_IN_INDEX = 1 AND COLUMN_NAME = 'gateway_type')
        OR (SEQ_IN_INDEX = 2 AND COLUMN_NAME = 'name'))
);

SET @gateway_enabled_index_name_count = (
    SELECT COUNT(DISTINCT INDEX_NAME)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'external_gateway_config'
      AND INDEX_NAME = 'idx_type_enabled'
);
SET @gateway_enabled_index_row_count = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'external_gateway_config'
      AND INDEX_NAME = 'idx_type_enabled'
);
SET @gateway_enabled_index_valid_row_count = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'external_gateway_config'
      AND INDEX_NAME = 'idx_type_enabled'
      AND NON_UNIQUE = 1
      AND SUB_PART IS NULL
      AND INDEX_TYPE = 'BTREE'
      AND ((SEQ_IN_INDEX = 1 AND COLUMN_NAME = 'gateway_type')
        OR (SEQ_IN_INDEX = 2 AND COLUMN_NAME = 'enabled'))
);

DROP TEMPORARY TABLE IF EXISTS dreamy_gateway_integrity_guard;
CREATE TEMPORARY TABLE dreamy_gateway_integrity_guard (
    table_exists TINYINT NOT NULL,
    physical_delete_complete TINYINT NOT NULL,
    duplicate_name_absent TINYINT NOT NULL,
    api_key_column_compatible TINYINT NOT NULL,
    version_column_compatible TINYINT NOT NULL,
    unique_index_compatible TINYINT NOT NULL,
    enabled_index_compatible TINYINT NOT NULL,
    CONSTRAINT chk_gateway_table_exists CHECK (table_exists = 1),
    CONSTRAINT chk_gateway_physical_delete_complete CHECK (physical_delete_complete = 1),
    CONSTRAINT chk_gateway_duplicate_name_absent CHECK (duplicate_name_absent = 1),
    CONSTRAINT chk_gateway_api_key_column CHECK (api_key_column_compatible = 1),
    CONSTRAINT chk_gateway_version_column CHECK (version_column_compatible = 1),
    CONSTRAINT chk_gateway_unique_index CHECK (unique_index_compatible = 1),
    CONSTRAINT chk_gateway_enabled_index CHECK (enabled_index_compatible = 1)
);
INSERT INTO dreamy_gateway_integrity_guard VALUES (
    @gateway_table_count = 1,
    @gateway_deleted_at_count = 0,
    @gateway_duplicate_count = 0,
    @gateway_api_key_column_count = 1 AND @gateway_api_key_column_compatible = 1,
    @gateway_version_column_count = 0 OR (
        @gateway_version_column_count = 1 AND @gateway_valid_version_column_count = 1
    ),
    @gateway_uk_name_count = 0 OR (
        @gateway_uk_name_count = 1
        AND @gateway_uk_row_count = 2
        AND @gateway_uk_valid_row_count = 2
    ),
    @gateway_enabled_index_name_count = 0 OR (
        @gateway_enabled_index_name_count = 1
        AND @gateway_enabled_index_row_count = 2
        AND @gateway_enabled_index_valid_row_count = 2
    )
);

SET @gateway_expand_api_key_sql = IF(
    @gateway_api_key_length < 4096,
    'ALTER TABLE external_gateway_config MODIFY COLUMN api_key_encrypted VARCHAR(4096) NOT NULL COMMENT ''API Key密文(AES-256-GCM, v1:前缀+IV+密文 base64)''',
    'DO 0'
);
PREPARE dreamy_gateway_stmt FROM @gateway_expand_api_key_sql;
EXECUTE dreamy_gateway_stmt;
DEALLOCATE PREPARE dreamy_gateway_stmt;

SET @gateway_add_version_sql = IF(
    @gateway_version_column_count = 0,
    'ALTER TABLE external_gateway_config ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER extra_config',
    'DO 0'
);
PREPARE dreamy_gateway_stmt FROM @gateway_add_version_sql;
EXECUTE dreamy_gateway_stmt;
DEALLOCATE PREPARE dreamy_gateway_stmt;

SET @gateway_add_unique_index_sql = IF(
    @gateway_uk_name_count = 0,
    'ALTER TABLE external_gateway_config ADD UNIQUE INDEX uk_type_name (gateway_type, name)',
    'DO 0'
);
PREPARE dreamy_gateway_stmt FROM @gateway_add_unique_index_sql;
EXECUTE dreamy_gateway_stmt;
DEALLOCATE PREPARE dreamy_gateway_stmt;

SET @gateway_add_enabled_index_sql = IF(
    @gateway_enabled_index_name_count = 0,
    'ALTER TABLE external_gateway_config ADD INDEX idx_type_enabled (gateway_type, enabled)',
    'DO 0'
);
PREPARE dreamy_gateway_stmt FROM @gateway_add_enabled_index_sql;
EXECUTE dreamy_gateway_stmt;
DEALLOCATE PREPARE dreamy_gateway_stmt;

DROP TEMPORARY TABLE dreamy_gateway_integrity_guard;

SELECT 'gateway integrity constraints ready' AS status;
