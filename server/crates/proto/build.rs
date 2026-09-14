fn main() -> Result<(), Box<dyn std::error::Error>> {
    // vendored protoc(版本随 Cargo.lock 锁定,与系统 protoc 解耦)
    std::env::set_var("PROTOC", protoc_bin_vendored::protoc_bin_path()?);
    // 源头:仓库顶层 proto/(与 Java protobuf-maven-plugin 同一份 .proto)
    // 后续域按 dreamy.<domain>.v1 增量加文件
    tonic_build::configure()
        .build_server(true)
        .build_client(true)
        .compile_protos(
            &["../../../proto/dreamy/identity/v1/identity.proto"],
            &["../../../proto"],
        )?;
    Ok(())
}
