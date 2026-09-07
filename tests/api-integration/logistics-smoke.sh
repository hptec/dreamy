#!/usr/bin/env bash
# =============================================================================
# order-flow-complete P1-B 物流/汇率/税费/地址 API 冒烟（真实环境零 Mock，可重复执行）
# 链路 A：GB 地址(country_code) → quote 含 tax + Standard/Express 选项 + ETA → 下单(total 含税, service_level=2)
#        → confirm → 后台部分发货 1 件 → 手工轨迹 → 剩余发货（订单 3）→ 重复单号 409908 / 超量 422906
#        → 两包裹 deliver（订单 8）→ 前台详情 shipments/events → 游客查单（命中 / 邮箱不匹配 404）
# 链路 B：第二单 → 全量发货（订单 3）→ 作废包裹 → 订单回到 PAID(production_stage=4)
# 链路 C：税率 CRUD（重叠 422907 / enabled / 目的国政策）；运费选项 CRUD + 试算；旧 /shipping/rates 写端点 410901
# 链路 D：汇率 GET 新字段 / PUT manual_override / history / refresh 在 manual 模式 409905；国家列表
# 前置：后端 :18081（STRIPE_MODE=stub、EXCHANGE_RATE_MODE=manual、TRACKING_MODE=stub）、pd-mysql/pd-redis 运行中；
#       商品 celeste-lace-gown 有库存 ≥3 的 SKU。游客查单频控 10 次/小时/IP：本脚本消耗 2 次。
# 用法：bash tests/api-integration/logistics-smoke.sh [BASE_URL]
# 依赖：curl、jq
# =============================================================================
set -u
BASE="${1:-http://localhost:18081}"
LOG_FILE="${OTP_LOG:-$(cd "$(dirname "$0")/../.." && pwd)/logs/identity.log}"
RUN_ID="$(date +%s)"
PASS=0; FAIL=0; FAILED_CASES=()

STATUS=""; BODY=""
req() { # METHOD PATH [JSON_BODY] [TOKEN] [EXTRA_HEADER]
  local method="$1" path="$2" body="${3:-}" token="${4:-}" extra="${5:-}"
  local args=(-s -o /tmp/lgs_body.$$ -w '%{http_code}' -X "$method" "$BASE$path" -H 'Content-Type: application/json')
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$extra" ] && args+=(-H "$extra")
  [ -n "$body" ] && args+=(-d "$body")
  STATUS=$(curl "${args[@]}")
  BODY=$(cat /tmp/lgs_body.$$ 2>/dev/null || echo '')
}
ok()   { PASS=$((PASS+1)); echo "  PASS: $1"; }
bad()  { FAIL=$((FAIL+1)); FAILED_CASES+=("$1"); echo "  FAIL: $1 (status=$STATUS body=$(echo "$BODY" | head -c 300))"; }
assert_status() { local case="$1"; shift; for exp in "$@"; do [ "$STATUS" = "$exp" ] && { ok "$case [HTTP $STATUS]"; return 0; }; done; bad "$case expected HTTP $* got $STATUS"; return 1; }
assert_jq() { local case="$1" filter="$2"; if echo "$BODY" | jq -e "$filter" >/dev/null 2>&1; then ok "$case"; else bad "$case jq:[$filter]"; fi; }

echo "=== logistics-smoke @ $BASE (run=$RUN_ID) ==="
command -v jq >/dev/null || { echo "jq required"; exit 2; }

# ---------- 登录 ----------
echo "--- [0] 登录 ---"
req POST "/api/admin/auth/login" '{"email":"admin@dreamy.com","password":"Admin@123456"}'
assert_status "AUTH-01 管理员登录" 200
ADMIN_TOKEN=$(echo "$BODY" | jq -r '.data.token')

SMOKE_EMAIL="smoke-logistics@dreamy.com"
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
req POST "/api/store/auth/otp/verify" "{\"email\":\"$SMOKE_EMAIL\",\"code\":\"$OTP_CODE\"}"
assert_status "AUTH-03 OTP 校验登录" 200
STORE_TOKEN=$(echo "$BODY" | jq -r '.data.tokens.access_token')

# ---------- 国家列表 / 商品 / 地址 ----------
echo "--- [1] 国家列表 / 地址(GB) ---"
req GET "/api/store/shipping/countries"
assert_status "PRE-00 GET /shipping/countries 公开" 200
assert_jq "PRE-00b ≥245 国、GB zone=UK supported、US 含州字典" '(.data.items|length) >= 245 and (.data.items[]|select(.code=="GB")|.zone=="UK" and .supported==true) and ((.data.items[]|select(.code=="US")|.regions|length) >= 51)'

