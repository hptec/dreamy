#!/usr/bin/env bash
# =============================================================
# 脚本名称: remote-build.sh
# 功能描述: 在香港服务器(x86_64 原生 amd64)上构建三镜像并推送 ACR,
#           由本地 release.sh 远程模式触发(JAR/dist 已 scp 就位),也可服务器手动执行。
#           背景:本地 Mac 上 buildx 模拟拉取基础镜像回源慢且 Rosetta 版本敏感,
#           服务器原生构建 + Docker Hub 直连最稳,产物直接 docker push(免疫 ACR 拒收 OCI attestation)。
# 使用方式: bash scripts/remote-build.sh          # 在服务器 /opt/dreamy 下执行
# 依赖环境: 服务器已装 docker/compose、已 docker login ACR、已 clone 仓库、.env.deploy 已填写
# 日志:     本脚本幂等,日志直接输出(stdout 供 release.sh 采集)
# =============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

ENV_FILE=".env.deploy"
if [ ! -f "${ENV_FILE}" ]; then
  echo "[remote-build] 未找到 ${ENV_FILE}" >&2
  exit 1
fi
set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

for v in ACR_REGISTRY ACR_NAMESPACE; do
  if [ -z "$(eval "echo \${${v}:-}")" ]; then
    echo "[remote-build] 错误: .env.deploy 缺少 ${v}" >&2
    exit 1
  fi
done

TAG="$(date +%Y%m%d%H%M)-$(git rev-parse --short HEAD)"
REPO="${ACR_REGISTRY}/${ACR_NAMESPACE}"

# 前置产物校验(JAR/dist 由 release.sh scp 上来,或服务器手动放置)
if [ ! -f backend/build/libs/identity-app.jar ]; then
  echo "[remote-build] 错误: backend/build/libs/identity-app.jar 缺失(应由 release.sh 传输)" >&2
  exit 1
fi
if [ ! -f frontend/portal-admin/dist/index.html ]; then
  echo "[remote-build] 错误: frontend/portal-admin/dist 缺失" >&2
  exit 1
fi

build_and_push() {
  local name="$1" dir="$2"; shift 2
  echo "[remote-build] ${name} 构建开始 $(date +%T)"
  # --provenance/--sbom off:ACR 个人版不识别 OCI attestation manifest
  docker buildx build --platform linux/amd64 --provenance=false --sbom=false \
    -t "${REPO}/dreamy-${name}:latest" -t "${REPO}/dreamy-${name}:${TAG}" \
    --load "$@" "${dir}"
  echo "[remote-build] ${name} 推送开始 $(date +%T)"
  docker push "${REPO}/dreamy-${name}:latest"
  docker push "${REPO}/dreamy-${name}:${TAG}"
  echo "[remote-build] ${name} 推送完成 $(date +%T)"
}

build_and_push backend backend/
build_and_push admin frontend/portal-admin/
build_and_push store frontend/portal-store/ \
  --build-arg NEXT_PUBLIC_SITE_URL="${PUBLIC_STORE_URL:-}" \
  --build-arg NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY="${NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY:-}" \
  --build-arg NEXT_PUBLIC_GA4_ID="${NEXT_PUBLIC_GA4_ID:-}" \
  --build-arg ADMIN_ORIGIN="${ADMIN_ORIGIN:-${PUBLIC_STORE_URL:-}}"

echo "[remote-build] ALL DONE TAG=${TAG}"
