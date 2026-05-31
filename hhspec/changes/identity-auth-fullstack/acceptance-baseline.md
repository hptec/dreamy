# 验收基准：identity-auth-fullstack

生成时间：2026-05-31T08:55:00Z

## FUNC — 功能验收

| 编号 | 场景标题 | 页面/端 | 来源 |
|------|---------|---------|------|
| FUNC-001 | 发送邮箱验证码 | portal-store/account/login | REQ-IDENTITY-001 |
| FUNC-002 | 校验验证码登录成功 | portal-store/account/login | REQ-IDENTITY-001 |
| FUNC-003 | 重发验证码（间隔约束） | portal-store/account/login | REQ-IDENTITY-001 |
| FUNC-004 | Google OIDC 登录/归并/新建 | portal-store/account/login | REQ-IDENTITY-001 |
| FUNC-005 | Apple OIDC 登录（Hide My Email） | portal-store/account/login | REQ-IDENTITY-001 |
| FUNC-006 | 登录方式开关生效 | portal-store / AuthSettings | REQ-IDENTITY-001 |
| FUNC-007 | 查看登录方式 | portal-store/account/security | REQ-IDENTITY-002 |
| FUNC-008 | 绑定登录方式 | portal-store/account/security | REQ-IDENTITY-002 |
| FUNC-009 | 解绑登录方式 | portal-store/account/security | REQ-IDENTITY-002 |
| FUNC-010 | 查看活跃会话 | portal-store/account/security | REQ-IDENTITY-002 |
| FUNC-011 | 登出单个设备 | portal-store/account/security | REQ-IDENTITY-002 |
| FUNC-012 | 登出其他全部设备 | portal-store/account/security | REQ-IDENTITY-002 |
| FUNC-013 | 账户设置（无修改密码） | portal-store/account/settings | REQ-IDENTITY-002 |
| FUNC-014 | 管理员登录 | portal-admin/Login | REQ-IDENTITY-003 |
| FUNC-015 | 管理员 CRUD | portal-admin/AdminList | REQ-IDENTITY-003 |
| FUNC-016 | 禁用/启用管理员 | portal-admin/AdminList | REQ-IDENTITY-003 |
| FUNC-017 | 重置密码 | portal-admin/AdminList | REQ-IDENTITY-003 |
| FUNC-018 | 角色管理 | portal-admin/RoleManagement | REQ-IDENTITY-003 |
| FUNC-019 | 权限矩阵保存生效 | portal-admin/RoleManagement | REQ-IDENTITY-003 |
| FUNC-020 | 删除角色约束 | portal-admin/RoleManagement | REQ-IDENTITY-003 |
| FUNC-021 | 权限生效（守卫+菜单渲染） | portal-admin | REQ-IDENTITY-003 |
| FUNC-022 | 用户身份运营（登录记录/强制下线/禁用） | portal-admin/CustomerDetail | REQ-IDENTITY-004 |
| FUNC-023 | 登录与认证配置保存 | portal-admin/AuthSettings | REQ-IDENTITY-004 |
| FUNC-024 | 操作日志筛选/详情/导出 | portal-admin/OperationLogs | REQ-IDENTITY-004 |
| FUNC-025 | 系统自动账户归并 | 后端（系统） | REQ-IDENTITY-001/004 |
| FUNC-026 | 更换主邮箱 | portal-store/account/security | REQ-IDENTITY-006 |
| FUNC-027 | 账户注销/删除（软删除） | portal-store/account/settings | REQ-IDENTITY-006 |
| FUNC-028 | 未验证邮箱冲突完整流 | portal-store/account/login | REQ-IDENTITY-006 |
| FUNC-029 | Apple relay 失效处理 | 后端（系统） | REQ-IDENTITY-006 |
| FUNC-030 | access 过期 refresh 续期 | portal-store | REQ-IDENTITY-005 |
| FUNC-031 | 新设备登录通知邮件 | 后端（系统） | REQ-IDENTITY-005 |
| FUNC-032 | 数据保留清理（OTP/会话/登录记录） | 后端（定时） | REQ-IDENTITY-008 |
| FUNC-033 | 注销超宽限期匿名化 PII | 后端（定时） | REQ-IDENTITY-008 |
| FUNC-034 | 邮件发送失败重试（SMTP） | 后端（系统） | REQ-IDENTITY-008 |

## EDGE — 边界/异常验收

