//! 身份域基线种子(auth_config 单例 + 权限字典 + 超管角色 + 超管账户,主库;启动幂等)。
//!
//! 对齐 Java DataInitializer 语义(Java 删码后,本模块是新环境唯一自举来源——
//! 此前权限/超管种子靠 Java 播种 identity 库再经 migrate-identity.sh 迁移):
//! - auth_config 单例 id=1:email/google/apple 恒开,OTP 6 位 5 分钟,重发 60s,
//!   尝试 5 次,admin 登录锁 5 次/15 分钟(与 DDL 默认值一致)
//! - permission 权限字典 27 点(portal-admin 菜单级路由,对齐 DataInitializer.initPermissions)
//! - role 超管(is_locked,type=1)缺则建,并绑定全部权限(幂等:已绑定跳过)
//! - admin_user 超管缺则建:凭据取 DREAMY_BOOTSTRAP_ADMIN_EMAIL/PASSWORD;
//!   两者均缺且 DREAMY_SEED_DEMO_ENABLED=true 时用演示账号(Admin@123456);
//!   密码 <12 字符视为配置错误,FAIL FAST(与 Java IllegalStateException 同语义)
//!
//! 单项失败仅 WARN 不阻断启动;但超管缺失时 admin 无法登录,由日志提示补配。

use sea_orm::{ColumnTrait, DatabaseConnection, EntityTrait, QueryFilter, Set};

use crate::entity::{admin_user, auth_config, permission, role, role_permission};

const DEMO_ADMIN_EMAIL: &str = "admin@dreamy.com";
const DEMO_ADMIN_PASSWORD: &str = "Admin@123456";
const SUPER_ADMIN_ROLE: &str = "超级管理员";
const ROLE_TYPE_PRESET: i8 = 1;
const ADMIN_STATUS_ACTIVE: i8 = 1;

/// (perm_code, group, label)——portal-admin 菜单级权限点,与 Java DataInitializer 逐条对齐
const PERMISSIONS: &[(&str, &str, &str)] = &[
    ("/", "工作台", "工作台"),
    ("/dashboard", "工作台", "工作台"),
    ("/site/home", "站点装修", "首页装修"),
    ("/site/navigation", "站点装修", "导航与页脚"),
    ("/site/announcement", "站点装修", "公告管理"),
    ("/banners", "站点装修", "Banner 管理"),
    ("/products", "商品管理", "商品列表"),
    ("/categories", "商品管理", "品类与主题"),
    ("/orders", "订单管理", "订单列表"),
    ("/refunds", "订单管理", "退款工单"),
    ("/customers", "用户管理", "用户列表"),
    ("/promotions", "营销活动", "优惠券与促销"),
    ("/marketing/email", "营销活动", "邮件营销"),
    ("/content/blog", "内容管理", "Blog 文章"),
    ("/content/weddings", "内容管理", "Real Weddings"),
    ("/content/lookbook", "内容管理", "Lookbook 与指南"),
    ("/reviews", "内容管理", "评价与 Q&A"),
    ("/analytics", "数据分析", "数据看板"),
    ("/shipping", "发布与系统", "物流配置"),
    ("/settings", "发布与系统", "汇率与结算配置"),
    ("/system/admins", "系统管理", "管理员管理"),
    ("/system/roles", "系统管理", "角色权限"),
    ("/system/auth", "系统管理", "登录与认证"),
    ("/system/logs", "系统管理", "操作日志"),
    ("/system/cache", "系统管理", "缓存管理"),
    // i18n-complete-with-ai-assist:网关配置(AI 翻译代理仍用)
    ("/system/gateways", "系统管理", "外部网关配置"),
    ("/attribute-sets", "商品管理", "属性集"),
];

/// 启动期播种入口(幂等:按业务键查→缺则建)
pub async fn seed_identity_baseline(db: &DatabaseConnection) {
    seed_auth_config(db).await;
    let perm_ids = seed_permissions(db).await;
    let role_id = seed_super_role(db, &perm_ids).await;
    seed_super_admin(db, role_id).await;
}

