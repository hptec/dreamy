#!/usr/bin/env bash
# =============================================================
# 脚本名称: backend-api.sh
# 功能描述: 启动后端服务（内置 restart：端口占用检测 + kill + 启动）
# 使用方式: bash scripts/backend-api.sh [PORT]
# 默认端口: 8080
# 依赖环境: JDK 25（GraalVM）、Gradle Wrapper 9.3.1
# =============================================================
set -euo pipefail

PORT="${1:-8080}"

# 基于脚本自身位置定位项目根目录，避免依赖调用时的工作目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# JDK 25（identity-auth-fullstack 后端要求 GraalVM 25）
if [ -d "/Library/Java/JavaVirtualMachines/graalvm-jdk-25.0.2+10.1/Contents/Home" ]; then
  export JAVA_HOME="/Library/Java/JavaVirtualMachines/graalvm-jdk-25.0.2+10.1/Contents/Home"
fi

echo "[backend] 检查端口 ${PORT} 占用..."
PID=$(lsof -t -i:"${PORT}" 2>/dev/null || true)
if [ -n "${PID:-}" ]; then
  echo "[backend] 端口 ${PORT} 被占用 (PID: ${PID})，正在终止..."
  kill "${PID}" 2>/dev/null || true
  sleep 1
fi

echo "[backend] 启动后端服务 (端口: ${PORT})..."
# 进入后端目录并启动（单一入口模块 :app）
cd "${PROJECT_ROOT}/backend"

# 日志文件：application.yml 配置 logging.file.name=../logs/identity.log（相对 backend/，即项目根 logs/）
LOG_FILE="${PROJECT_ROOT}/logs/identity.log"
mkdir -p "${PROJECT_ROOT}/logs"
# 清空旧日志，确保 tail 跟踪到的是本次启动的输出
: > "${LOG_FILE}"

# exec 前台运行主进程：信号直达 gradle/bootRun，Ctrl-C 即停止服务；
# 日志仍按 application.yml 写入 ${LOG_FILE}，同时输出到当前终端
echo "[backend] bootRun 前台启动，日志文件: ${LOG_FILE}（Ctrl-C 停止服务）"
if [ -f "gradlew" ]; then
  exec ./gradlew bootRun --args="--server.port=${PORT}"
elif command -v gradle &> /dev/null; then
  exec gradle bootRun --args="--server.port=${PORT}"
else
  echo "[backend] 错误: 未找到 gradlew 或 gradle 命令"
  exit 1
fi
