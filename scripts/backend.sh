#!/usr/bin/env bash
# =============================================================
# 脚本名称: backend.sh
# 功能描述: 启动后端服务（内置 restart：端口占用检测 + kill + 启动）
# 使用方式: bash scripts/backend.sh [PORT]
# 默认端口: 8080
# 依赖环境: Java 17+、Gradle 8+
# =============================================================
set -euo pipefail

PORT="${1:-8080}"

echo "[backend] 检查端口 ${PORT} 占用..."
PID=$(lsof -t -i:"${PORT}" 2>/dev/null || true)
if [ -n "${PID:-}" ]; then
  echo "[backend] 端口 ${PORT} 被占用 (PID: ${PID})，正在终止..."
  kill "${PID}" 2>/dev/null || true
  sleep 1
fi

echo "[backend] 启动后端服务 (端口: ${PORT})..."
# 进入后端目录并启动（假设后端在 backend/ 或根目录下）
if [ -d "backend" ]; then
  cd backend
fi

if [ -f "gradlew" ]; then
  exec ./gradlew bootRun --args="--server.port=${PORT}"
elif command -v gradle &> /dev/null; then
  exec gradle bootRun --args="--server.port=${PORT}"
else
  echo "[backend] 错误: 未找到 gradlew 或 gradle 命令"
  exit 1
fi