/// auth_config 单例(id=1):存在即跳过,不覆盖运营态配置
async fn seed_auth_config(db: &DatabaseConnection) {
    if auth_config::Entity::find_by_id(1u64)
        .one(db)
        .await
        .is_ok_and(|r| r.is_some())
    {
        return;
    }
    let row = auth_config::ActiveModel {
        id: Set(1),
        email_enabled: Set(1),
        google_enabled: Set(1),
        apple_enabled: Set(1),
        otp_length: Set(6),
        otp_ttl_minutes: Set(5),
        otp_resend_seconds: Set(60),
        otp_max_attempts: Set(5),
        min_methods: Set(1),
        admin_login_max_attempts: Set(5),
        admin_login_lock_minutes: Set(15),
        google_client_id: Set(None),
        apple_service_id: Set(None),
        ..Default::default()
    };
    match auth_config::Entity::insert(row).exec(db).await {
        Ok(_) => tracing::info!("[bootstrap] auth_config 单例已初始化 id=1"),
        Err(e) => tracing::warn!("[bootstrap] auth_config 播种失败:{e}"),
    }
}

/// 权限字典:按 perm_code 幂等登记,返回 code→id 映射(绑角色用)
async fn seed_permissions(db: &DatabaseConnection) -> Vec<i64> {
    let mut ids = Vec::with_capacity(PERMISSIONS.len());
    for (code, group, label) in PERMISSIONS {
        let existing = permission::Entity::find()
            .filter(permission::Column::PermCode.eq(*code))
            .one(db)
            .await;
        if let Ok(Some(row)) = existing {
            ids.push(row.id as i64);
            continue;
        }
        let row = permission::ActiveModel {
            perm_code: Set((*code).to_string()),
            group: Set((*group).to_string()),
            label: Set((*label).to_string()),
            ..Default::default()
        };
        match permission::Entity::insert(row).exec(db).await {
            Ok(result) => ids.push(result.last_insert_id as i64),
            Err(e) => tracing::warn!("[bootstrap] 权限 {code} 播种失败:{e}"),
        }
    }
    ids
}

/// 超管角色(is_locked,type=1)缺则建,并绑定全部权限(幂等);返回 role_id
async fn seed_super_role(db: &DatabaseConnection, perm_ids: &[i64]) -> Option<i64> {
    let role_id: i64 = match role::Entity::find()
        .filter(role::Column::Name.eq(SUPER_ADMIN_ROLE))
        .one(db)
        .await
    {
        Ok(Some(r)) => r.id as i64,
        Ok(None) => {
            let row = role::ActiveModel {
                name: Set(SUPER_ADMIN_ROLE.to_string()),
                r#type: Set(ROLE_TYPE_PRESET),
                is_locked: Set(1),
                version: Set(0),
                ..Default::default()
            };
            match role::Entity::insert(row).exec(db).await {
                Ok(result) => {
                    let id = result.last_insert_id as i64;
                    tracing::info!("[bootstrap] 超管角色已初始化 id={id}");
                    id
                }
                Err(e) => {
                    tracing::warn!("[bootstrap] 超管角色播种失败:{e}");
                    return None;
                }
            }
        }
        Err(e) => {
            tracing::warn!("[bootstrap] 超管角色查询失败:{e}");
            return None;
        }
    };
    // 全量绑定(幂等:已存在的跳过)
    let bound: std::collections::HashSet<i64> = role_permission::Entity::find()
        .filter(role_permission::Column::RoleId.eq(role_id))
        .all(db)
        .await
        .unwrap_or_default()
        .into_iter()
        .map(|rp| rp.permission_id)
        .collect();
    let mut created = 0usize;
    for perm_id in perm_ids {
        if bound.contains(perm_id) {
            continue;
        }
        let rp = role_permission::ActiveModel {
            role_id: Set(role_id),
            permission_id: Set(*perm_id),
            ..Default::default()
        };
        if role_permission::Entity::insert(rp).exec(db).await.is_ok() {
            created += 1;
        }
    }
    if created > 0 {
        tracing::info!("[bootstrap] 超管角色补绑权限 {created} 条");
    }
    Some(role_id)
}

