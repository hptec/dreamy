-- Dreamy 清库脚本:清除开发/演示数据,保留配置类表(恒跑 seed 与权限/管理员)
-- 执行方式见 scripts/seed/reset.mjs(经 SSH 在 mysql 容器内执行),随后 flush Redis 并重启 backend

SET FOREIGN_KEY_CHECKS = 0;

-- 商品目录域
TRUNCATE product; TRUNCATE product_translation; TRUNCATE product_image;
TRUNCATE product_attribute_value; TRUNCATE product_collection; TRUNCATE sku;
TRUNCATE size_chart_row;
TRUNCATE category; TRUNCATE category_translation;
TRUNCATE attribute_def; TRUNCATE attribute_def_translation;
TRUNCATE attribute_set; TRUNCATE attribute_set_item;
TRUNCATE collection; TRUNCATE collection_group;
TRUNCATE collection_translation; TRUNCATE collection_group_translation;

-- 评价与问答
TRUNCATE review; TRUNCATE review_image; TRUNCATE product_question;

-- 营销
TRUNCATE coupon; TRUNCATE coupon_translation;
TRUNCATE flash_sale; TRUNCATE flash_sale_product; TRUNCATE flash_sale_translation;
TRUNCATE banner; TRUNCATE banner_translation;

-- 内容域
TRUNCATE blog_post; TRUNCATE blog_post_translation;
TRUNCATE real_wedding; TRUNCATE real_wedding_product; TRUNCATE real_wedding_translation;
TRUNCATE lookbook; TRUNCATE lookbook_product; TRUNCATE lookbook_translation;
TRUNCATE guide; TRUNCATE guide_task; TRUNCATE guide_translation; TRUNCATE guide_task_translation;

-- 站点搭建
TRUNCATE home_sections; TRUNCATE navigation_items;
TRUNCATE footer_columns; TRUNCATE footer_links; TRUNCATE announcements;

-- 运费
TRUNCATE carrier; TRUNCATE shipping_rate; TRUNCATE shipping_option;

-- 试衣间(showroom)
TRUNCATE showroom; TRUNCATE showroom_comment; TRUNCATE showroom_item;
TRUNCATE showroom_member; TRUNCATE showroom_vote;

-- 用户域(开发期测试账号)
TRUNCATE user; TRUNCATE user_identity; TRUNCATE user_session;
TRUNCATE address; TRUNCATE browse_history; TRUNCATE wishlist_item;
TRUNCATE cart_item; TRUNCATE cart_merge_record;
TRUNCATE login_history; TRUNCATE otp_code;
TRUNCATE newsletter_subscriber; TRUNCATE contact_message; TRUNCATE mail_record;

-- 订单与事件域(开发期测试订单)
TRUNCATE orders; TRUNCATE order_line; TRUNCATE order_event;
TRUNCATE payment; TRUNCATE refund;
TRUNCATE shipment; TRUNCATE shipment_event; TRUNCATE shipment_line;
TRUNCATE event_outbox; TRUNCATE processed_event;

SET FOREIGN_KEY_CHECKS = 1;
