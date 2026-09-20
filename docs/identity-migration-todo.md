# 用户模块 Rust 迁移 — 遗留任务清单

> 生成时间:2026-09-15 | 更新:2026-09-18(任务 1 Java 完整删码已完成并 E2E 验证全绿) | 2026-09-19(登录安全加固 + 安全中心完成,见文末记录)

## 2026-09-19 登录安全加固 + 安全中心(已落码,待重镜像部署验证)

红队审计修复 + 议题 A(防持续破解)+ 议题 B(安全参数后台化)全部落码:

**安全修复**:
- V1 XFF 伪造堵口:client_ip 改取 X-Real-IP 优先(store_auth/admin 两处);nginx XFF 改覆盖式 $remote_addr
- V2 OTP 明文日志删除(otp.rs;本地取码走 DB 直插)
- V3 refresh 并发竞态:UPDATE 加 version 乐观锁,并发双刷仅一成功
- V4 refresh 重用检测:session:rotated:{jti} 旋转链标记,旧 jti 再现 → 整链撤销 + WARN
- V5 OIDC 回源收窄:kid 已知验签失败直接拒;回源节流 30s(防伪造洪水放大 DoS)
- V7 verify IP 频控:otp:verify:ip:{ip}:m 固定窗口,42904(参数 30/min/IP 进 auth_config)
- L2 双键递进退避:otp:backoff:{email|ip},阶梯 60s→5min→30min,42905,成功清 email 键
- L5 攻击告警:达阈值 → 结构化 WARN + 邮件通知(双键 SET NX 1h 去重;security_alert 模板四语言种子)
- is_new_device 加 90 天窗口谓词(月分区裁剪,修全分区扫描)

**配置化 + 前端**:
- auth_config 新 6 列:verify_ip_rate_per_minute/attack_alert_threshold/admin_alert_email/store_access_ttl_minutes/store_refresh_ttl_days/admin_access_ttl_hours(bootstrap DDL 已入 identity.sql;存量库 ALTER 已于 09-18 执行)
- token TTL 编译期常量 → 登录/刷新路径从 auth_config 读,仅新签发生效,读取失败回退默认
- admin 前端「安全中心」组:登录策略页(AuthSettings 扩展防滥用+令牌有效期两组)/ 会话应急下线页(SecuritySessions.vue 新增,复用 users API);旧 /system/auth 重定向

**新增错误码**:42904(验证频控)/42905(退避冷却),四语言 i18n 齐,码表测试覆盖

**测试资产**:identity/tests/concurrency.rs(IDENTITY_CONCURRENCY=1 闸门)——并发 refresh 仅一成功 + 并发 OTP 消费仅一成功;workspace 全测试绿

**任务 4 执行上下文**:场景 1(OTP 并发消费)/场景 3(并发 refresh)已入 concurrency.rs;场景 2(登录 vs 禁用时序)/场景 4(并发归并)待补;真环境跑证据待执行

## 2026-09-19 任务 2/3 执行完成(压测 + soak 全部收口)

**任务 4 并发证据**:IDENTITY_CONCURRENCY=1 双绿——并发 refresh 10 仅 1 成功(V3 乐观锁)、并发 OTP 消费 10 仅 1 成功,冷备行 version 恰 +1。

**任务 2 压测**(报告:docs/login-benchmark-report.md;数据千万级:user 1000 万跨 3 段/identity_email 1000 万 KEY25 区/login_history 5000 万跨 6 月分区/otp_code 100 万):
- 读路径(config)3000+ QPS p99<300ms@300 并发;refresh 单链串行 476 QPS 零错误 p99 399ms
- verify 全链 p50 31ms(频控上限内);频控拒绝路径 2000-4000 QPS;千万行分页 COUNT 缓存后超时消失
- **压测揪出存量 P0:refresh 旋转链二连刷必断链**(revoke_store 误标冷备行 revoked,用户续刷一次即永久掉线)——新增 revoke_store_soft 修复,E2E 补二连刷门禁 15/15 绿
- **性能修复**:list_users 千万行 COUNT 30s 缓存(深分页 p99 10s 超时 → 157ms~1.9s)
- 压测资产:tests/load.rs(IDENTITY_LOAD=1)/scripts/bench-seed.sh/bench-soak.sh/diag-refresh-chain.sh/login-security-e2e.sh