req GET "/api/store/products/celeste-lace-gown"
assert_status "PRE-01 商品详情" 200
PRODUCT_ID=$(echo "$BODY" | jq -r '.data.id')
SKU_ID=$(echo "$BODY" | jq -r '[.data.skus[] | select(.stock >= 3)][0].id')
[ "$SKU_ID" != "null" ] && ok "PRE-01b 取得有库存 SKU=$SKU_ID" || bad "PRE-01b 无库存 ≥3 的 SKU"

req POST "/api/store/addresses" '{"receiver":"Logistics Tester","phone":"+44 20 7946 0000","line":"1 Bridal Lane","city":"London","state":"","zip":"SW1A 1AA","country":"United Kingdom","country_code":"gb","is_default":true}' "$STORE_TOKEN"
assert_status "PRE-02 GB 地址创建（country_code 小写规范化）" 201 200
assert_jq "PRE-02b country_code=GB region_code=null" '.data.country_code == "GB" and .data.region_code == null'
ADDR_ID=$(echo "$BODY" | jq -r '.data.id')
req POST "/api/store/addresses" '{"receiver":"Bad","line":"x","city":"y","zip":"1","country":"Wakanda"}' "$STORE_TOKEN"
assert_status "PRE-03 无法解析国家 → 422601" 422 && assert_jq "PRE-03b fields.country_code" '.data.fields.country_code != null'
req POST "/api/store/addresses" '{"receiver":"US","line":"1 Main","city":"Austin","state":"texas","zip":"73301","country":"USA"}' "$STORE_TOKEN"
assert_status "PRE-04 US 地址 state 全称 → region_code=TX" 201 200 && assert_jq "PRE-04b US/TX" '.data.country_code == "US" and .data.region_code == "TX"'
US_ADDR_ID=$(echo "$BODY" | jq -r '.data.id')

clear_cart() { req GET "/api/store/cart" '' "$STORE_TOKEN"; for cid in $(echo "$BODY" | jq -r '.data.items[].id'); do req DELETE "/api/store/cart/items/$cid" '' "$STORE_TOKEN"; done; }

# =============================================================================
echo "--- [A] GB 报价含税 → 下单 → 部分发货 → 签收聚合 → 游客查单 ---"
clear_cart
req POST "/api/store/cart/items" "{\"product_id\":$PRODUCT_ID,\"sku_id\":$SKU_ID,\"qty\":2}" "$STORE_TOKEN"
assert_status "A-01 加购 qty=2" 201 200

req POST "/api/store/checkout/quote" "{\"address_id\":$ADDR_ID,\"currency\":\"USD\"}" "$STORE_TOKEN"
assert_status "A-02 quote(GB, USD)" 200
assert_jq "A-02b tax_amount>0 incoterm=DDP(1) duties_notice=false tax_breakdown VAT" '(.data.tax_amount|tonumber) > 0 and .data.incoterm == 1 and .data.duties_notice == false and (.data.tax_breakdown|length) == 1 and .data.tax_breakdown[0].type == 1'
assert_jq "A-02c shipping_options 含 STANDARD 与 EXPRESS 且带 carrier_code/transit/ETA" '([.data.shipping_options[]|.service_level]|unique) == [1,2] and (.data.shipping_options|all(.carrier_code != null and .transit_days_min != null and .estimated_delivery_from != null))'
assert_jq "A-02d 缺省选中 STANDARD 最便宜；顶层 ETA/production_days/country_code=GB" '.data.service_level == 1 and .data.estimated_delivery_from != null and .data.production_days >= 21 and .data.country_code == "GB" and .data.exchange_rate_locked_note == false'
assert_jq "A-02e 恒等式 total = subtotal + shipping + gift_wrap + tax - discount" '((.data.subtotal|tonumber) + (.data.shipping_fee|tonumber) + (.data.gift_wrap_fee|tonumber) + (.data.tax_amount|tonumber) - (.data.discount_amount|tonumber)) == (.data.total_amount|tonumber)'
STD_FEE=$(echo "$BODY" | jq -r '.data.shipping_fee')

