#!/usr/bin/env bash
# =============================================================================
# portal-api-integration L3 E2E API smoke (七域，真实环境零 Mock)
# 前置：后端 bootRun（dev stub 模式）已监听 :8080，pd-mysql/pd-redis 运行中。
# 用法：bash tests/api-integration/portal-api-smoke.sh [BASE_URL]
# 可重复执行：动态 RUN_ID 命名、幂等端点、临时数据用后清理（标签/维度/Showroom/承运商状态还原/汇率还原）。
# 依赖：curl、jq
# =============================================================================
set -u
BASE="${1:-http://localhost:8080}"
LOG_FILE="${OTP_LOG:-$(cd "$(dirname "$0")/../.." && pwd)/logs/identity.log}"
RUN_ID="$(date +%s)"
PASS=0; FAIL=0; FAILED_CASES=()

# ---------- helpers ----------
STATUS=""; BODY=""
req() { # METHOD PATH [JSON_BODY] [TOKEN]
  local method="$1" path="$2" body="${3:-}" token="${4:-}"
  local args=(-s -o /tmp/smoke_body.$$ -w '%{http_code}' -X "$method" "$BASE$path" -H 'Content-Type: application/json')
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-d "$body")
  STATUS=$(curl "${args[@]}")
  BODY=$(cat /tmp/smoke_body.$$ 2>/dev/null || echo '')
}
ok()   { PASS=$((PASS+1)); echo "  PASS: $1"; }
bad()  { FAIL=$((FAIL+1)); FAILED_CASES+=("$1"); echo "  FAIL: $1 (status=$STATUS body=$(echo "$BODY" | head -c 300))"; }
assert_status() { # CASE EXPECTED...
  local case="$1"; shift
  for exp in "$@"; do [ "$STATUS" = "$exp" ] && { ok "$case [HTTP $STATUS]"; return 0; }; done
  bad "$case expected HTTP $* got $STATUS"; return 1
}
assert_jq() { # CASE FILTER (returns true/false)
  local case="$1" filter="$2"
  if echo "$BODY" | jq -e "$filter" >/dev/null 2>&1; then ok "$case"; else bad "$case jq:[$filter]"; fi
}

echo "=== portal-api-smoke @ $BASE (run=$RUN_ID) ==="
command -v jq >/dev/null || { echo "jq required"; exit 2; }

# =============================================================================
echo "--- [1] catalog 公开端点 ---"
req GET "/api/store/products?page=1&page_size=4"
assert_status "CAT-01 商品列表" 200 && assert_jq "CAT-01b 列表非空" '.data.data | length > 0'

req GET "/api/store/products/celeste-lace-gown"
assert_status "CAT-02 商品详情(slug)" 200
PRODUCT_ID=$(echo "$BODY" | jq -r '.data.id')
SKU_ID=$(echo "$BODY" | jq -r '.data.skus[0].id')
SKU_COLOR=$(echo "$BODY" | jq -r '.data.skus[0].color')
assert_jq "CAT-02b slug 回显" '.data.slug == "celeste-lace-gown"'

req GET "/api/store/products/search?q=lace"
assert_status "CAT-03 搜索" 200

req GET "/api/store/categories"
assert_status "CAT-04 分类树" 200 && assert_jq "CAT-04b items 数组" '.data.items | type == "array"'

req GET "/api/store/tags"
assert_status "CAT-05 标签" 200

req POST "/api/store/products/$PRODUCT_ID/size-recommendation" '{"height":65,"bust":36,"waist":28,"hips":38,"fit_preference":"regular"}'
assert_status "CAT-06 尺码推荐(合法)" 200 && assert_jq "CAT-06b matched 字段存在" '.data | has("matched")'

req POST "/api/store/products/$PRODUCT_ID/size-recommendation" '{"height":300,"bust":36,"waist":28,"hips":38}'
assert_status "CAT-07 尺码推荐越界 422" 422 && assert_jq "CAT-07b code=422502" '.code == 422502'