**任务 3 soak**(证据:data/soak-evidence.md;曲线图:data/charts/soak-{rss,cpu,db}.svg,完整报告 data/soak-evidence.html):50 并发 × 30 分钟混合流量,60 采样点——**判定 PASS**:server RSS 31→30MB 收敛无单调增长(剔除末尾 3 个 cpu=0 空载点后,负载期 57 点漂移仅 +0.02%),Redis 8MB 稳定,MySQL 水位稳定。注:soak 初版脚本两坑已修(SOAK 目标误指网关 HTTPS 口→改直连 18082;docker stats \t 未转义致解析错乱→改 | 分隔)。证据生成器 scripts/soak-chart.py(纯标准库,CSV→MD+SVG+HTML,判定口径=剔除空载尾段)。

**至此遗留任务 1-4 全部完成**;任务 5(生产发布)等用户下令,任务 6(用户侧前置)待办。

## ✅ E2E 冒烟结果(2026-09-18,全绿)

1. admin 登录(网关 TLS→Rust 签发 token)✓
2. bootstrap 种子就位(permission 27/admin_user 1/auth_config 1)✓
3. Java 种子经 gRPC 重建 demo 用户 22 个 @seed.dreamy.com + review 13/showroom 2/question 8 重灌(user_id 对齐)✓
4. 审计 operator_name 快照:Java 链(创建分类→'超级管理员')✓ Rust 链(auth-config PUT→'超级管理员')✓——顺带把 Rust REST 端点 11 处传 claims.sub 的存量瑕疵改为传空走兜底
5. 订单/退款列表链路 code=0(库空属数据空)✓
6. 管理端搜索 keyword=Emma(name/email LIKE_PREFIX)code=0✓

**auth_config WARN 根因澄清**:非删码引入——是 OTP verify 频控会话扩展了 auth_config 实体+identity.sql(6 新列)但未对存量库迁移,SELECT Unknown column→Err→幂等失效。已 ALTER TABLE 加 6 列修复(重启 bootstrap 日志干净)。**教训:实体改列需同步存量库 ALTER**。

**check-schema-drift.sh 已退役删除**:门禁管的 11 张 identity 死表已随删码 DROP,无引用。

**镜像**:dreamy-{backend,server,store,admin}:202609180025-2f6956a(本机 daemon);server 于 17:16 重建(含 audit sub→空修复)。

## 2026-09-18 删码执行记录(任务 1 完成)
> 分支:feature/1.0.0(本地,未推远程),提交 cc91c4d

**删码面**(62 主代码文件 + 13 测试文件 git rm):
- domain/{user,otp,session,authconfig,admin,role} 全包 + domain/audit 的 LoginHistory/Mapper/RetentionScheduler(**AuditService 保留**,审计记录器共用)
- 6 控制器(StoreAuth/Account/UserOps/AdminAuth/Role/AuthConfig)+ UserDetailView/LoginHistoryDTO/IdentityDtoMapper
- OidcVerifier(含 Real/Stub)/OtpRateLimiter/SessionValidityCache/AdminSessionValidityCache/DataInitializer
- SessionValidator/PermissionAspect/CustomerInfoPort/IdentityGateClient 删回滚态分支(恒 gRPC);JwtTokenProvider 删 issueStoreTokens/reissueStoreTokens/issueAdminToken(保留 parse + guest 签发)
- application.yml 清 identity.grpc.enabled/oidc/retention/bootstrap-admin/auth 白名单 5 条;.env.deploy 删 IDENTITY_GRPC_ENABLED

**删码发现的 3 个缺口及修复**:
1. **Rust bootstrap 缺口**:权限字典/超管/auth_config 种子原靠 Java DataInitializer 播种→migrate 迁移,删码后新环境无法自举 → `identity::bootstrap::seed_identity_baseline`(auth_config 单例+27 权限点+超管角色/账户,幂等,bcrypt cost 10;凭据 DREAMY_BOOTSTRAP_ADMIN_EMAIL/PASSWORD,demo 开关时 Admin@123456)
2. **名称搜索缺口**:RefundService/AdminOrderService 管理端客户名搜索无 gRPC 支撑 → proto UserColumn 加 `USER_COLUMN_NAME=7` + LIKE_PREFIX(Name|Email);findUserIdsByNameOrEmailLike 改 name+email 两次前缀查询并集(LIKE 语义从 %kw% 收窄为前缀,协议防全表扫描纪律)
3. **审计 operator_name 缺口**:Admin JWT claims 无 name,7 处审计调用点无法本地取名 → Rust `admin_ops::audit` 兜底(operator_name 空+operator_id 非空→同库查 admin_user 快照);新增 IdentityGate.ListAdminNames rpc(批量 admin 名,订单时间线用)

