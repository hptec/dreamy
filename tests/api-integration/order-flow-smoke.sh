#!/usr/bin/env bash
# =============================================================================
# order-flow-complete P1-A 交易核心 API 冒烟（真实环境零 Mock，可重复执行）
# 链路 A：加购 → quote → 下单 → POST payment/confirm → status=2 & production_stage=1
#        → admin production-stage 1→2 → /ship(status=3) → PATCH status=8 → 前台 confirm-delivery(status=4)
#        → 时间线 events 断言 → reorder
# 链路 B：第二单 → confirm → admin 部分退款 → approve → 订单还原 PAID + refunded_amount 累计
#        → 再申请剩余 → 第二张 pending 被 409907 拒绝 → 超额 422908 → approve 全额 → REFUNDED
# 链路 C：后台取消已支付（2→5 同事务全额退款）；checkout-config 新字段 GET/PUT + 范围校验；备注
# 前置：后端 :18081（STRIPE_MODE=stub）、pd-mysql/pd-redis 运行中；商品 celeste-lace-gown 存在且有库存。
# 用法：bash tests/api-integration/order-flow-smoke.sh [BASE_URL]
# 依赖：curl、jq
# =============================================================================
set -u
BASE="${1:-http://localhost:18081}"
LOG_FILE="${OTP_LOG:-$(cd "$(dirname "$0")/../.." && pwd)/logs/identity.log}"
RUN_ID="$(date +%s)"
PASS=0; FAIL=0; FAILED_CASES=()

STATUS=""; BODY=""
req() { # METHOD PATH [JSON_BODY] [TOKEN]
  local method="$1" path="$2" body="${3:-}" token="${4:-}"
  local args=(-s -o /tmp/ofs_body.$$ -w '%{http_code}' -X "$method" "$BASE$path" -H 'Content-Type: application/json')
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-d "$body")
  STATUS=$(curl "${args[@]}")
  BODY=$(cat /tmp/ofs_body.$$ 2>/dev/null || echo '')
}
ok()   { PASS=$((PASS+1)); echo "  PASS: $1"; }
bad()  { FAIL=$((FAIL+1)); FAILED_CASES+=("$1"); echo "  FAIL: $1 (status=$STATUS body=$(echo "$BODY" | head -c 300))"; }
assert_status() { local case="$1"; shift; for exp in "$@"; do [ "$STATUS" = "$exp" ] && { ok "$case [HTTP $STATUS]"; return 0; }; done; bad "$case expected HTTP $* got $STATUS"; return 1; }
assert_jq() { local case="$1" filter="$2"; if echo "$BODY" | jq -e "$filter" >/dev/null 2>&1; then ok "$case"; else bad "$case jq:[$filter]"; fi; }

echo "=== order-flow-smoke @ $BASE (run=$RUN_ID) ==="
command -v jq >/dev/null || { echo "jq required"; exit 2; }

# ---------- 登录 ----------
echo "--- [0] 登录 ---"
req POST "/api/admin/auth/login" '{"email":"admin@dreamy.com","password":"Admin@123456"}'
assert_status "AUTH-01 管理员登录" 200
ADMIN_TOKEN=$(echo "$BODY" | jq -r '.data.token')

SMOKE_EMAIL="smoke-orderflow@dreamy.com"
req POST "/api/store/auth/otp/send" "{\"email\":\"$SMOKE_EMAIL\",\"locale\":\"en\"}"
if [ "$STATUS" = "429" ]; then
  WAIT=$(echo "$BODY" | jq -r '.data.retry_after_seconds // 61' 2>/dev/null); WAIT=${WAIT:-61}
  echo "  (OTP 频控 429，等待 ${WAIT}s 重试)"; sleep "$WAIT"
  req POST "/api/store/auth/otp/send" "{\"email\":\"$SMOKE_EMAIL\",\"locale\":\"en\"}"
fi
assert_status "AUTH-02 OTP 发送(stub)" 200
sleep 1
OTP_CODE=$(grep '\[MAIL-STUB\]' "$LOG_FILE" | grep 'code=otp' | tail -1 | grep -oE 'code=[0-9]{4,8}' | head -1 | cut -d= -f2)
[ -z "$OTP_CODE" ] && OTP_CODE=$(grep '\[MAIL-STUB\]' "$LOG_FILE" | tail -1 | grep -oE 'code=[0-9]{4,8}' | head -1 | cut -d= -f2)
[ -n "$OTP_CODE" ] && ok "AUTH-02b 从 stub 日志取得 OTP" || bad "AUTH-02b stub 日志未找到 OTP ($LOG_FILE)"
req POST "/api/store/auth/otp/verify" "{\"email\":\"$SMOKE_EMAIL\",\"code\":\"$OTP_CODE\"}"
assert_status "AUTH-03 OTP 校验登录" 200
STORE_TOKEN=$(echo "$BODY" | jq -r '.data.tokens.access_token')
[ -n "$STORE_TOKEN" ] && [ "$STORE_TOKEN" != "null" ] && ok "AUTH-03b store token 取得" || { bad "AUTH-03b store token 缺失"; }

