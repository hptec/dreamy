//! REST 层(对齐 StoreLeadController):3 个公开 POST 端点,WAF 限流在网关层(决策 11),
//! 不缓存、不发 MQ、不写 OperationLog。
//!
//! 契约:
//! - POST /api/store/newsletter            → 200 {code:0,data:{subscribed:true}}(不泄露存在性)
//! - POST /api/store/newsletter/unsubscribe → 200 {code:0,data:{unsubscribed:true}}(token 无效 → 422704)
//! - POST /api/store/contact               → 201 {code:0,data:{submitted:true}}

use axum::extract::State;
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;
use serde_json::json;

use common::error::ErrorCode;
use common::state::SharedState;

use crate::service::{self, FieldErrors, UnsubscribeError};

/// 唯一错误位置(error_site! 注册表,全局唯一断言由 common 测试覆盖)
common::error_site!(SITE_NEWSLETTER_SUBSCRIBE = "marketing/newsletter/subscribe");
common::error_site!(SITE_NEWSLETTER_UNSUBSCRIBE = "marketing/newsletter/unsubscribe");
common::error_site!(SITE_CONTACT_SUBMIT = "marketing/contact/submit");

#[derive(Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct NewsletterRequest {
    pub email: Option<String>,
    /// Java 侧 Integer:缺字段与 null 同为 None
    pub source: Option<i64>,
    pub locale: Option<String>,
}

/// 订阅:恒 200 {subscribed:true}(无论新增/重复/复活)
pub async fn subscribe(
    State(state): State<SharedState>,
    Json(req): Json<NewsletterRequest>,
) -> Response {
    let cfg = marketing_config(&state);
    match service::subscribe(&state.db, req.email.as_deref(), req.source, req.locale.as_deref()).await {
        Ok(()) => (StatusCode::OK, Json(json!({"code": 0, "message": null, "service_id": null, "data": {"subscribed": true}}))).into_response(),
        Err(errors) => field_validation_422(errors, SITE_NEWSLETTER_SUBSCRIBE, cfg),
    }
}

#[derive(Deserialize)]
pub struct UnsubscribeRequest {
    pub token: Option<String>,
}

/// 退订:幂等 200;token 无效/过期/代际落后 → 422704 field=token
pub async fn unsubscribe(
    State(state): State<SharedState>,
    Json(req): Json<UnsubscribeRequest>,
) -> Response {
    let cfg = marketing_config(&state);
    match service::unsubscribe(&state.db, &cfg.unsubscribe_secret, req.token.as_deref()).await {
        Ok(()) => (StatusCode::OK, Json(json!({"code": 0, "message": null, "service_id": null, "data": {"unsubscribed": true}}))).into_response(),
        Err(UnsubscribeError::InvalidToken) => {
            let mut fe = FieldErrors::default();
            fe.reject("token", "invalid_or_expired");
            field_validation_422(fe, SITE_NEWSLETTER_UNSUBSCRIBE, cfg)
        }
        Err(UnsubscribeError::Infra) => internal_error(SITE_NEWSLETTER_UNSUBSCRIBE),
    }
}

#[derive(Deserialize)]
pub struct ContactRequest {
    pub name: Option<String>,
    pub email: Option<String>,
    pub subject: Option<String>,
    pub message: Option<String>,
}

/// 提交联系表单:恒 201 {submitted:true}
pub async fn submit_contact(
    State(state): State<SharedState>,
    Json(req): Json<ContactRequest>,
) -> Response {
    match service::submit_contact(
        &state.db,
        req.name.as_deref(),
        req.email.as_deref(),
        req.subject.as_deref(),
        req.message.as_deref(),
    )
    .await
    {
        Ok(()) => (StatusCode::CREATED, Json(json!({"code": 0, "message": null, "service_id": null, "data": {"submitted": true}}))).into_response(),
        Err(errors) => {
            // contact 无独立配置依赖;内部错误同样 50000
            if errors.has() && errors.to_details()["fields"].get("__internal__").is_some() {
                internal_error(SITE_CONTACT_SUBMIT)
            } else {
                field_validation_422(errors, SITE_CONTACT_SUBMIT, marketing_config(&state))
            }
        }
    }
}

// ────────────────────────── 内部 ──────────────────────────

#[derive(Clone)]
struct MktConfig {
    unsubscribe_secret: String,
}

fn marketing_config(state: &SharedState) -> MktConfig {
    // 配置经 state 扩展字段注入(P1 接线时落到 AppState;此处从 env 直读,启动已 fail-fast)
    let _ = state;
    MktConfig {
        unsubscribe_secret: std::env::var("NEWSLETTER_UNSUBSCRIBE_SECRET").unwrap_or_default(),
    }
}

/// 422704 字段级包络:code=422704,message=i18n key error.422704,data={fields:{...}}
fn field_validation_422(errors: FieldErrors, site: &str, _cfg: MktConfig) -> Response {
    let details = errors.to_details();
    tracing::warn!(site, code = 422704, "[mkt] field validation failed");
    let body = json!({
        "code": 422704,
        "message": common::i18n::message(422704, common::i18n::Locale::En),
        "service_id": null,
        "data": details,
    });
    (StatusCode::UNPROCESSABLE_ENTITY, Json(body)).into_response()
}

/// 50000 兜底(基础设施失败;Java 侧 GlobalExceptionHandler 同构)
fn internal_error(site: &'static str) -> Response {
    let b = common::error::BizError::new(site, ErrorCode::Internal);
    b.into_response()
}

/// 路由挂载(由 server crate router 调用;路径与 Java 逐字一致)。
/// 返回已物化的 Router(内部 with_state),与 identity::api 同风格。
pub fn router(state: SharedState) -> axum::Router {
    use axum::routing::post;
    axum::Router::new()
        .route("/api/store/newsletter", post(subscribe))
        .route("/api/store/newsletter/unsubscribe", post(unsubscribe))
        .route("/api/store/contact", post(submit_contact))
        .with_state(state)
}
