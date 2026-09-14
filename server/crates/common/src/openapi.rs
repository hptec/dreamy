//! OpenAPI 契约共享 schema。
//! openapi.yaml 由 dreamy-server --export-openapi 导出入库,scripts/export-openapi.sh 做 diff 防漂移。

use utoipa::ToSchema;

/// huihao R 包络的 OpenAPI 投影(与 crate::error::R 运行时结构一一对应;
/// 字段仅作 schema 声明,P2 起由业务端点引用)
#[allow(dead_code)]
#[derive(ToSchema)]
pub struct REnvelope {
    /// 0=成功;其余为业务错误码(见各端点错误响应)
    pub code: i32,
    pub message: Option<String>,
    pub service_id: Option<String>,
    /// 成功载荷;错误时为错误明细(如校验字段映射,注意:Map key 保持 Java 字段名不转 snake_case)
    pub data: Option<serde_json::Value>,
}