# ---------- 商品 / 地址 ----------
req GET "/api/store/products/celeste-lace-gown"
assert_status "PRE-01 商品详情" 200
PRODUCT_ID=$(echo "$BODY" | jq -r '.data.id')
SKU_ID=$(echo "$BODY" | jq -r '[.data.skus[] | select(.stock >= 3)][0].id')
[ "$SKU_ID" != "null" ] && ok "PRE-01b 取得有库存 SKU=$SKU_ID" || bad "PRE-01b 无库存 ≥3 的 SKU"

req POST "/api/store/addresses" '{"receiver":"Flow Tester","phone":"+1 555 0199","line":"9 Flow Ave","city":"Austin","state":"TX","zip":"73301","country":"US","is_default":true}' "$STORE_TOKEN"
assert_status "PRE-02 地址创建" 201 200
ADDR_ID=$(echo "$BODY" | jq -r '.data.id')

# place_order <idem-suffix> → sets ORDER_ID / ORDER_TOTAL
place_order() {
  local suffix="$1"
  req GET "/api/store/cart" '' "$STORE_TOKEN"
  for cid in $(echo "$BODY" | jq -r '.data.items[].id'); do req DELETE "/api/store/cart/items/$cid" '' "$STORE_TOKEN"; done
  req POST "/api/store/cart/items" "{\"product_id\":$PRODUCT_ID,\"sku_id\":$SKU_ID,\"qty\":1}" "$STORE_TOKEN"
  assert_status "ORD-$suffix-01 加购" 201 200
  req POST "/api/store/checkout/quote" "{\"address_id\":$ADDR_ID,\"currency\":\"USD\"}" "$STORE_TOKEN"
  assert_status "ORD-$suffix-02 quote" 200
  local carrier; carrier=$(echo "$BODY" | jq -r '.data.shipping_options[0].carrier')
  req POST "/api/store/checkout/orders" "{\"idempotency_key\":\"of-$RUN_ID-$suffix\",\"address_id\":$ADDR_ID,\"currency\":\"USD\",\"carrier\":\"$carrier\",\"payment_method\":\"Stripe\",\"locale\":\"en\"}" "$STORE_TOKEN"
  assert_status "ORD-$suffix-03 下单(stub Stripe)" 201 200
  ORDER_ID=$(echo "$BODY" | jq -r '.data.order.id')
  ORDER_TOTAL=$(echo "$BODY" | jq -r '.data.order.total_amount')
  assert_jq "ORD-$suffix-03b status=1 amount_version=2 tax_amount=0 refunded_amount=0" \
    '.data.order.status == 1 and .data.order.amount_version == 2 and (.data.order.tax_amount|tonumber) == 0 and (.data.order.refunded_amount|tonumber) == 0'
  assert_jq "ORD-$suffix-03c client_secret 以 _secret_stub 结尾（前端识别 stub）" '.data.payment.client_secret | endswith("_secret_stub")'
  assert_jq "ORD-$suffix-03d events 含创建 PENDING(STATUS_CHANGED, CUSTOMER)" \
    '.data.order.events | map(select(.type == 1 and .actor_type == 2 and .payload.to == 1)) | length == 1'
}

# =============================================================================
echo "--- [A] 支付闭环 → 制作 → 发货 → 签收 → 完成 ---"
place_order A
A_ORDER=$ORDER_ID

req POST "/api/store/orders/$A_ORDER/payment/confirm" '' "$STORE_TOKEN"
assert_status "A-01 stub 支付确认" 200
assert_jq "A-01b status=2 production_stage=1 paid_at 非空" '.data.status == 2 and .data.production_stage == 1 and .data.paid_at != null'
assert_jq "A-01c payment.status=3 card visa ···4242" '.data.payment.status == 3 and (.data.payment.card_summary | contains("4242"))'
assert_jq "A-01d events 含 PAYMENT(SYSTEM, 可见)" '.data.events | map(select(.type == 4 and .actor_type == 1)) | length == 1'

