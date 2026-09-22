#!/usr/bin/env bash
# =============================================================
# 脚本名称: test-gateway-routes.sh
# 功能描述: 网关分流路由矩阵冒烟测试(Gate A 门禁):
#           用真实 nginx/gateway.conf.template + 4 个 stub 上游,
#           断言每条身份域前缀路由到 server、负向样本仍回 backend、
#           页面路由不变;覆盖 OPTIONS 预检与 URL 编码路径。
#           变更网关前缀/新增路由必须同步本矩阵。
# 使用方式: bash scripts/test-gateway-routes.sh
# 依赖环境: Docker(拉起临时 nginx + http-echo stub,测试完自动清理)
# =============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
NET="dreamy-routes-test"
GW="dreamy-routes-gw"
GW_PORT=18443

cleanup() {
  docker rm -f "${GW}" >/dev/null 2>&1 || true
  docker network rm "${NET}" >/dev/null 2>&1 || true
  rm -rf "${TMP_DIR:-}"
}
trap cleanup EXIT

TMP_DIR="$(mktemp -d)"
openssl req -x509 -newkey rsa:2048 -nodes -days 1 \
  -keyout "${TMP_DIR}/privkey.pem" -out "${TMP_DIR}/fullchain.pem" \
  -subj "/CN=localhost" >/dev/null 2>&1

docker network create "${NET}" >/dev/null

# stub 上游:各自回显身份标记,用于断言路由归属
run_stub() { # name port text
  docker run -d --rm --name "rt-$1" --network "${NET}" \
    hashicorp/http-echo -listen=":$2" -text="$3" >/dev/null
}
run_stub store 3000 "UPSTREAM-STORE"
run_stub admin 80 "UPSTREAM-ADMIN"
run_stub backend 18081 "UPSTREAM-BACKEND"  # Phase B:Java 已删;stub 留作矩阵对照(无路由指向)
run_stub server 18082 "UPSTREAM-SERVER"

# 真实网关模板渲染(与 compose 同机制:nginx 官方镜像 envsubst-on-templates)
docker run -d --rm --name "${GW}" --network "${NET}" \
  -p "127.0.0.1:${GW_PORT}:5173" \
  -e ADMIN_API_SECRET="s3cr3t42" \
  -e STORE_UPSTREAM="http://rt-store:3000" \
  -e ADMIN_UPSTREAM="http://rt-admin:80" \
  -e BACKEND_UPSTREAM="http://rt-backend:18081" \
  -e SERVER_UPSTREAM="http://rt-server:18082" \
  -v "${PROJECT_ROOT}/nginx/gateway.conf.template:/etc/nginx/templates/default.conf.template:ro" \
  -v "${TMP_DIR}:/etc/nginx/certs:ro" \
  nginx:1.27-alpine >/dev/null

for _ in $(seq 1 20); do
  if docker exec "${GW}" nginx -t >/dev/null 2>&1; then break; fi
  sleep 0.5
done
docker exec "${GW}" nginx -t

PASS=0; FAIL=0
assert() { # method path expected_marker
  local method="$1" path="$2" expect="$3"
  local body
  body="$(curl -sk -X "${method}" --max-time 5 "https://127.0.0.1:${GW_PORT}${path}" || true)"
  if echo "${body}" | grep -q "${expect}"; then
    PASS=$((PASS + 1))
  else
    FAIL=$((FAIL + 1))
    echo "[routes] FAIL ${method} ${path} → 期望命中 ${expect},实际: $(echo "${body}" | head -c 120)" >&2
  fi
}

echo "[routes] ── 身份域 → server ──"
assert GET  /api/store/auth/config            UPSTREAM-SERVER
assert POST /api/store/auth/otp/send          UPSTREAM-SERVER
assert POST /api/store/auth/oidc/google/callback UPSTREAM-SERVER
assert POST /api/store/auth/refresh           UPSTREAM-SERVER
assert GET  /api/store/account/profile        UPSTREAM-SERVER
assert GET  /api/store/account/identities     UPSTREAM-SERVER
echo "[routes] ── admin 加密前缀(secret 段)──"
assert POST /s3cr3t42/api/admin/auth/login             UPSTREAM-SERVER
assert GET  /s3cr3t42/api/admin/auth/me                UPSTREAM-SERVER
assert GET  /s3cr3t42/api/admin/auth/permissions       UPSTREAM-SERVER
assert GET  /s3cr3t42/api/admin/auth-config            UPSTREAM-SERVER
assert GET  /s3cr3t42/api/admin/admins                 UPSTREAM-SERVER
assert GET  /s3cr3t42/api/admin/roles                  UPSTREAM-SERVER
assert GET  /s3cr3t42/api/admin/permissions            UPSTREAM-SERVER
assert GET  /s3cr3t42/api/admin/users                  UPSTREAM-SERVER
assert GET  /s3cr3t42/api/admin/users/1                UPSTREAM-SERVER
assert GET  /s3cr3t42/api/admin/operation-logs         UPSTREAM-SERVER
assert GET  /s3cr3t42/api/admin/operation-logs/export  UPSTREAM-SERVER
assert GET  /s3cr3t42/api/admin/orders                 UPSTREAM-SERVER
assert GET  /s3cr3t42/api/admin/products               UPSTREAM-SERVER
assert GET  /s3cr3t42/api/admin/navigation             UPSTREAM-SERVER

echo "[routes] ── 旧裸 admin 路径对外关闭(统一 404)──"
assert GET  /api/admin/auth/login                      "404 Not Found"
assert GET  /api/admin/auth-config                     "404 Not Found"
assert GET  /api/admin/products                        "404 Not Found"
assert GET  /api/admin                                 "404 Not Found"

echo "[routes] ── 负向样本 → backend(身份域之外不得误吸)──"
assert GET  /api/store/products               UPSTREAM-SERVER
assert GET  /api/store/orders                 UPSTREAM-SERVER
assert GET  /api/store/wishlists              UPSTREAM-SERVER
assert GET  /api/store/browse-history         UPSTREAM-SERVER
assert GET  /api/store/showrooms/1            UPSTREAM-SERVER
# actuator/health → 302 /readyz(Phase B 删 Java 后由 server 承担健康检查)
assert GET  /readyz                            UPSTREAM-SERVER

echo "[routes] ── 页面路由不变 ──"
assert GET  /                                 UPSTREAM-STORE
assert GET  /admin/                           UPSTREAM-ADMIN

echo "[routes] ── OPTIONS 预检与编码路径 ──"
assert OPTIONS /api/store/auth/otp/send       UPSTREAM-SERVER
assert OPTIONS /s3cr3t42/api/admin/auth/login UPSTREAM-SERVER
assert OPTIONS /api/store/products            UPSTREAM-SERVER
assert GET  "/api/store/auth/%63onfig"        UPSTREAM-SERVER
assert GET  /api/store/account/profile/       UPSTREAM-SERVER

echo "[routes] 结果: PASS=${PASS} FAIL=${FAIL}"
[ "${FAIL}" -eq 0 ] || exit 1
