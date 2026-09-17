use std::env;

/// 运行时配置(env 注入;docker compose 场景由编排层下发,.env.deploy 复用 Java 侧同名变量)
#[derive(Clone, Debug)]
pub struct Config {
    pub http_port: u16,
    pub grpc_port: u16,
    pub db_host: String,
    pub db_port: u16,
    pub db_name: String,
    pub db_user: String,
    pub db_password: String,
    pub redis_host: String,
    pub redis_port: u16,
    pub store_jwt_secret: Option<String>,
    pub admin_jwt_secret: Option<String>,
    pub store_cors_origin: String,
    pub admin_cors_origin: String,
    /// 空库自举 DDL 路径(仅 CREATE IF NOT EXISTS,绝不动存量)
    pub schema_path: String,
}

fn var(key: &str, default: &str) -> String {
    env::var(key)
        .ok()
        .filter(|v| !v.trim().is_empty())
        .unwrap_or_else(|| default.to_string())
}

impl Config {
    pub fn from_env() -> Result<Self, String> {
        let cfg = Config {
            http_port: var("SERVER_HTTP_PORT", "18082")
                .parse()
                .map_err(|_| "SERVER_HTTP_PORT 非法")?,
            grpc_port: var("SERVER_GRPC_PORT", "18083")
                .parse()
                .map_err(|_| "SERVER_GRPC_PORT 非法")?,
            db_host: var("SERVER_DB_HOST", "localhost"),
            db_port: var("SERVER_DB_PORT", "3306")
                .parse()
                .map_err(|_| "SERVER_DB_PORT 非法")?,
            db_name: var("SERVER_DB_NAME", "dreamy_server"),
            db_user: var("DB_USERNAME", "root"),
            db_password: env::var("DB_PASSWORD").unwrap_or_default(),
            redis_host: var("REDIS_HOST", "localhost"),
            redis_port: var("REDIS_PORT", "6379")
                .parse()
                .map_err(|_| "REDIS_PORT 非法")?,
            store_jwt_secret: env::var("STORE_JWT_SECRET")
                .ok()
                .filter(|v| !v.trim().is_empty()),
            admin_jwt_secret: env::var("ADMIN_JWT_SECRET")
                .ok()
                .filter(|v| !v.trim().is_empty()),
            store_cors_origin: var("STORE_CORS_ORIGIN", "http://localhost:5173"),
            admin_cors_origin: var("ADMIN_CORS_ORIGIN", "http://localhost:5174"),
            schema_path: var("SERVER_SCHEMA_PATH", "schema/identity.sql"),
        };
        cfg.validate_secrets()?;
        Ok(cfg)
    }

    /// 密钥校验:已提供即必须合法(≥32 字节、双钥不同)。
    /// P2(令牌签发上线)起两者必填;P0/P1 允许缺席以便骨架本地裸跑。
    fn validate_secrets(&self) -> Result<(), String> {
        for (name, val) in [
            ("STORE_JWT_SECRET", &self.store_jwt_secret),
            ("ADMIN_JWT_SECRET", &self.admin_jwt_secret),
        ] {
            if let Some(v) = val {
                if v.len() < 32 {
                    return Err(format!("{name} 至少 32 字节(当前 {})", v.len()));
                }
            } else {
                tracing::warn!("[config] {name} 未设置:令牌签发功能不可用(P2 起必填)");
            }
        }
        if let (Some(s), Some(a)) = (&self.store_jwt_secret, &self.admin_jwt_secret) {
            if s == a {
                return Err("STORE_JWT_SECRET 与 ADMIN_JWT_SECRET 必须不同".into());
            }
        }
        Ok(())
    }

    /// 不带库名的连接 URL(建库用)
    pub fn db_base_url(&self) -> String {
        format!(
            "mysql://{}:{}@{}:{}",
            urlencoding::encode(&self.db_user),
            urlencoding::encode(&self.db_password),
            self.db_host,
            self.db_port
        )
    }

    pub fn db_main_url(&self) -> String {
        format!("{}/{}", self.db_base_url(), self.db_name)
    }

    pub fn redis_url(&self) -> String {
        format!("redis://{}:{}", self.redis_host, self.redis_port)
    }
}
