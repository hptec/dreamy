//! IdentityGate gRPC 服务(Java backend ⇄ server 内部通道)。
//! P0:接口面编译就绪 + 全 rpc 返回 UNIMPLEMENTED(P1 以真实实现替换)。

use sea_orm::DatabaseConnection;
use tonic::{Request, Response, Status};

use proto::dreamy::identity::v1 as pb;

pub struct IdentityGateImpl {
    #[allow(dead_code)] // P1 接入数据层
    pub db: DatabaseConnection,
}

#[tonic::async_trait]
impl pb::identity_gate_server::IdentityGate for IdentityGateImpl {
    async fn validate_store_session(
        &self,
        request: Request<pb::ValidateStoreSessionRequest>,
    ) -> Result<Response<pb::ValidateStoreSessionResponse>, Status> {
        let _ = request;
        Err(Status::unimplemented("P1: 会话校验未实现"))
    }

    async fn validate_admin_session(
        &self,
        request: Request<pb::ValidateAdminSessionRequest>,
    ) -> Result<Response<pb::ValidateAdminSessionResponse>, Status> {
        let _ = request;
        Err(Status::unimplemented("P1: 会话校验未实现"))
    }

    async fn resolve_permissions(
        &self,
        request: Request<pb::ResolvePermissionsRequest>,
    ) -> Result<Response<pb::ResolvePermissionsResponse>, Status> {
        let _ = request;
        Err(Status::unimplemented("P1: 权限解析未实现"))
    }

    async fn get_user(
        &self,
        request: Request<pb::GetUserRequest>,
    ) -> Result<Response<pb::GetUserResponse>, Status> {
        let _ = request;
        Err(Status::unimplemented("P1: 用户查询未实现"))
    }

    async fn list_users(
        &self,
        request: Request<pb::ListUsersRequest>,
    ) -> Result<Response<pb::ListUsersResponse>, Status> {
        let _ = request;
        Err(Status::unimplemented("P1: 用户查询未实现"))
    }

    async fn ensure_demo_user(
        &self,
        request: Request<pb::EnsureDemoUserRequest>,
    ) -> Result<Response<pb::EnsureDemoUserResponse>, Status> {
        let _ = request;
        Err(Status::unimplemented("P1: demo 种子通道未实现"))
    }
}
