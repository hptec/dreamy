-- V20260617_drop_ai_translation_modules.sql
-- 翻译方案简化：移除 AI 翻译日志 + 翻译术语表两个模块。
-- AI 翻译调用端点（POST /api/admin/ai/translate）保留，不再写日志；术语表注入逻辑移除。
-- external_gateway_config 保留（AI 网关配置仍在用）。

DROP TABLE IF EXISTS ai_translation_log;
DROP TABLE IF EXISTS ai_translation_glossary;
