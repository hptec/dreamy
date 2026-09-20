#!/usr/bin/env bash
# refresh 旋转链三连刷诊断
set -uo pipefail
cd "$(dirname "$0")/.."
set -a; source .env.deploy; set +a

E="bench-chain-$RANDOM@load.test"
HASH=$(python3 -c "import bcrypt;print(bcrypt.hashpw(b'424242', bcrypt.gensalt(4)).decode())")

docker compose --env-file .env.deploy exec -T -e MQ="INSERT INTO otp_code (email,code_hash,length,expires_at,attempts,max_attempts,status,last_sent_at,version,created_at,updated_at) VALUES ('$E', '$HASH', 6, DATE_ADD(DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR), INTERVAL 10 MINUTE), 0, 5, 1, DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR), 0, DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR), DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR));" mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" dreamy_server -N -e "$MQ"' 2>/dev/null
echo "INSERT done: $E"

RT0=$(curl -sk -m 8 -X POST "http://127.0.0.1:18082/api/store/auth/otp/verify" -H 'Content-Type: application/json' -d "{\"email\":\"$E\",\"code\":\"424242\"}" | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['tokens']['refresh_token'])")
echo "verify RT0=${RT0:0:14}"

for N in 1 2 3; do
  PREV="RT$((N-1))"
  R=$(curl -sk -m 8 -X POST "http://127.0.0.1:18082/api/store/auth/refresh" -H 'Content-Type: application/json' -d "{\"refresh_token\":\"${!PREV}\"}")
  CODE=$(echo "$R" | python3 -c "import json,sys;print(json.load(sys.stdin)['code'])")
  NEXT=$(echo "$R" | python3 -c "import json,sys;d=json.load(sys.stdin);print(d.get('data',{}).get('tokens',{}).get('refresh_token',''))" 2>/dev/null)
  echo "刷$N: code=$CODE 新token=${NEXT:0:14}"
  eval "RT$N=\"$NEXT\""
done
