#!/usr/bin/env bash
# =============================================================
# 脚本名称: release.sh
# 功能描述: 编译 + 构建三镜像 + 推送阿里云 ACR(双 tag: latest + <时间戳>-<短SHA>)
#
# 模式(默认远程):
#   远程模式(推荐,稳定): 本地编译 JAR/admin dist → scp 到香港服务器 →
#     服务器原生 x86_64 构建 linux/amd64 镜像(scripts/remote-build.sh)→ docker push ACR。
#     服务器 Docker Hub 直连快,且产物直 push 免疫 ACR 对 OCI attestation 的拒收。
#   --local 本地模式: 本地 buildx linux/amd64 构建(依赖 Rosetta 与 Docker Hub 拉取速度,不稳定时用远程)。
#   --load 本地验证: 不推送,平台跟随本机,docker compose --env-file .env.deploy up -d 直接跑。
#
# 使用方式: bash scripts/release.sh --env .env.deploy.prod          # 生产发布(远程构建+推送)
#           bash scripts/release.sh --env .env.deploy.prod --local  # 本地构建+推送
#           bash scripts/release.sh --load                           # 本地验证镜像
# 依赖环境: GraalVM JDK25、pnpm、远程模式需 SSH 免密到服务器且服务器已 docker login ACR
# 配置来源: --env 指定(默认 .env.deploy;生产用 .env.deploy.prod,含 DEPLOY_SSH)
# 发布完成后: 服务器执行 bash scripts/deploy.sh
# =============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

MODE="remote"
LOAD_ONLY=0
ENV_FILE="${RELEASE_ENV_FILE:-${PROJECT_ROOT}/.env.deploy}"
while [ $# -gt 0 ]; do
  case "$1" in
    --local) MODE="local" ;;
    --load) MODE="local"; LOAD_ONLY=1 ;;
    --env)
      [ -n "${2:-}" ] || { echo "[release] --env 需要配置文件路径" >&2; exit 1; }
      ENV_FILE="$2"; shift ;;
    *) echo "[release] 未知参数: $1" >&2; exit 1 ;;
  esac
  shift
done
if [ ! -f "${ENV_FILE}" ]; then
  echo "[release] 未找到 ${ENV_FILE}" >&2
  echo "[release] 请先执行: cp .env.deploy.example .env.deploy 并填写(--load 模式 ACR_NAMESPACE 可填任意值)" >&2
  exit 1
fi
set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

# ── 公网地址派生:只在 PUBLIC_STORE_URL/PUBLIC_ADMIN_URL 填一次,构建期内联变量默认取同值 ──
NEXT_PUBLIC_SITE_URL="${NEXT_PUBLIC_SITE_URL:-${PUBLIC_STORE_URL:-}}"
VITE_STORE_BASE_URL="${VITE_STORE_BASE_URL:-${PUBLIC_STORE_URL:-}}"

# ── 前置校验 ─────────────────────────────────────────────
missing=()
for v in ACR_REGISTRY ACR_NAMESPACE; do
  if [ -z "$(eval "echo \${${v}:-}")" ]; then missing+=("${v}"); fi
done
if [ "${#missing[@]}" -gt 0 ]; then
  echo "[release] 错误: ${ENV_FILE} 缺少必填变量: ${missing[*]}" >&2
  exit 1
fi

if [ "${MODE}" = "remote" ] && [ "${LOAD_ONLY}" = "0" ]; then
  if [ -z "${DEPLOY_SSH:-}" ]; then
    echo "[release] 错误: 远程模式需要在配置中设置 DEPLOY_SSH(如 root@<服务器IP>)" >&2
    exit 1
  fi
  if ! ssh -o ConnectTimeout=10 -o BatchMode=yes "${DEPLOY_SSH}" true 2>/dev/null; then
    echo "[release] 错误: 无法 SSH 免密连接 ${DEPLOY_SSH}" >&2
    exit 1
  fi
fi

