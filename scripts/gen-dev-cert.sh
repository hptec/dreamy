#!/usr/bin/env bash
# =============================================================
# 脚本名称: gen-dev-cert.sh
# 功能描述: 生成本地验证用自签 TLS 证书(网关已 ssl 化,本地 compose 起动也需要证书)
#           产物: nginx/certs/fullchain.pem + privkey.pem(已被 .gitignore 排除)
# 使用方式: bash scripts/gen-dev-cert.sh
# 注意:     仅限本地验证。生产证书用阿里云免费证书(DNS 验证)下载 nginx 格式,
#           重命名为同名文件放入 nginx/certs/ 后随部署生效。
# =============================================================
set -euo pipefail

CERT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/nginx/certs"
mkdir -p "${CERT_DIR}"

openssl req -x509 -newkey rsa:2048 -nodes -days 825 \
  -keyout "${CERT_DIR}/privkey.pem" \
  -out "${CERT_DIR}/fullchain.pem" \
  -subj "/CN=dreamy-local-dev" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"

echo "[cert] 自签证书已生成: ${CERT_DIR}/{fullchain.pem,privkey.pem}"
echo "[cert] 浏览器访问 https://localhost:<端口> 会告警属预期(测试环境可手动信任)"
