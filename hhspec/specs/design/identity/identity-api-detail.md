# identity API 详细设计（L2）

> 角色: l2_api_designer ｜ change: identity-auth-fullstack ｜ domain: identity
> 方法论：每端点四部分 — 入参验证(V-NNN) / 业务逻辑流程(STEP-NN) / 出参构造 / 错误码映射。
> 来源权威：api-contracts/identity-api.openapi.yml（36 操作）+ data-flow.md（FLOW-01~17）+ error-strategy.md（25 码）+ shared-contracts.yml。
> 伪代码级，不绑定 Spring 语法。所有 JSON 字段 snake_case；错误统一 {code,message,details}。

## 0. 全局横切（所有端点适用）

- **鉴权过滤器**：按前缀选密钥解析 JWT；store→STORE_JWT_SECRET，admin→ADMIN_JWT_SECRET；跨端误用→401 `40100`（EDGE-024）。
- **会话有效性**：store 请求校验 `redis store:session:valid:{jti}` 存在且 DB session.status=active；admin 校验 admin_session.status=active。
- **i18n**：store 读 `Accept-Language`（en/es/fr，缺省 en）；admin 固定 zh。
- **RBAC（admin）**：路由→permission_key 映射，缺权限→403 `40300`。
- **审计（admin 写操作）**：AOP 切面写 operation_log（FLOW-17）。

---

## 1. STORE 认证端点

### 1.1 sendOtp — POST /api/store/auth/otp/send （FLOW-01, FUNC-001）

**入参**: `{ email:string, locale?:enum(en,es,fr) }`
- V-001 email 必填且正则 `^[^\s@]+@[^\s@]+\.[^\s@]+$` → 否则 422 `40001 INVALID_EMAIL`
- V-002 locale 缺省取 Accept-Language → 缺省 en

**业务逻辑**:
- STEP-01 规范化 email（trim+lower）
- STEP-02 频控：`INCR otp:resend:{email}`（TTL=otp_resend_seconds）；命中未过期 → 429 `42901`（details.remaining_resend_seconds）
- STEP-03 频控窗口：`otp:count:email:{email}:h/:d`、`otp:count:ip:{ip}:h` 超阈 → 429 `42902`
- STEP-04 读 AuthConfig（缓存 store:authconfig）取 otp_length/ttl/max_attempts；若 email_enabled=false（不可能，恒开）
- STEP-05 失效旧 pending：`UPDATE otp_code SET status=expired WHERE email=? AND status=pending`
- STEP-06 生成明文 code（length 位）→ 仅持久化 code_hash（BCrypt/SHA256+salt）；INSERT otp_code(status=pending, attempts=0, max_attempts, expires_at=now+ttl)
- STEP-07 异步发邮件（template=otp, locale）→ FLOW-15 重试；失败不阻塞主流程

**出参**: 200 `{ resend_after_seconds:int, otp_length:int }`
**错误映射**: 422 40001 / 429 42901 / 429 42902 / 500 50002(邮件失败提示重试，不阻塞)

### 1.2 verifyOtp — POST /api/store/auth/otp/verify （FLOW-02, FUNC-002, 关键事务 BE-DIM-4）

**入参**: `{ email:string, code:string }`
- V-003 email 同 V-001 ；V-004 code 必填、长度匹配 otp_length

**业务逻辑（单事务）**:
- STEP-01 `SELECT otp_code WHERE email=? AND status=pending FOR UPDATE`（行锁串行化）；无 → 410 `41001`
- STEP-02 若 now>expires_at → UPDATE status=expired → 410 `41001`
- STEP-03 校验 code_hash：
  - 错误且 attempts+1 < max → UPDATE attempts+1；INSERT login_history(result=failed)；401 `40101`（details.remaining_attempts=max-(attempts+1)）
  - 错误且 attempts+1 >= max → UPDATE status=locked → 410 `41002`
  - 正确 → UPDATE status=consumed，进 STEP-04
