# 关键决策：identity-auth-fullstack

> 范围：认证 + 用户域垂直切片（消费端登录 / 后台用户管理 / 多渠道认证），前后端 + JetCache 缓存。
> 不含：两端 CMS、商品/订单/购物车/营销/数据等其他域。

## 决策 1：工程拓扑——两前端独立工程 + 单后端多模块

- **选择**：三个独立可编译工程。
  - `frontend/portal-store`（Next.js 15 App Router + TS + Tailwind），独立 build，端口 5173。
  - `frontend/portal-admin`（Vue3 + Vite + TS + Tailwind + Headless UI），独立 build，端口 5174。
  - `backend`（Spring Boot 单工程多模块：common / store / admin），统一打包。
- **理由**：消费端需 SEO（RSC/SSG），后台是富交互内部系统；原型已按此双栈成型，复用成本最低。后端合一便于共享领域模型（User/Identity/Session）与归并逻辑。
- **API 前缀**：消费端 `/api/store/*`，后台 `/api/admin/*`，按角色与 JWT 类型隔离路由。
- **备选**：前端 monorepo 统一框架——被否，需重写原型，违背"沿用原型双栈"。

## 决策 2：两端独立 JWT Token 类型

- **选择**：store 与 admin 使用**完全独立**的 JWT（独立签名密钥、独立 claims、独立过期策略、互不通用）。
  - store JWT：subject=user_id，claims 含 tier/status，jti 关联 UserSession，可被撤销。
  - admin JWT：subject=admin_id，claims 含 role + 权限 key 集合，jti 关联 AdminSession。
- **理由**：消费态与管理态安全隔离（feature-map 明确要求 session 不复用）；管理态泄露不得影响消费端，反之亦然。
- **校验**：网关/过滤器按路由前缀选择对应密钥与解析器；store token 命中 `/api/admin/*` 直接 401。

## 决策 3：身份模型——canonical user + 多 identity

- **选择**：`User`（自然人）1:N `UserIdentity`（登录凭证）。识别同一人靠 `(provider, provider_uid)`，**不靠 email**。
  - email 凭证 provider_uid = 已验证邮箱；Google/Apple = OIDC `sub`。
  - Apple Hide My Email：以 `sub` 为稳定主键，首次授权即落库，relay 邮箱存 `relay_email`。
- **理由**：迭代3 决策，行业主流，避免改邮箱/隐藏邮箱导致账户分裂或劫持。

## 决策 4：账户归并——系统自动，已验证邮箱一致才并

- **选择**：登录/注册时，凡 `email_verified=true` 且邮箱与既有账户一致，系统**自动**将新 identity 挂到既有 user；邮箱未验证或冲突时**不静默合并**，提示用户用原方式登录后再绑定。无后台人工合并入口、无开关。
- **理由**：迭代4 决策（已下线人工合并页面）；防账户劫持。
- **Steelman（人工合并的最强理由）**：人工可处理复杂冲突。**但**自动归并 + 冲突即拒的策略在 passwordless 场景下既安全又零运营成本，胜出。

## 决策 5：多渠道认证后端做真实实现

- **选择**：OTP 走真实邮件发送（SMTP，可配置 host/port/账号）；Google/Apple 走真实 OIDC `sub` 校验与回调。OAuth 凭据从配置/密钥管理读取。
- **理由**：用户明确要求后端真实落地，而非 mock。
- **约束**：沙箱无网络时，邮件与 OIDC 通过可配置开关切到本地 stub，便于联调；正式环境走真实通道。

## 决策 6：消费端只读接口统一 JetCache 两级缓存

- **选择**：消费端（`/api/store/*`）所有读接口加 JetCache，本地 Caffeine + 远程 Redis 两级。
  - 缓存对象：用户资料、登录方式列表、活跃会话列表、AuthConfig（消费端登录页读取）。
  - 失效：写操作（绑定/解绑、会话撤销、账户禁用、AuthConfig 保存）即时 `@CacheInvalidate`/`@CacheUpdate`；账户被禁用时清除该用户全部缓存并撤销会话。
- **理由**：用户明确要求消费端接口增加缓存；AuthConfig、用户资料读多写少，缓存收益高。
- **约束**：会话/凭证类缓存 TTL 短（秒~分钟级）并与撤销强一致，避免已下线会话因缓存仍可用。

## 决策 7：后台权限——菜单级二元 RBAC

