#!/usr/bin/env bash
# =============================================================
# 脚本名称: backend-api.sh
# 功能描述: 启动后端服务（内置 restart：端口占用检测 + kill + 启动）
# 使用方式: bash scripts/backend-api.sh [PORT]
# 默认端口: 18081
# 依赖环境: JDK 25（GraalVM）、Gradle Wrapper 9.3.1
# =============================================================
set -euo pipefail

PORT="${1:-18081}"

# 基于脚本自身位置定位项目根目录，避免依赖调用时的工作目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# 本地开发统一从 Git 忽略的环境文件加载敏感配置；部署环境仍由宿主/密钥管理器注入。
# 可用 BACKEND_ENV_FILE=/absolute/path/to/file 覆盖默认位置，便于 CI 或多套本地环境。
ENV_FILE="${BACKEND_ENV_FILE:-${PROJECT_ROOT}/.env.backend.local}"
if [ -f "${ENV_FILE}" ]; then
  echo "[backend] 加载本地环境文件: ${ENV_FILE}"
  set -a
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  set +a
fi

# JDK 25（identity-auth-fullstack 后端要求 GraalVM 25）
if [ -d "/Library/Java/JavaVirtualMachines/graalvm-jdk-25.0.2+10.1/Contents/Home" ]; then
  export JAVA_HOME="/Library/Java/JavaVirtualMachines/graalvm-jdk-25.0.2+10.1/Contents/Home"
fi

# 敏感配置没有代码库默认值。启动前必须显式注入，防止开发凭据被误带到部署环境。
missing=()
for variable in DB_PASSWORD STORE_JWT_SECRET ADMIN_JWT_SECRET DREAMY_GATEWAY_AES_KEY; do
  if [ -z "${!variable:-}" ]; then
    missing+=("${variable}")
  fi
done
if [ "${#missing[@]}" -gt 0 ]; then
  echo "[backend] 错误: 缺少必需环境变量: ${missing[*]}" >&2
  echo "[backend] 本地开发请复制 scripts/.env.backend.example 为 .env.backend.local 后填写现有值。" >&2
  echo "[backend] JWT 密钥需至少 32 字节且两端不同；AES 密钥需为 Base64 编码的 32 字节稳定密钥。" >&2
  exit 1
fi

echo "[backend] 检查端口 ${PORT} 占用..."
# 仅匹配 LISTEN 进程：lsof -t -i 会把连到该端口的客户端进程（浏览器/IM 等）也列出来，不能 kill
PIDS=$(lsof -t -i:"${PORT}" -sTCP:LISTEN 2>/dev/null || true)
if [ -n "${PIDS:-}" ]; then
  echo "[backend] 端口 ${PORT} 被占用 (PID: ${PIDS})，正在终止..."
  for P in ${PIDS}; do kill "${P}" 2>/dev/null || true; done
  # 等待端口真正释放，避免新进程 EADDRINUSE 启动失败
  for _ in $(seq 1 20); do
    lsof -t -i:"${PORT}" -sTCP:LISTEN >/dev/null 2>&1 || break
    sleep 1
  done
  if lsof -t -i:"${PORT}" -sTCP:LISTEN >/dev/null 2>&1; then
    for P in $(lsof -t -i:"${PORT}" -sTCP:LISTEN 2>/dev/null); do kill -9 "${P}" 2>/dev/null || true; done
    sleep 1
  fi
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