# =============================================================================
echo "--- [2] 鉴权 ---"
req POST "/api/admin/auth/login" '{"email":"admin@dreamy.com","password":"Admin@123456"}'
assert_status "AUTH-01 管理员登录" 200
ADMIN_TOKEN=$(echo "$BODY" | jq -r '.data.token')
[ -n "$ADMIN_TOKEN" ] && [ "$ADMIN_TOKEN" != "null" ] && ok "AUTH-01b admin token 取得" || bad "AUTH-01b admin token 缺失"

req GET "/api/admin/products"
assert_status "AUTH-02 未带 token 访问受保护端点 401" 401

req GET "/api/store/products?page=1"
assert_status "AUTH-03 公开端点免鉴权" 200

# ---- store OTP stub 登录 ----
SMOKE_EMAIL="smoke-user@dreamy.com"
req POST "/api/store/auth/otp/send" "{\"email\":\"$SMOKE_EMAIL\",\"locale\":\"en\"}"
if [ "$STATUS" = "429" ]; then
  WAIT=$(echo "$BODY" | jq -r '.data.retry_after_seconds // 61' 2>/dev/null); WAIT=${WAIT:-61}
  echo "  (OTP 频控 429，等待 ${WAIT}s 重试)"; sleep "$WAIT"
  req POST "/api/store/auth/otp/send" "{\"email\":\"$SMOKE_EMAIL\",\"locale\":\"en\"}"
fi
assert_status "AUTH-04 OTP 发送(stub)" 200
sleep 1
OTP_CODE=$(grep '\[MAIL-STUB\]' "$LOG_FILE" | tail -1 | grep -oE 'code=[0-9]{4,8}' | head -1 | cut -d= -f2)
if [ -n "$OTP_CODE" ]; then ok "AUTH-04b 从 stub 日志取得 OTP"; else bad "AUTH-04b stub 日志未找到 OTP ($LOG_FILE)"; fi

req POST "/api/store/auth/otp/verify" "{\"email\":\"$SMOKE_EMAIL\",\"code\":\"$OTP_CODE\"}"
assert_status "AUTH-05 OTP 校验登录" 200
STORE_TOKEN=$(echo "$BODY" | jq -r '.data.tokens.access_token')
[ -n "$STORE_TOKEN" ] && [ "$STORE_TOKEN" != "null" ] && ok "AUTH-05b store token 取得" || bad "AUTH-05b store token 缺失"

# =============================================================================
echo "--- [3] admin CRUD 闭环 ---"
req POST "/api/admin/tag-dimensions" "{\"name\":\"SmokeDim-$RUN_ID\",\"description\":\"smoke\"}" "$ADMIN_TOKEN"
assert_status "ADM-01 创建标签维度" 201 200
DIM_ID=$(echo "$BODY" | jq -r '.data.id')

req POST "/api/admin/tags" "{\"dimension_id\":$DIM_ID,\"name\":\"SmokeTag-$RUN_ID\",\"status\":\"enabled\"}" "$ADMIN_TOKEN"
assert_status "ADM-02 创建标签" 201 200
TAG_ID=$(echo "$BODY" | jq -r '.data.id')

req DELETE "/api/admin/tag-dimensions/$DIM_ID" '' "$ADMIN_TOKEN"
assert_status "ADM-03 删除非空维度 409 guard" 409 && assert_jq "ADM-03b code=409506" '.code == 409506'

req DELETE "/api/admin/tags/$TAG_ID" '' "$ADMIN_TOKEN"
assert_status "ADM-04 删除标签" 204 200

req DELETE "/api/admin/tag-dimensions/$DIM_ID" '' "$ADMIN_TOKEN"
assert_status "ADM-05 删除空维度" 204 200

req PUT "/api/admin/exchange-rates/EUR" '{"rate":0.93}' "$ADMIN_TOKEN"
assert_status "ADM-06 汇率更新" 200 && assert_jq "ADM-06b rate=0.93" '.data.rate == 0.93'
req PUT "/api/admin/exchange-rates/EUR" '{"rate":0.92}' "$ADMIN_TOKEN"
assert_status "ADM-07 汇率还原" 200

