# Dreamy 部署手册(本地编译 → 服务器本地构建镜像 → 香港服务器)

## 架构总览

```
本地 Mac(release.sh)                     香港服务器 x86_64(remote-build.sh + deploy.sh)
──────────────────────────               ──────────────────────────────────────────────
gradlew bootJar ──┐                      git pull 源码 + 接收 scp 上来的 JAR/dist/Rust 二进制
pnpm build(admin)─┼→ scp 产物 ────────→  buildx linux/amd64 原生构建四镜像(--load 进本机 daemon)
cargo zigbuild ───┘                        dreamy-{backend,server,store,admin}
(Rust musl 静态)                            双 tag:latest + <时间戳>-<短SHA>(仅存本机)
                                         deploy.sh:校验本地镜像 → up -d(替换容器)
                                           → 健康检查 → prune 旧镜像
```

- **无外部镜像仓库**:镜像构建后直接装载进服务器本机 docker daemon,tag 历史即本机 `docker images` 所见(回滚/发布档案都依赖它)。
- 编译分工:后端 JAR / admin 静态产物 / Rust server musl 二进制跨架构,在本地 Mac 原生编译后 scp;store(standalone 含平台 SWC 二进制)在服务器 `linux/amd64` 环境内构建。服务器需装 `docker` + `git`,无需 JDK/Node/Rust。
- **全部配置外置** `.env.deploy`:镜像内零地址零密钥;compose 内只有 `${VAR:-默认值}` 引用。公网地址只填 `PUBLIC_STORE_URL`/`PUBLIC_ADMIN_URL` 两行,其余(CORS/SITE_BASE_URL/NEXT_PUBLIC_*/VITE_*)自动派生。
- 服务间请求全程同源:store 浏览器 `/api` 由 Next middleware 反代(身份域前缀分流至 `SERVER_INTERNAL_URL`,其余 `BACKEND_INTERNAL_URL`)、admin 浏览器 `/api` 由其容器内 nginx 反代、store RSC 取数直连 `BACKEND_INTERNAL_URL`。
- **边缘网关**:`gateway` 容器(nginx)是唯一对外入口,**单端口按路径分流并终结 TLS**:`/` → store、`/admin/` → admin(网关剥离前缀)、**身份域 API(9 条前缀:`/api/store/auth/`、`/api/store/account/`、`/api/admin/auth/`、`/api/admin/auth-config`、`/api/admin/admins`、`/api/admin/roles`、`/api/admin/permissions`、`/api/admin/users`、`/api/admin/operation-logs`)→ server(Rust,18082)**、其余 `/api/` 与 `/actuator/` → backend;backend/server 仅绑宿主回环,admin/store 不映射宿主端口。HTTPS 为硬要求(Google OIDC 等外部回调的公网 redirect_uri 仅认 https),证书挂载自 `nginx/certs/`。
- **双库拓扑**(server 引入后):同一 MySQL 实例内 `identity` 库(Java 既有,含 operation_log/email_template 两张共享过渡表)与 `dreamy_server` 库(Rust 身份域 11 张表,启动幂等自举;生产切换前由 `scripts/migrate-identity.sh` 在维护窗口做一次性数据迁移)共存。分流前缀矩阵由 `scripts/test-gateway-routes.sh` 门禁保障。

## 一、一次性准备

### 1. TLS 证书(公网域名必配,本地验证可自签)

```bash
# 生产:阿里云控制台 → 数字证书管理服务 → SSL 证书 → 免费证书(20 张/年)
#   申请单域名证书(如 dreamy.cerestech.cn,DNS 验证域名在同账号时自动通过)
#   下载 nginx 格式,重命名后放到服务器与本地仓库的 nginx/certs/(目录已被 .gitignore 排除):
#     <证书>.pem  → nginx/certs/fullchain.pem   (若下载包含中间证书链,拼接: cat cert.pem chain.pem > fullchain.pem)
#     <证书>.key  → nginx/certs/privkey.pem
# 注意:阿里云免费证书有效期 3 个月,到期需重新申请替换(替换后 docker compose up -d gateway 即生效)。

# 本地验证:一键自签(浏览器告警属预期)
bash scripts/gen-dev-cert.sh
```

### 2. 本地 Mac

```bash
# 首次使用先预拉基础镜像(仅供本地 --load 验证构建提速;服务器构建会自行直连 Docker Hub 拉取)
bash scripts/pull-base-images.sh

cd <仓库>
cp .env.deploy.example .env.deploy
# 填写:<服务器IP>、5 个密钥(JWT/AES/HMAC 至少 32 字节)、MYSQL_ROOT_PASSWORD;生产发布用 .env.deploy.prod(含 DEPLOY_SSH)
# 注意:CORS 变量要填浏览器实际访问的完整来源(协议+域名+端口,127.0.0.1 与 localhost 是不同源,逗号分隔多值)
```

### 3. 香港服务器(一次性初始化)

```bash
# 以 root 或 sudo 执行;之后日常部署需要 docker 组权限或 sudo
curl -fsSL https://get.docker.com | sh          # 安装 Docker(含 compose 插件)
apt-get install -y git                          # Debian/Ubuntu;CentOS 用 yum

# clone 私有仓库(需要凭证:HTTPS personal access token 或部署密钥)
git clone <仓库地址> /opt/dreamy && cd /opt/dreamy

cp .env.deploy.example .env.deploy
vim .env.deploy        # 与本地同一份内容:公网地址、密钥等
```

## 二、日常发布(每次上线)