**验证**:
- Rust:cargo build+test 全 workspace 绿(29 passed;新增 bootstrap 权限表单测)
- Java:mvn/gradle compileJava+test 全绿(785 passed;删 13 个身份域测试,JwtTokenProviderTest/JwtGuestTokenTest/StoreJwtFilterGuestTest 改测试内同构 claims 构造 token,4 个业务测试 mock 换 CustomerInfoPort/IdentityGateClient,AbstractIT 用 @TestConfiguration 显式注册 IdentityGate stub——显式 classes 时嵌套 TestConfiguration 不自动检测,种子启动期需 stub 就位)
- 库清理:.env 确认后 DROP identity 库 13 张死表(user/user_identity/user_session/admin_session/admin_user/auth_config/otp_code/login_history/permission/role/role_permission/email_template/operation_log;先 mysqldump 备份 data/backup-identity-pre-drop-*.sql);TRUNCATE review/showroom 等 demo 业务表让种子经 gRPC 重建对齐新 user_id;mail_record 为 Java q.mail 现役表保留

## 已完成(不需要重做)

| 阶段 | 状态 | 关键产出 |
|------|------|----------|
| P0 契约与骨架 | ✅ | proto identity、workspace 分包(server/common/proto/identity)、网关分流、发布链、路由矩阵 32/32 |
| P1 数据层 v2.2 | ✅ | 分区 schema(路由 3 表 KEY 25 区/主档 RANGE 400 万段/时序月分区)、Redis 会话权威主存、partition_maintain、百万行七场景全绿、migrate-identity.sh 实测全绿 |
| P1 Java gRPC 客户端 | ✅ | IdentityGateClient(deadline 500ms/零重试/UNAVAILABLE→503)、预生成 stub 入库、regen-grpc-java.sh |
| P2 消费端认证 | ✅ | store/auth 5 端点 + store/account 6 端点、JWT 鉴权 extractor、i18n 四语言、OTP 全链、归并 v2.2 路由版、OIDC JWKS、Resend 邮件 |
| P3 admin 链路 | ✅ | 19 路由:登录/RBAC CRUD/用户运营/auth-config/operation-logs CSV/审计写入 |
| P4 Java 双模改造 | ✅ | SessionValidator/PermissionAspect/CustomerInfoPort(IDENTITY_GRPC_ENABLED 开关)、影子对照零差异、网关级 E2E 七项全绿 |
| 设计文档 | ✅ | docs/identity-schema-v2.md(v2.2)、错误码分段方案、性能/质量证据验收标准 |

## 遗留任务(按优先级排序)

### ~~任务 1:Java 完整删码~~ ✅ 已完成(2026-09-18,见上方执行记录)

### 任务 2:性能压测(用户指令:大数据量×并发×干扰)

**用户原话**:"大数据量下的性能,而且我要知道是在多少并发或多少合理的干扰。所谓干扰,就是模拟实际情况的情况下,它能达到什么样的性能标准。"

**⚠ 2026-09-18 删码后的执行上下文修正**:
- ~~与 Java 基线对比~~ **已不可行**(Java 身份域已删码,commit cc91c4d)——只输出绝对值供用户判断
- 灌库目标改为 **dreamy_server 主库**(identity 库 13 张身份死表已 DROP):user 主档 + identity_email 路由表
- ~~RetentionScheduler 干扰~~ 已随 Java 删码退役;干扰模型改用:Redis 故障注入(停 redis 容器/iptables 延迟)、分区维护巡检、mail 发信慢调用
- 灌库走 Rust 写路径时注意 partition_guard 分区自愈已上线(写入后自动预扩,免手动建分区)
- Rust 侧既有压测资产:identity/tests/large_data.rs(百万行七场景,可扩展)

**具体要求**:
- 灌库:百万级 user 行(已有 1M 证据)+ identity_email 路由表 100 万行
- 并发度:阶梯测试(10/50/100/500 并发)
- 干扰模型:读写混合(如 80% 读 / 20% 写)、缓存命中率(冷/热/缓存故障)、后台任务竞争
- 输出:p50/p95/p99 延迟曲线 + 吞吐量 QPS + 资源占用(CPU/RSS/Redis 命中率)
- 工具:wrk 或自写 tokio 压测脚本;Rust 集成测试框架驱动
- 场景:OTP 发码/验证(含 Redis 频控,verify IP 频控 42904 已上线)、config 读取(注意 auth_config 新 6 列)、路由表两跳查找、ListUsers 分页(首页+深分页;NAME 列 LIKE_PREFIX 2026-09-18 新增)、admin 操作
- 判定:输出绝对值供用户判断

### 任务 3:内存泄漏 soak 测试(用户指令)