/// 超管账户缺则建(email 唯一);凭据策略见模块注释
async fn seed_super_admin(db: &DatabaseConnection, role_id: Option<i64>) {
    let Some(role_id) = role_id else {
        tracing::warn!("[bootstrap] 超管角色缺席,跳过超管账户播种");
        return;
    };
    let mut email = env_non_empty("DREAMY_BOOTSTRAP_ADMIN_EMAIL");
    let mut password = env_non_empty("DREAMY_BOOTSTRAP_ADMIN_PASSWORD");
    if email.is_none() && demo_enabled() {
        email = Some(DEMO_ADMIN_EMAIL.to_string());
        password = Some(DEMO_ADMIN_PASSWORD.to_string());
    }
    let (Some(email), Some(password)) = (email, password) else {
        tracing::warn!("[bootstrap] 未创建首个超管:请设置 DREAMY_BOOTSTRAP_ADMIN_EMAIL/PASSWORD(demo seed 关闭时不使用默认凭据)");
        return;
    };
    if password.len() < 12 {
        // 配置错误 FAIL FAST(与 Java DataInitializer IllegalStateException 同语义)
        panic!("Bootstrap admin password must contain at least 12 characters");
    }
    if admin_user::Entity::find()
        .filter(admin_user::Column::Email.eq(&email))
        .one(db)
        .await
        .is_ok_and(|r| r.is_some())
    {
        return;
    }
    // bcrypt cost 10,与 Java PasswordEncoder(默认 $2a$10$)及 admin_auth verify 兼容
    let hash = match bcrypt::hash(&password, 10) {
        Ok(h) => h,
        Err(e) => {
            tracing::warn!("[bootstrap] 超管口令哈希失败:{e}");
            return;
        }
    };
    let row = admin_user::ActiveModel {
        name: Set(Some("超级管理员".to_string())),
        email: Set(email),
        password_hash: Set(hash),
        role_id: Set(role_id),
        status: Set(ADMIN_STATUS_ACTIVE),
        version: Set(0),
        ..Default::default()
    };
    match admin_user::Entity::insert(row).exec(db).await {
        Ok(result) => tracing::info!("[bootstrap] 超管账户已初始化 id={}", result.last_insert_id),
        Err(e) => tracing::warn!("[bootstrap] 超管账户播种失败:{e}"),
    }
}

fn env_non_empty(key: &str) -> Option<String> {
    std::env::var(key).ok().filter(|v| !v.trim().is_empty())
}

fn demo_enabled() -> bool {
    env_non_empty("DREAMY_SEED_DEMO_ENABLED")
        .map(|v| v.eq_ignore_ascii_case("true") || v == "1")
        .unwrap_or(false)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn permissions_table_matches_java_initializer() {
        // 与 Java DataInitializer.initPermissions 的 27 项逐条对齐(删码后唯一权威)
        assert_eq!(PERMISSIONS.len(), 27);
        assert!(PERMISSIONS.iter().all(|(c, g, l)| !c.is_empty() && !g.is_empty() && !l.is_empty()));
        // perm_code 唯一
        let mut codes: Vec<_> = PERMISSIONS.iter().map(|(c, _, _)| *c).collect();
        codes.sort();
        codes.dedup();
        assert_eq!(codes.len(), PERMISSIONS.len());
        // 关键权限点抽查
        for key in ["/settings", "/attribute-sets", "/system/logs", "/dashboard"] {
            assert!(PERMISSIONS.iter().any(|(c, _, _)| *c == key), "缺权限点 {key}");
        }
    }
}