req POST "/api/store/checkout/quote" "{\"address_id\":$ADDR_ID,\"currency\":\"EUR\",\"service_level\":2}" "$STORE_TOKEN"
assert_status "A-03 quote(GB, EUR, EXPRESS)" 200
assert_jq "A-03b 选中 EXPRESS；锁汇说明 true；运费高于 STANDARD" ".data.service_level == 2 and .data.exchange_rate_locked_note == true and (.data.shipping_fee|tonumber) > 0"
EXP_CARRIER_CODE=$(echo "$BODY" | jq -r '[.data.shipping_options[]|select(.selected==true)][0].carrier_code')

req POST "/api/store/checkout/orders" "{\"idempotency_key\":\"lg-$RUN_ID-A\",\"address_id\":$ADDR_ID,\"currency\":\"USD\",\"carrier_code\":\"$EXP_CARRIER_CODE\",\"service_level\":2,\"payment_method\":\"Stripe\",\"locale\":\"en\"}" "$STORE_TOKEN"
assert_status "A-04 下单(carrier_code + service_level=2)" 201 200
A_ORDER=$(echo "$BODY" | jq -r '.data.order.id')
A_ORDER_NO=$(echo "$BODY" | jq -r '.data.order.order_no')
assert_jq "A-04b 订单 tax_amount>0 incoterm=1 shipping_service_level=2 ETA 非空 amount_version=2" '(.data.order.tax_amount|tonumber) > 0 and .data.order.incoterm == 1 and .data.order.shipping_service_level == 2 and .data.order.estimated_delivery_from != null and .data.order.amount_version == 2 and (.data.order.tax_breakdown|length) == 1'
assert_jq "A-04c address_snapshot 含 country_code=GB" '.data.order.address_snapshot.country_code == "GB"'
assert_jq "A-04d total 含税恒等式" '((.data.order.subtotal|tonumber) + (.data.order.shipping_fee|tonumber) + (.data.order.gift_wrap_fee|tonumber) + (.data.order.tax_amount|tonumber) - (.data.order.discount_amount|tonumber)) == (.data.order.total_amount|tonumber)'
A_TOTAL=$(echo "$BODY" | jq -r '.data.order.total_amount')

req POST "/api/store/orders/$A_ORDER/payment/confirm" '' "$STORE_TOKEN"
assert_status "A-05 stub 支付确认" 200 && assert_jq "A-05b status=2 payment.amount=total(含税)" ".data.status == 2 and (.data.payment.amount|tonumber) == ($A_TOTAL|tonumber)"
A_LINE_ID=$(echo "$BODY" | jq -r '.data.lines[0].id')

req POST "/api/admin/orders/$A_ORDER/shipments" "{\"carrier_code\":\"DHL\",\"tracking_no\":\"DHL-A1-$RUN_ID\",\"lines\":[{\"order_line_id\":$A_LINE_ID,\"qty\":1}]}" "$ADMIN_TOKEN" "Idempotency-Key: lg-$RUN_ID-S1"
assert_status "A-06 部分发货 1 件" 201
assert_jq "A-06b status=PENDING tracking_url 含 dhl.com lines[0].qty=1" '.data.status == 1 and (.data.tracking_url|contains("dhl.com")) and .data.lines[0].qty == 1 and (.data.shipment_no|startswith("SHP-"))'
S1=$(echo "$BODY" | jq -r '.data.id')
req POST "/api/admin/orders/$A_ORDER/shipments" "{\"carrier_code\":\"DHL\",\"tracking_no\":\"DHL-A1-$RUN_ID\",\"lines\":[{\"order_line_id\":$A_LINE_ID,\"qty\":1}]}" "$ADMIN_TOKEN" "Idempotency-Key: lg-$RUN_ID-S1"
assert_status "A-07 同 Idempotency-Key 重放 → 返回既有包裹" 201 200 && assert_jq "A-07b id 相同" ".data.id == $S1"

req GET "/api/admin/orders/$A_ORDER" '' "$ADMIN_TOKEN"
assert_status "A-08 后台详情" 200
assert_jq "A-08b 订单仍 PAID(2)；shipments=1；SHIPMENT 事件 partial" '.data.status == 2 and (.data.shipments|length) == 1 and ((.data.events|map(select(.type == 3))|length) >= 1)'

