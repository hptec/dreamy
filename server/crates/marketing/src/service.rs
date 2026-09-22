//! marketing 底座域 service 层(newsletter 订阅/退订 + contact 提交)。
//! 逐条对齐 Java NewsletterService / ContactService 注释里的 L2 TRACE 规则。

use sea_orm::{
    ActiveModelTrait, ColumnTrait, ConnectionTrait, DatabaseConnection, DbBackend,
    EntityTrait, InsertResult, QueryFilter, Set, Statement,
};
use serde_json::json;

use crate::entity::contact_message;
use crate::entity::newsletter_subscriber::{self, NewsletterSource, SubscriberStatus};
use crate::token;

/// 422704 字段级错误收集器(对齐 MarketingFieldErrors:details = { fields: { <field>: <reason> } })
#[derive(Default)]
pub struct FieldErrors {
    fields: Vec<(&'static str, &'static str)>,
}

impl FieldErrors {
    pub fn reject(&mut self, field: &'static str, reason: &'static str) {
        if !self.fields.iter().any(|(f, _)| *f == field) {
            self.fields.push((field, reason));
        }
    }
    pub fn has(&self) -> bool {
        !self.fields.is_empty()
    }
    pub fn to_details(&self) -> serde_json::Value {
        let map: serde_json::Map<String, serde_json::Value> = self
            .fields
            .iter()
            .map(|(f, r)| (f.to_string(), json!(r)))
            .collect();
        json!({ "fields": map })
    }
}

/// RFC5322 实用子集(V-MKT-009,bs-543/544;与 Java Pattern 逐字符等价)
pub fn email_valid(email: &str) -> bool {
    // ^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$
    let Some((local, domain)) = email.rsplit_once('@') else {
        return false;
    };
    if local.is_empty() || domain.is_empty() {
        return false;
    }
    !local.chars().any(|c| !(c.is_ascii_alphanumeric() || "._%+-".contains(c)))
        && !domain.chars().any(|c| !(c.is_ascii_alphanumeric() || ".-".contains(c)))
        && {
            // [A-Za-z0-9.-]+\.[A-Za-z]{2,}$:末个点前前缀 ≥1 字符,点后 TLD ≥2 字母
            let Some((prefix, tld)) = domain.rsplit_once('.') else {
                return false;
            };
            !prefix.is_empty()
                && !prefix.ends_with('.')
                && tld.len() >= 2
                && tld.chars().all(|c| c.is_ascii_alphabetic())
        }
}

pub const LOCALES: [&str; 3] = ["en", "es", "fr"];