# =============================================================================
echo "--- [4] trading 端到端 ---"
req POST "/api/store/cart/merge" "{\"anon_token\":\"smoke-anon-$RUN_ID\",\"items\":[{\"product_id\":$PRODUCT_ID,\"sku_id\":$SKU_ID,\"qty\":1}]}" "$STORE_TOKEN"
assert_status "TRD-01 游客购物车合并" 200 && assert_jq "TRD-01b 合并后含 1 件" '.data.items | length >= 1'

req POST "/api/store/addresses" '{"receiver":"Smoke Tester","phone":"+1 555 0100","line":"1 Main St","city":"Austin","state":"TX","zip":"73301","country":"US","is_default":true}' "$STORE_TOKEN"
assert_status "TRD-02 地址创建" 201 200
ADDR_ID=$(echo "$BODY" | jq -r '.data.id')

req POST "/api/store/checkout/quote" "{\"address_id\":$ADDR_ID,\"currency\":\"USD\"}" "$STORE_TOKEN"
assert_status "TRD-03 quote 试算" 200 && assert_jq "TRD-03b 三承运商选项" '.data.shipping_options | length == 3'
CARRIER=$(echo "$BODY" | jq -r '.data.shipping_options[0].carrier')

req POST "/api/store/checkout/orders" "{\"idempotency_key\":\"smoke-$RUN_ID\",\"address_id\":$ADDR_ID,\"currency\":\"USD\",\"carrier\":\"$CARRIER\",\"payment_method\":\"Stripe\",\"locale\":\"en\"}" "$STORE_TOKEN"
assert_status "TRD-04 下单(stub Stripe)" 201 200
ORDER_ID=$(echo "$BODY" | jq -r '.data.order.id')
assert_jq "TRD-04b stub payment intent 返回" '.data.payment.payment_intent_id != null'

req GET "/api/store/orders" '' "$STORE_TOKEN"
assert_status "TRD-05 订单列表" 200 && assert_jq "TRD-05b 含本次订单" ".data.data | map(.id) | index($ORDER_ID) != null"

