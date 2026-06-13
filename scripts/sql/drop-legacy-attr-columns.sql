-- 商品版型属性 EAV 化后的旧固定列手工清理脚本。
-- 前置条件：后端已启动过一次（CatalogAttrEavMigrationInitializer 完成旧列 → product_attribute_value 迁移），
-- 且核对 PDP/后台编辑回读正常后再执行。huihao auto-DDL 不会自动删列，本脚本需手工执行。
-- 执行后迁移 initializer 将因 information_schema 探测不到旧列而自动跳过。

ALTER TABLE product
    DROP COLUMN silhouette,
    DROP COLUMN neckline,
    DROP COLUMN sleeve,
    DROP COLUMN back_style,
    DROP COLUMN waistline,
    DROP COLUMN train,
    DROP COLUMN `length`,
    DROP COLUMN fabric,
    DROP COLUMN support,
    DROP COLUMN season,
    DROP COLUMN embellishments,
    DROP COLUMN occasions,
    DROP COLUMN style_tags,
    DROP COLUMN care_instructions,
    DROP COLUMN model_height,
    DROP COLUMN model_size,
    DROP COLUMN model_body_type,
    DROP COLUMN country_of_origin;
