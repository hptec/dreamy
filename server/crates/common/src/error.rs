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
use thiserror::Error;

use crate::i18n::Locale;

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
    AdminLoginLocked,        // 42903 → 429
    VerifyRateLimited,       // 42904 → 429(verify OTP IP 频控)
    VerifyBackoff,           // 42905 → 429(verify OTP 失败递进退避)
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
            Self::AdminLoginLocked => 42903,
            Self::VerifyRateLimited => 42904,
            Self::VerifyBackoff => 42905,
            Self::Internal => 50000,
            Self::Database => 50001,
            Self::EmailSendFailed => 50002,
            Self::OidcUnavailable => 50201,
            Self::OidcTimeout => 50401,
        }
    }

    /// 数值码 → 枚举(REST 层映射 SvcError::Code 用;未知值 None → 调用方兜底 Internal)
    pub fn from_wire(code: i32) -> Option<Self> {
        Some(match code {
            40000 => Self::Validation,
            40001 => Self::InvalidEmail,
            40002 => Self::ConfigOutOfRange,
            40010 => Self::BadRequestBody,
            40100 => Self::Unauthorized,
            40101 => Self::OtpInvalid,
            40102 => Self::RefreshInvalid,
            40103 => Self::CredentialsInvalid,
            40300 => Self::Forbidden,
            40301 => Self::AccountDisabled,
            40302 => Self::AdminDisabled,
            40303 => Self::ProviderDisabled,
            40304 => Self::PrimaryEmailRequired,
            40305 => Self::MinMethodsRequired,
            40306 => Self::SuperAdminProtected,
            40307 => Self::CannotDeleteSelf,
            40308 => Self::RoleLocked,
            40400 => Self::NotFound,
            40500 => Self::MethodNotAllowed,
            40901 => Self::EmailExists,
            40902 => Self::EmailConflictUnverified,
            40903 => Self::IdentityTaken,
            40904 => Self::RoleInUse,
            41001 => Self::OtpExpired,
            41002 => Self::OtpLocked,
            42901 => Self::ResendTooSoon,
            42902 => Self::RateLimited,
            42903 => Self::AdminLoginLocked,
            42904 => Self::VerifyRateLimited,
            42905 => Self::VerifyBackoff,
            50000 => Self::Internal,
            50001 => Self::Database,
            50002 => Self::EmailSendFailed,
            50201 => Self::OidcUnavailable,
            50401 => Self::OidcTimeout,
            _ => return None,
        })
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
            Self::ResendTooSoon | Self::RateLimited | Self::AdminLoginLocked => {
                StatusCode::TOO_MANY_REQUESTS
            }
            Self::VerifyRateLimited | Self::VerifyBackoff => StatusCode::TOO_MANY_REQUESTS,
            Self::Internal | Self::Database | Self::EmailSendFailed => {
                StatusCode::INTERNAL_SERVER_ERROR
            }
            Self::OidcUnavailable => StatusCode::BAD_GATEWAY,
            Self::OidcTimeout => StatusCode::GATEWAY_TIMEOUT,
        }
    }
}

/// 业务错误:details(如校验字段错误映射)放入 R.data。
/// `site` 是**唯一错误位置标识**(用户指令:错误能正确反映唯一出错的代码地点),
/// 经 [`crate::error_site!`] 宏声明——注册进全局表,测试断言全局唯一。
#[derive(Debug)]
pub struct BizError {
    pub code: ErrorCode,
    pub site: &'static str,
    pub message: Option<String>,
    pub details: Option<serde_json::Value>,
    /// 响应消息语言(store=Accept-Language;admin 固定 zh)
    pub locale: &'static str,
}