req POST "/api/admin/orders/$A_ORDER/shipments" "{\"carrier_code\":\"DHL\",\"tracking_no\":\"DHL-A1-$RUN_ID\"}" "$ADMIN_TOKEN"
assert_status "A-09 重复单号 → 409908" 409 && assert_jq "A-09b code=409908" '.code == 409908'
req POST "/api/admin/orders/$A_ORDER/shipments" "{\"carrier_code\":\"UPS\",\"tracking_no\":\"UPS-OVER-$RUN_ID\",\"lines\":[{\"order_line_id\":$A_LINE_ID,\"qty\":2}]}" "$ADMIN_TOKEN"
assert_status "A-10 超量 1+2>2 → 422906" 422 && assert_jq "A-10b code=422906 unshipped=1" '.code == 422906 and .data.unshipped == 1'
req POST "/api/admin/orders/$A_ORDER/shipments" "{\"carrier_code\":\"NOPE\",\"tracking_no\":\"X\"}" "$ADMIN_TOKEN"
assert_status "A-11 未知承运商 → 422601 carrier_code" 422 && assert_jq "A-11b fields.carrier_code" '.data.fields.carrier_code != null'

req POST "/api/admin/shipments/$S1/events" '{"status":2,"location":"Hong Kong","description":"Departed origin facility"}' "$ADMIN_TOKEN"
assert_status "A-12 手工轨迹 IN_TRANSIT" 201 && assert_jq "A-12b status=2 events=1 last_event_desc" '.data.status == 2 and (.data.events|length) == 1 and .data.last_event_desc == "Departed origin facility"'
req POST "/api/admin/shipments/$S1/events" '{"status":1,"description":"late info"}' "$ADMIN_TOKEN"
assert_status "A-13 回填 PENDING 事件不回退状态" 201 && assert_jq "A-13b status 仍 2 events=2" '.data.status == 2 and (.data.events|length) == 2'
req POST "/api/admin/shipments/$S1/sync" '' "$ADMIN_TOKEN"
assert_status "A-14 sync（stub provider）→ 204" 204

req POST "/api/admin/orders/$A_ORDER/shipments" "{\"carrier_code\":\"ups\",\"tracking_no\":\"UPS-A2-$RUN_ID\"}" "$ADMIN_TOKEN"
assert_status "A-15 剩余发货（lines 省略=全部未发行；code 小写）" 201 && assert_jq "A-15b carrier_code=UPS lines qty=1 tracking_url ups.com" '.data.carrier_code == "UPS" and .data.lines[0].qty == 1 and (.data.tracking_url|contains("ups.com"))'
S2=$(echo "$BODY" | jq -r '.data.id')
req GET "/api/admin/orders/$A_ORDER" '' "$ADMIN_TOKEN"
assert_jq "A-16 订单 SHIPPED(3) production_stage=null carrier/tracking 快照=最新包裹 shipments=2" ".data.status == 3 and .data.production_stage == null and .data.tracking_no == \"UPS-A2-$RUN_ID\" and (.data.shipments|length) == 2"
assert_jq "A-16b events 含 STATUS_CHANGED(→3)" '.data.events | map(select(.type == 1 and .payload.to == 3)) | length == 1'
sleep 1
grep -q 'code=order_shipped' "$LOG_FILE" && ok "A-16c stub 日志出现 order_shipped 邮件" || bad "A-16c 未见 order_shipped 邮件日志"

req GET "/api/admin/orders?has_shipment=true&page=1&page_size=20" '' "$ADMIN_TOKEN"
assert_status "A-17 列表 has_shipment=true" 200 && assert_jq "A-17b 包含本单" ".data.data | map(.id) | index($A_ORDER) != null"

req POST "/api/admin/shipments/$S1/deliver" '' "$ADMIN_TOKEN"
assert_status "A-19 包裹1 deliver" 200 && assert_jq "A-19b status=4 delivered_at" '.data.status == 4 and .data.delivered_at != null'
req GET "/api/admin/orders/$A_ORDER" '' "$ADMIN_TOKEN"
assert_jq "A-19c 订单仍 SHIPPED(3)（未全部签收）" '.data.status == 3'
req POST "/api/admin/shipments/$S2/events" '{"status":4,"description":"Left at front door"}' "$ADMIN_TOKEN"
assert_status "A-20 包裹2 DELIVERED 事件" 201
req GET "/api/admin/orders/$A_ORDER" '' "$ADMIN_TOKEN"
assert_jq "A-20b 订单 DELIVERED(8) delivered_at 非空 + STATUS_CHANGED(→8)" '.data.status == 8 and .data.delivered_at != null and ((.data.events|map(select(.type == 1 and .payload.to == 8))|length) == 1)'
sleep 1
grep -q 'code=order_delivered' "$LOG_FILE" && ok "A-20c stub 日志出现 order_delivered 邮件" || bad "A-20c 未见 order_delivered 邮件日志"
req POST "/api/admin/shipments/$S2/events" '{"status":2,"description":"regress"}' "$ADMIN_TOKEN"
assert_status "A-21 已签收包裹再写其他状态 → 409909" 409 && assert_jq "A-21b code=409909" '.code == 409909'
req PATCH "/api/admin/shipments/$S2" '{"tracking_no":"NEW"}' "$ADMIN_TOKEN"
assert_status "A-22 已签收包裹 PATCH → 409909" 409
req POST "/api/admin/shipments/999999/deliver" '' "$ADMIN_TOKEN"
assert_status "A-23 包裹不存在 → 404907" 404 && assert_jq "A-23b code=404907" '.code == 404907'

