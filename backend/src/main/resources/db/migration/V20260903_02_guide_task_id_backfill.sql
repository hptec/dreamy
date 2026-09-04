-- Legacy guide.tasks stored JSON strings and early progress rows stored the same
-- task label in task_id. Convert both to the stable {task_id,label} contract.
-- The UUID expression exactly matches Java UUID.nameUUIDFromBytes(UTF-8 label).

UPDATE user_guide_task progress
JOIN guide ON guide.id = progress.guide_id
JOIN JSON_TABLE(
  guide.tasks,
  '$[*]' COLUMNS (label VARCHAR(256) PATH '$')
) legacy_task ON progress.task_id = legacy_task.label
SET progress.task_id = CONCAT(
  SUBSTRING(MD5(legacy_task.label), 1, 8),
  '-',
  SUBSTRING(MD5(legacy_task.label), 9, 4),
  '-',
  '3',
  SUBSTRING(MD5(legacy_task.label), 14, 3),
  '-',
  HEX((CONV(SUBSTRING(MD5(legacy_task.label), 17, 1), 16, 10) & 3) | 8),
  SUBSTRING(MD5(legacy_task.label), 18, 3),
  '-',
  SUBSTRING(MD5(legacy_task.label), 21)
)
WHERE JSON_TYPE(JSON_EXTRACT(guide.tasks, '$[0]')) = 'STRING';

UPDATE guide
SET tasks = (
  SELECT JSON_ARRAYAGG(JSON_OBJECT(
    'task_id', CONCAT(
      SUBSTRING(MD5(legacy_task.label), 1, 8),
      '-',
      SUBSTRING(MD5(legacy_task.label), 9, 4),
      '-',
      '3',
      SUBSTRING(MD5(legacy_task.label), 14, 3),
      '-',
      HEX((CONV(SUBSTRING(MD5(legacy_task.label), 17, 1), 16, 10) & 3) | 8),
      SUBSTRING(MD5(legacy_task.label), 18, 3),
      '-',
      SUBSTRING(MD5(legacy_task.label), 21)
    ),
    'label', legacy_task.label
  ))
  FROM JSON_TABLE(guide.tasks, '$[*]' COLUMNS (label VARCHAR(256) PATH '$')) legacy_task
)
WHERE JSON_TYPE(JSON_EXTRACT(tasks, '$[0]')) = 'STRING';
