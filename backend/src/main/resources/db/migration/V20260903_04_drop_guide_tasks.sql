-- guide_task is now the sole source of guide checklist tasks.
SET @has_tasks := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'guide' AND column_name = 'tasks'
);
SET @drop_tasks_sql := IF(@has_tasks > 0,
  'ALTER TABLE guide DROP COLUMN tasks',
  'SELECT 1');
PREPARE drop_tasks_stmt FROM @drop_tasks_sql;
EXECUTE drop_tasks_stmt;
DEALLOCATE PREPARE drop_tasks_stmt;
