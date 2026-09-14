//! IdentityGate gRPC 服务实现(Java backend ⇄ server 内部通道)。
//! 职责:协议解析与白名单校验(转不动即 InvalidArg)、调用领域服务、
//! SvcError → gRPC status 统一映射(解析帮手一律返回轻量 SvcError,避免
//! 大体积 Status 在解析层流转载运——clippy result_large_err 门禁)。

use common::state::SharedState;
use tonic::{Request, Response, Status};

use proto::dreamy::identity::v1 as pb;

use crate::service::{self, permissions, session, user_query, SvcError};
use crate::service::{Cond, CondVal, ListQuery, Lookup};

pub struct IdentityGateImpl {
    pub state: SharedState,
}

/// SvcError → gRPC status(契约约定:Infra→UNAVAILABLE,由 Java 转 HTTP 503)
fn map_err(err: SvcError) -> Status {
    match err {
        SvcError::NotFound => Status::not_found(err.to_string()),
        SvcError::InvalidArg(msg) => Status::invalid_argument(msg),
        SvcError::Infra(source) => {
            tracing::error!(error = %source, "[grpc] 基础设施错误");
            Status::unavailable("identity storage unavailable")
        }
    }
}

fn invalid(msg: impl Into<String>) -> SvcError {
    SvcError::InvalidArg(msg.into())
}

/// UserView(脱敏视图)→ proto UserRecord
impl From<user_query::UserView> for pb::UserRecord {
    fn from(v: user_query::UserView) -> Self {
        pb::UserRecord {
            id: v.id,
            email: v.email,
            email_verified: v.email_verified,
            name: v.name,
            phone: v.phone,
            tier: v.tier,
            status: v.status,
            avatar: v.avatar,
            locale_pref: v.locale_pref,
            joined_at: v.joined_at,
            anonymized: v.anonymized,
        }
    }
}

const MAX_CONDITIONS: usize = 8;
const MAX_IN_VALUES: usize = 100;
const MAX_PAGE_SIZE: i32 = 100;
const DEFAULT_PAGE_SIZE: i32 = 20;

fn parse_col(c: i32) -> Result<user_query::Col, SvcError> {
    let parsed = pb::UserColumn::try_from(c).map_err(|_| invalid(format!("未知列码: {c}")))?;
    match parsed {
        pb::UserColumn::Id => Ok(user_query::Col::Id),
        pb::UserColumn::Email => Ok(user_query::Col::Email),
        pb::UserColumn::Status => Ok(user_query::Col::Status),
        pb::UserColumn::Tier => Ok(user_query::Col::Tier),
        pb::UserColumn::CreatedAt => Ok(user_query::Col::CreatedAt),
        pb::UserColumn::JoinedAt => Ok(user_query::Col::JoinedAt),
        _ => Err(invalid("列码未指定")),
    }
}

fn parse_val(col: user_query::Col, raw: &str) -> Result<CondVal, SvcError> {
    match col {
        user_query::Col::Id | user_query::Col::Status | user_query::Col::Tier => raw
            .parse::<i64>()
            .map(CondVal::Num)
            .map_err(|_| invalid(format!("数值列收到非数值: {raw}"))),
        user_query::Col::Email => Ok(CondVal::Str(raw.to_string())),
        user_query::Col::CreatedAt | user_query::Col::JoinedAt => {
            // 双格式:MySQL 字面量与 ISO 均接受
            chrono::NaiveDateTime::parse_from_str(raw, "%Y-%m-%d %H:%M:%S")
                .or_else(|_| chrono::NaiveDateTime::parse_from_str(raw, "%Y-%m-%dT%H:%M:%S%.f"))
                .map(CondVal::Dt)
                .map_err(|_| invalid(format!("时间列格式非法: {raw}")))
        }
    }
}

fn parse_conditions(conds: Vec<pb::Condition>) -> Result<Vec<Cond>, SvcError> {
    if conds.len() > MAX_CONDITIONS {
        return Err(invalid(format!(
            "conditions 超上限 {MAX_CONDITIONS}(当前 {})",
            conds.len()
        )));
    }
    let mut parsed = Vec::with_capacity(conds.len());
    for c in conds {
        let col = parse_col(c.column)?;
        let op =
            pb::CondOp::try_from(c.op).map_err(|_| invalid(format!("未知操作码: {}", c.op)))?;
        if c.values.is_empty() {
            return Err(invalid("values 为空"));
        }
        let cond = match op {
            pb::CondOp::Eq => {
                if c.values.len() != 1 {
                    return Err(invalid("EQ 仅接受单值"));
                }
                Cond::Eq(col, parse_val(col, &c.values[0])?)
            }
            pb::CondOp::In => {
                if c.values.len() > MAX_IN_VALUES {
                    return Err(invalid(format!("IN 值超上限 {MAX_IN_VALUES}")));
                }
                let mut vals = Vec::with_capacity(c.values.len());
                for v in &c.values {
                    vals.push(parse_val(col, v)?);
                }
                Cond::In(col, vals)
            }
            pb::CondOp::LikePrefix => {
                if col != user_query::Col::Email {
                    return Err(invalid("LIKE_PREFIX 仅允许 Email 列"));
                }
                if c.values.len() != 1 {
                    return Err(invalid("LIKE_PREFIX 仅接受单值"));
                }
                let v = &c.values[0];
                // 拒绝通配符:保证「前缀」语义精确,不产生超预期匹配
                if v.contains('%') || v.contains('_') {
                    return Err(invalid("LIKE_PREFIX 值不允许包含 % 或 _"));
                }
                Cond::EmailLikePrefix(v.to_lowercase())
            }
            _ => return Err(invalid("操作码未指定")),
        };
        parsed.push(cond);
    }
    Ok(parsed)
}

