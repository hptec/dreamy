//! 身份域邮件:Resend API(替代 Java SMTP;交易域邮件仍走 Java SMTP 不动)。
//! 模板读 identity 库 email_template({{var}} 朴素替换 + en 回退;对齐 Java EmailTemplateRenderer)。
//! RESEND_API_KEY 缺席 → stub 模式(日志代替外发,对齐 MAIL_MODE=stub 语义)。

use common::state::SharedState;
use sea_orm::ConnectionTrait;
use sea_orm::Statement;
use serde::Deserialize;

use crate::service::SvcError;

#[derive(Debug, Deserialize)]
struct TemplateRow {
    subject: String,
    body: String,
}

/// 按 (code, locale) 读模板;缺失回退 en;再缺 → None(stub 日志)
async fn load_template(state: &SharedState, code: &str, locale: &str) -> Option<TemplateRow> {
    let legacy = state.db_legacy.as_ref()?;
    let row = |loc: &str| {
        let legacy = legacy.clone();
        let code = code.to_string();
        let loc = loc.to_string();
        async move {
            let qr = legacy
                .query_one(Statement::from_sql_and_values(
                    sea_orm::DatabaseBackend::MySql,
                    r#"SELECT subject, body FROM email_template WHERE code = ? AND locale = ?"#,
                    [code.into(), loc.into()],
                ))
                .await
                .ok()??;
            Some(TemplateRow {
                subject: qr.try_get_by_index(0).ok()?,
                body: qr.try_get_by_index(1).ok()?,
            })
        }
    };
    let mut found = row(locale).await;
    if found.is_none() && locale != "en" {
        found = row("en").await;
    }
    found
}

/// {{var}} 朴素替换(null → 空串;对齐 Java 渲染器)
fn render(template: &str, vars: &[(String, String)]) -> String {
    let mut out = template.to_string();
    for (k, v) in vars {
        out = out.replace(&format!("{{{{{k}}}}}"), v);
    }
    out
}

pub struct SendOutcome {
    pub sent: bool,
    pub stub: bool,
}

/// 发送模板邮件(身份域 4 code:otp/new_device/change_primary/account_deleted)
pub async fn send_template(
    state: &SharedState,
    to: &str,
    code: &str,
    locale: &str,
    vars: &[(String, String)],
) -> Result<SendOutcome, SvcError> {
    let Some(tpl) = load_template(state, code, locale).await else {
        tracing::warn!("[mail] 模板缺失 code={code} locale={locale}(stub)");
        return Ok(SendOutcome {
            sent: false,
            stub: true,
        });
    };
    let subject = render(&tpl.subject, vars);
    let body = render(&tpl.body, vars);
    send(state, to, &subject, &body).await
}

/// 底层发送:RESEND_API_KEY 缺席 → stub;失败重试 3 次(1s/2s/4s 退避,对齐 Java)
pub async fn send(
    _state: &SharedState,
    to: &str,
    subject: &str,
    body: &str,
) -> Result<SendOutcome, SvcError> {
    let key = std::env::var("RESEND_API_KEY").unwrap_or_default();
    let from = std::env::var("RESEND_FROM").unwrap_or_else(|_| "noreply@dreamy.com".into());
    if key.is_empty() {
        tracing::info!("[mail:stub] to={to} subject={subject}");
        return Ok(SendOutcome {
            sent: false,
            stub: true,
        });
    }

    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(8))
        .build()
        .map_err(|_| crate::service::SvcError::code(50002))?;
    let payload = serde_json::json!({
        "from": from,
        "to": [to],
        "subject": subject,
        "text": body,
    });

    let mut backoff: Vec<u64> = vec![0, 1, 2, 4]; // 立即首发 + 三轮退避
    let mut last_status = 0u16;
    while let Some(delay) = backoff.first().copied() {
        backoff.remove(0);
        if delay > 0 {
            tokio::time::sleep(std::time::Duration::from_secs(delay)).await;
        }
        match client
            .post("https://api.resend.com/emails")
            .bearer_auth(&key)
            .json(&payload)
            .send()
            .await
        {
            Ok(resp) if resp.status().is_success() => {
                return Ok(SendOutcome {
                    sent: true,
                    stub: false,
                });
            }
            Ok(resp) => {
                last_status = resp.status().as_u16();
            }
            Err(_) => { /* 网络错误重试 */ }
        }
        if backoff.is_empty() {
            break;
        }
    }
    tracing::error!("[mail] Resend 发送失败 to={to} status={last_status}");
    Err(crate::service::SvcError::code(50002))
}
