# Dreamy 部署手册(本地编译 → 阿里云 ACR → 香港服务器)

## 架构总览

```
本地 Mac(release.sh)                      阿里云 ACR 个人版·香港         香港服务器 x86_64(deploy.sh)
──────────────────────────                ─────────────────────         ──────────────────────────────
gradlew bootJar ──┐                       registry.cn-hongkong.         docker compose pull
pnpm build(admin)─┼→ buildx linux/amd64   aliyuncs.com/<ns>/            → up -d(替换容器)
buildx 构建 store ─┘   三镜像双 tag  ──────────→  dreamy-{backend,          → 健康检查
                                                  store,admin}              → prune 旧镜像
```

- **编译全部在本地**完成,服务器只拉镜像运行(需装 `docker` + `git`,无需 JDK/Node)。
- 后端 JAR / admin 静态产物本身跨架构,本地原生编译;store 镜像在 buildx 的 `linux/amd64` 环境内构建(standalone 含平台 SWC 二进制)。
- **全部配置外置** `.env.deploy`:镜像内零地址零密钥;compose 内只有 `${VAR:-默认值}` 引用。公网地址只填 `PUBLIC_STORE_URL`/`PUBLIC_ADMIN_URL` 两行,其余(CORS/SITE_BASE_URL/NEXT_PUBLIC_*/VITE_*)自动派生。
- 服务间请求全程同源:store 浏览器 `/api` 由 Next middleware 反代、admin 浏览器 `/api` 由其容器内 nginx 反代、store RSC 取数直连 `BACKEND_INTERNAL_URL`。
- **边缘网关**:`gateway` 容器(nginx)是唯一对外入口,5173/5174/18081 三端口转发到内网 store/admin/backend,内部服务不再直接暴露;上域名 + HTTPS 时只改 `nginx/gateway.conf.template` 一处。

## 一、一次性准备

### 1. 阿里云 ACR(个人版免费)

1. 控制台 → 容器镜像服务 → **个人实例**(选 **香港** 地域,与服务器同地域拉取最快)。
2. 创建**命名空间**(如 `dreamy-harryhe`),记入 `.env.deploy` 的 `ACR_NAMESPACE`。
3. 在命名空间下创建 3 个**私有仓库**:`dreamy-backend`、`dreamy-store`、`dreamy-admin`(代码源选"本地仓库")。
4. 左侧"访问凭证"设置**固定密码**,本地与服务器 `docker login` 都用它。

### 2. 本地 Mac

```bash
# Docker Desktop 设置确认:
#   Settings → General → Use Rosetta for x86/amd64 emulation on Apple Silicon(勾选,store 镜像构建提速明显)
docker login registry.cn-hongkong.aliyuncs.com   # 用户名=阿里云全账号,密码=固定密码

# 首次使用先预拉基础镜像(Docker Hub 直连极慢,脚本走 daocloud 镜像站并 retag 官方名;
# 多架构 manifest 一次覆盖本地验证与 amd64 发布,后续构建均命中本地缓存)
bash scripts/pull-base-images.sh

cd <仓库>
cp .env.deploy.example .env.deploy
# 填写:ACR_NAMESPACE、<服务器IP>、5 个密钥(JWT/AES/HMAC 至少 32 字节)、MYSQL_ROOT_PASSWORD
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
vim .env.deploy        # 与本地同一份内容:ACR、服务器 IP、全部密钥

docker login registry.cn-hongkong.aliyuncs.com
```

## 二、日常发布(每次上线)

```bash
# ① 本地 Mac:编译 + 推镜像(双 tag:latest + git-<sha>)
bash scripts/release.sh

# ② 香港服务器:拉取 + 替换 + 健康检查
cd /opt/dreamy && bash scripts/deploy.sh
```

`deploy.sh` 流程:`git pull`(仅同步编排/脚本)→ `docker compose pull` → `up -d`(MySQL/Redis 数据卷不受影响)→ 三个服务健康探测(后端 180s 超时,含首启动 DdlAuto 自动建表)→ `docker image prune`。

## 三、回滚

```bash
# 服务器上:改 tag 为任意历史版本,重跑部署
sed -i 's/^IMAGE_TAG=.*/IMAGE_TAG=git-a1b2c3d/' .env.deploy   # git log 查历史短 SHA
bash scripts/deploy.sh
```

## 四、首次启动须知

- **空库自动建表**:后端首次启动由 huihao-mysql `DdlAuto: update` 自动创建全部表结构。
- **演示数据**:`.env.deploy` 置 `DEMO_SEED_ENABLED=true` 可灌入演示商品/内容(验收完可关)。
- **首个管理员**:`DREAMY_BOOTSTRAP_ADMIN_EMAIL/PASSWORD` 填入后启动即创建,登录 `http://<IP>:5174` 管理端;创建后可将两项置空。
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

| 对外端口(gateway) | 转发到 | 说明 |
|---|---|---|
| 5173 | portal-store:3000 | 消费端门户 |
| 5174 | portal-admin:80 | 管理后台 |
| 18081 | backend:18081 | 后端 API(webhook/直连调试) |

- store/admin/backend 容器**不再直接暴露宿主端口**,全部经 gateway;MySQL 仅绑 127.0.0.1:3306(SSH 隧道管理),Redis 仅 compose 内网。
- 建议服务器防火墙/安全组仅放行 5173、5174、18081 与 SSH。

## 八、已知限制(当前 IP:端口直连模式)

- Stripe 支付(`STRIPE_MODE=stub`)、Stripe webhook、Google/Apple OIDC 回调、Cloudflare R2 图片存储(`STORAGE_MODE=stub`)均需公网域名 + HTTPS 后切换 `*_MODE=real` 并补配密钥。
- RabbitMQ 事件(`MQ_MODE=stub`)、GA4(`GA4_MODE=stub`)、邮件(`MAIL_MODE=stub`)当前为内置模拟。
- 消费端 OIDC 登录在无域名环境下不可用(回调地址限制);管理端登录不受影响。
- **部署后首页短暂空白**:backend 刚就绪时若 store 的 RSC 请求先于 demo seed 完成打到 home 接口,空结果会进 JetCache(5 分钟 TTL)。等 5 分钟自动恢复,或 `docker compose --env-file .env.deploy restart backend` 立即清除;根治需应用层对空 home 结果不缓存。
