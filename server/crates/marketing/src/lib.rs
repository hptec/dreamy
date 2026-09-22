//! marketing 底座域(第一批迁移):newsletter 订阅/退订 + contact 联系表单。
//!
//! 对齐 Java:
//! - `domain/subscriber`(NewsletterService/UnsubscribeTokenService/NewsletterSubscriberRepository)
//! - `domain/contact`(ContactService)
//! - `controller/StoreLeadController`(3 个公开 POST 端点)
//! - `error/MarketingErrorCode`(域段 7)+ MarketingExceptionHandler(422704 字段级 details)
//!
//! 契约不变量(逐条对齐,详见各文件头):
//! - 订阅/退订恒 200 且不泄露存在性(防枚举);contact 恒 201;
//! - 退订 token 5 段格式 u1.<email>.<exp>.<gen>.<sig>,HMAC-SHA256 域分离;
//! - upsert 复活/退订均为单语句原子(代际谓词防"旧链接退订新订阅")。

pub mod api;
pub mod trading_error;
pub mod api_attribute;
pub mod api_category;
pub mod api_collection;
pub mod api_product;
pub mod api_trading;
pub mod api_banner;
pub mod banner;
pub mod api_content;
pub mod api_cart;
pub mod cart;
pub mod api_checkout;
pub mod api_order;
pub mod checkout;
pub mod order;
pub mod content;
pub mod api_tax;
pub mod tax;
pub mod config;
pub mod entity;
pub mod entity_trading;
pub mod product;
pub mod service;
pub mod service_address;
pub mod service_category;
pub mod token;
