//! gRPC 契约生成代码。模块路径与 .proto 包名一一对应(dreamy.identity.v1)。
//! 后续域在顶层 proto/ 增量添加后,此处同步加对应 mod 声明。

pub mod dreamy {
    pub mod identity {
        pub mod v1 {
            // 生成代码(tonic/prost)不做 lint:改动源头在 .proto,重新生成即覆盖
            #![allow(clippy::all)]
            tonic::include_proto!("dreamy.identity.v1");
        }
    }
}