/// E-MKT-11 订阅(恒 Ok;校验失败 Err(422704 details)):
/// email 小写归一(trim+lowercase,CV-MKT-008)→ 单语句 upsert
/// (已订阅空操作首写胜出,已退订复活 status 2→1;TX-MKT-027)。
/// 不发码不发邮件(决策 26 显式降级)。
pub async fn subscribe(
    db: &DatabaseConnection,
    email: Option<&str>,
    source: Option<i64>,
    locale: Option<&str>,
) -> Result<(), FieldErrors> {
    let mut errors = FieldErrors::default();
    // STEP-MKT-01 归一 + V-MKT-009 校验
    let normalized = email.map(|e| e.trim().to_lowercase());
    match normalized.as_deref() {
        None | Some("") => errors.reject("email", "required"),
        Some(e) if e.len() > 255 || !email_valid(e) => errors.reject("email", "format_invalid"),
        _ => {}
    }
    // V-MKT-010 source ∈ {1,2,3,4}(整数枚举;Java 侧 Integer → NewsletterSource.of)
    let source_enum = source.and_then(NewsletterSource::from_key);
    if source_enum.is_none() {
        errors.reject("source", "invalid_enum");
    }
    // V-MKT-011 locale ∈ {en,es,fr}
    let locale_str = locale.unwrap_or("");
    if !LOCALES.contains(&locale_str) {
        errors.reject("locale", "invalid_enum");
    }
    if errors.has() {
        return Err(errors);
    }
    // STEP-MKT-02 单语句 upsert(ON DUPLICATE KEY UPDATE 条件赋值全在前、status 最后;
    // 对齐 NewsletterSubscriberMapper.upsertReactivating,AS new 别名 + 表名限定旧行引用)
    // Java LocalDateTime.now() = 服务器本地时区裸值;同口径用 Local(对齐 admin_ops 既有约定)
    let now = chrono::Local::now().naive_local();
    let email_v = normalized.unwrap();
    // 注意:Rust 枚举 discriminant 从 0 起,与 sea_orm num_value(1 起)不同——必须显式映射
    let source_v = match source_enum.unwrap() {
        crate::entity::newsletter_subscriber::NewsletterSource::Footer => 1i64,
        crate::entity::newsletter_subscriber::NewsletterSource::Modal => 2,
        crate::entity::newsletter_subscriber::NewsletterSource::ExitIntent => 3,
        crate::entity::newsletter_subscriber::NewsletterSource::HomeBlock => 4,
    };
    let sql = r#"INSERT INTO newsletter_subscriber(email, source, locale, status, subscribed_at, created_at, updated_at)
VALUES(?, ?, ?, 1, ?, NOW(3), NOW(3)) AS new
ON DUPLICATE KEY UPDATE
source = IF(newsletter_subscriber.status = 2, new.source, newsletter_subscriber.source),
locale = IF(newsletter_subscriber.status = 2, new.locale, newsletter_subscriber.locale),
subscribed_at = IF(newsletter_subscriber.status = 2, new.subscribed_at, newsletter_subscriber.subscribed_at),
unsubscribed_at = IF(newsletter_subscriber.status = 2, NULL, newsletter_subscriber.unsubscribed_at),
updated_at = IF(newsletter_subscriber.status = 2, NOW(3), newsletter_subscriber.updated_at),
status = IF(newsletter_subscriber.status = 2, 1, newsletter_subscriber.status)"#;
    let res = db
        .execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            sql,
            [email_v.into(), source_v.into(), locale_str.into(), now.into()],
        ))
        .await;
    match res {
        Ok(_) => Ok(()),
        Err(e) => {
            tracing::error!("[mkt] subscribe upsert failed:{e}");
            // 基础设施失败按 50000 抛(包络由 api 层兜底);此处以 panic-free 方式转 5xx
            let mut fe = FieldErrors::default();
            fe.reject("__internal__", "database");
            Err(fe)
        }
    }
}

/// 退订(对齐 unsubscribe):
/// token 校验失败 → Err(TokenInvalid)(api 层映射 422704 field=token);
/// UPDATE 单语句原子含代际谓词(subscribed_at = gen);0 行时 SELECT 区分
/// 「不存在/已退订(幂等 200)」与「代际落后(422)」。CLIENT_FOUND_ROWS 语义用
/// MySQL ROW_COUNT() 差异——直连协议 affected_rows 即命中行数,行为一致。
pub async fn unsubscribe(
    db: &DatabaseConnection,
    secret: &str,
    tok: Option<&str>,
) -> Result<(), UnsubscribeError> {
    let Some(tok) = tok else {
        return Err(UnsubscribeError::InvalidToken);
    };
    let now_millis = chrono::Utc::now().timestamp_millis();
    let parsed = token::parse(secret, tok, now_millis).map_err(|_| UnsubscribeError::InvalidToken)?;
    let email = parsed.email.to_lowercase();
    let gen_time = chrono::DateTime::from_timestamp_millis(parsed.gen_epoch_millis)
        .map(|dt| dt.naive_utc())
        .ok_or(UnsubscribeError::InvalidToken)?;
    // 单语句原子退订(WHERE 代际等值;重复退订保留首次 unsubscribed_at)
    let sql = r#"UPDATE newsletter_subscriber SET
unsubscribed_at = IF(status = 2, unsubscribed_at, NOW(3)),
updated_at = IF(status = 2, updated_at, NOW(3)),
status = 2
WHERE email = ? AND subscribed_at = ?"#;
    let updated = db
        .execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            sql,
            [email.clone().into(), gen_time.into()],
        ))
        .await
        .map_err(|e| {
            tracing::error!("[mkt] unsubscribe update failed:{e}");
            UnsubscribeError::Infra
        })?;
    if updated.rows_affected() > 0 {
        return Ok(());
    }
    // 0 行分类:仅用于错误响应,状态正确性已由 UPDATE 原子谓词保证
    let existing = newsletter_subscriber::Entity::find()
        .filter(newsletter_subscriber::Column::Email.eq(&email))
        .one(db)
        .await
        .map_err(|e| {
            tracing::error!("[mkt] unsubscribe lookup failed:{e}");
            UnsubscribeError::Infra
        })?;
    if let Some(row) = existing {
        if row.status == SubscriberStatus::Subscribed
            && token::db_time_to_epoch_millis(row.subscribed_at) != parsed.gen_epoch_millis
        {
            return Err(UnsubscribeError::InvalidToken);
        }
    }
    Ok(())
}

