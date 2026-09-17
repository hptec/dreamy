//! AuditGate / TemplateGate gRPC 服务实现(Java backend → server 内部通道)。
//! operation_log/email_template 自 identity 库收编主库后,Java 业务侧
//! (审计记录器/邮件渲染/admin 日志查询)经本通道访问;REST handler 与本
//! 服务共用 service 层单份 SQL。
//! 协议解析与白名单校验(转不动即 invalid_argument)、SvcError → status
//! 统一映射,均对齐 grpc.rs 的 IdentityGate 惯例。

use common::state::SharedState;
use tonic::{Request, Response, Status};

use proto::dreamy::audit::v1 as audit_pb;
use proto::dreamy::mail::v1 as mail_pb;

use crate::service::{admin_ops, SvcError};

pub struct AuditGateImpl {
    pub state: SharedState,
}

pub struct TemplateGateImpl {
    pub state: SharedState,
}

/// SvcError → gRPC status(契约约定:Infra→UNAVAILABLE,由 Java 侧决定 503/降级)
fn map_err(err: SvcError) -> Status {
    match err {
        SvcError::NotFound => Status::not_found(err.to_string()),
        SvcError::InvalidArg(msg) => Status::invalid_argument(msg),
        SvcError::Code { code, .. } => Status::internal(format!("identity code {code}")),
        SvcError::Infra(source) => {
            tracing::error!(error = %source, "[grpc] 基础设施错误");
            Status::unavailable("identity storage unavailable")
        }
    }
}

impl From<admin_ops::OperationLogRowData> for audit_pb::OperationLogRow {
    fn from(r: admin_ops::OperationLogRowData) -> Self {
        audit_pb::OperationLogRow {
            id: r.id as i64,
            operator_name: r.operator_name,
            action: r.action,
            target: r.target,
            ip: r.ip,
            changes: r.changes,
            created_at: r.created_at.map(common::time::format_iso),
        }
    }
}

const MAX_PAGE_SIZE: i32 = 100;
const DEFAULT_PAGE_SIZE: i32 = 20;

fn non_empty(v: Option<String>) -> Option<String> {
    v.filter(|s| !s.is_empty())
}

#[tonic::async_trait]
impl audit_pb::audit_gate_server::AuditGate for AuditGateImpl {
    async fn record_operation_log(
        &self,
        request: Request<audit_pb::RecordOperationLogRequest>,
    ) -> Result<Response<audit_pb::RecordOperationLogResponse>, Status> {
        let req = request.into_inner();
        if req.action.is_empty() {
            return Err(Status::invalid_argument("action 为空"));
        }
        if req.action.len() > 32 || req.target.as_deref().is_some_and(|t| t.len() > 255) {
            return Err(Status::invalid_argument("action/target 超长"));
        }
        // best-effort:audit 内部吞错(ERROR 日志),不向调用方回传失败
        admin_ops::audit(
            &self.state,
            req.operator_id,
            req.operator_name.as_deref().unwrap_or(""),
            &req.action,
            req.target.as_deref().unwrap_or(""),
            req.ip.as_deref().unwrap_or(""),
            req.user_agent.as_deref(),
        )
        .await;
        Ok(Response::new(audit_pb::RecordOperationLogResponse {}))
    }

    async fn list_operation_logs(
        &self,
        request: Request<audit_pb::ListOperationLogsRequest>,
    ) -> Result<Response<audit_pb::ListOperationLogsResponse>, Status> {
        let req = request.into_inner();
        let page_size = if req.page_size == 0 {
            DEFAULT_PAGE_SIZE
        } else if req.page_size > MAX_PAGE_SIZE {
            return Err(Status::invalid_argument(format!(
                "page_size 超上限 {MAX_PAGE_SIZE}"
            )));
        } else {
            req.page_size
        };
        let filter = admin_ops::OperationLogFilter {
            action: non_empty(req.action),
            operator_id: req.operator_id,
            from: non_empty(req.from),
            to: non_empty(req.to),
        };
        let (rows, total) =
            admin_ops::query_operation_logs(&self.state, &filter, req.page.max(1) as u64, page_size as u64)
                .await
                .map_err(map_err)?;
        Ok(Response::new(audit_pb::ListOperationLogsResponse {
            items: rows.into_iter().map(Into::into).collect(),
            total,
        }))
    }

    type StreamOperationLogsStream =
        tokio_stream::Iter<std::vec::IntoIter<Result<audit_pb::OperationLogRow, Status>>>;

    async fn stream_operation_logs(
        &self,
        request: Request<audit_pb::StreamOperationLogsRequest>,
    ) -> Result<Response<Self::StreamOperationLogsStream>, Status> {
        let req = request.into_inner();
        if req.from.is_empty() || req.to.is_empty() {
            return Err(Status::invalid_argument("from/to 必传"));
        }
        let filter = admin_ops::OperationLogFilter {
            action: non_empty(req.action),
            operator_id: req.operator_id,
            from: Some(req.from),
            to: Some(req.to),
        };
        let rows = admin_ops::stream_operation_logs(&self.state, &filter)
            .await
            .map_err(map_err)?;
        let items: Vec<Result<audit_pb::OperationLogRow, Status>> =
            rows.into_iter().map(Into::into).map(Ok).collect();
        Ok(Response::new(tokio_stream::iter(items)))
    }
}

#[tonic::async_trait]
impl mail_pb::template_gate_server::TemplateGate for TemplateGateImpl {
    async fn get_email_template(
        &self,
        request: Request<mail_pb::GetEmailTemplateRequest>,
    ) -> Result<Response<mail_pb::GetEmailTemplateResponse>, Status> {
        let req = request.into_inner();
        if req.code.is_empty() || req.locale.is_empty() {
            return Err(Status::invalid_argument("code/locale 为空"));
        }
        if req.code.len() > 32 || req.locale.len() > 8 {
            return Err(Status::invalid_argument("code/locale 超长"));
        }
        // load_template 含 en 回退;两级均未命中 → NOT_FOUND
        let Some(tpl) = crate::mail::load_template(&self.state, &req.code, &req.locale).await
        else {
            return Err(Status::not_found(format!(
                "模板缺失 code={} locale={}",
                req.code, req.locale
            )));
        };
        Ok(Response::new(mail_pb::GetEmailTemplateResponse {
            subject: tpl.subject,
            body: tpl.body,
        }))
    }
}