req POST "/api/store/orders/$A_ORDER/payment/confirm" '' "$STORE_TOKEN"
assert_status "A-02 重复确认 → 409602（订单已 PAID）" 409 && assert_jq "A-02b code=409602" '.code == 409602'

req GET "/api/admin/orders/$A_ORDER" '' "$ADMIN_TOKEN"
assert_status "A-03 后台详情" 200
assert_jq "A-03b production_stage=1 events≥2 shipments=[]" '.data.production_stage == 1 and (.data.events | length) >= 2 and (.data.shipments | length) == 0'

req PATCH "/api/admin/orders/$A_ORDER/production-stage" '{"stage":3}' "$ADMIN_TOKEN"
assert_status "A-04 跳级 1→3 → 409602" 409

req PATCH "/api/admin/orders/$A_ORDER/production-stage" '{"stage":2}' "$ADMIN_TOKEN"
assert_status "A-05 制作阶段 1→2" 200 && assert_jq "A-05b production_stage=2 + PRODUCTION 事件" '.data.production_stage == 2 and (.data.events | map(select(.type == 7)) | length) == 1'
sleep 1
grep -q 'code=order_production' "$LOG_FILE" && ok "A-05c stub 日志出现 order_production 邮件" || bad "A-05c 未见 order_production 邮件日志"

req POST "/api/admin/orders/$A_ORDER/notes" '{"content":"smoke internal note","customer_visible":false}' "$ADMIN_TOKEN"
assert_status "A-06 内部备注" 201 200 && assert_jq "A-06b type=NOTE customer_visible=false" '.data.type == 2 and .data.customer_visible == false'
req POST "/api/admin/orders/$A_ORDER/notes" '{"content":"smoke visible note","customer_visible":true}' "$ADMIN_TOKEN"
assert_status "A-07 可见备注" 201 200

req GET "/api/store/orders/$A_ORDER" '' "$STORE_TOKEN"
assert_status "A-08 前台详情" 200
assert_jq "A-08b 前台仅见 customer_visible 事件（含可见备注，不含内部备注）" \
  '(.data.events | map(select(.type == 2)) | length) == 1 and (.data.events | all(.customer_visible == true)) and (.data.events | all(.actor_name == null))'

req POST "/api/store/orders/$A_ORDER/confirm-delivery" '' "$STORE_TOKEN"
assert_status "A-09 PAID 态确认收货 → 409602" 409

req POST "/api/admin/orders/$A_ORDER/ship" '{"carrier":"DHL Express","tracking_no":"DHL-'$RUN_ID'"}' "$ADMIN_TOKEN"
assert_status "A-10 发货 /ship" 200 && assert_jq "A-10b status=3 production_stage=null shipped_at 非空" '.data.status == 3 and .data.production_stage == null and .data.shipped_at != null'
assert_jq "A-10c events 含 SHIPMENT + STATUS_CHANGED(→3)" '(.data.events | map(select(.type == 3)) | length) == 1 and (.data.events | map(select(.type == 1 and .payload.to == 3)) | length) == 1'

req GET "/api/admin/orders?production_stage=2&page=1&page_size=5" '' "$ADMIN_TOKEN"
assert_status "A-11 列表 production_stage 筛选" 200 && assert_jq "A-11b 已发货订单不在 stage=2 列表" ".data.data | map(.id) | index($A_ORDER) == null"

req PATCH "/api/admin/orders/$A_ORDER/status" '{"status":8}' "$ADMIN_TOKEN"
assert_status "A-12 3→8 DELIVERED" 200 && assert_jq "A-12b status=8 delivered_at 非空" '.data.status == 8 and .data.delivered_at != null'
sleep 1
grep -q 'code=order_delivered' "$LOG_FILE" && ok "A-12c stub 日志出现 order_delivered 邮件" || bad "A-12c 未见 order_delivered 邮件日志"

req POST "/api/store/orders/$A_ORDER/confirm-delivery" '' "$STORE_TOKEN"
assert_status "A-13 前台确认收货 8→4" 200 && assert_jq "A-13b status=4 completed_at 非空" '.data.status == 4 and .data.completed_at != null'
assert_jq "A-13c events 含 →COMPLETED(CUSTOMER)" '.data.events | map(select(.type == 1 and .actor_type == 2 and .payload.to == 4)) | length == 1'

req POST "/api/store/orders/$A_ORDER/confirm-delivery" '' "$STORE_TOKEN"
assert_status "A-14 已完成再确认 → 409602" 409