fn parse_order(order_by: &str) -> Result<(user_query::Col, bool), SvcError> {
    let raw = order_by.trim();
    if raw.is_empty() {
        return Ok((user_query::Col::CreatedAt, true));
    }
    let (name, desc) = match raw.rsplit_once(' ') {
        Some((n, d)) => (n.trim(), d.eq_ignore_ascii_case("desc")),
        None => (raw, false),
    };
    let col = match name {
        "id" => user_query::Col::Id,
        "email" => user_query::Col::Email,
        "status" => user_query::Col::Status,
        "tier" => user_query::Col::Tier,
        "created_at" => user_query::Col::CreatedAt,
        "joined_at" => user_query::Col::JoinedAt,
        other => return Err(invalid(format!("排序列不在白名单: {other}"))),
    };
    Ok((col, desc))
}

fn parse_lookup(req: pb::GetUserRequest) -> Result<Lookup, SvcError> {
    match req.lookup {
        Some(pb::get_user_request::Lookup::Id(id)) => Ok(Lookup::Id(id)),
        Some(pb::get_user_request::Lookup::Email(email)) => Ok(Lookup::Email(email)),
        Some(pb::get_user_request::Lookup::ProviderIdentity(pi)) => {
            if !(1..=3).contains(&pi.provider) {
                return Err(invalid(format!("provider 非法: {}", pi.provider)));
            }
            Ok(Lookup::ProviderIdentity {
                provider: pi.provider,
                provider_uid: pi.provider_uid,
            })
        }
        None => Err(invalid("lookup 未指定")),
    }
}

#[tonic::async_trait]
impl pb::identity_gate_server::IdentityGate for IdentityGateImpl {
    async fn validate_store_session(
        &self,
        request: Request<pb::ValidateStoreSessionRequest>,
    ) -> Result<Response<pb::ValidateStoreSessionResponse>, Status> {
        let req = request.into_inner();
        let valid = session::validate_store(&self.state, &req.token_id)
            .await
            .map_err(map_err)?;
        Ok(Response::new(pb::ValidateStoreSessionResponse { valid }))
    }

    async fn validate_admin_session(
        &self,
        request: Request<pb::ValidateAdminSessionRequest>,
    ) -> Result<Response<pb::ValidateAdminSessionResponse>, Status> {
        let req = request.into_inner();
        let v = session::validate_admin(&self.state, &req.token_id)
            .await
            .map_err(map_err)?;
        Ok(Response::new(pb::ValidateAdminSessionResponse {
            valid: v.valid,
            admin_active: v.admin_active,
        }))
    }

    async fn resolve_permissions(
        &self,
        request: Request<pb::ResolvePermissionsRequest>,
    ) -> Result<Response<pb::ResolvePermissionsResponse>, Status> {
        let req = request.into_inner();
        let keys = permissions::resolve(&self.state, req.admin_id)
            .await
            .map_err(map_err)?;
        Ok(Response::new(pb::ResolvePermissionsResponse {
            permission_keys: keys,
        }))
    }

    async fn get_user(
        &self,
        request: Request<pb::GetUserRequest>,
    ) -> Result<Response<pb::GetUserResponse>, Status> {
        let req = request.into_inner();
        let lookup = parse_lookup(req).map_err(map_err)?;
        let view = user_query::get_user(&self.state, &lookup)
            .await
            .map_err(map_err)?;
        Ok(Response::new(pb::GetUserResponse {
            record: Some(view.into()),
        }))
    }

    async fn list_users(
        &self,
        request: Request<pb::ListUsersRequest>,
    ) -> Result<Response<pb::ListUsersResponse>, Status> {
        let req = request.into_inner();
        let conds = parse_conditions(req.conditions).map_err(map_err)?;
        let (order_col, order_desc) = parse_order(&req.order_by).map_err(map_err)?;
        let page_size = if req.page_size == 0 {
            DEFAULT_PAGE_SIZE
        } else if req.page_size > MAX_PAGE_SIZE {
            return Err(map_err(invalid(format!(
                "page_size 超上限 {MAX_PAGE_SIZE}"
            ))));
        } else {
            req.page_size
        };
        let query = ListQuery {
            conds,
            order: (order_col, order_desc),
            page: req.page.max(1) as u64,
            page_size: page_size as u64,
        };
        let (items, total) = user_query::list_users(&self.state, &query)
            .await
            .map_err(map_err)?;
        Ok(Response::new(pb::ListUsersResponse {
            items: items.into_iter().map(Into::into).collect(),
            total: total as i64,
        }))
    }

    async fn ensure_demo_user(
        &self,
        request: Request<pb::EnsureDemoUserRequest>,
    ) -> Result<Response<pb::EnsureDemoUserResponse>, Status> {
        let req = request.into_inner();
        let user_id = service::demo_user::ensure(&self.state, &req.email, &req.name)
            .await
            .map_err(map_err)?;
        Ok(Response::new(pb::EnsureDemoUserResponse { user_id }))
    }
}
