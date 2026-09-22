#!/usr/bin/env bash
# =============================================================
# 脚本名称: sync-hk-to-local.sh
# 功能描述: HK 生产 → 本地 全量数据同步(决策:全量/不脱敏/双源)。
#           ① HK mysqldump --single-transaction 全量 identity(Java 单库)
#           ② 本地 DROP/CREATE identity 后导入(生产数据权威覆盖)
#           ③ dreamy_server 身份域镜像(user/admin_session;本地压测膨胀表 TRUNCATE)
#           ④ 图片:STORAGE_MODE=stub 双端一致,图片在仓库 public/ 随镜像部署,无需同步
#           ⑤ 对账:7 关键表行数比对(不齐即 exit 1)
# 使用方式: bash scripts/sync-hk-to-local.sh
# 依赖环境: ssh root@47.238.216.69(免密)、本地 .env.deploy、docker dreamy-mysql-1
# =============================================================
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
source .env.deploy

HK="${DEPLOY_SSH:-root@47.238.216.69}"
echo "[sync] ① HK mysqldump(--single-transaction)..."
ssh -o ConnectTimeout=15 "$HK" 'cd /opt/dreamy && HKPW=$(grep MYSQL_ROOT_PASSWORD .env.deploy | cut -d= -f2); docker exec dreamy-mysql-1 sh -c "mysqldump -uroot -p$HKPW --single-transaction --routines --triggers --databases identity" | gzip > /tmp/identity_full.sql.gz; ls -lh /tmp/identity_full.sql.gz'
scp -q "$HK:/tmp/identity_full.sql.gz" /tmp/identity_full.sql.gz

echo "[sync] ② 本地导入(drop/recreate identity)..."
docker exec dreamy-mysql-1 mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE IF EXISTS identity; CREATE DATABASE identity CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
gunzip -cf /tmp/identity_full.sql.gz | docker exec -i dreamy-mysql-1 mysql -uroot -p"$MYSQL_ROOT_PASSWORD" identity

echo "[sync] ③ dreamy_server 身份域镜像(user/admin_session)+ 膨胀清理..."
docker exec dreamy-mysql-1 mysql -uroot -p"$MYSQL_ROOT_PASSWORD" dreamy_server -e "
TRUNCATE login_history; TRUNCATE identity_email; TRUNCATE otp_code;
SET FOREIGN_KEY_CHECKS=0; TRUNCATE user;
INSERT INTO user(id, email, email_verified, locale_pref, name, phone, tier, status, avatar, joined_at, deleted_at, anonymized, anonymized_at, version, created_at, updated_at)
SELECT id, email, email_verified, locale_pref, name, phone, tier, status, avatar, joined_at, deleted_at, anonymized, anonymized_at, version, created_at, updated_at FROM identity.user;
TRUNCATE admin_session; INSERT INTO admin_session SELECT * FROM identity.admin_session;
SET FOREIGN_KEY_CHECKS=1;"

echo "[sync] ④ 对账(7 表)..."
ok=1
for pair in "dreamy_server.user identity.user" "identity.product identity.product" "identity.sku identity.sku" "identity.review identity.review" "identity.banner identity.banner" "identity.orders identity.orders" "identity.coupon identity.coupon"; do
  LT=${pair%% *}; RT=${pair##* }
  LN=$(docker exec dreamy-mysql-1 mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT COUNT(*) FROM ${LT%%.*}.${LT##*.}" 2>/dev/null)
  # 远端计数(临时建视图太重;直接再 dump 计数——用 information_schema 近似不可靠,取 dump 里 INSERT 计数省略,用远端直查)
  RN=$(ssh -o ConnectTimeout=15 "$HK" "cd /opt/dreamy && HKPW=\$(grep MYSQL_ROOT_PASSWORD .env.deploy | cut -d= -f2); docker exec dreamy-mysql-1 mysql -uroot -p\$HKPW -N -e 'SELECT COUNT(*) FROM ${RT%%.*}.${RT##*.}'" 2>/dev/null)
  if [ "$LN" != "$RN" ]; then echo "  ✗ $LT: local=$LN remote=$RN"; ok=0; else echo "  ✓ $LT=$LN"; fi
done
[ "$ok" = "1" ] && echo "[sync] ✔ 对账全部一致" || { echo "[sync] ✗ 对账不一致"; exit 1; }
echo "[sync] ⑤ 图片:STORAGE_MODE=stub,图片在仓库 public/(随镜像部署),无需同步"
echo "[sync] 完成"