- **选择**：Role × Permission(菜单 key) 二元矩阵；预设角色超级管理员 is_locked、拥有全部权限、不可删/不可降权/不可禁用；路由守卫 + 菜单动态渲染。
- **理由**：迭代2 决策，权限粒度到菜单项；无数据级/操作级权限。

## 限界上下文与领域

- **domain_code**：identity（单一限界上下文，跨 portal-store 与 portal-admin 两个表现层）。
- **错误处理**：统一错误码（如 INVALID_EMAIL/OTP_EXPIRED/RESEND_TOO_SOON/ACCOUNT_DISABLED/EMAIL_EXISTS/ROLE_IN_USE/FORBIDDEN），HTTP 状态语义化。

## 决策 8：JWT 过期与续期策略（细化）

- **选择**：store access **2h** + refresh **30d** 滑动续期，refresh 绑 UserSession 可撤销；admin access **8h** 无 refresh。
- **理由**：passwordless 下让消费者极少重新验证码（refresh 滑动），同时保持可撤销与短 access 安全性；后台内部系统用短 access 更安全。
- **Steelman（单长 token 的最强理由）**：实现简单、无 refresh 状态。**但** refresh 方案的可撤销性与体验在 passwordless 场景下更重要，胜出。

## 决策 9：OTP 频控阈值（细化）

- **选择**：重发 ≥ resend_seconds(30s)；单 email 5 次/小时 & 5 次/天；单 IP 20 次/小时；单码失败达 max_attempts(5) 锁定；超限 429。计数走 Redis 窗口，非 DB 表。
- **理由**：平衡防暴力与正常用户体验；阈值进 AuthConfig 可调。

## 决策 10：会话安全——不限会话数 + 新设备通知（细化）

- **选择**：不设会话数硬上限；新设备/新位置首次登录发提醒邮件（含一键登出）。`login_history.is_new_device/notified` 落库。
- **理由**：跨境用户多设备常态，硬上限易误伤；通知邮件兼顾安全感知与盗号自助处置。
- **Steelman（会话上限的最强理由）**：限制攻击者并发。**但**通知 + 可撤销会话已覆盖该风险，且不误伤多设备用户，胜出。

## 决策 11：JetCache 缓存——会话类仅远程 Redis + 资料类两级（细化）

- **选择**：
  - **会话/凭证类**（活跃会话列表、token 有效性）：**只用远程 Redis 单级，不挂本地 Caffeine**，TTL 30s + 写即失效。天然无多实例本地残留，强制下线/禁用立即全集群生效。
  - **资料/配置类**（用户资料、登录方式列表、AuthConfig）：本地 Caffeine + 远程 Redis 两级，TTL 分级 AuthConfig 10min / 资料·登录方式 5min，写即 `@CacheInvalidate`。
- **理由**：会话类对一致性敏感，单级 Redis 避免本地缓存导致已下线会话仍可用；资料类读多写少，两级提升吞吐。
- **Steelman（两级+pub/sub 广播的最强理由）**：会话读也能命中本地、最快。**但**广播失效有实现复杂度与时序风险，而会话查询走 Redis 已足够快，单级方案更简单且零残留，胜出。
- **key 规范**：`store:authconfig`、`store:user:{userId}`、`store:identities:{userId}`（两级）；`store:sessions:{userId}`、`store:session:valid:{tokenId}`（仅 Redis）。

## 决策 12：认证边缘场景纳入范围（细化）

- **选择**：本次纳入换主邮箱、账户注销/删除（软删除）、未验证邮箱冲突完整流、Apple relay 失效处理（见 REQ-IDENTITY-006）。
- **理由**：均为 passwordless + 多渠道身份模型下的真实边界，避免上线后账户状态不一致或锁死。
- **约束**：注销为软删除（status=deleted + deleted_at），注销后再次登录不自动复活，按冲突流处理；Apple relay 失效不锁死账户（sub 仍为稳定主键）。**注销保留策略见决策 16（合规修正：宽限后必须匿名化，非永久软删）。**

## 决策 13：统一错误码体系（细化）

- **选择**：所有错误返回 `{code, message, details?}`，`code` 为**数字码**（集中码表，高 3 位对应 HTTP 状态）；HTTP 语义化（400/401/403/404/409/410/429/500）。后端返回 code + 默认文案，前端按 code 本地化。
- **理由**：数字码便于集中码表管理与跨语言映射；用户明确选择数字码方案。