req POST "/api/store/orders/$A_ORDER/reorder" '' "$STORE_TOKEN"
assert_status "A-15 再次购买" 200 && assert_jq "A-15b added_count=1 skipped=[]" '.data.added_count == 1 and (.data.skipped | length) == 0'

req GET "/api/admin/orders/$A_ORDER" '' "$ADMIN_TOKEN"
assert_jq "A-16 后台 events 含 EMAIL(SYSTEM, 不可见) ≥2（paid/production/delivered）" '.data.events | map(select(.type == 6 and .customer_visible == false)) | length >= 2'

# =============================================================================
echo "--- [B] 部分退款 → 还原 → 累计达 total → REFUNDED ---"
place_order B
B_ORDER=$ORDER_ID
req POST "/api/store/orders/$B_ORDER/payment/confirm" '' "$STORE_TOKEN"
assert_status "B-01 stub 支付确认" 200
B_TOTAL=$(echo "$BODY" | jq -r '.data.total_amount')
PART=$(echo "$B_TOTAL" | awk '{printf "%.2f", $1/3}')
REMAIN=$(echo "$B_TOTAL $PART" | awk '{printf "%.2f", $1-$2}')

req POST "/api/admin/orders/$B_ORDER/refunds" "{\"amount\":$(echo "$B_TOTAL" | awk '{printf "%.2f", $1+0.01}'),\"reason\":\"over\"}" "$ADMIN_TOKEN"
assert_status "B-02 amount > total → 422603" 422 && assert_jq "B-02b code=422603" '.code == 422603'

req POST "/api/admin/orders/$B_ORDER/refunds" "{\"amount\":$PART,\"reason\":\"partial smoke\"}" "$ADMIN_TOKEN"
assert_status "B-03 后台创建部分退款" 201 200
R1=$(echo "$BODY" | jq -r '.data.id')
assert_jq "B-03b from_status=2 from_stage=1" '.data.from_status == 2 and .data.from_stage == 1'
sleep 1
grep -q 'code=refund_requested' "$LOG_FILE" && ok "B-03c stub 日志出现 refund_requested 邮件" || bad "B-03c 未见 refund_requested 邮件日志"

req GET "/api/store/orders/$B_ORDER" '' "$STORE_TOKEN"
assert_jq "B-04 订单 REFUNDING(6) refund_eligible=false" '.data.status == 6 and .data.refund_eligible == false'

req POST "/api/admin/orders/$B_ORDER/refunds" '{"amount":1.00,"reason":"second"}' "$ADMIN_TOKEN"
assert_status "B-05 第二张挂起工单 → 409907" 409 && assert_jq "B-05b code=409907" '.code == 409907'

req POST "/api/store/orders/$B_ORDER/refunds" '{"reason":"customer second"}' "$STORE_TOKEN"
assert_status "B-06 消费端第二张 → 409907" 409

req POST "/api/admin/refunds/$R1/approve" '{}' "$ADMIN_TOKEN"
assert_status "B-07 批准部分退款" 200 && assert_jq "B-07b status=2(approved) stripe_refund_id 非空" '.data.status == 2 and .data.stripe_refund_id != null'

req GET "/api/admin/orders/$B_ORDER" '' "$ADMIN_TOKEN"
assert_jq "B-08 订单还原 PAID(2) production_stage=1 refunded_amount=$PART" \
  ".data.status == 2 and .data.production_stage == 1 and (.data.refunded_amount|tonumber) == $PART"
assert_jq "B-08b payment.status=6(PARTIALLY_REFUNDED) refunded_amount=$PART" ".data.payment.status == 6 and (.data.payment.refunded_amount|tonumber) == $PART"
assert_jq "B-08c events 含 REFUND ≥2（requested + approved partial）" '.data.events | map(select(.type == 5)) | length >= 2'

req POST "/api/admin/orders/$B_ORDER/refunds" "{\"amount\":$(echo "$REMAIN" | awk '{printf "%.2f", $1+0.01}'),\"reason\":\"over remain\"}" "$ADMIN_TOKEN"
assert_status "B-09 amount > 剩余可退 → 422908" 422 && assert_jq "B-09b code=422908 max_refundable=$REMAIN" ".code == 422908 and (.data.max_refundable|tonumber) == $REMAIN"

req POST "/api/store/orders/$B_ORDER/refunds" '{"reason":"customer remaining"}' "$STORE_TOKEN"
assert_status "B-10 消费端申请剩余可退额" 201 200 && assert_jq "B-10b amount=$REMAIN" "(.data.amount|tonumber) == $REMAIN"
R2=$(echo "$BODY" | jq -r '.data.id')

