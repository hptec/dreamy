//! 用户通用查询(Java 跨域读 User 的唯一入口:GetUser 精确查找 + ListUsers 类型化条件)。
//!
//! 性能纪律:
//! - 按 id 查询 Redis-first:`identity:user:{id}`(TTL 300s,对齐 Java store:user: 键位),
//!   用户写路径(资料更新/禁用/匿名化/删号,P2/P3)主动失效
//! - email / (provider, provider_uid) 查找为低频路径,直查 DB 不缓存
//! - ListUsers 单条分页 SQL(零 N+1);排序白名单 + id 升序稳定兜底;LIKE 仅前缀
//!
//! 脱敏基线(UserView):字段集对齐 Java UserProfileDTO;
//! provider_uid 等凭证标识与 version/deleted_at 等审计内部字段永不出域。

use common::state::SharedState;
use common::time::format_iso;
use redis::AsyncCommands;
use sea_orm::{ColumnTrait, Condition, EntityTrait, PaginatorTrait, QueryFilter, QueryOrder};
use serde::{Deserialize, Serialize};

use crate::entity::{identity_apple, identity_email, identity_google, user};
use crate::service::SvcError;

const USER_TTL: u64 = 300;

pub fn cache_key(user_id: i64) -> String {
    format!("identity:user:{user_id}")
}

/// 用户写路径失效缓存(P2/P3 调用)
pub async fn invalidate_user(state: &SharedState, user_id: i64) {
    if let Some(mut conn) = state.redis.clone() {
        if let Err(e) = conn.del::<_, ()>(cache_key(user_id)).await {
            tracing::warn!(error = %e, "[user] 缓存失效 DEL 失败,等待 TTL(≤300s)");
        }
    }
}

/// 查找键(对齐 proto GetUserRequest.lookup)
#[derive(Debug, Clone)]
pub enum Lookup {
    Id(i64),
    Email(String),
    ProviderIdentity { provider: i32, provider_uid: String },
}

/// 用户只读视图(脱敏基线,缓存载体,proto UserRecord 的同构源)
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UserView {
    pub id: i64,
    pub email: String,
    pub email_verified: bool,
    pub name: Option<String>,
    pub phone: Option<String>,
    pub tier: i32,
    pub status: i32,
    pub avatar: Option<String>,
    pub locale_pref: Option<String>,
    pub joined_at: Option<String>,
    pub anonymized: bool,
}

impl From<&user::Model> for UserView {
    fn from(m: &user::Model) -> Self {
        UserView {
            id: m.id as i64,
            email: m.email.clone(),
            email_verified: m.email_verified != 0,
            name: m.name.clone(),
            phone: m.phone.clone(),
            tier: m.tier as i32,
            status: m.status as i32,
            avatar: m.avatar.clone(),
            locale_pref: m.locale_pref.clone(),
            joined_at: m.joined_at.map(format_iso),
            anonymized: m.anonymized != 0,
        }
    }
}

/// 精确查找(单值)。v2.2:email/provider 查找走路由表两跳(先解析 user_id 再查主档)
pub async fn get_user(state: &SharedState, lookup: &Lookup) -> Result<UserView, SvcError> {
    let model = match lookup {
        Lookup::Id(id) => get_by_id(state, *id).await?,
        Lookup::Email(email) => {
            let email = email.trim().to_lowercase();
            if email.is_empty() {
                return Err(SvcError::InvalidArg("email 为空".into()));
            }
            let route = identity_email::Entity::find_by_id(email)
                .one(&state.db)
                .await?
                .ok_or(SvcError::NotFound)?;
            user::Entity::find_by_id(route.user_id)
                .one(&state.db)
                .await?
                .ok_or(SvcError::NotFound)?
        }
        Lookup::ProviderIdentity {
            provider,
            provider_uid,
        } => {
            if provider_uid.is_empty() {
                return Err(SvcError::InvalidArg("provider_uid 为空".into()));
            }
            // 按登录类型路由(v2.2 三张路由表):1=email 2=google 3=apple
            let user_id = match provider {
                1 => {
                    identity_email::Entity::find_by_id(provider_uid.as_str())
                        .one(&state.db)
                        .await?
                        .ok_or(SvcError::NotFound)?
                        .user_id
                }
                2 => {
                    identity_google::Entity::find_by_id(provider_uid.as_str())
                        .one(&state.db)
                        .await?
                        .ok_or(SvcError::NotFound)?
                        .user_id
                }
                3 => {
                    identity_apple::Entity::find_by_id(provider_uid.as_str())
                        .one(&state.db)
                        .await?
                        .ok_or(SvcError::NotFound)?
                        .user_id
                }
                other => return Err(SvcError::InvalidArg(format!("provider 非法: {other}"))),
            };
            user::Entity::find_by_id(user_id)
                .one(&state.db)
                .await?
                .ok_or(SvcError::NotFound)?
        }
    };
    Ok(UserView::from(&model))
}

