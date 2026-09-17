# 用户模块 Rust 迁移 — 遗留任务清单

> 生成时间:2026-09-15
> 分支:feature/1.0.0(本地,未推远程)
> 上次提交:20095b3(基线) + 2026-09-15 新提交(含 admin API 加密前缀/审计邮件 gRPC 通道/CSPRNG 替换等,详见 git log)

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

### 任务 1:Java 完整删码(切换后清理)

**状态**:之前尝试完整删码后发现牵连 6 个 config 初始化器 + 7 个审计记录器 + 邮件渲染器(共 21+ 文件直接引用 identity 域实体),当时选择回退保留双模。**架构上已达成切换目标**(identity 流量全部到 Rust,Java 双模走 gRPC),Java 代码保留作回滚保障。

**具体步骤**:
1. 逐个改造 6 个 config 初始化器(DataInitializer/AnalyticsSeedInitializer/CatalogSeedInitializer/MarketingSeedInitializer/ReviewSeedInitializer/TradingSeedInitializer/ShowroomSeedInitializer/ReviewSeedInitializer)——删除 identity 种子部分(Rust 已覆盖),保留商品/内容种子;种子中 UserMapper 写 User 的改走 CustomerInfoPort.ensureDemoUser(gRPC)
2. 7 个审计记录器(OrderEventRecorder/CatalogAuditRecorder/MarketingAuditRecorder/ReviewAuditRecorder/ShippingAuditRecorder/TradingAuditRecorder)——AdminUserMapper 查 operator_name 改为从 gRPC AuditGate 或 IdentityGate 获取(审计通道已在 proto 中定义:dreamy.audit.v1)
3. MailPortConfig/EmailTemplateRenderer/MailTemplateSeedInitializer——UserMapper 改走 CustomerInfoPort;EmailTemplate 实体收编到 dreamy_server(已迁移,见 proto mail.v1)
4. CustomerInfoPort 删除回滚态分支(仅保留 gRPC 路径)
5. SessionValidator/PermissionAspect 删除回滚态分支(纯 gRPC)
6. JwtTokenProvider 裁剪——删 issueStoreTokens/issueAdminToken(仅保留 parse + issueShowroomGuestToken)
7. 删 domain/{user,otp,session,authconfig,admin,role}(44 文件)、6 控制器(StoreAuth/Account/UserOps/AdminAuth/Role/AuthConfig)、LoginHistory+RetentionScheduler、OidcVerifier/OtpRateLimiter/SessionValidityCache/AdminSessionValidityCache、IdentityDtoMapper
8. 删关联测试(~21 文件)
9. 编译+剩余测试全绿验证

**注意**:此步骤应在生产切换稳定运行一段时间后执行,作为独立变更提交。

### 任务 2:性能压测(用户指令:大数据量×并发×干扰)

**用户原话**:"大数据量下的性能,而且我要知道是在多少并发或多少合理的干扰。所谓干扰,就是模拟实际情况的情况下,它能达到什么样的性能标准。"

**具体要求**:
- 灌库:百万级 user 行(已有 1M 证据)+ identity_email 路由表 100 万行
- 并发度:阶梯测试(10/50/100/500 并发)
- 干扰模型:模拟真实流量——读写混合(如 80% 读 / 20% 写)、缓存命中率(冷/热/缓存故障)、后台任务竞争(RetentionScheduler 同时运行)
- 输出:p50/p95/p99 延迟曲线 + 吞吐量 QPS + 资源占用(CPU/RSS/Redis 命中率)
- 工具:wrk 或自写 tokio 压测脚本;Rust 集成测试框架驱动
- 场景:OTP 发码/验证(含 Redis 频控)、config 读取、路由表两跳查找、ListUsers 分页(首页+深分页)、admin 操作
- 判定:与 Java 基线对比(需 Java 同条件跑一组);或至少输出绝对值供用户判断

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
- 并发 OTP 消费:同 email 并发 10 请求仅一成功(对齐 OtpConcurrencyIT)
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
6. 回滚预案:IMAGE_TAG 回退 + IDENTITY_GRPC_ENABLED=false

### 任务 6:用户侧前置(非开发工作)

- [ ] 注册 Resend、验证发信域名(SPF/DKIM)、获取 API key
- [ ] Google/Apple OIDC client_id 配置(OIDC real 模式登录用)
- [ ] 确认是否推送到远程仓库(git push origin feature/1.0.0 + tag)

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
