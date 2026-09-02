-- 手动执行（项目无 Flyway）
-- Lookbook 可选独立封面；为空时由服务端回退到关联商品的首张主图。
ALTER TABLE lookbook
    ADD COLUMN IF NOT EXISTS cover VARCHAR(512) NULL COMMENT '独立封面；为空时回退关联商品主图' AFTER theme;
