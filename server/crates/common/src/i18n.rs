//! i18n 消息解析(key=error.{code};properties 文件从 Java resources 同源拷贝,UTF-8)。
//! locale 路由:store 按 Accept-Language(en/es/fr,缺省 en);admin 固定 zh(错误消息层)。

use std::collections::HashMap;
use std::sync::OnceLock;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Locale {
    En,
    Es,
    Fr,
    Zh,
}

impl Locale {
    pub fn code(self) -> &'static str {
        match self {
            Locale::En => "en",
            Locale::Es => "es",
            Locale::Fr => "fr",
            Locale::Zh => "zh",
        }
    }

    /// Accept-Language 首选匹配(en/es/fr;admin 侧调用方直接传 Zh)
    pub fn from_accept_language(header: Option<&str>) -> Locale {
        let Some(header) = header else {
            return Locale::En;
        };
        let tag = header.split(',').next().unwrap_or("").trim();
        let prefix = tag.get(..2).unwrap_or("").to_lowercase();
        match prefix.as_str() {
            "es" => Locale::Es,
            "fr" => Locale::Fr,
            _ => Locale::En,
        }
    }
}

/// properties 解析(本文件仅 key=value 行与 # 注释,无转义陷阱)
fn parse_properties(raw: &str) -> HashMap<String, String> {
    raw.lines()
        .filter_map(|line| {
            let trimmed = line.trim();
            if trimmed.is_empty() || trimmed.starts_with('#') {
                return None;
            }
            let (k, v) = trimmed.split_once('=')?;
            Some((k.trim().to_string(), v.trim().to_string()))
        })
        .collect()
}

fn table(locale: Locale) -> &'static HashMap<String, String> {
    static TABLES: OnceLock<HashMap<Locale, HashMap<String, String>>> = OnceLock::new();
    TABLES
        .get_or_init(|| {
            let mut all = HashMap::new();
            macro_rules! load {
                ($loc:expr, $file:expr) => {
                    all.insert(
                        $loc,
                        parse_properties(include_str!(concat!("i18n/", $file))),
                    );
                };
            }
            load!(Locale::En, "messages_en.properties");
            load!(Locale::Es, "messages_es.properties");
            load!(Locale::Fr, "messages_fr.properties");
            load!(Locale::Zh, "messages_zh.properties");
            all
        })
        .get(&locale)
        .unwrap()
}

/// 解析错误消息(key=error.{code});缺 key → None(与 Java 行为一致:message=null)
pub fn message(code: i32, locale: Locale) -> Option<String> {
    table(locale)
        .get(&format!("error.{code}"))
        .cloned()
        .or_else(|| table(Locale::En).get(&format!("error.{code}")).cloned())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn resolves_all_identity_codes() {
        // identity 域全部错误码四语言齐备(缺一即 wire 契约漂移)
        for code in [
            40000, 40001, 40002, 40010, 40100, 40101, 40102, 40103, 40300, 40301, 40302, 40303,
            40304, 40305, 40306, 40307, 40308, 40400, 40500, 40901, 40902, 40903, 40904, 41001,
            41002, 42901, 42902, 42903, 42904, 42905, 50000, 50001, 50002, 50201, 50401,
        ] {
            for loc in [Locale::En, Locale::Es, Locale::Fr, Locale::Zh] {
                assert!(
                    message(code, loc).is_some(),
                    "缺 error.{code} 的 {loc:?} 文案"
                );
            }
        }
    }

    #[test]
    fn zh_message_content() {
        assert_eq!(message(40101, Locale::Zh).unwrap(), "验证码错误");
        assert_eq!(
            message(40101, Locale::En).unwrap(),
            "Incorrect verification code"
        );
    }

    #[test]
    fn accept_language_parsing() {
        assert_eq!(
            Locale::from_accept_language(Some("es-ES,es;q=0.9")),
            Locale::Es
        );
        assert_eq!(Locale::from_accept_language(Some("fr-FR")), Locale::Fr);
        assert_eq!(Locale::from_accept_language(Some("zh-CN")), Locale::En); // store 只认 en/es/fr
        assert_eq!(Locale::from_accept_language(None), Locale::En);
    }
}