req POST "/api/admin/refunds/$R2/reject" '{"reason":"smoke reject"}' "$ADMIN_TOKEN"
assert_status "B-11 驳回 → 还原 PAID" 200
req GET "/api/admin/orders/$B_ORDER" '' "$ADMIN_TOKEN"
assert_jq "B-11b 驳回后 status=2 production_stage=1 refunded_amount 不变" ".data.status == 2 and .data.production_stage == 1 and (.data.refunded_amount|tonumber) == $PART"

req POST "/api/admin/orders/$B_ORDER/refunds" "{\"amount\":$REMAIN,\"reason\":\"final\"}" "$ADMIN_TOKEN"
assert_status "B-12 创建剩余全额工单" 201 200
R3=$(echo "$BODY" | jq -r '.data.id')
req POST "/api/admin/refunds/$R3/approve" '{}' "$ADMIN_TOKEN"
assert_status "B-13 批准 → 累计达 total" 200
req GET "/api/admin/orders/$B_ORDER" '' "$ADMIN_TOKEN"
assert_jq "B-13b 订单 REFUNDED(7) refunded_amount=total production_stage=null payment.status=5" \
  ".data.status == 7 and (.data.refunded_amount|tonumber) == $B_TOTAL and .data.production_stage == null and .data.payment.status == 5"

# =============================================================================
echo "--- [C] 后台取消已支付 (2→5) + checkout-config ---"
place_order C
C_ORDER=$ORDER_ID
req POST "/api/store/orders/$C_ORDER/payment/confirm" '' "$STORE_TOKEN"
assert_status "C-01 stub 支付确认" 200
req PATCH "/api/admin/orders/$C_ORDER/status" '{"status":5}' "$ADMIN_TOKEN"
assert_status "C-02 后台取消已支付 2→5" 200
assert_jq "C-02b status=5 refunded_amount=total 含 REFUND + STATUS_CHANGED(→5) 事件 refunds[0].status=2" \
  ".data.status == 5 and (.data.refunded_amount|tonumber) == ($ORDER_TOTAL|tonumber) and (.data.events | map(select(.type == 1 and .payload.to == 5)) | length) == 1 and (.data.refunds | length) == 1 and .data.refunds[0].status == 2"
sleep 1
grep -q 'code=order_cancelled' "$LOG_FILE" && ok "C-02c stub 日志出现 order_cancelled 邮件" || bad "C-02c 未见 order_cancelled 邮件日志"

req GET "/api/admin/checkout-config" '' "$ADMIN_TOKEN"
assert_status "C-03 checkout-config GET" 200
assert_jq "C-03b 含 5 个新字段" '.data | has("auto_complete_days") and has("auto_deliver_days") and has("pending_timeout_minutes") and has("exchange_rate_spread_scaled") and has("production_days_default")'
ORIG_CFG=$(echo "$BODY" | jq -c '.data')

req PUT "/api/admin/checkout-config" "$(echo "$ORIG_CFG" | jq -c '.pending_timeout_minutes = 4')" "$ADMIN_TOKEN"
assert_status "C-04 pending_timeout_minutes=4 越界 → 422601" 422 && assert_jq "C-04b fields.pending_timeout_minutes" '.data.fields.pending_timeout_minutes != null'
req PUT "/api/admin/checkout-config" "$(echo "$ORIG_CFG" | jq -c '.auto_complete_days = 9 | .production_days_default = 25')" "$ADMIN_TOKEN"
assert_status "C-05 更新新字段" 200 && assert_jq "C-05b 回显 9/25" '.data.auto_complete_days == 9 and .data.production_days_default == 25'
req PUT "/api/admin/checkout-config" "$ORIG_CFG" "$ADMIN_TOKEN"
assert_status "C-06 还原 checkout-config" 200

# ---------- 清理 ----------
req DELETE "/api/store/addresses/$ADDR_ID" '' "$STORE_TOKEN"
[ "$STATUS" = "200" ] || [ "$STATUS" = "204" ] && ok "CLEAN-01 地址删除" || bad "CLEAN-01 地址删除"
req GET "/api/store/cart" '' "$STORE_TOKEN"
for cid in $(echo "$BODY" | jq -r '.data.items[].id'); do req DELETE "/api/store/cart/items/$cid" '' "$STORE_TOKEN"; done
ok "CLEAN-02 购物车清空"

rm -f /tmp/ofs_body.$$
echo "=== 结果：PASS=$PASS FAIL=$FAIL ==="
if [ "$FAIL" -gt 0 ]; then printf '  - %s\n' "${FAILED_CASES[@]}"; exit 1; fi
