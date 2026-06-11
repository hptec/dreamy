#!/usr/bin/env bash
# =============================================================
# 脚本名称: frontend-build.sh
# 功能描述: 构建前端两个门户（portal-store · Next.js / portal-admin · Vue3+Vite）
# 使用方式: bash scripts/frontend-build.sh [store|admin]（缺省构建全部）
# 依赖环境: Node 18+、pnpm
# =============================================================
set -euo pipefail

TARGET="${1:-all}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
export npm_config_registry="https://registry.npmmirror.com"

build_app() {
  local name="$1" dir="${PROJECT_ROOT}/frontend/$1"
  if [ ! -d "$dir" ]; then
    echo "[frontend-build] 未找到 $dir，跳过" >&2
    return 1
  fi
  cd "$dir"
  if [ ! -d node_modules ]; then
    echo "[frontend-build] $name 安装依赖（pnpm）..."
    pnpm install
  fi
  echo "[frontend-build] 构建 $name ..."
  pnpm build
}

case "$TARGET" in
  store) build_app portal-store ;;
  admin) build_app portal-admin ;;
  all)   build_app portal-store && build_app portal-admin ;;
  *)     echo "用法: bash scripts/frontend-build.sh [store|admin]" >&2; exit 1 ;;
esac
exec echo "[frontend-build] 完成"
