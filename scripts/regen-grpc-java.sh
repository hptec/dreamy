#!/usr/bin/env bash
# =============================================================
# 脚本名称: regen-grpc-java.sh
# 功能描述: 从仓库顶层 proto/ 重新生成 Java gRPC stub 到 backend/src/generated/java(入库)。
#           构建本身零 protoc 依赖(预生成入库);仅 proto 变更后手动执行本脚本。
# 依赖环境: protoc 3.25.5 + protoc-gen-grpc-java 1.66.0(下载见脚本内注释)
# =============================================================
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

TOOLS="${PROTOC_TOOLS_DIR:-/tmp/protoc-tools}"
[ -x "${TOOLS}/protoc" ] && [ -x "${TOOLS}/grpc-plugin" ] || {
  echo "[regen] 缺少工具链,下载(osx-aarch_64,linux 换对应 classifier):" >&2
  echo "[regen] curl -O ${TOOLS}/protoc https://repo1.maven.org/maven2/com/google/protobuf/protoc/3.25.5/protoc-3.25.5-osx-aarch_64.exe" >&2
  echo "[regen] curl -O ${TOOLS}/grpc-plugin https://repo1.maven.org/maven2/io/grpc/protoc-gen-grpc-java/1.66.0/protoc-gen-grpc-java-1.66.0-osx-aarch_64.exe" >&2
  exit 1
}
"${TOOLS}/protoc" --proto_path=proto \
  --java_out=backend/src/generated/java \
  --plugin=protoc-gen-grpc-java="${TOOLS}/grpc-plugin" \
  --grpc-java_out=backend/src/generated/java \
  proto/dreamy/identity/v1/identity.proto
echo "[regen] 完成:backend/src/generated/java"