```bash
# ① 本地 Mac:编译 JAR/dist/Rust 二进制 → scp → 触发服务器构建四镜像(本地双 tag:latest + <时间戳>-<短SHA>)
bash scripts/release.sh --env .env.deploy.prod

# ② 香港服务器:校验本地镜像 → 替换容器 + 健康检查
cd /opt/dreamy && bash scripts/deploy.sh
```

`deploy.sh` 流程:`git pull`(仅同步编排/脚本)→ 校验 `dreamy-{backend,server,store,admin}` 本地镜像存在 → `up -d`(MySQL/Redis 数据卷不受影响)→ 四个服务健康探测(后端 180s 超时,含首启动 DdlAuto 自动建表;server 探 `/readyz` 60s,启动即幂等自举 `dreamy_server` 库)→ `docker image prune`。

## 三、回滚

```bash
# 服务器上:改 tag 为仍在本机的历史版本,重跑部署(docker images 可查所有本地历史 tag)
sed -i 's/^IMAGE_TAG=.*/IMAGE_TAG=202609081551-a1b2c3d/' .env.deploy
bash scripts/deploy.sh

# 注意:tag 档案只存在于服务器本机 daemon,若旧 tag 已被清理,需 checkout 对应 commit
# 重新走一遍 release.sh 构建链恢复
```

## 四、首次启动须知

- **空库自动建表**:后端首次启动由 huihao-mysql `DdlAuto: update` 自动创建全部表结构。
- **演示数据**:`.env.deploy` 置 `DEMO_SEED_ENABLED=true` 可灌入演示商品/内容(验收完可关)。
- **首个管理员**:`DREAMY_BOOTSTRAP_ADMIN_EMAIL/PASSWORD` 填入后启动即创建,登录 `https://<域名>:<端口>/admin` 管理端;创建后可将两项置空。
- **MySQL 仅绑 127.0.0.1:3306**,外部管理走 SSH 隧道:
  ```bash
  ssh -L 3306:127.0.0.1:3306 root@<服务器IP>   # 然后本地用 127.0.0.1:3306 连接
  ```

## 五、(可选)本地数据迁移到服务器

```bash
# 本地导出(pd-mysql 容器)
docker exec pd-mysql mysqldump -uroot -p"<本地密码>" --single-transaction identity > identity-dump.sql

# 传到服务器后导入(先只起 mysql,导入完成再起全栈)
scp identity-dump.sql root@<服务器IP>:/opt/dreamy/
cd /opt/dreamy
docker compose --env-file .env.deploy up -d mysql
docker exec -i "$(docker compose ps -q mysql)" mysql -uroot -p"<MYSQL_ROOT_PASSWORD>" identity < identity-dump.sql
bash scripts/deploy.sh
```

## 六、变量生效速查

| 变更类型 | 例子 | 生效方式 |
|---|---|---|
| 运行时变量 | 密钥、`*_MODE`、`TZ`、`JAVA_OPTS`、内外网地址 | 改 `.env.deploy` → `docker compose --env-file .env.deploy up -d` |
| 构建变量 | `NEXT_PUBLIC_*`、`VITE_STORE_BASE_URL` | 改 `.env.deploy` → 本地 `release.sh` → 服务器 `deploy.sh` |
| 编排/脚本 | `docker-compose.yml`、`scripts/*` | `git pull`(deploy.sh 已含)→ `up -d` |

## 七、端口与安全

| 对外端口(gateway) | 路径 | 转发到 | 说明 |
|---|---|---|---|
| 5173(服务器按需改) | `/` | portal-store:3000 | 消费端门户 |
| ↑ 同端口 | `/admin/` | portal-admin:80 | 管理后台(网关剥离 /admin 前缀) |
| ↑ 同端口 | `/api/store/auth/`、`/api/store/account/` | server:18082 | 身份域 API(Rust) |
| ↑ 同端口 | `/api/admin/auth/`、`auth-config`、`admins`、`roles`、`permissions`、`users`、`operation-logs` | server:18082 | 身份域 API(Rust) |
| ↑ 同端口 | 其余 `/api/`、`/actuator/` | backend:18081 | 后端 API(webhook/OIDC 回调/调试) |

- **网关是唯一对外端口**;backend(18081)与 server(18082 REST)仅绑 127.0.0.1(健康检查/调试),server gRPC 18083 仅 compose 内网(backend 通道),MySQL 仅绑 127.0.0.1:3306(SSH 隧道管理),Redis 仅 compose 内网。
- 网关身份域分流前缀与 `scripts/test-gateway-routes.sh` 路由矩阵互为门禁,改前缀必须同步改矩阵。
- 建议服务器防火墙/安全组仅放行网关端口(如 60080)与 SSH。

## 八、已知限制(当前 IP:端口直连模式)

- Stripe 支付(`STRIPE_MODE=stub`)、Stripe webhook、Google/Apple OIDC 回调、Cloudflare R2 图片存储(`STORAGE_MODE=stub`)均需公网域名 + HTTPS 后切换 `*_MODE=real` 并补配密钥。
- RabbitMQ 事件(`MQ_MODE=stub`)、GA4(`GA4_MODE=stub`)、邮件(`MAIL_MODE=stub`)当前为内置模拟。
- 消费端 OIDC 登录在无域名环境下不可用(回调地址限制);管理端登录不受影响。
- **部署后首页短暂空白**:backend 刚就绪时若 store 的 RSC 请求先于 demo seed 完成打到 home 接口,空结果会进 JetCache(5 分钟 TTL)。等 5 分钟自动恢复,或 `docker compose --env-file .env.deploy restart backend` 立即清除;根治需应用层对空 home 结果不缓存。
