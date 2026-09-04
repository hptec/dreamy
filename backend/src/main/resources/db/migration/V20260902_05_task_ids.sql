ALTER TABLE guide MODIFY COLUMN tasks TEXT NULL COMMENT '待办任务 JSON 数组 [{task_id,label}]';

SET @has_old_task := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'user_guide_task' AND column_name = 'task'
);
SET @rename_sql := IF(@has_old_task > 0,
  'ALTER TABLE user_guide_task CHANGE COLUMN task task_id VARCHAR(64) NOT NULL',
  'SELECT 1');
PREPARE rename_stmt FROM @rename_sql;
EXECUTE rename_stmt;
DEALLOCATE PREPARE rename_stmt;
