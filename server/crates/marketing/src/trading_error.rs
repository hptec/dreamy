//! trading 域 6 位错误码全表(域段 6;对齐 Java TradingErrorCode,33 码)。
//! wire 语义:HTTP(3位) + 域段(1位=6) + 序号(2位)。

/// (code, http_status) 全表;message key = error.{code} 由 common::i18n 解析
pub const CODES: &[(i32, u16)] = &[
    (401601, 401),
    (404601, 404),
    (404602, 404),
    (404603, 404),
    (404604, 404),
    (404605, 404),
    (404906, 404),
    (404907, 404),
    (409601, 409),
    (409602, 409),
    (409603, 409),
    (409604, 409),
    (409605, 409),
    (409905, 409),
    (409906, 409),
    (409907, 409),
    (409908, 409),
    (409909, 409),
    (410601, 410),
    (422601, 422),
    (422602, 422),
    (422603, 422),
    (422604, 422),
    (422605, 422),
    (422906, 422),
    (422907, 422),
    (422908, 422),
    (429601, 429),
    (502601, 502),
    (502602, 502),
    (500601, 500),
    (500602, 500),
    (500603, 500),
];

/// code → HTTP status(表驱动;未登记回退 500)
pub fn http_status(code: i32) -> u16 {
    CODES
        .iter()
        .find(|(c, _)| *c == code)
        .map(|(_, h)| *h)
        .unwrap_or(500)
}

/// 业务错误(trading 域;api 层统一转 R 包络)
#[derive(Debug, Clone)]
pub struct TradingError {
    pub code: i32,
    /// 422601 字段级 details { fields: {...} }
    pub details: Option<serde_json::Value>,
}

impl TradingError {
    pub fn new(code: i32) -> Self {
        TradingError { code, details: None }
    }

    pub fn field_validation(fields: &[(&'static str, &'static str)]) -> Self {
        let map: serde_json::Map<String, serde_json::Value> = fields
            .iter()
            .map(|(f, r)| (f.to_string(), serde_json::json!(r)))
            .collect();
        TradingError {
            code: 422601,
            details: Some(serde_json::json!({ "fields": map })),
        }
    }
}

pub const ADDRESS_NOT_FOUND: i32 = 404602;
pub const WISHLIST_ITEM_NOT_FOUND: i32 = 404604;
pub const PRODUCT_NOT_FOUND_CATALOG: i32 = 404601;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn http_mapping_matches_high_digits() {
        assert_eq!(http_status(404602), 404);
        assert_eq!(http_status(422601), 422);
        assert_eq!(http_status(409909), 409);
        assert_eq!(http_status(429601), 429);
        assert_eq!(http_status(999999), 500, "未登记回退 500");
    }

    #[test]
    fn field_validation_shape() {
        let e = TradingError::field_validation(&[("receiver", "required"), ("zip", "too_long")]);
        assert_eq!(e.code, 422601);
        assert_eq!(e.details.unwrap()["fields"]["receiver"], "required");
    }
}
