//! 对外 REST 契约核心:R 响应包络 + ErrorCode 全表(HTTP 映射逐项对齐 Java
//! backend/src/main/java/com/dreamy/error/ErrorCode.java 与 GlobalExceptionHandler)。
//!
//! 契约不变量:
//! - 包络字段顺序恒为 code,message,service_id,data;成功 code=0 且三个 nullable 字段必须输出 null。
//! - 错误 details 放入 data 字段(Java 侧同构,无独立 details 字段)。
//! - 错误 message 经 i18n(key=error.{code})解析;过滤器级 401 短路体不走本包络(P2 实现时复刻)。

use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Serialize;

/// huihao.web.R 的 Rust 同构体(serde 按声明序序列化,与 Jackson 字段序一致)
#[derive(Serialize, Debug)]
pub struct R<T> {
    pub code: i32,
    pub message: Option<String>,
    pub service_id: Option<String>,
    pub data: Option<T>,
}

impl<T> R<T> {
    /// 成功响应(P2 起业务端点使用)
    #[allow(dead_code)]
    pub fn ok(data: Option<T>) -> Self {
        R {
            code: 0,
            message: None,
            service_id: None,
            data,
        }
    }
}

/// 错误码全表:数值 ↔ HTTP 状态(与 Java ErrorCode 一字不差)
/// (P0 骨架阶段部分码未使用,P2/P3 业务端点逐个启用,故整表保留)
#[allow(dead_code)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ErrorCode {
    Validation,              // 40000 → 422
    InvalidEmail,            // 40001 → 422
    ConfigOutOfRange,        // 40002 → 422
    BadRequestBody,          // 40010 → 400
    Unauthorized,            // 40100 → 401
    OtpInvalid,              // 40101 → 401
    RefreshInvalid,          // 40102 → 401
    CredentialsInvalid,      // 40103 → 401
    Forbidden,               // 40300 → 403
    AccountDisabled,         // 40301 → 403
    AdminDisabled,           // 40302 → 403
    ProviderDisabled,        // 40303 → 403
    PrimaryEmailRequired,    // 40304 → 403
    MinMethodsRequired,      // 40305 → 403
    SuperAdminProtected,     // 40306 → 403
    CannotDeleteSelf,        // 40307 → 403
    RoleLocked,              // 40308 → 403
    NotFound,                // 40400 → 404
    MethodNotAllowed,        // 40500 → 405
    EmailExists,             // 40901 → 409
    EmailConflictUnverified, // 40902 → 409
    IdentityTaken,           // 40903 → 409
    RoleInUse,               // 40904 → 409
    OtpExpired,              // 41001 → 410
    OtpLocked,               // 41002 → 410
    ResendTooSoon,           // 42901 → 429
    RateLimited,             // 42902 → 429
    Internal,                // 50000 → 500
    Database,                // 50001 → 500
    EmailSendFailed,         // 50002 → 500
    OidcUnavailable,         // 50201 → 502
    OidcTimeout,             // 50401 → 504
}

impl ErrorCode {
    pub fn code(&self) -> i32 {
        match self {
            Self::Validation => 40000,
            Self::InvalidEmail => 40001,
            Self::ConfigOutOfRange => 40002,
            Self::BadRequestBody => 40010,
            Self::Unauthorized => 40100,
            Self::OtpInvalid => 40101,
            Self::RefreshInvalid => 40102,
            Self::CredentialsInvalid => 40103,
            Self::Forbidden => 40300,
            Self::AccountDisabled => 40301,
            Self::AdminDisabled => 40302,
            Self::ProviderDisabled => 40303,
            Self::PrimaryEmailRequired => 40304,
            Self::MinMethodsRequired => 40305,
            Self::SuperAdminProtected => 40306,
            Self::CannotDeleteSelf => 40307,
            Self::RoleLocked => 40308,
            Self::NotFound => 40400,
            Self::MethodNotAllowed => 40500,
            Self::EmailExists => 40901,
            Self::EmailConflictUnverified => 40902,
            Self::IdentityTaken => 40903,
            Self::RoleInUse => 40904,
            Self::OtpExpired => 41001,
            Self::OtpLocked => 41002,
            Self::ResendTooSoon => 42901,
            Self::RateLimited => 42902,
            Self::Internal => 50000,
            Self::Database => 50001,
            Self::EmailSendFailed => 50002,
            Self::OidcUnavailable => 50201,
            Self::OidcTimeout => 50401,
        }
    }

    pub fn http(&self) -> StatusCode {
        match self {
            Self::Validation | Self::InvalidEmail | Self::ConfigOutOfRange => {
                StatusCode::UNPROCESSABLE_ENTITY
            }
            Self::BadRequestBody => StatusCode::BAD_REQUEST,
            Self::Unauthorized
            | Self::OtpInvalid
            | Self::RefreshInvalid
            | Self::CredentialsInvalid => StatusCode::UNAUTHORIZED,
            Self::Forbidden
            | Self::AccountDisabled
            | Self::AdminDisabled
            | Self::ProviderDisabled
            | Self::PrimaryEmailRequired
            | Self::MinMethodsRequired
            | Self::SuperAdminProtected
            | Self::CannotDeleteSelf
            | Self::RoleLocked => StatusCode::FORBIDDEN,
            Self::NotFound => StatusCode::NOT_FOUND,
            Self::MethodNotAllowed => StatusCode::METHOD_NOT_ALLOWED,
            Self::EmailExists
            | Self::EmailConflictUnverified
            | Self::IdentityTaken
            | Self::RoleInUse => StatusCode::CONFLICT,
            Self::OtpExpired | Self::OtpLocked => StatusCode::GONE,
            Self::ResendTooSoon | Self::RateLimited => StatusCode::TOO_MANY_REQUESTS,
            Self::Internal | Self::Database | Self::EmailSendFailed => {
                StatusCode::INTERNAL_SERVER_ERROR
            }
            Self::OidcUnavailable => StatusCode::BAD_GATEWAY,
            Self::OidcTimeout => StatusCode::GATEWAY_TIMEOUT,
        }
    }
}

/// 业务错误:details(如校验字段错误映射)放入 R.data
#[derive(Debug)]
pub struct BizError {
    pub code: ErrorCode,
    pub message: Option<String>,
    pub details: Option<serde_json::Value>,
}

impl BizError {
    #[allow(dead_code)]
    pub fn new(code: ErrorCode) -> Self {
        BizError {
            code,
            message: None,
            details: None,
        }
    }

    #[allow(dead_code)]
    pub fn with_details(mut self, details: serde_json::Value) -> Self {
        self.details = Some(details);
        self
    }

    #[allow(dead_code)]
    pub fn with_message(mut self, message: impl Into<String>) -> Self {
        self.message = Some(message.into());
        self
    }
}

impl From<ErrorCode> for BizError {
    fn from(code: ErrorCode) -> Self {
        BizError::new(code)
    }
}

/// i18n 消息解析(key=error.{code});语言包 P2 从 Java messages_*.properties 移植接入
fn resolve_message(code: i32, _locale: &str) -> Option<String> {
    let _ = code;
    None
}

impl IntoResponse for BizError {
    fn into_response(self) -> Response {
        // message 优先级:显式消息 > i18n > 缺省 null(Java 侧 i18n 缺 key 时 message=null)
        let message = self
            .message
            .or_else(|| resolve_message(self.code.code(), "en"));
        let body = R::<serde_json::Value> {
            code: self.code.code(),
            message,
            service_id: None,
            data: self.details,
        };
        (self.code.http(), Json(body)).into_response()
    }
}