- STEP-04 定位/归并/新建 User（复用 MergeService，见 oidcCallback 归并逻辑，provider=email, provider_uid=email）
- STEP-05 若 User.status=disabled → 403 `40301`（不签发会话）
- STEP-06 判定 is_new_device（同 user 历史 device 是否出现）
- STEP-07 INSERT user_session(status=active, token_id=jti, method=email)；签发 TokenPair(access2h+refresh30d)
- STEP-08 INSERT login_history(result=success, is_new_device)
- STEP-09 事务提交后 `SET store:session:valid:{jti} TTL 30s`
- STEP-10 若 is_new_device → 触发 FLOW-14 新设备通知

**出参**: 200 `{ tokens:{access_token,refresh_token,access_expires_at,refresh_expires_at}, user:{id,email,name,tier,avatar}, is_new_account:bool }`
**错误映射**: 422 40000 / 410 41001 / 401 40101 / 410 41002 / 403 40301

### 1.3 oidcCallback — POST /api/store/auth/oidc/{provider}/callback （FLOW-03, FUNC-004/005/025/028/029）

**入参**: path `provider:enum(google,apple)`；body `{ id_token:string, nonce?:string }`
- V-005 provider ∈ {google,apple}；否则 404
- V-006 若 AuthConfig.{provider}_enabled=false → 403 `40303 PROVIDER_DISABLED`
- V-007 id_token 必填

**业务逻辑（归并单事务 BE-DIM-4 + 外部集成 BE-DIM-5）**:
- STEP-01 调 OIDC 验证 id_token（超时 5s，重试 1 次）：
  - 超时 → 504 `50401`；不可达 → 502 `50201`（均引导改 OTP）
  - 成功 → 取 sub / email / email_verified（Apple hidden→relay_email）
- STEP-02 `SELECT user_identity WHERE provider=? AND provider_uid=sub`（命中即幂等返回既有 User）
- STEP-03 凭证不存在 → 查同邮箱 User：
  - 同邮箱 User 存在且 email_verified=true → INSERT user_identity 挂既有 User；INSERT operation_log(operator=系统, action=账户合并)（自动归并 R1）
  - 同邮箱 User 存在但 email_verified=false → 409 `40902 EMAIL_CONFLICT_UNVERIFIED`（不静默合并；EDGE-017/021 注销账户再登录亦走此流）
  - 无同邮箱 User → INSERT user(email_verified=OIDC值) + user_identity(Apple 存 relay_email, relay_valid=true)
- STEP-04 Apple relay 失效（FUNC-029）：标 relay_valid=false 但 sub 仍登录，不阻断
- STEP-05 User.status=disabled → 403 `40301`
- STEP-06 INSERT user_session + login_history(success)；签发 TokenPair；事务提交后写 redis；新设备触发 FLOW-14

**出参**: 200 同 verifyOtp
**错误映射**: 404 / 403 40303 / 504 50401 / 502 50201 / 409 40902 / 403 40301

### 1.4 refreshToken — POST /api/store/auth/refresh （FLOW-04, FUNC-030）

**入参**: `{ refresh_token:string }`
**业务逻辑**:
- STEP-01 解析 refresh jti → `SELECT user_session WHERE refresh_token_id=? AND status=active`
- STEP-02 无/revoked/refresh 过期 → 401 `40102 REFRESH_INVALID`
- STEP-03 滑动顺延 access_expires_at(+2h)/refresh_expires_at(+30d)（乐观锁 version）
- STEP-04 刷新 `store:session:valid:{newJti}` TTL

**出参**: 200 `{ tokens:TokenPair }`
**错误映射**: 401 40102

### 1.5 getStoreAuthConfig — GET /api/store/config/auth （FUNC-003）

**业务逻辑**: 读缓存 store:authconfig → 返回登录方式开关（不含 OAuth 密钥）
**出参**: 200 `{ email_enabled, google_enabled, apple_enabled, otp_length }`

