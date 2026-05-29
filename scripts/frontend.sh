#!/usr/bin/env bash
# =============================================================
# 脚本名称: frontend.sh
# 功能描述: 启动前端开发服务（内置 restart：端口占用检测 + kill + 启动）
# 使用方式: bash scripts/frontend.sh [PORT]
# 默认端口: 5173
# 依赖环境: Node 18+、pnpm
# =============================================================
set -euo pipefail

PORT="${1:-5173}"

echo "[frontend] 检查端口 ${PORT} 占用..."
PID=$(lsof -t -i:"${PORT}" 2>/dev/null || true)
if [ -n "${PID:-}" ]; then
  echo "[frontend] 端口 ${PORT} 被占用 (PID: ${PID})，正在终止..."
  kill "${PID}" 2>/dev/null || true
  sleep 1
fi

echo "[frontend] 启动前端服务 (端口: ${PORT})..."
# 进入前端目录并启动（假设前端在 frontend/ 或根目录下）
if [ -d "frontend" ]; then
  cd frontend
elif [ -d "web" ]; then
  cd web
fi

if [ ! -d "node_modules" ]; then
  echo "[frontend] 未找到 node_modules，正在执行 pnpm install..."
  pnpm install
fi

exec pnpm dev -- --port "${PORT}"
