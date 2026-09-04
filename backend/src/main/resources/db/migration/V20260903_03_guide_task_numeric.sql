CREATE TABLE IF NOT EXISTS guide_task (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  guide_id BIGINT NOT NULL,
  label VARCHAR(256) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_guide_task_guide_sort (guide_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Import legacy JSON task labels once. Existing rows are protected by the
-- NOT EXISTS guard so this migration is rerunnable.
INSERT INTO guide_task (guide_id, label, sort_order)
SELECT g.id, jt.label, jt.ord - 1
FROM guide g
JOIN JSON_TABLE(g.tasks, '$[*]' COLUMNS (
  ord FOR ORDINALITY,
  label VARCHAR(256) PATH '$'
)) jt
WHERE JSON_TYPE(JSON_EXTRACT(g.tasks, '$[0]')) = 'STRING'
  AND NOT EXISTS (SELECT 1 FROM guide_task t WHERE t.guide_id = g.id);

UPDATE user_guide_task p
JOIN guide g ON g.id = p.guide_id
JOIN guide_task t ON t.guide_id = g.id AND t.label = p.task_id
SET p.task_id = CAST(t.id AS CHAR);

-- Some early rows used Java UUID.nameUUIDFromBytes(label) instead of the label.
-- Resolve those UUIDs before converting the column to BIGINT.
UPDATE user_guide_task p
JOIN guide_task t ON t.guide_id = p.guide_id
SET p.task_id = CAST(t.id AS CHAR)
WHERE LOWER(p.task_id) = LOWER(CONCAT(
  SUBSTRING(MD5(t.label), 1, 8), '-',
  SUBSTRING(MD5(t.label), 9, 4), '-',
  '3', SUBSTRING(MD5(t.label), 14, 3), '-',
  HEX((CONV(SUBSTRING(MD5(t.label), 17, 1), 16, 10) & 3) | 8),
  SUBSTRING(MD5(t.label), 18, 3), '-',
  SUBSTRING(MD5(t.label), 21)
));

ALTER TABLE user_guide_task MODIFY COLUMN task_id BIGINT UNSIGNED NOT NULL;
