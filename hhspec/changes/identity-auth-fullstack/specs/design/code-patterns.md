# 编码模式库 — identity（code-patterns.md）

> **自动生成** — 由 l2_design_coordinator(Bootstrap 模式) 基于 L2 设计产出提取，需人工确认和补充。
> change: identity-auth-fullstack ｜ domain: identity ｜ generated_at: 2026-05-31

## 命名与结构
- CP-001 JSON/DB 字段 snake_case；Java 字段 camelCase；TS 前端边界统一转 camelCase（禁止前端散落 snake_case）。
- CP-002 主键字段统一 `id`（permission 例外业务主键 `key`；auth_config 单例 int=1）。
- CP-003 枚举落地 VARCHAR + CHECK + Java enum 双保险；取值见 shared-contracts enum_values。

## 数据访问
- CP-010 无物理 FOREIGN KEY，逻辑外键 + 应用层/事务维护引用完整性。
- CP-011 并发竞争表加 `version` 乐观锁（user/otp_code/user_session）。
- CP-012 行锁串行化关键校验：`SELECT ... FOR UPDATE`（OTP 校验防并发绕过 attempts）。
- CP-013 DTO 映射隐藏敏感字段（provider_uid/token_id/refresh_token_id/password_hash/code_hash）。
- CP-014 时间统一 DATETIME(3) UTC ↔ OffsetDateTime ↔ ISO8601，边界转换。

## 安全与鉴权
- CP-020 双 JWT 独立密钥（STORE_JWT_SECRET / ADMIN_JWT_SECRET），禁止复用。
- CP-021 跨端 token 误用 → 401 UNAUTHORIZED(40100)。
- CP-022 store access2h+refresh30d 可撤销；admin access8h 无 refresh。
- CP-023 密码仅存 BCrypt hash；OTP 仅存 code_hash；日志 [REDACTED]。
- CP-024 admin 端 RBAC：路由→permission_key 守卫，缺权限 403(40300)，超管 is_locked 短路全权限。

## 缓存
- CP-030 会话/凭证类 JetCache remote-only(Redis 单级 TTL30s 强一致全集群)；资料/配置类 two-level(Caffeine+Redis)。
- CP-031 写操作事务提交后失效对应缓存键（@CacheInvalidate），禁止脏读。

## 错误处理
- CP-040 统一错误信封 {code:int, message, details?}；code 高3位映射 HTTP 状态。
- CP-041 GlobalExceptionHandler 集中映射领域/基础设施异常→数字码；5xx 不暴露 SQL/堆栈。
- CP-042 store 按 Accept-Language(en/es/fr) 本地化；admin 固定中文；前端按 code 映射文案。

## 集成与降级
- CP-050 SMTP 发送 3 次指数退避重试，仍失败 50002 不阻塞主流程（沙箱切 stub）。
- CP-051 OIDC 超时5s/重试1次/熔断，失败 502/504 降级引导 OTP。

## 审计
- CP-060 后台关键写操作 AOP 切面写 operation_log(action + changes before/after)，只读不可删。

## 前端
- CP-070 portal-store: Next.js 15 App Router + zustand；portal-admin: Vue3 + Pinia。
- CP-071 危险操作（注销/删除/强制下线/超管）二次确认 + 前端约束预判（主邮箱/min_methods/超管）。
- CP-072 Headless-UI Vue 根组件传 class/style 必须配 `as` prop（否则级联崩溃 SPA，项目记忆）。

## 频控
- CP-080 OTP 频控全走 Redis 窗口键（resend/email-h/email-d/ip-h），非 DB 表，窗口到期自动清零。
