CREATE TABLE user_guide_task (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  guide_id BIGINT NOT NULL,
  task_id VARCHAR(64) NOT NULL,
  completed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_guide_task (user_id, guide_id, task_id),
  KEY idx_user_guide (user_id, guide_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消费者 Wedding Guide 任务进度';
