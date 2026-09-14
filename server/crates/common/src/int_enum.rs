//! Java IntEnum/Describe 同构的枚举机制。
//!
//! - `code()`/`from_code()`:数值 ↔ 变体(与 Java 枚举 tinyint 契约一字不差,未知值 None)
//! - `describe()`:中文描述(对齐 Java Describe 注解)
//! - serde 以 **int** 序列化/反序列化(与 Jackson 枚举整数输出一致,前端零改动前提)
//! - `ALL`:全量变量表(下拉/校验用)
//!
//! 用法(域 crate):
//! ```ignore
//! common::int_enum! {
//!     /// 用户状态
//!     UserStatus: i32 {
//!         Active = 1 => "正常",
//!         Disabled = 2 => "已禁用",
//!     }
//! }
//! ```

/// 声明宏:生成 IntEnum/Describe 同构枚举(见模块文档)
#[macro_export]
macro_rules! int_enum {
    (
        $(#[$meta:meta])*
        $name:ident : $repr:ty {
            $(
                $(#[$vmeta:meta])*
                $variant:ident = $value:expr => $desc:expr
            ),+ $(,)?
        }
    ) => {
        $(#[$meta])*
        #[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
        #[repr($repr)]
        pub enum $name {
            $(
                $(#[$vmeta])*
                $variant = $value
            ),+
        }

        impl $name {
            /// 全量变体(声明序)
            pub const ALL: &'static [$name] = &[$($name::$variant),+];

            /// 数值码(与 Java IntEnum 一致)
            pub const fn code(self) -> $repr {
                self as $repr
            }

            /// 中文描述(与 Java Describe 一致)
            pub const fn describe(self) -> &'static str {
                match self {
                    $($name::$variant => $desc),+
                }
            }

            /// 数值 → 变体;未知值返回 None(调用方按业务处理,不 panic)
            pub const fn from_code(code: $repr) -> Option<Self> {
                match code {
                    $($value => Some($name::$variant),)+
                    _ => None,
                }
            }
        }

        impl ::core::fmt::Display for $name {
            fn fmt(&self, f: &mut ::core::fmt::Formatter<'_>) -> ::core::fmt::Result {
                f.write_str(self.describe())
            }
        }

        impl ::serde::Serialize for $name {
            fn serialize<S: ::serde::Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
                serializer.serialize_i32(self.code() as i32)
            }
        }

        impl<'de> ::serde::Deserialize<'de> for $name {
            fn deserialize<D: ::serde::Deserializer<'de>>(deserializer: D) -> Result<Self, D::Error> {
                let code = i32::deserialize(deserializer)?;
                Self::from_code(code as $repr)
                    .ok_or_else(|| ::serde::de::Error::custom(format!("未知 {} 数值码: {code}", stringify!($name))))
            }
        }
    };
}