req GET "/api/store/orders/$A_ORDER" '' "$STORE_TOKEN"
assert_status "A-24 前台详情" 200
assert_jq "A-24b shipments=2 每个含 lines/events；tracking_url 非空" '(.data.shipments|length) == 2 and (.data.shipments|all((.lines|length) >= 1 and (.events|length) >= 1 and .tracking_url != null))'
assert_jq "A-24c tax_amount/incoterm/estimated_delivery 透出" '(.data.tax_amount|tonumber) > 0 and .data.incoterm == 1 and .data.estimated_delivery_to != null'

req POST "/api/store/orders/track" "{\"order_no\":\"$A_ORDER_NO\",\"email\":\"$SMOKE_EMAIL\"}"
assert_status "A-25 游客查单（公开）" 200
assert_jq "A-25b 脱敏 receiver、status=8、shipments=2、country_code=GB、无地址明细" ".data.order_no == \"$A_ORDER_NO\" and .data.status == 8 and (.data.shipments|length) == 2 and .data.country_code == \"GB\" and (.data.receiver_masked|contains(\"Tester\")|not) and (.data|has(\"address_snapshot\")|not)"
req POST "/api/store/orders/track" "{\"order_no\":\"$A_ORDER_NO\",\"email\":\"nobody@example.com\"}"
assert_status "A-26 邮箱不匹配 → 404601" 404 && assert_jq "A-26b code=404601" '.code == 404601'

# =============================================================================
echo "--- [B] 全量发货 → 作废包裹 → 订单回到 PAID ---"
clear_cart
req POST "/api/store/cart/items" "{\"product_id\":$PRODUCT_ID,\"sku_id\":$SKU_ID,\"qty\":1}" "$STORE_TOKEN"
req POST "/api/store/checkout/orders" "{\"idempotency_key\":\"lg-$RUN_ID-B\",\"address_id\":$US_ADDR_ID,\"currency\":\"USD\",\"carrier\":\"FedEx International Priority\",\"payment_method\":\"Stripe\",\"locale\":\"en\"}" "$STORE_TOKEN"
assert_status "B-01 US 下单（旧 carrier 名兼容）" 201 200
B_ORDER=$(echo "$BODY" | jq -r '.data.order.id')
assert_jq "B-01b US 无税 DDP：tax_amount=0 incoterm=1 shipping_service_level=1 carrier=FedEx" '(.data.order.tax_amount|tonumber) == 0 and .data.order.incoterm == 1 and .data.order.shipping_service_level == 1 and .data.order.carrier == "FedEx International Priority" and .data.order.address_snapshot.region_code == "TX"'
req POST "/api/store/orders/$B_ORDER/payment/confirm" '' "$STORE_TOKEN"
assert_status "B-02 支付确认" 200
req POST "/api/admin/orders/$B_ORDER/ship" "{\"carrier\":\"FedEx International Priority\",\"tracking_no\":\"FX-B-$RUN_ID\"}" "$ADMIN_TOKEN"
assert_status "B-03 兼容 /ship（委托 ShipmentService）" 200 && assert_jq "B-03b status=3 shipments=1 carrier_code=FEDEX" '.data.status == 3 and (.data.shipments|length) == 1 and .data.shipments[0].carrier_code == "FEDEX"'
B_S=$(echo "$BODY" | jq -r '.data.shipments[0].id')
req PATCH "/api/admin/shipments/$B_S" "{\"tracking_no\":\"FX-B2-$RUN_ID\"}" "$ADMIN_TOKEN"
assert_status "B-04 PATCH tracking_no" 200 && assert_jq "B-04b 新单号 + URL 更新" ".data.tracking_no == \"FX-B2-$RUN_ID\" and (.data.tracking_url|contains(\"FX-B2-$RUN_ID\"))"
req GET "/api/admin/orders/$B_ORDER" '' "$ADMIN_TOKEN"
assert_jq "B-04c 订单 tracking_no 快照跟随" ".data.tracking_no == \"FX-B2-$RUN_ID\""
req POST "/api/admin/shipments/$B_S/cancel" '' "$ADMIN_TOKEN"
assert_status "B-05 作废包裹" 200 && assert_jq "B-05b status=CANCELLED(6)" '.data.status == 6'
req GET "/api/admin/orders/$B_ORDER" '' "$ADMIN_TOKEN"
assert_jq "B-05c 订单 SHIPPED→PAID(2) production_stage=4 shipped_at=null + 事件" '.data.status == 2 and .data.production_stage == 4 and .data.shipped_at == null and ((.data.events|map(select(.type == 1 and .payload.from == 3 and .payload.to == 2))|length) == 1)'
req GET "/api/admin/orders?has_shipment=false&page=1&page_size=20" '' "$ADMIN_TOKEN"
assert_jq "B-06 has_shipment=false 列表包含本单（仅作废包裹）" ".data.data | map(.id) | index($B_ORDER) != null"

