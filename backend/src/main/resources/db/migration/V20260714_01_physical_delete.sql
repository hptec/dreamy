-- Catalog、Marketing、Shipping、Identity 管理员与 Gateway 配置均使用物理删除；集合封面由集合内
-- 前 4 张商品主图动态拼贴。本迁移统一清除历史 tombstone 并移除应用已不再映射的 deleted_at 列，
-- 避免升级后历史软删数据重新出现在查询中。
--
-- 部署约束（必须遵守）：
-- 1. 进入维护窗口，停止全部 backend 实例和 Catalog 写入；不要让旧、新版本应用与本脚本并行运行。
-- 2. 备份数据库后执行本文件；脚本成功完成后再一次性启动新版本 backend。
-- 3. 旧代码依赖 deleted_at，先执行脚本会导致旧实例查询失败；新代码不读取 deleted_at，先启动新代码又会让
--    历史软删行短暂可见。因此本变更不支持滚动部署，只支持停机迁移。
--
-- 可重跑性：每个表都先检查列是否存在，再清理 tombstone 并删列。若中途失败，已完成的表会在重跑时跳过，
-- 未完成表会从其事务起点继续；collection.cover 也按列存在性条件删除。

DELIMITER //

DROP PROCEDURE IF EXISTS dreamy_catalog_physical_delete//
CREATE PROCEDURE dreamy_catalog_physical_delete()
BEGIN
    DECLARE has_column INT DEFAULT 0;

    SELECT COUNT(*) INTO has_column
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND COLUMN_NAME = 'deleted_at';
    IF has_column > 0 THEN
        START TRANSACTION;
        DELETE pi FROM product_image pi
        JOIN product p ON p.id = pi.product_id
        WHERE p.deleted_at IS NOT NULL;
        DELETE s FROM sku s
        JOIN product p ON p.id = s.product_id
        WHERE p.deleted_at IS NOT NULL;
        DELETE scr FROM size_chart_row scr
        JOIN product p ON p.id = scr.product_id
        WHERE p.deleted_at IS NOT NULL;
        DELETE pc FROM product_collection pc
        JOIN product p ON p.id = pc.product_id
        WHERE p.deleted_at IS NOT NULL;
        DELETE pt FROM product_translation pt
        JOIN product p ON p.id = pt.product_id
        WHERE p.deleted_at IS NOT NULL;
        DELETE pav FROM product_attribute_value pav
        JOIN product p ON p.id = pav.product_id
        WHERE p.deleted_at IS NOT NULL;
        DELETE FROM product WHERE deleted_at IS NOT NULL;
        COMMIT;
        ALTER TABLE product DROP COLUMN deleted_at;
    END IF;

    SELECT COUNT(*) INTO has_column
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'category' AND COLUMN_NAME = 'deleted_at';
    IF has_column > 0 THEN
        START TRANSACTION;
        DELETE ct FROM category_translation ct
        JOIN category c ON c.id = ct.category_id
        WHERE c.deleted_at IS NOT NULL;
        DELETE FROM category WHERE deleted_at IS NOT NULL;
        COMMIT;
        ALTER TABLE category DROP COLUMN deleted_at;
    END IF;

    SELECT COUNT(*) INTO has_column
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'attribute_set' AND COLUMN_NAME = 'deleted_at';
    IF has_column > 0 THEN
        START TRANSACTION;
        DELETE asi FROM attribute_set_item asi
        JOIN attribute_set aset ON aset.id = asi.attribute_set_id
        WHERE aset.deleted_at IS NOT NULL;
        DELETE FROM attribute_set WHERE deleted_at IS NOT NULL;
        COMMIT;
        ALTER TABLE attribute_set DROP COLUMN deleted_at;
    END IF;

    SELECT COUNT(*) INTO has_column
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'attribute_def' AND COLUMN_NAME = 'deleted_at';
    IF has_column > 0 THEN
        START TRANSACTION;
        DELETE asi FROM attribute_set_item asi
        JOIN attribute_def adef ON adef.id = asi.attribute_id
        WHERE adef.deleted_at IS NOT NULL;
        DELETE pav FROM product_attribute_value pav
        JOIN attribute_def adef ON adef.id = pav.attribute_id
        WHERE adef.deleted_at IS NOT NULL;
        DELETE adt FROM attribute_def_translation adt
        JOIN attribute_def adef ON adef.id = adt.attribute_def_id
        WHERE adef.deleted_at IS NOT NULL;
        DELETE FROM attribute_def WHERE deleted_at IS NOT NULL;
        COMMIT;
        ALTER TABLE attribute_def DROP COLUMN deleted_at;
    END IF;

    SELECT COUNT(*) INTO has_column
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'collection' AND COLUMN_NAME = 'deleted_at';
    IF has_column > 0 THEN
        START TRANSACTION;
        DELETE pc FROM product_collection pc
        JOIN collection c ON c.id = pc.collection_id
        WHERE c.deleted_at IS NOT NULL;
        DELETE ct FROM collection_translation ct
        JOIN collection c ON c.id = ct.collection_id
        WHERE c.deleted_at IS NOT NULL;
        DELETE FROM collection WHERE deleted_at IS NOT NULL;
        COMMIT;
        ALTER TABLE collection DROP COLUMN deleted_at;
    END IF;

    SELECT COUNT(*) INTO has_column
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'collection_group' AND COLUMN_NAME = 'deleted_at';
    IF has_column > 0 THEN
        START TRANSACTION;
        DELETE cgt FROM collection_group_translation cgt
        JOIN collection_group cg ON cg.id = cgt.collection_group_id
        WHERE cg.deleted_at IS NOT NULL;
        DELETE FROM collection_group WHERE deleted_at IS NOT NULL;
        COMMIT;
        ALTER TABLE collection_group DROP COLUMN deleted_at;
    END IF;

    SELECT COUNT(*) INTO has_column
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_user' AND COLUMN_NAME = 'deleted_at';
    IF has_column > 0 THEN
        START TRANSACTION;
        DELETE s FROM admin_session s
        JOIN admin_user a ON a.id = s.admin_id
        WHERE a.deleted_at IS NOT NULL;
        DELETE FROM admin_user WHERE deleted_at IS NOT NULL;
        COMMIT;
        ALTER TABLE admin_user DROP COLUMN deleted_at;
    END IF;

    SELECT COUNT(*) INTO has_column
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'banner' AND COLUMN_NAME = 'deleted_at';
    IF has_column > 0 THEN
        START TRANSACTION;
        DELETE bt FROM banner_translation bt
        JOIN banner b ON b.id = bt.banner_id
        WHERE b.deleted_at IS NOT NULL;
        DELETE FROM banner WHERE deleted_at IS NOT NULL;
        COMMIT;
        ALTER TABLE banner DROP COLUMN deleted_at;
    END IF;

    SELECT COUNT(*) INTO has_column
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'blog_post' AND COLUMN_NAME = 'deleted_at';
    IF has_column > 0 THEN
        START TRANSACTION;
        DELETE bpt FROM blog_post_translation bpt
        JOIN blog_post bp ON bp.id = bpt.blog_post_id
        WHERE bp.deleted_at IS NOT NULL;
        DELETE FROM blog_post WHERE deleted_at IS NOT NULL;
        COMMIT;
        ALTER TABLE blog_post DROP COLUMN deleted_at;
    END IF;

    SELECT COUNT(*) INTO has_column
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'carrier' AND COLUMN_NAME = 'deleted_at';
    IF has_column > 0 THEN
        DELETE FROM carrier WHERE deleted_at IS NOT NULL;
        ALTER TABLE carrier DROP COLUMN deleted_at;
    END IF;

    SELECT COUNT(*) INTO has_column
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'coupon' AND COLUMN_NAME = 'deleted_at';
    IF has_column > 0 THEN
        START TRANSACTION;
        DELETE ct FROM coupon_translation ct
        JOIN coupon c ON c.id = ct.coupon_id
        WHERE c.deleted_at IS NOT NULL;
        DELETE FROM coupon WHERE deleted_at IS NOT NULL;
        COMMIT;
        ALTER TABLE coupon DROP COLUMN deleted_at;
    END IF;

    SELECT COUNT(*) INTO has_column
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flash_sale' AND COLUMN_NAME = 'deleted_at';
    IF has_column > 0 THEN
        START TRANSACTION;
        DELETE fsp FROM flash_sale_product fsp
        JOIN flash_sale fs ON fs.id = fsp.flash_sale_id
        WHERE fs.deleted_at IS NOT NULL;
        DELETE fst FROM flash_sale_translation fst
        JOIN flash_sale fs ON fs.id = fst.flash_sale_id
        WHERE fs.deleted_at IS NOT NULL;
        DELETE FROM flash_sale WHERE deleted_at IS NOT NULL;
        COMMIT;
        ALTER TABLE flash_sale DROP COLUMN deleted_at;
    END IF;

    SELECT COUNT(*) INTO has_column
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'guide' AND COLUMN_NAME = 'deleted_at';
    IF has_column > 0 THEN
        START TRANSACTION;
        DELETE gt FROM guide_translation gt
        JOIN guide g ON g.id = gt.guide_id
        WHERE g.deleted_at IS NOT NULL;
        DELETE FROM guide WHERE deleted_at IS NOT NULL;
        COMMIT;
        ALTER TABLE guide DROP COLUMN deleted_at;
    END IF;

    SELECT COUNT(*) INTO has_column
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lookbook' AND COLUMN_NAME = 'deleted_at';
    IF has_column > 0 THEN
        START TRANSACTION;
        DELETE lp FROM lookbook_product lp
        JOIN lookbook l ON l.id = lp.lookbook_id
        WHERE l.deleted_at IS NOT NULL;
        DELETE lt FROM lookbook_translation lt
        JOIN lookbook l ON l.id = lt.lookbook_id
        WHERE l.deleted_at IS NOT NULL;
        DELETE FROM lookbook WHERE deleted_at IS NOT NULL;
        COMMIT;
        ALTER TABLE lookbook DROP COLUMN deleted_at;
    END IF;

    SELECT COUNT(*) INTO has_column
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'real_wedding' AND COLUMN_NAME = 'deleted_at';
    IF has_column > 0 THEN
        START TRANSACTION;
        DELETE rwp FROM real_wedding_product rwp
        JOIN real_wedding rw ON rw.id = rwp.real_wedding_id
        WHERE rw.deleted_at IS NOT NULL;
        DELETE rwt FROM real_wedding_translation rwt
        JOIN real_wedding rw ON rw.id = rwt.real_wedding_id
        WHERE rw.deleted_at IS NOT NULL;
        DELETE FROM real_wedding WHERE deleted_at IS NOT NULL;
        COMMIT;
        ALTER TABLE real_wedding DROP COLUMN deleted_at;
    END IF;

    SELECT COUNT(*) INTO has_column
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'external_gateway_config' AND COLUMN_NAME = 'deleted_at';
    IF has_column > 0 THEN
        DELETE FROM external_gateway_config WHERE deleted_at IS NOT NULL;
        ALTER TABLE external_gateway_config DROP COLUMN deleted_at;
    END IF;

    SELECT COUNT(*) INTO has_column
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'collection' AND COLUMN_NAME = 'cover';
    IF has_column > 0 THEN
        ALTER TABLE collection DROP COLUMN cover;
    END IF;
END//

CALL dreamy_catalog_physical_delete()//
DROP PROCEDURE dreamy_catalog_physical_delete//

DELIMITER ;

SELECT 'physical-delete migration complete' AS status;