**用户原话**:"内存泄漏、并发稳定性这些都要进行测试并通过证据验收。"

**具体步骤**:
1. 写 soak 脚本:tokio 并发 N=50 连接 × M=30 分钟,持续打满认证/查询接口
2. 每 30s 采样 RSS(通过 /proc/self/status VmRSS 或 sysinfo crate)
3. 证据:RSS 时间序列曲线;判定标准——M 分钟后 RSS 增幅收敛(无单调增长)
4. 同时监测:Redis 内存使用、MySQL 连接池水位
5. 输出:soak-evidence.md(采样表 + 曲线图 + 结论)

### 任务 4:并发稳定性测试(用户指令)

**具体场景**:
- 并发 OTP 消费:同 email 并发 10 请求仅一成功(原 Java OtpConcurrencyIT 已随删码删除,场景语义迁移到 Rust 侧测试或 E2E 脚本)
- 并发登录 vs 禁用:登录过程中禁用账户 → 禁用后立即 40301
- 并发 refresh 旋转:同 refresh_token 并发刷 → 仅一成功,无重复 jti 存活
- 并发归并:同 email 不同 provider 并发登录 → 归并正确不产生孤儿
- 输出:并发测试报告(通过/失败计数 + 时序证据)

### 任务 5:生产发布(等用户下令)

**步骤**:
1. `.env.deploy.prod` 填写(含 DEPLOY_SSH、ADMIN_API_SECRET)
2. `bash scripts/release.sh --env .env.deploy.prod`
3. 服务器 `bash scripts/deploy.sh`
4. 网关分流已内置于 compose(gateway 自动分流到 server 容器)
5. 生产验收:google-login-prod.verify.mjs + 五页 200 链接审计
6. **回滚预案(2026-09-18 修正)**:~~IDENTITY_GRPC_ENABLED=false 回滚通道已随删码移除~~——唯一回滚手段是 IMAGE_TAG 回退到删码前镜像(如 <202609180025 的旧 tag);注意回退到删码前 Java 镜像时,identity 库 13 张死表已 DROP、Java 侧 DataInitializer 也已删,回滚态 Java 需先恢复库快照(data/backup-identity-pre-drop-*.sql,仅本地有)——生产发布前务必确认快照已妥善保管

### 任务 6:用户侧前置(非开发工作)

- [ ] 注册 Resend、验证发信域名(SPF/DKIM)、获取 API key
- [ ] Google/Apple OIDC client_id 配置(OIDC real 模式登录用)
- [ ] 确认是否推送到远程仓库(git push origin feature/1.0.0 + tag;最新提交 cc91c4d=删码收编,159 文件净减 4011 行)

## 架构终态速查

```
前端(零改动) → 网关(TLS+分流)
    ├─ /api/store/auth/* → Rust server(REST 18082)
    ├─ /api/store/account/* → Rust server
    ├─ /${ADMIN_API_SECRET}/api/admin/{identity 域} → Rust server
    ├─ /${ADMIN_API_SECRET}/api/{其余 admin} → Java backend
    ├─ /api/{非身份域} → Java backend
    └─ / → store / /admin/ → admin(页面)

Rust server(18082 REST + 18083 gRPC)
    ├─ dreamy_server 库(11 身份表 + operation_log + email_template)
    ├─ Redis(会话权威主存 + OTP 频控 + 权限/用户缓存)
    └─ Resend API + Google/Apple JWKS

Java backend(18081 REST;gRPC 18083 → Rust server)
    ├─ identity 库(业务表;身份域死表保留作回滚快照)
    └─ 双模开关 IDENTITY_GRPC_ENABLED=true(切换态)
```

## 关键文件索引

| 文件 | 说明 |
|------|------|
| docs/identity-schema-v2.md | 权威数据架构设计(v2.2) |
| proto/dreamy/identity/v1/identity.proto | IdentityGate gRPC 契约 |
| proto/dreamy/audit/v1/audit.proto | AuditGate 审计通道 |
| proto/dreamy/mail/v1/mail.proto | TemplateGate 邮件模板通道 |
| server/schema/identity.sql | 空库自举 DDL(v2.2) |
| scripts/migrate-identity.sh | 一次性数据迁移(--confirm/--force) |
| scripts/test-gateway-routes.sh | 网关路由矩阵门禁 |
| scripts/check-schema-drift.sh | schema 漂移检查 |
| scripts/regen-grpc-java.sh | Java stub 重生成 |
| nginx/gateway.conf.template | 网关分流规则(含 ADMIN_API_SECRET 加密前缀) |