if [ "${MODE}" = "local" ] && ! docker buildx version >/dev/null 2>&1; then
  echo "[release] 错误: docker buildx 不可用,请确认 Docker Desktop 已启动" >&2
  exit 1
fi

if [ -n "$(git -C "${PROJECT_ROOT}" status --porcelain --untracked-files=no)" ]; then
  echo "[release] 警告: 存在未提交改动,镜像内容可能与提交版本不一致" >&2
fi

# ── 平台与 tag ───────────────────────────────────────────
SHORT_SHA="$(git -C "${PROJECT_ROOT}" rev-parse --short HEAD)"
RELEASE_TAG="$(date +%Y%m%d%H%M)-${SHORT_SHA}"
PLATFORM="${PLATFORM:-linux/amd64}"
if [ "${LOAD_ONLY}" = "1" ]; then
  # 本地验证用本机平台:原生运行最快且不需要模拟
  case "$(uname -m)" in
    arm64)  PLATFORM="linux/arm64" ;;
    x86_64) PLATFORM="linux/amd64" ;;
  esac
  echo "[release] --load 模式:平台 ${PLATFORM},不推送(镜像名与 compose 一致,可直接 up)"
fi

# ── ① 后端:本地编译 bootJar ─────────────────────────────
echo "[release] 编译后端 identity-app.jar ..."
if [ -d "/Library/Java/JavaVirtualMachines/graalvm-jdk-25.0.2+10.1/Contents/Home" ]; then
  export JAVA_HOME="/Library/Java/JavaVirtualMachines/graalvm-jdk-25.0.2+10.1/Contents/Home"
fi
(
  cd "${PROJECT_ROOT}/backend"
  # --no-build-cache:一次性发布构建不依赖增量缓存,规避外置卷上 cache 打包 stat 异常
  ./gradlew bootJar --no-daemon --no-build-cache -q
)
if [ ! -f "${PROJECT_ROOT}/backend/build/libs/identity-app.jar" ]; then
  echo "[release] 错误: bootJar 产物缺失" >&2
  exit 1
fi

# ── ② admin:本地构建静态产物(同源模式,VITE_API_BASE_URL 置空;/admin/ 子路径部署)──
echo "[release] 构建 portal-admin 静态产物 ..."
export npm_config_registry="https://registry.npmmirror.com"
(
  cd "${PROJECT_ROOT}/frontend/portal-admin"
  if [ ! -d node_modules ]; then pnpm install --frozen-lockfile; fi
  VITE_API_BASE_URL="" VITE_STORE_BASE_URL="${VITE_STORE_BASE_URL:-}" ADMIN_BASE="/admin/" pnpm build
)

