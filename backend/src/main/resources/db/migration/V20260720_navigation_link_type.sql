-- 手动执行（项目无 Flyway）
-- 导航项链接类型扩展：taxonomy_id（未实现的空壳）→ ref_id + page_key。
-- link_type varchar('custom'/'taxonomy') → tinyint（LinkType IntEnum：1=custom 2=page
-- 3=category 4=collection 5=product 6=blog_post 7=real_wedding 8=lookbook 9=guide）。
-- 存量数据全部为 link_type='custom'，映射为 1，url 保留，行为不变。
-- 不向后兼容：旧代码读 tinyint link_type 会按字符串 '1' 处理，必须先执行 DDL 再回滚代码。

-- STEP 1：存量值映射为数字字符串（'taxonomy' 从未真正支持，落回 custom=1，url 为空时消费端降级隐藏）
UPDATE navigation_items SET link_type = '1' WHERE link_type IN ('custom', 'taxonomy');

-- STEP 2：结构调整
ALTER TABLE navigation_items
    DROP INDEX idx_navigation_items_taxonomy,
    DROP COLUMN taxonomy_id,
    ADD COLUMN ref_id bigint NULL COMMENT '引用资源 id（link_type=category/collection/product/blog_post/real_wedding/lookbook/guide 时非空）' AFTER link_type,
    ADD COLUMN page_key varchar(64) NULL COMMENT '系统页 key（link_type=page 时非空）' AFTER ref_id,
    MODIFY COLUMN link_type tinyint NOT NULL DEFAULT 1 COMMENT 'LinkType: 1=custom 2=page 3=category 4=collection 5=product 6=blog_post 7=real_wedding 8=lookbook 9=guide',
    ADD INDEX idx_navigation_items_ref (link_type, ref_id);
