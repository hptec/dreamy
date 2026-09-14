//! OpenAPI 契约装配:合并共享地基与各域的路径/组件。
//! openapi.yaml(--export-openapi 导出)是入库冻结物,scripts/export-openapi.sh 校验防漂移。

use common::openapi::REnvelope;
use utoipa::OpenApi;

#[derive(OpenApi)]
#[openapi(
    info(
        title = "dreamy server API",
        version = "0.1.0",
        description = "dreamy 服务端本体(Rust 核心工程)。已入驻域:identity(用户/认证)。\
        对外契约与 Java 原实现字节级对齐:R 包络、错误码、snake_case、ISO-8601 时间。"
    ),
    paths(common::health::healthz, common::health::readyz),
    components(schemas(REnvelope))
)]
pub struct ApiDoc;