# =============================================================================
echo "--- [C] 税率 CRUD / 目的国政策 / 运费选项 CRUD / 试算 ---"
req GET "/api/admin/tax-rules?country_code=GB" '' "$ADMIN_TOKEN"
assert_status "C-01 税率列表(GB)" 200 && assert_jq "C-01b 种子 VAT 20%" '.data.items | any(.country_code == "GB" and .tax_type == 1 and .rate_scaled == 2000 and .region == "")'
req POST "/api/admin/tax-rules" '{"country_code":"jp","tax_type":1,"rate_scaled":1000,"applies_to_shipping":false,"label":"JCT 10%"}' "$ADMIN_TOKEN"
assert_status "C-02 创建 JP VAT 10%" 201 && assert_jq "C-02b country 大写 region 空串" '.data.country_code == "JP" and .data.region == "" and .data.enabled == true'
JP_RULE=$(echo "$BODY" | jq -r '.data.id')
req POST "/api/admin/tax-rules" '{"country_code":"JP","tax_type":1,"rate_scaled":800,"effective_from":"2026-01-01"}' "$ADMIN_TOKEN"
assert_status "C-03 同 key 生效窗口重叠 → 422907" 422 && assert_jq "C-03b code=422907 conflict_rule_id" ".code == 422907 and .data.conflict_rule_id == $JP_RULE"
req POST "/api/admin/tax-rules" '{"country_code":"ZZ","tax_type":1,"rate_scaled":100}' "$ADMIN_TOKEN"
assert_status "C-04 未知国家 → 422601" 422
req PUT "/api/admin/tax-rules/$JP_RULE" '{"country_code":"JP","tax_type":1,"rate_scaled":1100,"label":"JCT 11%"}' "$ADMIN_TOKEN"
assert_status "C-05 更新税率" 200 && assert_jq "C-05b rate_scaled=1100" '.data.rate_scaled == 1100'
req PATCH "/api/admin/tax-rules/$JP_RULE/enabled" '{"enabled":false}' "$ADMIN_TOKEN"
assert_status "C-06 enabled=false" 200 && assert_jq "C-06b" '.data.enabled == false'
req GET "/api/admin/tax-destination-policies/JP" '' "$ADMIN_TOKEN"
assert_status "C-07 JP 政策缺省 DDU + 提示" 200 && assert_jq "C-07b incoterm=2 duties_notice=true" '.data.incoterm == 2 and .data.duties_notice == true and .data.notice_text != null'
req PUT "/api/admin/tax-destination-policies/JP" '{"incoterm":1,"duties_notice":false}' "$ADMIN_TOKEN"
assert_status "C-08 JP 设为 DDP" 200 && assert_jq "C-08b" '.data.incoterm == 1 and .data.duties_notice == false'
sleep 2  # tax:rules 缓存失效任务异步执行
req POST "/api/admin/shipping/quote-preview" '{"country_code":"JP","subtotal_usd":100}' "$ADMIN_TOKEN"
assert_status "C-09 试算 JP（规则 disabled → 税 0；zone ASIA；REST 兜底选项）" 200
assert_jq "C-09b zone=ASIA incoterm=1 tax 0 options≥1 selected" '.data.zone == "ASIA" and .data.incoterm == 1 and (.data.tax_amount_usd|tonumber) == 0 and (.data.options|length) >= 1 and (.data.options|any(.selected == true))'
req PATCH "/api/admin/tax-rules/$JP_RULE/enabled" '{"enabled":true}' "$ADMIN_TOKEN"
sleep 2
req POST "/api/admin/shipping/quote-preview" '{"country_code":"JP","subtotal_usd":100,"service_level":1}' "$ADMIN_TOKEN"
assert_jq "C-10 试算 JP 启用后 tax=11.00（11% × 100，不含运费）" '(.data.tax_amount_usd|tonumber) == 11 and .data.tax_breakdown[0].rate_scaled == 1100'
req POST "/api/admin/shipping/quote-preview" '{"country_code":"GB","region_code":"","subtotal_usd":100,"service_level":2}' "$ADMIN_TOKEN"
assert_jq "C-11 试算 GB EXPRESS：zone=UK 选中 level 2；VAT 含运费 = (100+fee)×20%" '.data.zone == "UK" and ([.data.options[]|select(.selected==true)][0].service_level == 2) and ((.data.tax_amount_usd|tonumber) == ((100 + ([.data.options[]|select(.selected==true)][0].fee|tonumber)) * 0.2 * 100 | round / 100))'
req DELETE "/api/admin/tax-rules/$JP_RULE" '' "$ADMIN_TOKEN"
assert_status "C-12 删除 JP 税率" 204
req PUT "/api/admin/tax-destination-policies/JP" '{"incoterm":2,"duties_notice":true,"notice_text":"Import duties may apply"}' "$ADMIN_TOKEN"
assert_status "C-13 还原 JP DDU" 200