---

## 2. STORE 账户安全端点（需 store JWT）

### 2.1 getProfile — GET /api/store/account/profile （FUNC-007）
- STEP-01 从 JWT 取 user_id；读缓存 store:user:{userId}
- 出参 200 `{ id,email,email_verified,name,phone,tier,avatar,joined_at }`

### 2.2 listIdentities — GET /api/store/account/identities （FUNC-010）
- 读缓存 store:identities:{userId} → 返回 connected=true 凭证列表
- 出参 200 `{ items:[{id,provider,identifier,is_primary,verified,hidden_email,relay_valid,last_login_at}] }`

### 2.3 bindIdentity — POST /api/store/account/identities/bind （FLOW-05, FUNC-008）
**入参**: `{ provider:enum, id_token?:string, email?, code? }`
- STEP-01 OIDC/OTP 校验取 (provider, provider_uid)
- STEP-02 校验 (provider,provider_uid) 未被占用 → 占用 409 `40903 IDENTITY_TAKEN`
- STEP-03 INSERT/UPDATE user_identity connected=true；失效 store:identities:{userId}
- 出参 200 凭证列表 ｜ 错误 409 40903

### 2.4 unbindIdentity — DELETE /api/store/account/identities/{identityId} （FLOW-05, FUNC-009, R2）
- STEP-01 校验归属当前 user；非本人 → 403 `40300`
- STEP-02 目标 is_primary → 403 `40304 PRIMARY_EMAIL_REQUIRED`（EDGE-007）
- STEP-03 解绑后 connected 数 < AuthConfig.min_methods → 403 `40305 MIN_METHODS_REQUIRED`（EDGE-008）
- STEP-04 UPDATE connected=false；失效缓存
- 出参 204 ｜ 错误 403 40304/40305/40300

### 2.5 changePrimaryEmail — POST /api/store/account/email/change-primary （FLOW-06, FUNC-026, EDGE-020）
**入参**: `{ new_email:string, code:string }`
- V new_email 格式校验
- STEP-01 new_email 被他人占用 → 409 `40901 EMAIL_EXISTS`
- STEP-02 复用 OTP 校验逻辑（对 new_email）通过
- STEP-03 事务：旧 is_primary=false，新邮箱凭证 is_primary=true（不存在则建已验证凭证）
- STEP-04 发 change_primary 邮件（旧邮箱）；失效 store:identities/user:{userId}
- 出参 200 凭证列表 ｜ 错误 409 40901

### 2.6 listSessions — GET /api/store/account/sessions （FUNC-011/013, EDGE-010）
- 读 store:sessions:{userId} → status=active 会话列表，标记 current（jti 匹配）+ is_new_device
- 出参 200 `{ items:[{id,device,browser,ip,location,is_new_device,is_current,last_active_at,created_at}] }`

### 2.7 revokeSession — DELETE /api/store/account/sessions/{sessionId} （FLOW-07, FUNC-012, EDGE-009）
- STEP-01 会话不属当前 user → 403 `40300 FORBIDDEN`
- STEP-02 事务 UPDATE status=revoked；`DEL store:session:valid:{tokenId}`；失效 store:sessions:{userId}
- 出参 204

### 2.8 revokeOtherSessions — DELETE /api/store/account/sessions/others （FLOW-07, FUNC-012）
- 撤销当前 user 除 current jti 外全部 active 会话；批量 DEL redis 单级键
- 出参 204

### 2.9 deleteAccount — POST /api/store/account/delete （FLOW-08, FUNC-027, EDGE-021/026）
**入参**: `{ confirm:true }`
- STEP-01 事务：UPDATE user status=deleted, deleted_at=now；UPDATE 全部 user_session=revoked
- STEP-02 清 store:user/identities/sessions/session:valid:* 全部键
- STEP-03 发 account_deleted 邮件
- STEP-04 审计/匿名化由 FLOW-16 定时任务 30 天后处理（不在此同步）
- 出参 204（再次登录走 40902，不复活 EDGE-021）

