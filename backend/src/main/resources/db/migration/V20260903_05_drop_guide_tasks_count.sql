SET @has_tasks_count := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'guide' AND column_name = 'tasks_count'
);
SET @drop_tasks_count_sql := IF(@has_tasks_count > 0,
  'ALTER TABLE guide DROP COLUMN tasks_count',
  'SELECT 1');
PREPARE drop_tasks_count_stmt FROM @drop_tasks_count_sql;
EXECUTE drop_tasks_count_stmt;
DEALLOCATE PREPARE drop_tasks_count_stmt;