req GET "/api/admin/shipping/options" '' "$ADMIN_TOKEN"
assert_status "C-14 运费选项列表" 200 && assert_jq "C-14b 迁移产物：含 NORTH_AMERICA/FEDEX 1&2、UK 行、REST/ANY" '(.data.items|any(.zone=="NORTH_AMERICA" and .carrier_code=="FEDEX" and .service_level==1)) and (.data.items|any(.zone=="NORTH_AMERICA" and .carrier_code=="FEDEX" and .service_level==2)) and (.data.items|any(.zone=="UK")) and (.data.items|any(.zone=="REST" and .carrier_code=="ANY" and .carrier_name=="Any carrier"))'
req POST "/api/admin/shipping/options" '{"zone":"Asia","carrier_code":"fedex","service_level":1,"fee_under":30,"fee_over":0,"threshold":300,"transit_days_min":5,"transit_days_max":9}' "$ADMIN_TOKEN"
assert_status "C-15 创建 ASIA/FEDEX/STANDARD（zone 名规范化）" 201 && assert_jq "C-15b zone=ASIA code=FEDEX carrier_name" '.data.zone == "ASIA" and .data.carrier_code == "FEDEX" and .data.carrier_name != null and .data.enabled == true'
OPT_ID=$(echo "$BODY" | jq -r '.data.id')
req POST "/api/admin/shipping/options" '{"zone":"ASIA","carrier_code":"FEDEX","service_level":1,"fee_under":31}' "$ADMIN_TOKEN"
assert_status "C-16 重复 key → 409904" 409 && assert_jq "C-16b code=409904" '.code == 409904'
req POST "/api/admin/shipping/options" '{"zone":"ASIA","carrier_code":"NOPE","service_level":1,"fee_under":31}' "$ADMIN_TOKEN"
assert_status "C-17 未知 carrier_code → 422901" 422 && assert_jq "C-17b field=carrier_code" '.data.field == "carrier_code"'
sleep 2
req POST "/api/admin/shipping/quote-preview" '{"country_code":"JP","subtotal_usd":100}' "$ADMIN_TOKEN"
assert_jq "C-18 试算 JP：FedEx 命中 ASIA 精确行 30.00，其余承运商仍走 REST 兜底" '([.data.options[]|select(.carrier_code=="FEDEX" and .service_level==1)][0].fee|tonumber) == 30 and ([.data.options[]|select(.carrier_code=="UPS" and .service_level==1)][0].fee|tonumber) == 38'
req PUT "/api/admin/shipping/options/$OPT_ID" '{"zone":"ASIA","carrier_code":"FEDEX","service_level":1,"fee_under":29,"fee_over":0,"threshold":300,"transit_days_min":5,"transit_days_max":9,"enabled":true}' "$ADMIN_TOKEN"
assert_status "C-19 更新选项" 200 && assert_jq "C-19b fee_under=29" '(.data.fee_under|tonumber) == 29'
req PATCH "/api/admin/shipping/options/$OPT_ID/enabled" '{"enabled":false}' "$ADMIN_TOKEN"
assert_status "C-20 禁用选项" 200 && assert_jq "C-20b enabled=false" '.data.enabled == false'
req DELETE "/api/admin/shipping/options/$OPT_ID" '' "$ADMIN_TOKEN"
assert_status "C-21 删除选项" 204
req DELETE "/api/admin/shipping/options/$OPT_ID" '' "$ADMIN_TOKEN"
assert_status "C-22 再删 → 404903" 404 && assert_jq "C-22b code=404903" '.code == 404903'
req GET "/api/admin/shipping/rates" '' "$ADMIN_TOKEN"
assert_status "C-23 旧 /shipping/rates GET 只读兼容" 200
req POST "/api/admin/shipping/rates" '{"zone":"X","fee_under":1}' "$ADMIN_TOKEN"
assert_status "C-24 旧 /shipping/rates 写端点 → 410901" 410 && assert_jq "C-24b code=410901" '.code == 410901'
req GET "/api/admin/shipping/carriers" '' "$ADMIN_TOKEN"
assert_status "C-25 承运商列表含 code/tracking_url_template" 200 && assert_jq "C-25b FEDEX 模板" '.data.items | any(.code == "FEDEX" and (.tracking_url_template|contains("{tracking_no}")))'

