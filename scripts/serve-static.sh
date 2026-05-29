#!/usr/bin/env bash
# serve-static.sh — 构建并以静态 HTTP 服务托管 Maison Eden 原型产物
set -euo pipefail
ROOT="$(git rev-parse --show-toplevel 2>/dev/null || cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/hhspec/prototype"
PORT="${1:-4173}"
if [[ ! -d dist ]]; then
  echo "未发现 dist/，正在构建..."
  npm_config_registry=https://registry.npmmirror.com pnpm install --prefer-offline
  pnpm build
fi
echo "静态产物服务于 http://localhost:${PORT}"
echo "（hash 路由，任意静态服务器均可；按 Ctrl+C 停止）"
cd dist && python3 -m http.server "$PORT"