async fn get_by_id(state: &SharedState, id: i64) -> Result<user::Model, SvcError> {
    if id <= 0 {
        return Err(SvcError::InvalidArg("id 非法".into()));
    }
    let key = cache_key(id);
    if let Some(mut conn) = state.redis.clone() {
        match conn.get::<_, Option<String>>(&key).await {
            Ok(Some(json)) => match serde_json::from_str::<CachedModel>(&json) {
                // 缓存命中:零 DB(热路径)
                Ok(cached) => return Ok(cached.into_model()),
                Err(e) => tracing::warn!(error = %e, "[user] 缓存反序列化失败,降级 DB"),
            },
            Ok(_) => {}
            Err(e) => tracing::warn!(error = %e, "[user] Redis 读失败,降级 DB"),
        }
    }
    let model = user::Entity::find_by_id(id as u64)
        .one(&state.db)
        .await?
        .ok_or(SvcError::NotFound)?;
    if let Some(mut conn) = state.redis.clone() {
        let cached = CachedModel::from_model(&model);
        if let Ok(json) = serde_json::to_string(&cached) {
            if let Err(e) = conn.set_ex::<_, _, ()>(&key, json, USER_TTL).await {
                tracing::warn!(error = %e, "[user] Redis 回填失败(不影响本次结果)");
            }
        }
    }
    Ok(model)
}

/// 缓存载体(整行含审计字段,DB 回读时无损;对外投影由 UserView 负责脱敏)
#[derive(Debug, Serialize, Deserialize)]
struct CachedModel {
    id: i64,
    email: String,
    email_verified: i8,
    locale_pref: Option<String>,
    name: Option<String>,
    phone: Option<String>,
    tier: i8,
    status: i8,
    avatar: Option<String>,
    joined_at: Option<String>,
    deleted_at: Option<String>,
    anonymized: i8,
    anonymized_at: Option<String>,
    version: i32,
}

impl CachedModel {
    fn from_model(m: &user::Model) -> Self {
        CachedModel {
            id: m.id as i64,
            email: m.email.clone(),
            email_verified: m.email_verified,
            locale_pref: m.locale_pref.clone(),
            name: m.name.clone(),
            phone: m.phone.clone(),
            tier: m.tier,
            status: m.status,
            avatar: m.avatar.clone(),
            joined_at: m.joined_at.map(format_iso),
            deleted_at: m.deleted_at.map(format_iso),
            anonymized: m.anonymized,
            anonymized_at: m.anonymized_at.map(format_iso),
            version: m.version,
        }
    }

    fn into_model(self) -> user::Model {
        user::Model {
            created_at: None,
            updated_at: None,
            id: self.id as u64,
            email: self.email,
            email_verified: self.email_verified,
            locale_pref: self.locale_pref,
            name: self.name,
            phone: self.phone,
            tier: self.tier,
            status: self.status,
            avatar: self.avatar,
            joined_at: parse_iso(self.joined_at),
            deleted_at: parse_iso(self.deleted_at),
            anonymized: self.anonymized,
            anonymized_at: parse_iso(self.anonymized_at),
            version: self.version,
        }
    }
}

fn parse_iso(s: Option<String>) -> Option<chrono::NaiveDateTime> {
    s.and_then(|v| chrono::NaiveDateTime::parse_from_str(&v, "%Y-%m-%dT%H:%M:%S%.f").ok())
}

// ===== ListUsers:类型化条件构造 =====

/// 排序/过滤列白名单(与 proto UserColumn 一致;NAME 仅用于 LIKE_PREFIX 过滤,不参与排序)
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Col {
    Id,
    Email,
    Name,
    Status,
    Tier,
    CreatedAt,
    JoinedAt,
}

/// 单个条件值(按列类型强转,转不动 → InvalidArg)
#[derive(Debug, Clone)]
pub enum CondVal {
    Num(i64),
    Str(String),
    Dt(chrono::NaiveDateTime),
}

#[derive(Debug, Clone)]
pub enum Cond {
    Eq(Col, CondVal),
    In(Col, Vec<CondVal>),
    /// 仅 Email/Name 列;通配符(%/_)由 gRPC 层预先拒绝(语义精确前缀)。
    /// Email 值由 gRPC 层小写化(路由表全小写),Name 保留原样(collation _ai_ci 大小写不敏感)。
    LikePrefix(Col, String),
}

#[derive(Debug, Clone)]
pub struct ListQuery {
    pub conds: Vec<Cond>,
    /// (列, 是否倒序);服务端恒定追加 id 升序稳定兜底
    pub order: (Col, bool),
    pub page: u64,
    pub page_size: u64,
}

fn col(c: Col) -> user::Column {
    match c {
        Col::Id => user::Column::Id,
        Col::Email => user::Column::Email,
        Col::Name => user::Column::Name,
        Col::Status => user::Column::Status,
        Col::Tier => user::Column::Tier,
        Col::CreatedAt => user::Column::CreatedAt,
        Col::JoinedAt => user::Column::JoinedAt,
    }
}