| 编号 | 场景 | 来源 |
|------|------|------|
| EDGE-001 | 邮箱格式非法 → 422 INVALID_EMAIL | REQ-IDENTITY-001 |
| EDGE-002 | 验证码错误未达上限 → attempts+1 | REQ-IDENTITY-001 |
| EDGE-003 | 验证码错误达上限 → locked | REQ-IDENTITY-001 |
| EDGE-004 | 验证码过期 → 410 OTP_EXPIRED | REQ-IDENTITY-001 |
| EDGE-005 | 重发间隔未到 → 429 RESEND_TOO_SOON | REQ-IDENTITY-001 |
| EDGE-006 | 被禁用账户登录 → 403 ACCOUNT_DISABLED | REQ-IDENTITY-001 |
| EDGE-007 | 解绑主邮箱被拒 | REQ-IDENTITY-002 |
| EDGE-008 | 解绑低于 min_methods 被拒 | REQ-IDENTITY-002 |
| EDGE-009 | 跨用户撤销会话 → 403 | REQ-IDENTITY-002 |
| EDGE-010 | 设置页无修改密码入口 | REQ-IDENTITY-002 |
| EDGE-011 | 管理员禁用账户登录 → 403 ADMIN_DISABLED | REQ-IDENTITY-003 |
| EDGE-012 | 管理员邮箱重复 → 409 EMAIL_EXISTS | REQ-IDENTITY-003 |
| EDGE-013 | 删除自己被拒 | REQ-IDENTITY-003 |
| EDGE-014 | 禁用超级管理员被拒 | REQ-IDENTITY-003 |
| EDGE-015 | 删除有成员角色 → 409 ROLE_IN_USE | REQ-IDENTITY-003 |
| EDGE-016 | 无权限路由守卫拦截 | REQ-IDENTITY-003 |
| EDGE-017 | 邮箱未验证/冲突不静默合并，提示绑定 | REQ-IDENTITY-001/004 |
| EDGE-018 | 操作日志不可删（只读） | REQ-IDENTITY-004 |
| EDGE-019 | OTP 数值越界保存被拒 | REQ-IDENTITY-004 |
| EDGE-020 | 新主邮箱被他人占用 → 409 EMAIL_EXISTS | REQ-IDENTITY-006 |
| EDGE-021 | 注销账户再次登录不复活 | REQ-IDENTITY-006 |
| EDGE-022 | OTP 发码超频 → 429 RATE_LIMITED | REQ-IDENTITY-005 |
| EDGE-023 | 强制下线全集群即时生效（会话仅 Redis 单级） | REQ-IDENTITY-005 |
| EDGE-024 | 错误响应结构统一（数字码）+ HTTP 语义化 | REQ-IDENTITY-007 |
| EDGE-025 | 消费端按 Accept-Language 返回 ES/FR 错误文案 | REQ-IDENTITY-007 |
| EDGE-026 | 注销不删除审计日志（正当利益） | REQ-IDENTITY-008 |

> 完整机器可读验收见 `acceptance.yml`（275 条，含 7 类边界 242 条）。

## UI — UI 验收检查点

仅 `linked_prototype_snapshots` 非空时生成。

### 页面清单（11 页）

| page_id | 原型文件 | 路由 | 端 |
|---------|---------|------|----|
| portal-store/account/login | app/account/login/page.tsx | /account/login | Next.js |
| portal-store/account | app/account/page.tsx | /account | Next.js |
| portal-store/account/settings | app/account/settings/page.tsx | /account/settings | Next.js |
| portal-store/account/security | app/account/security/page.tsx | /account/security | Next.js |
| portal-admin/Login | portal-admin/src/views/Login.vue | /login | Vue3 |
| portal-admin/Customers | portal-admin/src/views/Customers.vue | /customers | Vue3 |
| portal-admin/CustomerDetail | portal-admin/src/views/CustomerDetail.vue | /customers/:id | Vue3 |
| portal-admin/AdminList | portal-admin/src/views/AdminList.vue | /system/admins | Vue3 |
| portal-admin/RoleManagement | portal-admin/src/views/RoleManagement.vue | /system/roles | Vue3 |
| portal-admin/AuthSettings | portal-admin/src/views/AuthSettings.vue | /system/auth | Vue3 |
| portal-admin/OperationLogs | portal-admin/src/views/OperationLogs.vue | /system/logs | Vue3 |

### 核心约束
→ 见 ui-verification-checklist.md 逐页检查项；视觉/交互对照各原型页面。

### 详细字段/交互规格
> 详细页面交互规格将在 L2 前端详设阶段产出，届时更新此引用。

## PERF — 性能基线
- 消费端只读接口经 JetCache 缓存：**会话/凭证类仅远程 Redis 单级**（TTL 30s，无本地残留）；**资料/配置类两级**（Caffeine+Redis，AuthConfig 10min / 资料 5min）；写即失效。

## SEC — 安全要求
- 两端独立 JWT，互不复用；store token 访问 `/api/admin/*` 必返 401。
- OTP 仅存哈希；防暴力发码/校验频控。
- 后台菜单级 RBAC + 路由守卫；超级管理员不可删/降权/禁用。
- 归并仅在 email_verified 且邮箱一致时自动进行，冲突即拒，防账户劫持。
- 后台关键操作全量审计（OperationLog 只读不可删）。
- store JWT：access 2h + refresh 30d 滑动续期（refresh 可撤销）；admin access 8h 无 refresh。
- 会话/凭证类缓存仅 Redis 单级，强制下线/禁用立即全集群生效，无本地残留。
- 数据保留合规（GDPR/CCPA/PIPEDA）：OTP 24h 清、revoked 会话 30 天、登录记录 1 年、审计日志 1–3 年；注销 30 天宽限后**不可逆匿名化 PII**（非永久软删）。
- 错误码统一 `{code,message,details}`（**数字码** + HTTP 语义化）；消费端 UI/错误/邮件 **EN/ES/FR** 三语；后台中文；邮件经 SMTP 发送。
- OTP 频控：单 email 5次/时 & 5次/天、单 IP 20次/时、单码失败达上限锁定，超限 429。
- 新设备登录通知邮件（含一键登出）；账户注销为软删除并撤销全部会话。
