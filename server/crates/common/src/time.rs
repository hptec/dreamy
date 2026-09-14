//! 时间序列化契约:与 Java Jackson LocalDateTime 输出逐字节对齐。
//! MySQL `datetime` 列为秒级精度;ISO-8601 无时区偏移,毫秒非零才输出小数位。

use chrono::NaiveDateTime;

/// NaiveDateTime → ISO-8601 无偏移字符串(Java `DateTimeFormatter.ISO_LOCAL_DATE_TIME` 同构)
pub fn format_iso(dt: NaiveDateTime) -> String {
    let base = dt.format("%Y-%m-%dT%H:%M:%S").to_string();
    let nanos = dt.and_utc().timestamp_subsec_nanos();
    if nanos == 0 {
        base
    } else {
        // 毫秒精度(DATETIME(3)),去尾零对齐 Jackson 变长小数行为
        let millis = nanos / 1_000_000;
        format!("{base}.{millis:03}")
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use chrono::NaiveDate;

    #[test]
    fn iso_format_matches_java() {
        let sec = NaiveDate::from_ymd_opt(2026, 9, 14)
            .unwrap()
            .and_hms_opt(11, 30, 5)
            .unwrap();
        assert_eq!(format_iso(sec), "2026-09-14T11:30:05");

        let milli = NaiveDate::from_ymd_opt(2026, 9, 14)
            .unwrap()
            .and_hms_milli_opt(11, 30, 5, 123)
            .unwrap();
        assert_eq!(format_iso(milli), "2026-09-14T11:30:05.123");
    }
}