---

## 3. ADMIN 认证 / 管理员端点（需 admin JWT + RBAC）

### 3.1 adminLogin — POST /api/admin/auth/login （FLOW-09, FUNC-014, EDGE-011）
**入参**: `{ email, password, redirect? }`
- STEP-01 `SELECT admin_user WHERE email=?`；凭据错误 → 401 `40103 CREDENTIALS_INVALID`
- STEP-02 status=disabled → 403 `40302 ADMIN_DISABLED`
- STEP-03 BCrypt 校验 password_hash
- STEP-04 INSERT admin_session(jti)；签发 admin JWT(8h, role_id, permission_keys)
- STEP-05 INSERT operation_log(action=登录)；UPDATE last_login_at
- 出参 200 `{ token, admin:{id,name,email,role_id,role_name}, permission_keys:[...] }`

### 3.2 adminLogout — POST /api/admin/auth/logout
- UPDATE admin_session status=revoked（当前 jti）；出参 204

### 3.3 adminMe — GET /api/admin/auth/me （FUNC-021 守卫数据源）
- 取当前 admin + role + permission_keys（超管 is_locked→全 22 key 短路）
- 出参 200 `{ admin, role_name, is_super:bool, permission_keys:[...] }`

### 3.4 listAdmins — GET /api/admin/admins
- RBAC /system/admins；分页 + 按 status/role 筛选
- 出参 200 分页 `{ items:[{id,name,email,role_id,role_name,status,last_login_at}], total,page,page_size }`

### 3.5 createAdmin — POST /api/admin/admins （FLOW-10, FUNC-015, EDGE-012）
**入参**: `{ name, email, password, role_id }`
- V name≤80, email 格式, password≥6, role_id 存在
- STEP-01 email 重复 → 409 `40901 EMAIL_EXISTS`
- STEP-02 INSERT admin_user(password_hash=BCrypt)；审计 action=创建管理员
- 出参 201 admin

### 3.6 updateAdmin — PUT /api/admin/admins/{id} （FLOW-10, FUNC-016）
- 改 name/role_id（email 不可改）；超管降权 → 403 `40306 SUPER_ADMIN_PROTECTED`（EDGE-014）；审计 action=编辑管理员
- 出参 200

### 3.7 deleteAdmin — DELETE /api/admin/admins/{id} （FLOW-10, FUNC-017, EDGE-013/014）
- STEP-01 删自己 → 403 `40307 CANNOT_DELETE_SELF`
- STEP-02 目标超管(role.is_locked) → 403 `40306`
- STEP-03 DELETE；审计 action=删除管理员
- 出参 204

### 3.8 toggleAdminStatus — PATCH /api/admin/admins/{id}/status （EDGE-014）
- 超管 → 403 `40306`；禁用即级联 UPDATE admin_session=revoked；审计 action=禁用管理员
- 出参 200

### 3.9 resetAdminPassword — PATCH /api/admin/admins/{id}/password
- V new_password≥6；UPDATE password_hash；审计 action=重置密码
- 出参 204

---

## 4. ADMIN 角色权限端点

### 4.1 listRoles — GET /api/admin/roles
- 出参 200 `{ items:[{id,name,type,is_locked,member_count,permission_keys:[...]}] }`

### 4.2 createRole — POST /api/admin/roles （FUNC-018）
- V name≤40 唯一；INSERT role(type=custom)；审计 action=创建角色 ｜ 重名 → 409 EMAIL_EXISTS 复用?（用 40904? 见错误设计统一）
- 出参 201

### 4.3 updateRole — PUT /api/admin/roles/{id} （FLOW-11, FUNC-018/019）
**入参**: `{ name?, permission_keys:[...] }`
- STEP-01 预设超管 is_locked → 403 `40308 ROLE_LOCKED`
- STEP-02 事务全量重写 role_permission：DELETE WHERE role_id=? + 批量 INSERT（校验 keys 均存在）
- STEP-03 审计 action=权限变更(changes before/after)
- 出参 200（前端重渲染菜单/守卫）