## 决策 14：国际化范围（细化）

- **选择**：消费端 UI、错误文案、邮件支持 **EN / ES / FR** 三语（按 Accept-Language/用户偏好，缺省 EN，ES/FR 可先占位）；后台仅中文。
- **理由**：对齐 David's Bridal 双语 + 加拿大法语市场；面向美国+加拿大+全球。用户选定 EN+ES+FR。
- **Steelman（EN 单语的最强理由）**：最简、竞品 birdygrey/kissprom 均英语。**但**目标市场含美国西语裔与加拿大法语区，三语结构先就位避免后期返工，胜出。

## 决策 15：邮件与 OIDC 集成（细化）

- **选择**：邮件通过 **SMTP** 发送（通用，不绑定云厂商），模板可配置支持 EN/ES/FR；OIDC 凭据从配置/密钥管理读取；集成失败降级（邮件重试+告警、OIDC 超时引导改用 OTP）。沙箱可切 stub。
- **理由**：SMTP 通用可移植，部署不锁定特定云邮件服务；用户明确选择仅 SMTP。

## 决策 16：数据保留与清理——GDPR 合规（细化，含重要修正）

- **选择**（每日定时任务）：
  - OTP 已用/过期 24h 内清；revoked 会话保留 30 天；login_history 保留 1 年；OperationLog 保留 1–3 年。
  - **账户注销：30 天软删除宽限期 → 到期后不可逆匿名化 PII**（`anonymized=true` + `anonymized_at`），**不得永久保留软删除标记**。
- **理由（合规研究结论）**：GDPR Art.17「被遗忘权」要求注销后真正删除或不可逆匿名化 PII，永久软删除标记不合规；安全日志可依正当利益（Art.6(1)(f)）超出删除请求保留但须时间受限；CCPA/CPRA、PIPEDA、澳洲隐私法方向一致（仅保留必要时长、安全销毁）。
- **修正**：本决策**覆盖决策 12 中"软删除永久保留"的表述**——软删除仅为 30 天宽限，宽限后必须匿名化。
- **约束**：L1 须落地匿名化字段范围与定时任务调度；保留期限形成书面可审计策略。

## 后端关键决策

> 来源：Phase 2.3.1 后端实现深度探索（基于原型业务模式推断 + 用户决策）
> 下游消费：L1 architect（error-strategy.md / data-flow.md）、L3 implementer

### BE-DIM-4 状态机/并发/事务
- **决策**：OTP 校验、账户归并、强制下线为关键事务。OTP verify 需对同一 email 串行/乐观锁防并发绕过 attempts；归并在单事务内迁移 identity 并写日志。
- **触发信号**：state-machine（otp_code/user_session）+ account_auto_merge flow
- **理由**：防并发暴力破解与归并竞态。
- **约束**：L1 须定义 OTP 校验的并发控制（行锁或 Redis 原子计数）与归并事务边界。

### BE-DIM-5 外部集成与第三方依赖
- **决策**：邮件服务（SMTP/阿里云）+ Google OIDC + Apple OIDC（含 Hide My Email relay）。
- **触发信号**：store_passwordless_otp_login / store_oidc_login flow
- **理由**：多渠道真实认证。
- **约束**：L1 须定义集成失败降级（邮件发送失败提示重试、OIDC 超时回退）与 stub 开关。

### BE-DIM-6 安全与权限
- **决策**：两端独立 JWT；admin 菜单级 RBAC + 路由守卫；OTP 仅存哈希；防账户劫持归并策略；会话可撤销。
- **触发信号**：admin_manage_roles flow + 身份模型
- **理由**：消费态/管理态隔离与数据安全。
- **约束**：L1 OpenAPI 须在 admin 端点注入权限校验；store token 不得访问 admin 路由。

### BE-DIM-7 可观测性与运维
- **决策**：后台所有关键操作写 OperationLog（含变更前后对比），只读不可删；登录成功/失败写 LoginHistory。
- **触发信号**：operation_log 实体 + admin flows
- **理由**：审计合规。
- **约束**：L1 须定义日志切面（AOP）统一记录，避免漏记。

### BE-DIM-8 性能与可扩展性
- **决策**：消费端读接口 JetCache 两级缓存（见决策6）。
- **触发信号**：用户明确缓存要求
- **理由**：读多写少，降低 DB 压力。
- **约束**：L1 须定义缓存 key 规范、TTL 分级与写失效策略。