fn typed_expr(c: Col, v: CondVal) -> Result<sea_orm::sea_query::SimpleExpr, SvcError> {
    let expr = match (c, v) {
        (Col::Id, CondVal::Num(n)) => col(c).eq(n as u64),
        (Col::Status, CondVal::Num(n)) => col(c).eq(n as i8),
        (Col::Tier, CondVal::Num(n)) => col(c).eq(n as i8),
        (Col::Email, CondVal::Str(s)) => col(c).eq(s),
        // Name 仅支持 LIKE_PREFIX 过滤(见 Cond::LikePrefix),Eq/IN 组合为调用方编码 BUG
        (Col::Name, _) => return Err(SvcError::InvalidArg("Name 列仅支持 LIKE_PREFIX".into())),
        (Col::CreatedAt | Col::JoinedAt, CondVal::Dt(d)) => col(c).eq(d),
        _ => return Err(SvcError::InvalidArg(format!("列 {c:?} 与值类型不匹配"))),
    };
    Ok(expr)
}

/// IN 表达式:全部值按列类型强转后 is_in(转不动 → InvalidArg)
fn typed_in_expr(c: Col, vals: Vec<CondVal>) -> Result<sea_orm::sea_query::SimpleExpr, SvcError> {
    let expr = match c {
        Col::Id => {
            let mut nums = Vec::with_capacity(vals.len());
            for v in vals {
                match v {
                    CondVal::Num(n) => nums.push(n as u64),
                    _ => return Err(SvcError::InvalidArg("Id 列要求数值".into())),
                }
            }
            col(c).is_in(nums)
        }
        Col::Status | Col::Tier => {
            let mut nums = Vec::with_capacity(vals.len());
            for v in vals {
                match v {
                    CondVal::Num(n) => nums.push(n as i8),
                    _ => return Err(SvcError::InvalidArg("数值列要求数值".into())),
                }
            }
            col(c).is_in(nums)
        }
        Col::Email => {
            let mut strs = Vec::with_capacity(vals.len());
            for v in vals {
                match v {
                    CondVal::Str(s) => strs.push(s),
                    _ => return Err(SvcError::InvalidArg("Email 列要求字符串".into())),
                }
            }
            col(c).is_in(strs)
        }
        Col::CreatedAt | Col::JoinedAt => {
            let mut dts = Vec::with_capacity(vals.len());
            for v in vals {
                match v {
                    CondVal::Dt(d) => dts.push(d),
                    _ => return Err(SvcError::InvalidArg("时间列要求时间值".into())),
                }
            }
            col(c).is_in(dts)
        }
        // Name 仅支持 LIKE_PREFIX 过滤,Eq/IN 组合为调用方编码 BUG
        Col::Name => return Err(SvcError::InvalidArg("Name 列仅支持 LIKE_PREFIX".into())),
    };
    Ok(expr)
}

fn build_condition(conds: &[Cond]) -> Result<Condition, SvcError> {
    let mut all = Condition::all();
    for cond in conds {
        match cond {
            Cond::Eq(c, v) => all = all.add(typed_expr(*c, v.clone())?),
            Cond::In(c, vals) => all = all.add(typed_in_expr(*c, vals.clone())?),
            Cond::LikePrefix(c, prefix) => all = all.add(col(*c).starts_with(prefix.as_str())),
        }
    }
    Ok(all)
}

/// 条件分页查询;返回 (当前页, 命中总数)
pub async fn list_users(
    state: &SharedState,
    q: &ListQuery,
) -> Result<(Vec<UserView>, u64), SvcError> {
    let condition = build_condition(&q.conds)?;
    let primary = col(q.order.0);
    let mut query = user::Entity::find().filter(condition).order_by(
        primary,
        if q.order.1 {
            sea_orm::Order::Desc
        } else {
            sea_orm::Order::Asc
        },
    );
    // 稳定兜底:主排序列非主键时追加 id 升序(主键序天然稳定;
    // 无条件追加会产生 ORDER BY id DESC, id ASC 矛盾子句,实测把主键排序拖成全表排序)
    if !matches!(primary, user::Column::Id) {
        query = query.order_by_asc(user::Column::Id);
    }
    let paginator = query.paginate(&state.db, q.page_size);
    // 计数缓存:无过滤条件时 total 走 Redis 30s 缓存——千万行量级下每页
    // COUNT 全索引扫描是分页热路径主瓶颈(压测实证 p99 超时),30s 近似计数
    // 对运营列表可接受;过滤条件各异的计数无法安全缓存,保持实时
    let total = if q.conds.is_empty() {
        match state.redis.as_ref() {
            Some(conn) => {
                let mut conn = conn.clone();
                let cached: Option<String> = conn.get("users:count:total").await.ok().flatten();
                match cached.and_then(|v| v.parse::<u64>().ok()) {
                    Some(v) => v,
                    None => {
                        let n = paginator.num_items().await?;
                        let _: Result<(), _> =
                            conn.set_ex::<_, _, ()>("users:count:total", n.to_string(), 30)
                                .await;
                        n
                    }
                }
            }
            None => paginator.num_items().await?,
        }
    } else {
        paginator.num_items().await?
    };
    let items = paginator.fetch_page(q.page.saturating_sub(1)).await?;
    Ok((items.iter().map(UserView::from).collect(), total))
}