# ── ③a 远程模式:scp 产物 → 服务器构建推送 ────────────────
if [ "${MODE}" = "remote" ]; then
  REMOTE_DIR="${DEPLOY_DIR:-/opt/dreamy}"
  echo "[release] 远程模式:同步代码到 ${DEPLOY_SSH}:${REMOTE_DIR} ..."
  ssh -o ConnectTimeout=15 "${DEPLOY_SSH}" "cd ${REMOTE_DIR} && git pull --ff-only -q"

  echo "[release] 传输 JAR 与 admin dist ..."
  ssh -o ConnectTimeout=15 "${DEPLOY_SSH}" "mkdir -p ${REMOTE_DIR}/backend/build/libs ${REMOTE_DIR}/frontend/portal-admin"
  scp -o ConnectTimeout=15 -q "${PROJECT_ROOT}/backend/build/libs/identity-app.jar" "${DEPLOY_SSH}:${REMOTE_DIR}/backend/build/libs/"
  scp -o ConnectTimeout=15 -q -r "${PROJECT_ROOT}/frontend/portal-admin/dist" "${DEPLOY_SSH}:${REMOTE_DIR}/frontend/portal-admin/"

  echo "[release] 触发服务器构建(后台 nohup,断线不影响)..."
  ssh -o ConnectTimeout=15 "${DEPLOY_SSH}" "cd ${REMOTE_DIR} && nohup bash scripts/remote-build.sh > /tmp/remote-build.log 2>&1 & echo \"[release] 构建进程 PID=\$!\""

  # 轮询远程构建日志(SSH 短连接,抖动自动重试)
  echo "[release] 跟踪构建进度(超时 40 分钟)..."
  waited=0
  while [ "${waited}" -lt 2400 ]; do
    sleep 20; waited=$((waited + 20))
    OUT="$(ssh -o ConnectTimeout=10 -o BatchMode=yes "${DEPLOY_SSH}" \
      'grep -E "^\[remote-build\]|^ERROR|error from registry|failed to" /tmp/remote-build.log 2>/dev/null | tail -2' 2>/dev/null || true)"
    [ -z "${OUT}" ] && continue
    if echo "${OUT}" | grep -q "ALL DONE"; then
      echo "${OUT}" | tail -1
      echo "[release] 完成。"
      echo "[release] 已推送 ${ACR_REGISTRY}/${ACR_NAMESPACE}/dreamy-{backend,store,admin}:latest 及对应时间戳 tag(见上方输出)"
      echo "[release] 服务器执行: cd ${REMOTE_DIR} && bash scripts/deploy.sh"
      exit 0
    fi
    if echo "${OUT}" | grep -qE "^ERROR|error from registry|failed to"; then
      echo "[release] 远程构建失败:" >&2; echo "${OUT}" >&2
      echo "[release] 完整日志: ssh ${DEPLOY_SSH} cat /tmp/remote-build.log" >&2
      exit 1
    fi
    echo "  [${waited}s] $(echo "${OUT}" | tail -1)"
  done
  echo "[release] 错误: 远程构建超时(40 分钟)" >&2
  echo "[release] 查看日志: ssh ${DEPLOY_SSH} tail -50 /tmp/remote-build.log" >&2
  exit 1
fi

# ── ③b 本地模式:buildx 三镜像 ───────────────────────────
PUSH_FLAG="--push"
if [ "${LOAD_ONLY}" = "1" ]; then PUSH_FLAG="--load"; fi

build_image() {
  local name="$1" dir="$2"; shift 2
  local repo="${ACR_REGISTRY}/${ACR_NAMESPACE}/dreamy-${name}"
  echo "[release] 构建镜像 ${repo} (platform: ${PLATFORM}, tags: latest / ${RELEASE_TAG}) ..."
  # shellcheck disable=SC2086
  # --provenance/--sbom off:新 buildkit 默认附 OCI attestation manifest,ACR 个人版不识别(unknown manifest class)
  docker buildx build \
    --platform "${PLATFORM}" \
    --provenance=false --sbom=false \
    -t "${repo}:latest" \
    -t "${repo}:${RELEASE_TAG}" \
    "$@" \
    ${PUSH_FLAG} \
    "${dir}"
}

build_image backend "${PROJECT_ROOT}/backend"
build_image admin "${PROJECT_ROOT}/frontend/portal-admin"
build_image store "${PROJECT_ROOT}/frontend/portal-store" \
  --build-arg NEXT_PUBLIC_SITE_URL="${NEXT_PUBLIC_SITE_URL:-}" \
  --build-arg NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY="${NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY:-}" \
  --build-arg NEXT_PUBLIC_GA4_ID="${NEXT_PUBLIC_GA4_ID:-}" \
  --build-arg ADMIN_ORIGIN="${ADMIN_ORIGIN:-${PUBLIC_STORE_URL:-}}"

echo "[release] 完成。"
if [ "${LOAD_ONLY}" = "0" ]; then
  echo "[release] 已推送 ${ACR_REGISTRY}/${ACR_NAMESPACE}/dreamy-{backend,store,admin}:{latest,${RELEASE_TAG}}"
  echo "[release] 服务器执行: bash scripts/deploy.sh"
else
  echo "[release] 本地验证: docker compose --env-file .env.deploy up -d"
fi