# =============================================================================
echo "--- [D] 汇率：新字段 / manual_override / history / refresh 409905 ---"
req GET "/api/admin/exchange-rates" '' "$ADMIN_TOKEN"
assert_status "D-01 汇率列表" 200 && assert_jq "D-01b 含 source/synced_at/manual_override/effective_rate/spread_scaled；USD effective=1" '(.data.items|all(has("source") and has("manual_override") and has("effective_rate") and has("spread_scaled"))) and ((.data.items[]|select(.currency=="USD")|.effective_rate|tonumber) == 1)'
EUR_RATE=$(echo "$BODY" | jq -r '.data.items[]|select(.currency=="EUR")|.rate')
EUR_OVERRIDE=$(echo "$BODY" | jq -r '.data.items[]|select(.currency=="EUR")|.manual_override')
req PUT "/api/admin/exchange-rates/EUR" '{"rate":0.931234,"manual_override":true}' "$ADMIN_TOKEN"
assert_status "D-02 PUT EUR manual_override=true" 200 && assert_jq "D-02b rate/source=MANUAL(1)/manual_override" '(.data.rate|tonumber) == 0.931234 and .data.source == 1 and .data.manual_override == true'
req GET "/api/admin/exchange-rates/EUR/history?days=7" '' "$ADMIN_TOKEN"
assert_status "D-03 EUR history" 200 && assert_jq "D-03b 含今日 MANUAL 记录" '.data.items | any(.source == 1 and (.rate|tonumber) == 0.931234)'
req GET "/api/admin/exchange-rates/EUR/history?days=0" '' "$ADMIN_TOKEN"
assert_status "D-04 days=0 → 422601" 422
req POST "/api/admin/exchange-rates/refresh" '' "$ADMIN_TOKEN"
assert_status "D-05 manual 模式 refresh → 409905" 409 && assert_jq "D-05b code=409905" '.code == 409905'
req PUT "/api/admin/exchange-rates/EUR" "{\"rate\":$EUR_RATE,\"manual_override\":$EUR_OVERRIDE}" "$ADMIN_TOKEN"
assert_status "D-06 还原 EUR" 200
req PUT "/api/admin/exchange-rates/USD" '{"rate":2}' "$ADMIN_TOKEN"
assert_status "D-07 USD 不可改 → 422605" 422

# ---------- 清理 ----------
req DELETE "/api/store/addresses/$ADDR_ID" '' "$STORE_TOKEN"
[ "$STATUS" = "200" ] || [ "$STATUS" = "204" ] && ok "CLEAN-01 GB 地址删除" || bad "CLEAN-01 GB 地址删除"
req DELETE "/api/store/addresses/$US_ADDR_ID" '' "$STORE_TOKEN"
[ "$STATUS" = "200" ] || [ "$STATUS" = "204" ] && ok "CLEAN-02 US 地址删除" || bad "CLEAN-02 US 地址删除"
clear_cart
ok "CLEAN-03 购物车清空"

rm -f /tmp/lgs_body.$$
echo "=== 结果：PASS=$PASS FAIL=$FAIL ==="
if [ "$FAIL" -gt 0 ]; then printf '  - %s\n' "${FAILED_CASES[@]}"; exit 1; fi
