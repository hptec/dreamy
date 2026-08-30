-- 2026-08-30: real_wedding_translation 增加 theme 列（ES/FR 翻译 theme 字段）
-- 决策: theme 是分类标签（Beach/Vineyard/Garden...），应跟随 locale 翻译
-- 主表 real_wedding.theme 仍为 EN 基准；消费端按 locale 回退
ALTER TABLE real_wedding_translation
    ADD COLUMN theme VARCHAR(32) NULL COMMENT '主题翻译（EN 主表 real_wedding.theme 的译文）' AFTER story;
