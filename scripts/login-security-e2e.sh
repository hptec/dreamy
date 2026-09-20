#!/usr/bin/env bash
# =============================================================
# 脚本名称: login-security-e2e.sh(2026-09-19 安全加固 E2E 回归)
# 断言面:V1 XFF 伪造拦截 / V4 refresh 重用整链撤销 / 42904 verify 频控 /
#        auth_config 新 6 字段读写 / 安全中心 API / OTP 全链 / admin 登录
# 使用方式: bash scripts/login-security-e2e.sh(需 7 容器 healthy)
# =============================================================
set -uo pipefail
cd "$(dirname "$0")/.."
set -a; source .env.deploy; set +a

GW="https://127.0.0.1:${STORE_PORT:-5173}"
SECRET="${ADMIN_API_SECRET}"
PASS=0; FAIL=0

jqget() { python3 -c "import json,sys;d=json.load(sys.stdin);print(eval(sys.argv[1]))" "$1" 2>/dev/null; }

check() { # check <名> <条件结果 0/1>
  if [ "$2" = "1" ]; then echo "  ✓ $1"; PASS=$((PASS+1)); else echo "  ✗ $1"; FAIL=$((FAIL+1)); fi
}

msql() { docker compose --env-file .env.deploy exec -T -e MQ="$1" mysql sh -c \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" dreamy_server -N -e "$MQ"' 2>/dev/null; }

EMAIL="e2e-sec-$(date +%s)@dreamy.test"

# 测试可重复性:重置 IP 维度频控键(send 配额/verify 频控/退避)——网关注入的真实 IP 为
# Docker Desktop 宿主网关(192.168.65.1),故清全部 otp IP 键;仅测试环境使用
for PAT in 'otp:count:ip:*' 'otp:verify:ip:*' 'otp:backoff:ip:*'; do
  docker compose --env-file .env.deploy exec -T redis sh -c "redis-cli --scan --pattern '$PAT' | xargs -r redis-cli DEL" >/dev/null 2>&1
done

echo "═══ E2E-A OTP 全链(网关 HTTPS) ═══"
R=$(curl -sk "$GW/api/store/auth/config"); C=$(echo "$R" | jqget "d['code']"); check "config 读取 code=0" "$([ "$C" = "0" ] && echo 1)"
R=$(curl -sk -X POST "$GW/api/store/auth/otp/send" -H 'Content-Type: application/json' -d "{\"email\":\"$EMAIL\"}")
C=$(echo "$R" | jqget "d['code']"); check "sendOtp code=0" "$([ "$C" = "0" ] && echo 1)"
HASH=$(docker run --rm httpd:alpine htpasswd -nbB x '778899' 2>/dev/null | cut -d: -f2 | sed 's/\$/\\$/g' || true)
HASH=$(python3 -c "import bcrypt;print(bcrypt.hashpw(b'778899', bcrypt.gensalt(4)).decode())" 2>/dev/null || echo "$HASH")
msql "INSERT INTO otp_code (email,code_hash,length,expires_at,attempts,max_attempts,status,last_sent_at,version,created_at,updated_at) VALUES ('$EMAIL','$HASH',6,NOW()+INTERVAL 10 MINUTE,0,5,1,NOW(),0,NOW(),NOW());" >/dev/null
R=$(curl -sk -X POST "$GW/api/store/auth/otp/verify" -H 'Content-Type: application/json' -d "{\"email\":\"$EMAIL\",\"code\":\"778899\"}")
C=$(echo "$R" | jqget "d['code']"); NEW_RT=$(echo "$R" | jqget "d['data']['tokens']['refresh_token']" )
check "verifyOtp 全链登录 code=0(归并/会话/历史)" "$([ "$C" = "0" ] && echo 1)"
check "refresh_token 下发" "$([ -n "$NEW_RT" ] && [ "$NEW_RT" != "None" ] && echo 1)"

echo "═══ E2E-B V4 重用检测:旧 refresh 重放 → 40102 + 整链撤销 ═══"
R=$(curl -sk -X POST "$GW/api/store/auth/refresh" -H 'Content-Type: application/json' -d "{\"refresh_token\":\"$NEW_RT\"}")
C=$(echo "$R" | jqget "d['code']"); NEW2_RT=$(echo "$R" | jqget "d['data']['tokens']['refresh_token']")
check "首次刷新 code=0(旋转)" "$([ "$C" = "0" ] && echo 1)"
# 断链回归门禁(2026-09-19 抓到的存量 P0:第二次刷新曾必 40102)
R=$(curl -sk -X POST "$GW/api/store/auth/refresh" -H 'Content-Type: application/json' -d "{\"refresh_token\":\"$NEW2_RT\"}")
C=$(echo "$R" | jqget "d['code']"); NEW3_RT=$(echo "$R" | jqget "d['data']['tokens']['refresh_token']")
check "二连刷新 code=0(旋转链连续推进)" "$([ "$C" = "0" ] && echo 1)"
R=$(curl -sk -X POST "$GW/api/store/auth/refresh" -H 'Content-Type: application/json' -d "{\"refresh_token\":\"$NEW_RT\"}")
C=$(echo "$R" | jqget "d['code']")
check "旧 refresh 重放 → 40102" "$([ "$C" = "40102" ] && echo 1)"
# V4 整链撤销:被旋转掉的新链(NEW_RT 旋转出的 NEW2_RT)也应已失效
if [ -n "$NEW2_RT" ] && [ "$NEW2_RT" != "None" ]; then
  R=$(curl -sk -X POST "$GW/api/store/auth/refresh" -H 'Content-Type: application/json' -d "{\"refresh_token\":\"$NEW2_RT\"}")
  C=$(echo "$R" | jqget "d['code']")
  check "盗用触发整链撤销:旋转链后续 token 一并失效(40102)" "$([ "$C" = "40102" ] && echo 1)"
fi

echo "═══ E2E-C V7 verify IP 频控 42904 ═══"
CODE42904=0
for i in $(seq 1 40); do
  R=$(curl -sk -o /dev/null -w '%{http_code}' -X POST "$GW/api/store/auth/otp/verify" \
    -H 'Content-Type: application/json' -d "{\"email\":\"bench-flood-$i@load.test\",\"code\":\"000000\"}")
  [ "$R" = "429" ] && CODE42904=1 && break
done
check "verify 40 次/分钟内触发 429(42904)" "$CODE42904"

echo "═══ E2E-D V1 XFF 伪造拦截:伪造头不绕过 send IP 配额 ═══"
# 网关注入真实 IP(127.0.0.1),伪造 XFF 头应被无视;IP 配额 20/h,换 25 个不同 email 打满
HIT429=0
for i in $(seq 1 25); do
  R=$(curl -sk -o /dev/null -w '%{http_code}' -X POST "$GW/api/store/auth/otp/send" \
    -H 'Content-Type: application/json' -H "X-Forwarded-For: 10.9.9.$((i % 250 + 1))" \
    -d "{\"email\":\"xff-flood-$i@load.test\"}")
  [ "$R" = "429" ] && HIT429=$((HIT429+1))
done
check "伪造 25 个假 IP 仍触发 send IP 配额(42902,V1 生效)" "$([ "$HIT429" -ge 1 ] && echo 1)"

echo "═══ E2E-E auth_config 新字段读写 + 安全中心 API(admin) ═══"
R=$(curl -sk -X POST "$GW/$SECRET/api/admin/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"admin@dreamy.com","password":"Admin@123456"}')
TK=$(echo "$R" | jqget "d['data']['token']")
check "admin 登录(恒定响应三件套路径)" "$([ -n "$TK" ] && [ "$TK" != "None" ] && echo 1)"
AUTH="Authorization: Bearer $TK"
R=$(curl -sk "$GW/$SECRET/api/admin/auth-config" -H "$AUTH")
V=$(echo "$R" | jqget "d['data']['verify_ip_rate_per_minute']")
TTL=$(echo "$R" | jqget "d['data']['store_refresh_ttl_days']")
check "auth-config 新字段可读(verify=$V,refresh_ttl=$TTL)" "$([ -n "$V" ] && [ "$V" != "None" ] && [ -n "$TTL" ] && [ "$TTL" != "None" ] && echo 1)"
R=$(curl -sk -X PUT "$GW/$SECRET/api/admin/auth-config" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"verifyIpRatePerMinute":30,"attackAlertThreshold":20,"storeAccessTtlMinutes":120,"storeRefreshTtlDays":30,"adminAccessTtlHours":8}')
C=$(echo "$R" | jqget "d['code']")
check "auth-config 写回默认值 code=0" "$([ "$C" = "0" ] && echo 1)"
USERID=$(msql "SELECT user_id FROM identity_email WHERE email='$EMAIL';")
R=$(curl -sk "$GW/$SECRET/api/admin/users/$USERID" -H "$AUTH")
HASST=$(echo "$R" | python3 -c "import json,sys;d=json.load(sys.stdin);s=d['data']['sessions'];print(1 if s and 'status' in json.dumps(s) else 0)" 2>/dev/null)
check "会话列表 API 返回 sessions(含 status)" "$([ "$HASST" = "1" ] && echo 1)"

echo "═══ E2E-F 安全中心前端 ═══"
ST=$(curl -sk -o /dev/null -w '%{http_code}' "$GW/admin/")
check "admin SPA 200" "$([ "$ST" = "200" ] && echo 1)"

echo ""
echo "═══ 结果:PASS=$PASS FAIL=$FAIL ═══"
exit $([ "$FAIL" = "0" ] && echo 0 || echo 1)
