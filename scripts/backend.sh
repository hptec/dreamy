#!/usr/bin/env bash
# =============================================================
# 脚本名称: backend.sh
# 功能描述: 启动后端服务（内置 restart：端口占用检测 + kill + 启动）
# 使用方式: bash scripts/backend.sh [PORT]
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

if [ -f "gradlew" ]; then
  ./gradlew bootRun --args="--server.port=${PORT}" &
elif command -v gradle &> /dev/null; then
  gradle bootRun --args="--server.port=${PORT}" &
else
  echo "[backend] 错误: 未找到 gradlew 或 gradle 命令"
  exit 1
fi

GRADLE_PID=$!
echo "[backend] bootRun 已后台启动 (PID: ${GRADLE_PID})，开始跟踪日志: ${LOG_FILE}"
echo "[backend] (Ctrl-C 将同时停止日志跟踪与后端服务)"

# Ctrl-C / 终止信号时，连带停止 bootRun 及其监听端口的 Java 子进程
cleanup() {
  echo ""
  echo "[backend] 收到停止信号，正在停止后端服务 (PID: ${GRADLE_PID})..."
  kill "${GRADLE_PID}" 2>/dev/null || true
  # bootRun 会派生监听端口的 Java 子进程，按端口兜底清理
  PORT_PID=$(lsof -t -i:"${PORT}" 2>/dev/null || true)
  if [ -n "${PORT_PID:-}" ]; then
    kill "${PORT_PID}" 2>/dev/null || true
  fi
  # 一并停止后台 tail，避免其变成孤儿进程
  kill "${TAIL_PID:-}" 2>/dev/null || true
  exit 0
}
trap cleanup INT TERM

# 启动后立刻跟踪日志；-F 容忍文件尚未创建/轮转
# 用后台 tail + wait（而非 exec），确保 trap 能在 Ctrl-C 时执行 cleanup
tail -F "${LOG_FILE}" &
TAIL_PID=$!
wait "${TAIL_PID}"