# ---- 承运商 toggle 409902 guard（quote 断言后执行并还原）----
req GET "/api/admin/shipping/carriers" '' "$ADMIN_TOKEN"
assert_status "TRD-06 承运商列表" 200
ENABLED_IDS=$(echo "$BODY" | jq -r '[.data.items[] | select(.status=="enabled") | .id] | @sh' | tr -d "'")
set -- $ENABLED_IDS
N_ENABLED=$#
if [ "$N_ENABLED" -ge 2 ]; then
  RESTORE_IDS=""
  while [ $# -gt 1 ]; do
    req PATCH "/api/admin/shipping/carriers/$1/status" '{"status":"disabled"}' "$ADMIN_TOKEN"
    [ "$STATUS" = "200" ] && RESTORE_IDS="$RESTORE_IDS $1"
    shift
  done
  LAST_ID=$1
  req PATCH "/api/admin/shipping/carriers/$LAST_ID/status" '{"status":"disabled"}' "$ADMIN_TOKEN"
  assert_status "TRD-07 最后启用承运商禁用 409 guard" 409 && assert_jq "TRD-07b code=409902" '.code == 409902'
  for cid in $RESTORE_IDS; do
    req PATCH "/api/admin/shipping/carriers/$cid/status" '{"status":"enabled"}' "$ADMIN_TOKEN"
    [ "$STATUS" = "200" ] || bad "TRD-08 承运商状态还原 id=$cid"
  done
  ok "TRD-08 承运商状态已还原"
else
  bad "TRD-07 启用承运商不足 2，无法验证 409902"
fi

# =============================================================================
echo "--- [5] marketing ---"
req POST "/api/store/promotions/coupons/validate" '{"code":"SPRING50","subtotal":600}' "$STORE_TOKEN"
assert_status "MKT-01 过期券校验 200" 200 && assert_jq "MKT-01b valid=false 口径" '.data.valid == false'

req POST "/api/store/newsletter" "{\"email\":\"smoke-news@dreamy.com\",\"source\":\"footer\",\"locale\":\"en\"}"
assert_status "MKT-02 Newsletter 首次" 200 && assert_jq "MKT-02b subscribed=true" '.data.subscribed == true'
req POST "/api/store/newsletter" "{\"email\":\"smoke-news@dreamy.com\",\"source\":\"footer\",\"locale\":\"en\"}"
assert_status "MKT-03 Newsletter 重复幂等 200" 200 && assert_jq "MKT-03b subscribed=true" '.data.subscribed == true'

# =============================================================================
echo "--- [6] review ---"
req POST "/api/store/reviews" "{\"product_id\":$PRODUCT_ID,\"rating\":5,\"content\":\"smoke review\"}" "$STORE_TOKEN"
assert_status "REV-01 未完成订单用户提交评价 403" 403 && assert_jq "REV-01b code=403801" '.code == 403801'

req GET "/api/store/reviews?product_id=$PRODUCT_ID"
assert_status "REV-02 公开评价列表" 200

# =============================================================================
echo "--- [7] showroom ---"
req POST "/api/store/showrooms" "{\"name\":\"SmokeRoom-$RUN_ID\",\"wedding_date\":\"2026-12-01\"}" "$STORE_TOKEN"
assert_status "SHR-01 创建 Showroom" 201 200
SHOWROOM_ID=$(echo "$BODY" | jq -r '.data.id')
INVITE_TOKEN=$(echo "$BODY" | jq -r '.data.invite_token')

req POST "/api/store/showrooms/$SHOWROOM_ID/items" "{\"product_id\":$PRODUCT_ID,\"color\":\"$SKU_COLOR\"}" "$STORE_TOKEN"
assert_status "SHR-02 添加款式" 201 200
ITEM_ID=$(echo "$BODY" | jq -r '.data.id')

req POST "/api/store/showrooms/guest-session" "{\"invite_token\":\"$INVITE_TOKEN\",\"nickname\":\"SmokeGuest\"}"
assert_status "SHR-03 guest-session 换 token" 200
GUEST_TOKEN=$(echo "$BODY" | jq -r '.data.guest_token')

req PUT "/api/store/showrooms/$SHOWROOM_ID/items/$ITEM_ID/vote" '{"vote":"like"}' "$GUEST_TOKEN"
assert_status "SHR-04 guest 投票" 200 && assert_jq "SHR-04b like_count=1" '.data.like_count == 1'
req PUT "/api/store/showrooms/$SHOWROOM_ID/items/$ITEM_ID/vote" '{"vote":"like"}' "$GUEST_TOKEN"
assert_status "SHR-05 同值重放幂等" 200 && assert_jq "SHR-05b like_count 仍=1" '.data.like_count == 1'

req POST "/api/store/showrooms/$SHOWROOM_ID/invite/reset" '' "$STORE_TOKEN"
assert_status "SHR-06 重置邀请" 200

req GET "/api/store/showrooms/$SHOWROOM_ID" '' "$GUEST_TOKEN"
assert_status "SHR-07 旧 guest token 失效 401" 401 && assert_jq "SHR-07b code=401101" '.code == 401101'

req DELETE "/api/store/showrooms/$SHOWROOM_ID" '' "$STORE_TOKEN"
assert_status "SHR-08 清理 Showroom" 204 200

# =============================================================================
echo "--- [8] dashboard ---"
req GET "/api/admin/dashboard" '' "$ADMIN_TOKEN"
assert_status "DSH-01 admin dashboard" 200

# =============================================================================
rm -f /tmp/smoke_body.$$
echo ""
echo "=== SUMMARY: PASS=$PASS FAIL=$FAIL ==="
if [ "$FAIL" -gt 0 ]; then
  printf 'FAILED: %s\n' "${FAILED_CASES[@]}"
  exit 1
fi
exit 0