/// 错误位置注册表项(inventory 收集,测试断言全局唯一)
pub struct ErrorSite(pub &'static str);

inventory::collect!(ErrorSite);

/// 声明一个唯一错误位置标识(同时登记进全局注册表)。
/// 约定命名:`<domain>/<模块>/<位置>`,如 `identity/otp/consume_valid_code`。
#[macro_export]
macro_rules! error_site {
    ($(#[$m:meta])* $vis:vis $name:ident = $val:literal) => {
        $(#[$m])*
        $vis const $name: &str = $val;
        ::inventory::submit! { $crate::error::ErrorSite($val) }
    };
}

impl BizError {
    pub fn new(site: &'static str, code: ErrorCode) -> Self {
        BizError {
            code,
            site,
            message: None,
            details: None,
            locale: "en",
        }
    }

    /// 响应语言(store 端点按 Accept-Language 注入;admin 用 zh)
    pub fn with_locale(mut self, locale: &'static str) -> Self {
        self.locale = locale;
        self
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
        // 仅测试/骨架便捷路径;业务代码必须显式传 site 定位错误点
        BizError::new("common/anonymous", code)
    }
}

/// i18n 消息解析(key=error.{code});消息层与响应解耦:
/// BizError 携带 locale(过滤器/中间件按 Accept-Language 注入),响应时解析
fn resolve_message(code: i32, locale: &str) -> Option<String> {
    let loc = match locale {
        "es" => crate::i18n::Locale::Es,
        "fr" => crate::i18n::Locale::Fr,
        "zh" => crate::i18n::Locale::Zh,
        _ => crate::i18n::Locale::En,
    };
    crate::i18n::message(code, loc)
}

impl IntoResponse for BizError {
    fn into_response(self) -> Response {
        // message 优先级:显式消息 > i18n > 缺省 null(Java 侧 i18n 缺 key 时 message=null)
        let message = self
            .message
            .or_else(|| resolve_message(self.code.code(), self.locale));
        // 对齐 Java GlobalExceptionHandler 日志口径:4xx 客户端类不打日志,5xx WARN
        // 日志含 site(唯一错误位置)+ code,配合 X-Request-Id 可精确定位出错代码点
        if self.code.http().is_server_error() {
            tracing::warn!(
                site = self.site,
                code = self.code.code(),
                "[error] 服务端错误"
            );
        }
        let body = R::<serde_json::Value> {
            code: self.code.code(),
            message,
            service_id: None,
            data: self.details,
        };
        (self.code.http(), Json(body)).into_response()
    }
}

// ══════════════════ 领域服务错误(全域 crate 共用的 service 层契约) ══════════════════

/// service 层错误(REST 层映射:NotFound→40400,InvalidArg→40000,Code→对应业务码,Infra→50001;
/// gRPC 层映射:NotFound→NOT_FOUND,InvalidArg→INVALID_ARGUMENT,其余→UNAVAILABLE)
/// DbErr 装箱:控制错误体积(Rust 惯例),`?` 自动转换不受影响
#[derive(Debug, Error)]
pub enum SvcError {
    #[error("未命中")]
    NotFound,
    #[error("非法参数: {0}")]
    InvalidArg(String),
    /// 业务错误码(wire 契约值,如 40101/40902/42901)+ REST details(gRPC 查询路径不产生此变体)
    #[error("业务错误 {code}")]
    Code {
        code: i32,
        details: Option<serde_json::Value>,
    },
    #[error("基础设施错误: {0}")]
    Infra(#[from] Box<sea_orm::DbErr>),
}

impl SvcError {
    /// 业务码便捷构造(details 缺省 None)
    pub fn code(code: i32) -> Self {
        SvcError::Code {
            code,
            details: None,
        }
    }

    /// 业务码 + details(如 40101 remaining_attempts)
    pub fn code_with(code: i32, details: serde_json::Value) -> Self {
        SvcError::Code {
            code,
            details: Some(details),
        }
    }

    /// wire 业务码(仅 Code 变体有;调用方按码分流逻辑用,如 verify 失败计数)
    pub fn biz_code(&self) -> Option<i32> {
        match self {
            SvcError::Code { code, .. } => Some(*code),
            _ => None,
        }
    }
}

impl From<sea_orm::DbErr> for SvcError {
    fn from(err: sea_orm::DbErr) -> Self {
        SvcError::Infra(Box::new(err))
    }
}

/// SvcError → BizError(REST 映射契约:NotFound→40400,InvalidArg→40000,
/// Code→业务码(from_wire,未知兜底 50000),Infra→50001)。
/// store/admin 及未来全部域 crate 的 api 层共用此单份映射。
pub fn svc_to_biz(site: &'static str, locale: Locale, err: SvcError) -> BizError {
    let locale = locale.code();
    let biz = match err {
        SvcError::NotFound => BizError::new(site, ErrorCode::NotFound),
        SvcError::InvalidArg(msg) => {
            let mut b = BizError::new(site, ErrorCode::Validation).with_message(msg);
            b.locale = locale;
            return b;
        }
        SvcError::Code { code, details } => {
            // 业务码 → ErrorCode 枚举(数值同构);未知码兜底 50000
            let ec = ErrorCode::from_wire(code).unwrap_or(ErrorCode::Internal);
            let mut b = BizError::new(site, ec);
            if let Some(d) = details {
                b = b.with_details(d);
            }
            b.locale = locale;
            return b;
        }
        SvcError::Infra(source) => {
            tracing::error!(error = %source, site, "[svc] 基础设施错误");
            BizError::new(site, ErrorCode::Database)
        }
    };
    let mut b = biz;
    b.locale = locale;
    b
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn svc_error_rest_mapping() {
        let f = |e: SvcError, code: ErrorCode| {
            let b = svc_to_biz("t/one", Locale::En, e);
            assert_eq!(b.code, code);
            b
        };
        f(SvcError::NotFound, ErrorCode::NotFound);
        let b = f(SvcError::InvalidArg("x".into()), ErrorCode::Validation);
        assert_eq!(b.message.as_deref(), Some("x"));
        let b = f(SvcError::code(40103), ErrorCode::CredentialsInvalid);
        assert_eq!(b.locale, "en");
        // 未知业务码兜底 50000
        f(SvcError::code(99999), ErrorCode::Internal);
    }

    #[test]
    fn error_sites_globally_unique() {
        let mut sites: Vec<&'static str> = inventory::iter::<ErrorSite>
            .into_iter()
            .map(|s| s.0)
            .collect();
        sites.sort_unstable();
        let before = sites.len();
        sites.dedup();
        assert_eq!(before, sites.len(), "存在重复 error site: {sites:?}");
        // 骨架期至少已有兜底 site 注册(identity 域随 P1-P3 增量登记)
        assert!(!sites.is_empty(), "error site 注册表为空");
    }

    #[test]
    fn error_code_http_mapping() {
        assert_eq!(
            ErrorCode::Validation.http(),
            StatusCode::UNPROCESSABLE_ENTITY
        );
        assert_eq!(ErrorCode::RefreshInvalid.http(), StatusCode::UNAUTHORIZED);
        assert_eq!(ErrorCode::RoleLocked.http(), StatusCode::FORBIDDEN);
        assert_eq!(ErrorCode::OtpExpired.http(), StatusCode::GONE);
        assert_eq!(
            ErrorCode::ResendTooSoon.http(),
            StatusCode::TOO_MANY_REQUESTS
        );
        assert_eq!(ErrorCode::OidcUnavailable.http(), StatusCode::BAD_GATEWAY);
        assert_eq!(ErrorCode::OidcTimeout.http(), StatusCode::GATEWAY_TIMEOUT);
    }
}
