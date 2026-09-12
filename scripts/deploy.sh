#!/usr/bin/env bash
# =============================================================
# 脚本名称: deploy.sh
# 功能描述: 香港服务器一键部署(不在服务器上编译)
#           同步编排文件 → 拉取 ACR 镜像 → 替换容器 → 健康检查 → 清理旧镜像
# 使用方式: bash scripts/deploy.sh
# 前置条件: 服务器已装 docker + git、已 clone 仓库、.env.deploy 已填写、已 docker login ACR
# 回滚方式: .env.deploy 改 IMAGE_TAG=<旧时间戳-短SHA>(见 release.sh 输出历史 tag) 后重跑本脚本
# =============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

ENV_FILE=".env.deploy"
if [ ! -f "${ENV_FILE}" ]; then
  echo "[deploy] 未找到 ${ENV_FILE},已从模板生成,请填写后重跑:" >&2
  echo "[deploy]   vim ${ENV_FILE}" >&2
  cp .env.deploy.example "${ENV_FILE}"
  exit 1
fi
set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

echo "[deploy] 同步编排文件 (git pull)..."
git pull --ff-only

echo "[deploy] 校验 compose 配置..."
if ! docker compose --env-file "${ENV_FILE}" config --quiet; then
  echo "[deploy] 错误: compose 配置校验失败,检查 .env.deploy 变量" >&2
  exit 1
fi

echo "[deploy] 拉取最新镜像..."
if ! docker compose --env-file "${ENV_FILE}" pull; then
  echo "[deploy] 拉取失败:若为认证错误,先执行 docker login ${ACR_REGISTRY:-registry.cn-hongkong.aliyuncs.com}" >&2
  exit 1
fi

echo "[deploy] 启动/替换容器..."
docker compose --env-file "${ENV_FILE}" up -d

wait_http() {
  local label="$1" url="$2" timeout="${3:-180}"
  local waited=0
  printf "[deploy] 等待 %s 就绪 (%s) " "${label}" "${url}"
  while [ "${waited}" -lt "${timeout}" ]; do
    if curl -fsSk --max-time 5 "${url}" >/dev/null 2>&1; then
      echo "OK (${waited}s)"
      return 0
    fi
    printf "."
    sleep 5
    waited=$((waited + 5))
  done
  echo "超时 ${timeout}s" >&2
  echo "[deploy] 排查: docker compose --env-file ${ENV_FILE} ps && docker compose logs backend --tail 100" >&2
  return 1
}

# 后端含 JVM 启动 + DdlAuto 自动建表,给足时间(回环端口,不对外)
wait_http "backend" "http://127.0.0.1:${BACKEND_PORT:-18081}/actuator/health" 180
# 网关已 TLS 化:探活打 127.0.0.1 而证书 CN 是域名,curl -k 免校验
wait_http "portal-store" "https://127.0.0.1:${STORE_PORT:-5173}/" 60
# admin 经网关 /admin/ 路径(单端口分流,不映射独立宿主端口)
wait_http "portal-admin" "https://127.0.0.1:${STORE_PORT:-5173}/admin/" 60

echo "[deploy] 清理旧镜像层..."
docker image prune -f

echo "[deploy] 部署完成 (IMAGE_TAG=${IMAGE_TAG:-latest}):"
echo "  消费端门户: ${PUBLIC_STORE_URL:-http://<服务器IP>:${STORE_PORT:-5173}}"
echo "  管理后台:   ${PUBLIC_ADMIN_URL:-http://<服务器IP>:${STORE_PORT:-5173}/admin}"
echo "  后端 API:   http://127.0.0.1:${BACKEND_PORT:-18081} (回环,公网经网关 /api)"
echo "  后端日志:   tail -f data/logs/backend/identity.log"
