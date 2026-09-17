//! IdentityGate gRPC 集成冒烟(需真实 MySQL/Redis + 本地 server 已启动):
//!
//! ```bash
//! # 终端 1:启动 server(见 server/.env.example)
//! # 终端 2:
//! DB_PASSWORD=<pwd> SERVER_DB_HOST=127.0.0.1 SERVER_DB_PORT=3307 REDIS_HOST=127.0.0.1 \
//!   IDENTITY_SMOKE_ADDR=http://127.0.0.1:18083 \
//!   cargo test -p identity --test grpc_smoke -- --nocapture
//! ```
//!
//! 未设置 IDENTITY_SMOKE_ADDR 时自动跳过(cargo test 全量跑不依赖外部服务)。

use identity::entity::{
    admin_session, admin_user, identity_email, identity_google, permission, role, role_permission,
    user, user_identity,
};
use sea_orm::{
    ActiveModelTrait, ColumnTrait, Database, DatabaseConnection, EntityTrait, QueryFilter,
};
use tonic::transport::Channel;
use tonic::Request;

use proto::dreamy::identity::v1::identity_gate_client::IdentityGateClient;
use proto::dreamy::identity::v1::{CondOp, Condition, UserColumn};

fn env(key: &str, default: &str) -> String {
    std::env::var(key).unwrap_or_else(|_| default.to_string())
}

/// 清理历史冒烟残留(幂等,任何一次运行前先清)
async fn cleanup(db: &DatabaseConnection) {
    admin_session::Entity::delete_many()
        .filter(admin_session::Column::TokenId.starts_with("smoke-"))
        .exec(db)
        .await
        .ok();
    identity_email::Entity::delete_many()
        .filter(identity_email::Column::Email.starts_with("smoke-"))
        .exec(db)
        .await
        .ok();
    identity_google::Entity::delete_many()
        .filter(identity_google::Column::GoogleSub.starts_with("smoke-uid"))
        .exec(db)
        .await
        .ok();
    // Redis 会话主存键清理(v2.2)
    if let Ok(client) = redis::Client::open(format!(
        "redis://{}:{}",
        env("REDIS_HOST", "127.0.0.1"),
        env("REDIS_PORT", "6379")
    )) {
        if let Ok(mut conn) = client.get_connection() {
            let _: Result<(), _> = redis::cmd("DEL")
                .arg("session:smoke-jti-store-1")
                .arg("session:smoke-jti-missing")
                .arg("user_sessions:1")
                .query(&mut conn);
        }
    }
    for email_prefix in ["smoke-", "demo-seed@"] {
        user::Entity::delete_many()
            .filter(user::Column::Email.starts_with(email_prefix))
            .exec(db)
            .await
            .ok();
    }
    admin_user::Entity::delete_many()
        .filter(admin_user::Column::Email.starts_with("smoke-admin"))
        .exec(db)
        .await
        .ok();
    // 先按 smoke 角色/权限反查关联再删(role 无外键,孤儿行会残留并污染迁移守卫)
    let smoke_role_ids: Vec<i64> = role::Entity::find()
        .filter(role::Column::Name.starts_with("smoke-"))
        .all(db)
        .await
        .unwrap_or_default()
        .into_iter()
        .map(|r| r.id as i64)
        .collect();
    let smoke_perm_ids: Vec<i64> = permission::Entity::find()
        .filter(permission::Column::PermCode.starts_with("smoke."))
        .all(db)
        .await
        .unwrap_or_default()
        .into_iter()
        .map(|p| p.id as i64)
        .collect();
    if !smoke_role_ids.is_empty() || !smoke_perm_ids.is_empty() {
        use sea_orm::QueryFilter as _;
        let mut del = role_permission::Entity::delete_many();
        if !smoke_role_ids.is_empty() {
            del = del.filter(role_permission::Column::RoleId.is_in(smoke_role_ids));
        }
        if !smoke_perm_ids.is_empty() {
            del = del.filter(role_permission::Column::PermissionId.is_in(smoke_perm_ids));
        }
        del.exec(db).await.ok();
    }
    permission::Entity::delete_many()
        .filter(permission::Column::PermCode.starts_with("smoke."))
        .exec(db)
        .await
        .ok();
    role::Entity::delete_many()
        .filter(role::Column::Name.starts_with("smoke-"))
        .exec(db)
        .await
        .ok();
}