pub enum UnsubscribeError {
    /// token 无效/过期/代际落后 → 422704 field=token
    InvalidToken,
    /// 基础设施失败 → 50000
    Infra,
}

/// 生成退订 token(供未来邮件模板;仅已订阅记录可生成,gen 取持久化 subscribed_at)
pub async fn generate_unsubscribe_token(
    db: &DatabaseConnection,
    secret: &str,
    email: &str,
    ttl_seconds: i64,
) -> Option<String> {
    let normalized = email.trim().to_lowercase();
    let row = newsletter_subscriber::Entity::find()
        .filter(newsletter_subscriber::Column::Email.eq(normalized))
        .one(db)
        .await
        .ok()??;
    if row.status != SubscriberStatus::Subscribed {
        return None;
    }
    let gen = token::db_time_to_epoch_millis(row.subscribed_at);
    Some(token::generate(
        secret,
        &row.email,
        gen,
        chrono::Utc::now().timestamp_millis(),
        ttl_seconds,
    ))
}

/// E-MKT-12 提交(恒 201;无判重,同人多次留言均落表,TX-MKT-028 单语句)。
/// V-MKT-012 name trim 非空 ≤100;V-MKT-013 email 必填格式 ≤255;
/// V-MKT-014 subject 可选 ≤200;V-MKT-015 message trim 非空 ≤5000。
pub async fn submit_contact(
    db: &DatabaseConnection,
    name: Option<&str>,
    email: Option<&str>,
    subject: Option<&str>,
    message: Option<&str>,
) -> Result<(), FieldErrors> {
    let mut errors = FieldErrors::default();
    let parsed_name = name.map(str::trim).filter(|s| !s.is_empty());
    match parsed_name {
        None => errors.reject("name", "required"),
        Some(n) if n.len() > 100 => errors.reject("name", "too_long"),
        _ => {}
    }
    let parsed_email = email.map(str::trim);
    match parsed_email {
        None | Some("") => errors.reject("email", "required"),
        Some(e) if e.len() > 255 || !email_valid(e) => errors.reject("email", "format_invalid"),
        _ => {}
    }
    let parsed_subject = subject.map(str::trim).filter(|s| !s.is_empty());
    if parsed_subject.is_some_and(|s| s.len() > 200) {
        errors.reject("subject", "too_long");
    }
    let parsed_message = message.map(str::trim).filter(|s| !s.is_empty());
    match parsed_message {
        None => errors.reject("message", "required"),
        Some(m) if m.len() > 5000 => errors.reject("message", "too_long"),
        _ => {}
    }
    if errors.has() {
        return Err(errors);
    }
    // Java LocalDateTime.now() = 本地时区裸值(对齐)
    let now = chrono::Local::now().naive_local();
    let row = contact_message::ActiveModel {
        name: Set(parsed_name.unwrap().to_string()),
        email: Set(parsed_email.unwrap().to_string()),
        subject: Set(parsed_subject.map(String::from)),
        message: Set(parsed_message.unwrap().to_string()),
        submitted_at: Set(now),
        ..Default::default()
    };
    let res: Result<InsertResult<contact_message::ActiveModel>, sea_orm::DbErr> =
        contact_message::Entity::insert(row).exec(db).await;
    match res {
        Ok(_) => Ok(()),
        Err(e) => {
            tracing::error!("[mkt] contact insert failed:{e}");
            let mut fe = FieldErrors::default();
            fe.reject("__internal__", "database");
            Err(fe)
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn email_pattern_parity() {
        for ok in ["a@b.co", "first.last+tag@sub.domain.org", "u%1_@x-y.io"] {
            assert!(email_valid(ok), "{ok}");
        }
        for bad in ["", "a@b", "@b.co", "a@.co", "a@b.c", "a b@x.io", "a@b..co", "a@@x.io"] {
            assert!(!email_valid(bad), "{bad}");
        }
    }

    #[test]
    fn field_errors_first_wins() {
        let mut fe = FieldErrors::default();
        fe.reject("email", "required");
        fe.reject("email", "format_invalid"); // putIfAbsent 语义:首次胜出
        let d = fe.to_details();
        assert_eq!(d["fields"]["email"], "required");
        assert_eq!(d["fields"].as_object().unwrap().len(), 1);
    }
}
