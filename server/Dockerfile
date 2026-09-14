# ============================================================
# dreamy-server 镜像:静态二进制预编译模式(与 backend JAR 同口径:
# 本地编译产物 → 服务器/本机仅打包,不在镜像内编译)
#
# 二进制由 release.sh 在本地预编译:
#   cd server && cargo zigbuild --release --target x86_64-unknown-linux-musl
# 产物路径:target/x86_64-unknown-linux-musl/release/dreamy-server
# 依赖:cargo-zigbuild + zig(brew install zig && cargo install cargo-zigbuild)
# musl 静态链接:无 glibc 版本摩擦,基础镜像仅提供 curl(healthcheck)与 CA 证书
# ============================================================
# 二进制三元组随构建平台切换(release.sh 注入):
#   远程(香港服务器 x86_64):target/x86_64-unknown-linux-musl/release/dreamy-server
#   --load 本地验证(Apple Silicon):target/aarch64-unknown-linux-musl/release/dreamy-server
FROM debian:bookworm-slim
ARG BINARY=target/x86_64-unknown-linux-musl/release/dreamy-server

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY ${BINARY} /app/dreamy-server
COPY schema /app/schema

ENV SERVER_SCHEMA_PATH=/app/schema/identity.sql
EXPOSE 18082 18083

# readiness 探活(主库连通才 ready,见 src/http.rs readyz)
HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=6 \
    CMD curl -fsS http://127.0.0.1:18082/readyz || exit 1

ENTRYPOINT ["/app/dreamy-server"]