#[tokio::test]
async fn grpc_smoke() {
    let Ok(addr) = std::env::var("IDENTITY_SMOKE_ADDR") else {
        eprintln!("[smoke] 跳过:未设置 IDENTITY_SMOKE_ADDR");
        return;
    };
    let db_url = format!(
        "mysql://root:{}@{}:{}/dreamy_server",
        urlencoding::encode(&env("DB_PASSWORD", "")),
        env("SERVER_DB_HOST", "127.0.0.1"),
        env("SERVER_DB_PORT", "3307")
    );
    let db = Database::connect(&db_url)
        .await
        .expect("连接 dreamy_server 失败");
    cleanup(&db).await;

    let channel = Channel::from_shared(addr.clone())
        .expect("地址非法")
        .connect()
        .await
        .expect("连接 server gRPC 失败");
    let mut gate = IdentityGateClient::new(channel);

    // ── 夹具:user + user_identity + user_session ──
    let user = user::ActiveModel {
        email: sea_orm::Set("smoke-user@dreamy.test".into()),
        email_verified: sea_orm::Set(1),
        name: sea_orm::Set(Some("Smoke User".into())),
        locale_pref: sea_orm::Set(Some("en".into())),
        ..Default::default()
    };
    let user = user.insert(&db).await.expect("插入 user 失败");
    let identity = user_identity::ActiveModel {
        user_id: sea_orm::Set(user.id),
        provider: sea_orm::Set(2), // GOOGLE
        provider_uid: sea_orm::Set("smoke-uid-google-1".into()),
        identifier: sea_orm::Set(Some("smoke-user@dreamy.test".into())),
        is_primary: sea_orm::Set(0),
        verified: sea_orm::Set(1),
        connected: sea_orm::Set(1),
        ..Default::default()
    };
    // SeaORM 限制:复合主键+自增(insert/exec 均 UnpackInsertId)→ exec_without_returning
    user_identity::Entity::insert(identity)
        .exec_without_returning(&db)
        .await
        .expect("插入 user_identity 失败");
    // v2.2 路由层:email + google 各一行(凭证 → user_id)
    identity_email::ActiveModel {
        email: sea_orm::Set("smoke-user@dreamy.test".into()),
        user_id: sea_orm::Set(user.id),
        ..Default::default()
    }
    .insert(&db)
    .await
    .expect("插入 identity_email 失败");
    identity_google::ActiveModel {
        google_sub: sea_orm::Set("smoke-uid-google-1".into()),
        user_id: sea_orm::Set(user.id),
        ..Default::default()
    }
    .insert(&db)
    .await
    .expect("插入 identity_google 失败");
    // v2.2 会话权威主存:Redis session:{token_id}(完整 JSON,TTL=refresh 期)
    let now = chrono::Utc::now().timestamp();
    let session_json = serde_json::json!({
        "id": 1, "user_id": user.id as i64, "method": 1,
        "refresh_token_id": "smoke-jti-store-refresh",
        "access_exp": now + 7200, "refresh_exp": now + 2_592_000,
        "device": null, "browser": null, "ip": "127.0.0.1", "location": null
    });
    let redis_client = redis::Client::open(format!(
        "redis://{}:{}",
        env("REDIS_HOST", "127.0.0.1"),
        env("REDIS_PORT", "6379")
    ))
    .expect("Redis 地址非法");
    let mut rconn = redis_client.get_connection().expect("连接 Redis 失败");
    let _: Result<(), _> = redis::cmd("SET")
        .arg("session:smoke-jti-store-1")
        .arg(session_json.to_string())
        .arg("EX")
        .arg(3600)
        .query(&mut rconn);

    // ── ValidateStoreSession:命中 / 未命中 / 空参 ──
    let resp = gate
        .validate_store_session(Request::new(
            proto::dreamy::identity::v1::ValidateStoreSessionRequest {
                token_id: "smoke-jti-store-1".into(),
            },
        ))
        .await
        .expect("rpc 失败");
    assert!(resp.into_inner().valid, "Redis 主存中的活跃会话应为 valid");

    let resp = gate
        .validate_store_session(Request::new(
            proto::dreamy::identity::v1::ValidateStoreSessionRequest {
                token_id: "smoke-jti-missing".into(),
            },
        ))
        .await
        .expect("rpc 失败");
    assert!(
        !resp.into_inner().valid,
        "key miss = invalid(撤销/过期/不存在无歧义)"
    );

    let resp = gate
        .validate_store_session(Request::new(
            proto::dreamy::identity::v1::ValidateStoreSessionRequest {
                token_id: " ".into(),
            },
        ))
        .await;
    assert!(resp.is_err(), "空 token_id 应 INVALID_ARGUMENT");

    // ── GetUser:id / email / provider_identity / 未命中 / 缓存路径 ──
    let by_id = gate
        .get_user(Request::new(proto::dreamy::identity::v1::GetUserRequest {
            lookup: Some(proto::dreamy::identity::v1::get_user_request::Lookup::Id(
                user.id as i64,
            )),
        }))
        .await
        .expect("rpc 失败")
        .into_inner()
        .record
        .expect("record 缺失");
    assert_eq!(by_id.email, "smoke-user@dreamy.test");
    assert!(by_id.email_verified);
    assert_eq!(by_id.status, 1);
    assert_eq!(by_id.name.as_deref(), Some("Smoke User"));

    // 二次按 id 查:走 Redis 缓存路径,结果必须与 DB 路径一致
    let by_id_cached = gate
        .get_user(Request::new(proto::dreamy::identity::v1::GetUserRequest {
            lookup: Some(proto::dreamy::identity::v1::get_user_request::Lookup::Id(
                user.id as i64,
            )),
        }))
        .await
        .expect("rpc 失败")
        .into_inner()
        .record
        .expect("record 缺失");
    assert_eq!(
        by_id_cached.email, by_id.email,
        "缓存路径结果与 DB 路径不一致"
    );

    let by_email = gate
        .get_user(Request::new(proto::dreamy::identity::v1::GetUserRequest {
            lookup: Some(
                proto::dreamy::identity::v1::get_user_request::Lookup::Email(
                    "SMOKE-USER@dreamy.test".into(),
                ),
            ),
        }))
        .await
        .expect("rpc 失败")
        .into_inner()
        .record
        .expect("record 缺失");
    assert_eq!(
        by_email.id, user.id as i64,
        "email 查找应服务端规范化大小写"
    );

    let by_provider = gate
        .get_user(Request::new(proto::dreamy::identity::v1::GetUserRequest {
            lookup: Some(
                proto::dreamy::identity::v1::get_user_request::Lookup::ProviderIdentity(
                    proto::dreamy::identity::v1::ProviderIdentity {
                        provider: 2,
                        provider_uid: "smoke-uid-google-1".into(),
                    },
                ),
            ),
        }))
        .await
        .expect("rpc 失败")
        .into_inner()
        .record
        .expect("record 缺失");
    assert_eq!(by_provider.id, user.id as i64);

    let missing = gate
        .get_user(Request::new(proto::dreamy::identity::v1::GetUserRequest {
            lookup: Some(proto::dreamy::identity::v1::get_user_request::Lookup::Id(
                987_654_321,
            )),
        }))
        .await;
    assert!(missing.is_err(), "未命中应 NOT_FOUND");
    assert_eq!(missing.err().unwrap().code(), tonic::Code::NotFound);

    // ── ListUsers:条件 + 分页 + 稳定序 ──
    let list = gate
        .list_users(Request::new(
            proto::dreamy::identity::v1::ListUsersRequest {
                conditions: vec![
                    Condition {
                        column: UserColumn::Email as i32,
                        op: CondOp::LikePrefix as i32,
                        values: vec!["smoke-".into()],
                    },
                    Condition {
                        column: UserColumn::Status as i32,
                        op: CondOp::Eq as i32,
                        values: vec!["1".into()],
                    },
                ],
                order_by: "id asc".into(),
                page: 1,
                page_size: 10,
            },
        ))
        .await
        .expect("rpc 失败")
        .into_inner();
    assert_eq!(list.total, 1, "过滤后应恰命中 1 个 smoke 用户");
    assert_eq!(list.items.len(), 1);

    // 超限 page_size → INVALID_ARGUMENT
    let bad = gate
        .list_users(Request::new(
            proto::dreamy::identity::v1::ListUsersRequest {
                conditions: vec![],
                order_by: String::new(),
                page: 1,
                page_size: 200,
            },
        ))
        .await;
    assert!(bad.is_err());
    assert_eq!(bad.err().unwrap().code(), tonic::Code::InvalidArgument);

    // LIKE 含通配符 → INVALID_ARGUMENT(语义精确前缀)
    let bad = gate
        .list_users(Request::new(
            proto::dreamy::identity::v1::ListUsersRequest {
                conditions: vec![Condition {
                    column: UserColumn::Email as i32,
                    op: CondOp::LikePrefix as i32,
                    values: vec!["smoke%".into()],
                }],
                order_by: String::new(),
                page: 1,
                page_size: 10,
            },
        ))
        .await;
    assert!(bad.is_err());
    assert_eq!(bad.err().unwrap().code(), tonic::Code::InvalidArgument);

    // ── ResolvePermissions:超管短路全量 / 普通角色两步查 / 不存在的 admin ──
    let perm = permission::ActiveModel {
        perm_code: sea_orm::Set("smoke.perm".into()),
        group: sea_orm::Set("smoke".into()),
        label: sea_orm::Set("冒烟权限".into()),
        ..Default::default()
    };
    let perm = perm.insert(&db).await.expect("插入 permission 失败");
    let super_role = role::ActiveModel {
        name: sea_orm::Set("smoke-super".into()),
        r#type: sea_orm::Set(2),
        is_locked: sea_orm::Set(1),
        version: sea_orm::Set(0),
        ..Default::default()
    };
    let super_role = super_role.insert(&db).await.expect("插入 role 失败");
    let normal_role = role::ActiveModel {
        name: sea_orm::Set("smoke-normal".into()),
        r#type: sea_orm::Set(2),
        is_locked: sea_orm::Set(0),
        version: sea_orm::Set(0),
        ..Default::default()
    };
    let normal_role = normal_role.insert(&db).await.expect("插入 role 失败");
    role_permission::ActiveModel {
        role_id: sea_orm::Set(normal_role.id as i64),
        permission_id: sea_orm::Set(perm.id as i64),
        ..Default::default()
    }
    .insert(&db)
    .await
    .expect("插入 role_permission 失败");
    let super_admin = admin_user::ActiveModel {
        email: sea_orm::Set("smoke-admin-super@dreamy.test".into()),
        password_hash: sea_orm::Set("$2a$10$smokehash".into()),
        role_id: sea_orm::Set(super_role.id as i64),
        status: sea_orm::Set(1),
        ..Default::default()
    };
    let super_admin = super_admin.insert(&db).await.expect("插入 admin 失败");
    admin_session::ActiveModel {
        admin_id: sea_orm::Set(super_admin.id as i64),
        token_id: sea_orm::Set("smoke-jti-admin-1".into()),
        status: sea_orm::Set(1),
        ..Default::default()
    }
    .insert(&db)
    .await
    .expect("插入 admin_session 失败");

    let perms = gate
        .resolve_permissions(Request::new(
            proto::dreamy::identity::v1::ResolvePermissionsRequest {
                admin_id: super_admin.id as i64,
            },
        ))
        .await
        .expect("rpc 失败")
        .into_inner()
        .permission_keys;
    assert!(
        perms.contains(&"smoke.perm".to_string()),
        "超管应短路全量权限:{perms:?}"
    );

    let ghost = gate
        .resolve_permissions(Request::new(
            proto::dreamy::identity::v1::ResolvePermissionsRequest {
                admin_id: 987_654_321,
            },
        ))
        .await
        .expect("rpc 失败")
        .into_inner()
        .permission_keys;
    assert!(ghost.is_empty(), "不存在的 admin 应返回空列表");

    // ── ValidateAdminSession:有效 / 管理员禁用(缓存不投毒)──
    let resp = gate
        .validate_admin_session(Request::new(
            proto::dreamy::identity::v1::ValidateAdminSessionRequest {
                token_id: "smoke-jti-admin-1".into(),
            },
        ))
        .await
        .expect("rpc 失败")
        .into_inner();
    assert!(resp.valid && resp.admin_active, "活跃 admin 会话应双真");

    // 禁用管理员 → admin_active=false。
    // 先 DEL 缓存键:模拟 P3 禁用路径的主动失效调用(session::revoke_admin 同款语义),
    // 断言 DB 真实状态不被双真缓存遮蔽。
    if let Ok(client) = redis::Client::open(format!(
        "redis://{}:{}",
        env("REDIS_HOST", "127.0.0.1"),
        env("REDIS_PORT", "6379")
    )) {
        if let Ok(mut conn) = client.get_connection() {
            let _: Result<(), _> = redis::cmd("DEL")
                .arg("admin:session:valid:smoke-jti-admin-1")
                .query(&mut conn);
        }
    }
    let mut disabled: admin_user::ActiveModel = super_admin.clone().into();
    disabled.status = sea_orm::Set(2); // DISABLED
    disabled.update(&db).await.expect("禁用管理员失败");
    let resp = gate
        .validate_admin_session(Request::new(
            proto::dreamy::identity::v1::ValidateAdminSessionRequest {
                token_id: "smoke-jti-admin-1".into(),
            },
        ))
        .await
        .expect("rpc 失败")
        .into_inner();
    assert!(resp.valid, "会话本身仍 ACTIVE");
    assert!(
        !resp.admin_active,
        "管理员已禁用(失效后),admin_active 应为 false"
    );

    // ── EnsureDemoUser:幂等 ──
    let first = gate
        .ensure_demo_user(Request::new(
            proto::dreamy::identity::v1::EnsureDemoUserRequest {
                email: "demo-seed@dreamy.test".into(),
                name: "Demo Seed".into(),
            },
        ))
        .await
        .expect("rpc 失败")
        .into_inner()
        .user_id;
    let second = gate
        .ensure_demo_user(Request::new(
            proto::dreamy::identity::v1::EnsureDemoUserRequest {
                email: "DEMO-SEED@dreamy.test".into(),
                name: "Demo Seed".into(),
            },
        ))
        .await
        .expect("rpc 失败")
        .into_inner()
        .user_id;
    assert_eq!(first, second, "按 email 幂等(大小写规范化)");

    cleanup(&db).await;
    println!("[smoke] IdentityGate 全部断言通过");
}
