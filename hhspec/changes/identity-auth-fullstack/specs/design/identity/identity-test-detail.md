# identity 测试设计（L2）

> 角色: l2_test_designer ｜ change: identity-auth-fullstack ｜ domain: identity
> 多层测试骨架：单元 / 集成(模块内·模块间·端到端) / 契约 / 组件 / 异步集成 / API / 韧性 / **网络边界(NBT)**。
> AAA 伪代码骨架 + 测试数据工厂 + P0-P3 优先级。来源：api/data/error 三专家产出 + L0 验收(FUNC/EDGE) + shared-contracts。

## 1. 测试点清单（按 REQ 与优先级）
- P0（核心登录/安全/越权）：OTP 发送/校验/锁定、OIDC 归并、refresh、解绑约束、跨用户撤销、双 JWT 隔离、超管保护、RBAC 守卫、配置越界
- P1（运营/审计/降级）：管理员 CRUD、角色权限保存、强制下线、操作审计写入、OIDC/SMTP 降级、缓存写失效
- P2（边界/合规）：换主邮箱占用、注销复活拒绝、Apple relay 失效、数据保留清理、匿名化
- P3（展示/分页）：列表分页/筛选、空态、i18n 文案映射

## 2. 单元测试（UT，领域不变量）
- UT-01 OtpCode.verify：正确/错误(attempts+1)/达上限 locked/过期 expired【P0】
- UT-02 OtpCode.canResend：间隔判定【P1】
- UT-03 User.unbindIdentity：主邮箱拒绝(40304)/低于 min_methods(40305)/合法【P0】
- UT-04 MergeService：email_verified=true 同邮箱→归并；false→40902；provider_uid 命中→幂等【P0】
- UT-05 Role 超管 is_locked：改权限/删/降权拒绝【P0】
- UT-06 AuthConfig 区间校验：ttl/resend/attempts/min_methods/length 越界拒绝【P0】
- UT-07 AdminUser 删自己拒绝【P0】

## 3. 集成测试（IT，DB+事务+缓存）
- IT-01 verifyOtp 事务：行锁串行防并发绕过 attempts（并发两请求同码）【P0】
- IT-02 oidcCallback 归并单事务：identity 挂载 + operation_log(账户合并) 原子【P0】
- IT-03 重复 OIDC 回调幂等：唯一索引命中返回既有 user，不重复建号【P0】
- IT-04 changePrimary 事务：is_primary 迁移恒一个【P1】
- IT-05 updateRole 全量重写 role_permission 原子【P1】
- IT-06 forceLogout/禁用：session revoked + Redis 单级失效全集群即时生效（清键后请求即 401）【P0】
- IT-07 缓存写失效：FLOW-05/06/07/12/13 写后对应键失效，下次读新值【P1】
- IT-08 保留清理：otp 24h / session 30d / login 1y / 匿名化 30d 任务正确筛选【P2】
- IT-09 匿名化级联：user.anonymize 清 PII + identity 级联，operation_log 保留【P2】

## 4. 契约测试（CT，OpenAPI）
- CT-01 36 操作请求/响应 schema 校验对齐 identity-api.openapi.yml【P0】
- CT-02 错误响应 {code,message,details} 结构 + code↔HTTP 状态一致（25 码）【P0】

## 5. API 测试（端到端 HTTP）
- AT-01 store 登录全链路：sendOtp→verifyOtp→getProfile→refresh【P0】
- AT-02 admin 登录→adminMe→permission_keys→受限端点 RBAC【P0】
- AT-03 账户安全：bind/unbind/listSessions/revoke/changePrimary/delete【P1】
- AT-04 错误路径 25 个 MUST_TEST（见 error-detail 映射表）逐一断言 code/HTTP【P0】

## 6. 组件测试（前端，与 ui-test 互补，断言逻辑非视觉）
- FCT-01 LoginCard 两步切换 + OTP 自动跳格【P2】
- FCT-02 RolePanel 权限矩阵 group/项级复选 + is_locked 只读【P1】
- FCT-03 路由守卫：无权限路由重定向【P0】

## 7. 韧性测试（RT/CB，降级）
- RST-01 OIDC 超时→504 50401 引导 OTP【P1】
- RST-02 OIDC 不可达→502 50201；熔断快速失败【P1】
- RST-03 SMTP 失败→3 次重试→50002，OTP 不阻塞主流程【P1】
- RST-04 Redis 不可用→会话校验降级查 DB（[INFERRED] 待 L3 确认）【P2】

## 8. 网络边界测试（NBT，强制 — 前后端分离 5173/5174 ↔ backend）
> 触发条件满足：portal-store(5173) / portal-admin(5174) 独立端口 + backend API 端口分离。
- **NBT-01 CORS Preflight**：OPTIONS 预检对 /api/store/* 与 /api/admin/* 返回正确 Access-Control-Allow-Origin/Methods/Headers/Credentials【P0】
- **NBT-02 跨域实际请求**：从 origin http://localhost:5173 发 store 请求、http://localhost:5174 发 admin 请求，带 Authorization+Accept-Language 成功【P0】
- **NBT-03 非白名单来源拒绝**：生产白名单下，非白名单 origin 的预检/请求被拒绝（无 ACAO 头）【P0】
- NBT-04 跨端隔离网络层：5173 origin 带 store token 访问 /api/admin/* → 401（CORS 放行但鉴权 401，EDGE-024）【P0】
- NBT-05 凭证传递：credentials=true 时 cookie/Authorization 正确透传，预检 Allow-Credentials=true【P1】
> 测试环境与开发环境网络拓扑等价；若引入 Nginx 代理需补充跨域专项（当前直连，无代理）。

## 9. 测试数据工厂（FACTORY）
- F-User(active/disabled/deleted/anonymized 四态)
- F-UserIdentity(email/google/apple, is_primary, connected, relay_valid)
- F-OtpCode(pending/consumed/expired/locked, attempts)
- F-UserSession(active/revoked, refresh)
- F-AdminUser(super/normal) + F-Role(preset is_locked / custom) + F-Permission(22 key)
- F-AuthConfig(默认 + 越界变体)
- F-Tokens：store/admin 双密钥签发 helper

## 10. 优先级矩阵
| 优先级 | 测试数 | 覆盖 |
|--------|--------|------|
| P0 | UT-01/03/04/05/06/07, IT-01/02/03/06, CT-01/02, AT-01/02/04, FCT-03, NBT-01/02/03/04 | 核心登录/越权/隔离/网络边界 |
| P1 | UT-02, IT-04/05/07, AT-03, RST-01/02/03, FCT-02, NBT-05 | 运营/审计/降级/缓存 |
| P2 | IT-08/09, FCT-01, RST-04 | 合规/保留/前端逻辑 |
| P3 | 列表分页/i18n 映射断言 | 展示 |

## 11. 自检
- [x] 八层测试覆盖（UT/IT/CT/FCT/AT/RST/NBT + 数据工厂）
- [x] 网络边界 NBT-01~05 强制生成（CORS preflight/跨域/白名单拒绝/跨端隔离）
- [x] 25 MUST_TEST 错误路径纳入 AT-04
- [x] 每测试点可追溯 FUNC/EDGE/FLOW
- [x] P0-P3 优先级排序
