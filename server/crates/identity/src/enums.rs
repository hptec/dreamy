//! 身份域枚举——数值与 Java `com.dreamy.enums` 一字不差(tinyint 契约,前端零改动前提)。
//! 经 `common::int_enum!` 获得 code/describe/from_code 与 int 序列化(IntEnum/Describe 同构)。

common::int_enum! {
    /// 登录方式提供方(AuthProvider)
    AuthProvider: i32 {
        /// 邮箱验证码
        Email = 1 => "邮箱",
        /// Google OIDC
        Google = 2 => "Google",
        /// Apple OIDC
        Apple = 3 => "Apple",
    }
}

common::int_enum! {
    /// 用户状态(UserStatus)
    UserStatus: i32 {
        /// 正常
        Active = 1 => "正常",
        /// 已禁用
        Disabled = 2 => "已禁用",
        /// 已删除(软删,待匿名化)
        Deleted = 3 => "已删除",
        /// 已匿名化(PII 已不可逆覆写)
        Anonymized = 4 => "已匿名化",
    }
}

common::int_enum! {
    /// 用户等级(UserTier)
    UserTier: i32 {
        /// 常规
        Regular = 1 => "常规",
        /// VIP
        Vip = 2 => "VIP",
    }
}

common::int_enum! {
    /// 会话状态(SessionStatus,store/admin 通用)
    SessionStatus: i32 {
        /// 有效
        Active = 1 => "有效",
        /// 已撤销
        Revoked = 2 => "已撤销",
    }
}

common::int_enum! {
    /// 验证码状态(OtpStatus)
    OtpStatus: i32 {
        /// 待验证
        Pending = 1 => "待验证",
        /// 已消费(校验通过)
        Consumed = 2 => "已消费",
        /// 已过期
        Expired = 3 => "已过期",
        /// 已锁定(错误次数超限)
        Locked = 4 => "已锁定",
    }
}

common::int_enum! {
    /// 管理员状态(AdminStatus)
    AdminStatus: i32 {
        /// 正常
        Active = 1 => "正常",
        /// 已禁用
        Disabled = 2 => "已禁用",
    }
}

common::int_enum! {
    /// 登录结果(LoginOutcome)
    LoginOutcome: i32 {
        /// 成功
        Success = 1 => "成功",
        /// 失败
        Failed = 2 => "失败",
    }
}

common::int_enum! {
    /// 角色类型(RoleType)
    RoleType: i32 {
        /// 系统预设(不可删改)
        System = 1 => "系统预设",
        /// 自定义
        Custom = 2 => "自定义",
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn codes_match_java_contract() {
        // 数值与 Java 枚举 tinyint 契约逐项对齐(变更即破坏存量数据,禁止改动)
        assert_eq!(AuthProvider::Email.code(), 1);
        assert_eq!(AuthProvider::Google.code(), 2);
        assert_eq!(AuthProvider::Apple.code(), 3);
        assert_eq!(UserStatus::Active.code(), 1);
        assert_eq!(UserStatus::Disabled.code(), 2);
        assert_eq!(UserStatus::Deleted.code(), 3);
        assert_eq!(UserStatus::Anonymized.code(), 4);
        assert_eq!(UserTier::Regular.code(), 1);
        assert_eq!(UserTier::Vip.code(), 2);
        assert_eq!(SessionStatus::Active.code(), 1);
        assert_eq!(SessionStatus::Revoked.code(), 2);
        assert_eq!(OtpStatus::Pending.code(), 1);
        assert_eq!(OtpStatus::Consumed.code(), 2);
        assert_eq!(OtpStatus::Expired.code(), 3);
        assert_eq!(OtpStatus::Locked.code(), 4);
        assert_eq!(AdminStatus::Active.code(), 1);
        assert_eq!(AdminStatus::Disabled.code(), 2);
        assert_eq!(LoginOutcome::Success.code(), 1);
        assert_eq!(LoginOutcome::Failed.code(), 2);
        assert_eq!(RoleType::System.code(), 1);
        assert_eq!(RoleType::Custom.code(), 2);
    }

    #[test]
    fn serde_as_int() {
        assert_eq!(serde_json::to_string(&UserStatus::Disabled).unwrap(), "2");
        let parsed: UserStatus = serde_json::from_str("4").unwrap();
        assert_eq!(parsed, UserStatus::Anonymized);
        assert!(serde_json::from_str::<UserStatus>("99").is_err());
    }

    #[test]
    fn describe_and_from_code() {
        assert_eq!(OtpStatus::Locked.describe(), "已锁定");
        assert_eq!(UserStatus::from_code(3), Some(UserStatus::Deleted));
        assert_eq!(UserStatus::from_code(99), None);
    }
}
