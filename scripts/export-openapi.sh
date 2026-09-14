#!/usr/bin/env bash
# =============================================================
# 脚本名称: export-openapi.sh
# 功能描述: 导出 server 的 OpenAPI 契约并校验与入库文件一致(防漂移门禁)
# 使用方式: bash scripts/export-openapi.sh          # 导出后 git diff 校验
#           bash scripts/export-openapi.sh --write  # 覆写入库 openapi.yaml
# =============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}/server"

if [ "${1:-}" = "--write" ]; then
    cargo run --bin dreamy-server -- --export-openapi
    echo "[openapi] openapi.yaml 已覆写(记得提交)"
    exit 0
fi

cargo run --bin dreamy-server -- --export-openapi >/dev/null
if ! git diff --quiet -- openapi.yaml 2>/dev/null; then
    echo "[openapi] 错误: 入库 openapi.yaml 与代码生成结果不一致(契约漂移)" >&2
    echo "[openapi]   执行 bash scripts/export-openapi.sh --write 后审查差异并提交" >&2
    git diff -- openapi.yaml | head -30 >&2
    exit 1
fi
echo "[openapi] 契约一致:openapi.yaml 与代码同步"
