-- Hero section 单例改由应用层分布式写锁在完整事务范围内保证。
-- 本脚本只清理曾部署的生成列唯一索引方案，不创建存储过程、临时表或其他数据库对象。
-- 同名对象只有与旧方案的结构完全一致时才会删除；结构不符时迁移立即失败并保持原状。

SET @home_sections_count = (
    SELECT COUNT(*)
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'home_sections'
      AND TABLE_TYPE = 'BASE TABLE'
);

-- 约束清理前先确认现有数据仍满足单例不变量。超过一条 Hero 时必须保留旧对象并人工修复数据。
SET @hero_count = 0;
SET @count_hero_sql = IF(
    @home_sections_count = 1,
    'SELECT COUNT(*) INTO @hero_count FROM home_sections WHERE section_type = ''hero''',
    'DO 0'
);
PREPARE dreamy_home_hero_stmt FROM @count_hero_sql;
EXECUTE dreamy_home_hero_stmt;
DEALLOCATE PREPARE dreamy_home_hero_stmt;

SET @hero_column_count = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'home_sections'
      AND COLUMN_NAME = 'hero_singleton'
);

-- MySQL 会按连接字符集给字符串字面量补充 _latin1/_utf8mb3/_utf8mb4 introducer。
-- 这里只移除转义、引号、反引号、空白和括号，再与旧表达式的有限白名单逐字比较；
-- 不使用会吞掉任意前缀的宽松正则，避免把错误表达式误判为旧生成列。
SET @valid_hero_column_count = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'home_sections'
      AND COLUMN_NAME = 'hero_singleton'
      AND DATA_TYPE = 'tinyint'
      AND COLUMN_TYPE IN ('tinyint', 'tinyint(4)')
      AND IS_NULLABLE = 'YES'
      AND COLUMN_DEFAULT IS NULL
      AND EXTRA = 'STORED GENERATED'
      AND COLUMN_COMMENT = 'Hero section singleton guard'
      AND LOWER(
            REGEXP_REPLACE(
                REPLACE(
                    REPLACE(
                        REPLACE(GENERATION_EXPRESSION, CHAR(92), ''),
                        CHAR(39), ''
                    ),
                    CHAR(96), ''
                ),
                '[[:space:]()]',
                ''
            )
          ) IN (
              'casewhensection_type=herothen1elsenullend',
              'casewhensection_type=_latin1herothen1elsenullend',
              'casewhensection_type=_utf8mb3herothen1elsenullend',
              'casewhensection_type=_utf8mb4herothen1elsenullend'
          )
);

SET @hero_index_count = (
    SELECT COUNT(DISTINCT INDEX_NAME)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'home_sections'
      AND INDEX_NAME = 'uk_home_sections_hero_singleton'
);

SET @hero_index_part_count = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'home_sections'
      AND INDEX_NAME = 'uk_home_sections_hero_singleton'
);

SET @valid_hero_index_part_count = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'home_sections'
      AND INDEX_NAME = 'uk_home_sections_hero_singleton'
      AND NON_UNIQUE = 0
      AND SEQ_IN_INDEX = 1
      AND COLUMN_NAME = 'hero_singleton'
      AND COLLATION = 'A'
      AND SUB_PART IS NULL
      AND PACKED IS NULL
      AND NULLABLE = 'YES'
      AND INDEX_TYPE = 'BTREE'
      AND COMMENT = ''
      AND INDEX_COMMENT = ''
      AND IS_VISIBLE = 'YES'
      AND EXPRESSION IS NULL
);

-- DROP COLUMN 会连带删除引用该列的索引；除旧的同名单列唯一索引外，发现任何索引依赖都阻断迁移。
SET @all_hero_column_index_part_count = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'home_sections'
      AND COLUMN_NAME = 'hero_singleton'
);

SET @home_hero_cleanup_safe = (
    @home_sections_count = 1
    AND @hero_count <= 1
    AND (
        @hero_column_count = 0
        OR (@hero_column_count = 1 AND @valid_hero_column_count = 1)
    )
    AND (
        @hero_index_count = 0
        OR (
            @hero_index_count = 1
            AND @hero_index_part_count = 1
            AND @valid_hero_index_part_count = 1
        )
    )
    AND @all_hero_column_index_part_count = @hero_index_part_count
    AND (@hero_index_count = 0 OR @hero_column_count = 1)
);

-- 预处理语句协议不支持 SIGNAL；通过准备一个必然不存在的 information_schema 表来使 guard 失败。
-- 失败分支不创建对象；即使客户端配置为忽略错误继续执行，后续 DROP 仍由同一 safe 条件保护。
SET @home_hero_guard_sql = IF(
    @home_hero_cleanup_safe,
    'DO 0',
    'SELECT 1 FROM information_schema.DREAMY_ABORT_UNEXPECTED_HOME_HERO_SINGLETON_SCHEMA'
);
PREPARE dreamy_home_hero_stmt FROM @home_hero_guard_sql;
EXECUTE dreamy_home_hero_stmt;
DEALLOCATE PREPARE dreamy_home_hero_stmt;

SET @drop_hero_index_sql = IF(
    @home_hero_cleanup_safe AND @hero_index_count = 1,
    'ALTER TABLE home_sections DROP INDEX uk_home_sections_hero_singleton',
    'DO 0'
);
PREPARE dreamy_home_hero_stmt FROM @drop_hero_index_sql;
EXECUTE dreamy_home_hero_stmt;
DEALLOCATE PREPARE dreamy_home_hero_stmt;

SET @drop_hero_column_sql = IF(
    @home_hero_cleanup_safe AND @hero_column_count = 1,
    'ALTER TABLE home_sections DROP COLUMN hero_singleton',
    'DO 0'
);
PREPARE dreamy_home_hero_stmt FROM @drop_hero_column_sql;
EXECUTE dreamy_home_hero_stmt;
DEALLOCATE PREPARE dreamy_home_hero_stmt;

SELECT 'obsolete home hero database constraint removed' AS status;

SET @home_sections_count = NULL,
    @hero_count = NULL,
    @count_hero_sql = NULL,
    @hero_column_count = NULL,
    @valid_hero_column_count = NULL,
    @hero_index_count = NULL,
    @hero_index_part_count = NULL,
    @valid_hero_index_part_count = NULL,
    @all_hero_column_index_part_count = NULL,
    @home_hero_cleanup_safe = NULL,
    @home_hero_guard_sql = NULL,
    @drop_hero_index_sql = NULL,
    @drop_hero_column_sql = NULL;