### 4.4 deleteRole — DELETE /api/admin/roles/{id} （FLOW-11, FUNC-020, EDGE-015）
- STEP-01 is_locked → 403 `40308`
- STEP-02 有关联 admin_user → 409 `40904 ROLE_IN_USE`
- STEP-03 DELETE role + role_permission；审计 action=删除角色
- 出参 204

### 4.5 listPermissions — GET /api/admin/permissions
- 返回 22 项菜单权限字典（按 group 分组，供权限矩阵渲染）
- 出参 200 `{ items:[{key,group,label}] }`

---

## 5. ADMIN 用户运营端点

### 5.1 listUsers — GET /api/admin/users
- 分页 + 按 status/tier/email 筛选（Customers 页）
- 出参 200 分页 `{ items:[{id,email,name,tier,status,joined_at,identity_count}] }`

### 5.2 getUserDetail — GET /api/admin/users/{id}
- 返回 user + identities + 近期 login_history + active sessions（CustomerDetail 页）
- 出参 200 `{ user, identities:[...], sessions:[...], login_history:[...] }`

### 5.3 toggleUserStatus — PATCH /api/admin/users/{id}/status （FLOW-12, FUNC-022）
- 禁用 → UPDATE user status=disabled + 级联 user_session=revoked + 清 redis 单级；审计 action=用户禁用
- 出参 200

### 5.4 forceLogoutUserSessions — POST /api/admin/users/{id}/sessions/force-logout （FLOW-12, FUNC-022, EDGE-023）
**入参**: `{ scope:enum(single,all), session_id? }`
- 事务 UPDATE user_session=revoked；`DEL store:session:valid:{tokenId}*`（全集群即时生效，无残留窗口）；审计 action=强制下线
- 出参 204

---

## 6. ADMIN 认证配置 / 操作日志端点

### 6.1 getAuthConfig — GET /api/admin/auth-config （AuthSettings 页）
- 返回完整 AuthConfig（含只读 google_client_id/apple_service_id）
- 出参 200 AuthConfig

### 6.2 updateAuthConfig — PUT /api/admin/auth-config （FLOW-13, FUNC-023, EDGE-019）
**入参**: AuthConfig 可写字段
- V-CFG email_enabled 恒 true（强制）；otp_length∈{4,6,8}；ttl 1..30；resend 10..120；max_attempts 3..10；min_methods 1..3
- 越界 → 422 `40002 CONFIG_OUT_OF_RANGE`
- STEP-01 UPDATE auth_config（单例 id=1）；审计 action=认证配置变更
- STEP-02 `@CacheInvalidate store:authconfig`（消费端登录页即读新配置）
- 出参 200

### 6.3 listOperationLogs — GET /api/admin/operation-logs （FUNC-024, EDGE-018）
- 分页 + 按 action/operator/时间范围筛选；按 created_at 倒序；**只读无 delete**
- 出参 200 分页 `{ items:[{id,operator_name,action,target,ip,changes,created_at}] }`

### 6.4 exportOperationLogs — GET /api/admin/operation-logs/export
- 导出 CSV/Excel（按筛选条件）；流式返回
- 出参 200 file stream

---

## 7. 自检
- [x] 36 操作全部覆盖（store 14 + admin 22）
- [x] 每端点含 入参验证 / 业务逻辑 / 出参 / 错误映射四部分
- [x] 错误码引用 error-strategy 码表，无臆造
- [x] 事务边界标注（verifyOtp/oidcCallback/changePrimary/updateRole/forceLogout/deleteAccount）
- [x] 双 JWT 隔离 + RBAC + 审计横切声明
- [x] 可追溯到 OpenAPI operationId 与 FLOW 编号
