-- 首页装修改为保存后直接生效，清理旧发布快照和私有预览结构。
-- 项目不自动执行 migration；上线前请先备份数据库，再手动执行本文件。

UPDATE home_sections
SET data_json = NULL,
    i18n_json = NULL
WHERE section_type = 'hero';

DROP TABLE IF EXISTS home_page_preview_tokens;
DROP TABLE IF EXISTS home_page_releases;
DROP TABLE IF EXISTS site_builder_config;

SELECT 'home builder legacy publication data removed' AS status;
