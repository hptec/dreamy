# 数据流 - identity（身份认证与用户域）

本文档定义 identity 限界上下文核心业务流程的数据流转，并逐条响应 `decision.md` 「后端关键决策」（BE-DIM-4 ~ BE-DIM-8）。参与者命名：`User`（消费者）、`Admin`（管理员）、`StoreAPI`/`AdminAPI`（表现层 + 鉴权过滤器）、`Svc`（领域服务，IDENTITY-COMMON）、`Redis`（频控/会话缓存）、`DB`（MySQL）、`SMTP`（邮件）、`OIDC`（Google/Apple）、`Sched`（定时任务）。

## 核心业务流程清单

| 流程编号 | 流程名称 | 触发条件 | 参与模块 | 验收 |
|---------|---------|---------|---------|------|
| FLOW-01 | OTP 发送 | 用户提交邮箱 | StoreAPI, Svc, Redis, DB, SMTP | FUNC-001, EDGE-001/005/022 |
| FLOW-02 | OTP 校验登录 | 用户提交验证码 | StoreAPI, Svc, DB, Redis | FUNC-002, EDGE-002/003/004/006 |
| FLOW-03 | OIDC 登录 + 自动归并 | OIDC 回调 | StoreAPI, Svc, OIDC, DB | FUNC-004/005/025/028/029 |
| FLOW-04 | refresh 续期 | access 过期 | StoreAPI, Svc, Redis, DB | FUNC-030 |
| FLOW-05 | 绑定/解绑登录方式 | 用户操作安全页 | StoreAPI, Svc, DB, Redis | FUNC-008/009, EDGE-007/008 |
| FLOW-06 | 换主邮箱 | 用户发起 | StoreAPI, Svc, DB, SMTP | FUNC-026, EDGE-020 |
| FLOW-07 | 会话撤销（登出他设备） | 用户操作 | StoreAPI, Svc, Redis, DB | FUNC-011/012, EDGE-009 |
| FLOW-08 | 账户注销 + 匿名化 | 用户注销 / 定时任务 | StoreAPI, Svc, Redis, DB, SMTP, Sched | FUNC-027/033, EDGE-021/026 |
| FLOW-09 | 管理员登录 | 管理员提交凭据 | AdminAPI, Svc, DB | FUNC-014, EDGE-011 |
| FLOW-10 | 管理员 CRUD | 超管操作 | AdminAPI, Svc, DB | FUNC-015/016/017, EDGE-012/013/014 |
| FLOW-11 | 角色权限保存 | 超管保存矩阵 | AdminAPI, Svc, DB | FUNC-018/019/020, EDGE-015 |
| FLOW-12 | 用户身份运营（强制下线/禁用） | 管理员操作 | AdminAPI, Svc, Redis, DB | FUNC-022, EDGE-023 |
| FLOW-13 | 认证配置保存 | 超管保存 | AdminAPI, Svc, DB, Redis | FUNC-023, EDGE-019 |
| FLOW-14 | 新设备登录通知 | 登录成功且新设备 | Svc, SMTP, DB | FUNC-031 |
| FLOW-15 | 邮件发送重试 | 任意邮件发送 | Svc, SMTP | FUNC-034 |
| FLOW-16 | 数据保留清理 | 每日定时 | Sched, DB | FUNC-032 |
| FLOW-17 | 操作审计写入（横切） | 后台关键操作 | AdminAPI(AOP), DB | FUNC-024, EDGE-018 |

---

## 后端关键决策响应映射

| 决策维度 | 本文档响应位置 |
|---------|----------------|
| BE-DIM-4 事务（OTP 校验串行/归并单事务/强制下线） | FLOW-02 事务边界 + 乐观锁、FLOW-03 归并单事务、FLOW-12 撤销事务 |
| BE-DIM-5 外部集成降级（SMTP/OIDC） | FLOW-03 OIDC 超时/不可用降级、FLOW-15 SMTP 重试、FLOW-01 发送失败提示 |
| BE-DIM-6 权限（两端 JWT/RBAC/store token 隔离） | 全 admin 流程鉴权过滤器 + RBAC 守卫，见 FLOW-09~13；store token 命中 admin 返 401（见 error-strategy 跨端隔离） |
| BE-DIM-7 审计（OperationLog/LoginHistory） | FLOW-17 AOP 审计切面、FLOW-02/03 LoginHistory 落库 |
| BE-DIM-8 缓存（JetCache 分级写失效） | FLOW-05/06/07/08/12/13 写即失效，FLOW-07/12 会话仅 Redis 单级强一致 |

幂等（BE-DIM-4）：OTP verify 以 `(email, code)` + Redis 原子计数去重并发；OIDC 归并以 `(provider, provider_uid)` 唯一索引保证重复回调幂等（命中即返回既有 user，不重复建号）。限流/降级（BE-DIM-8 关联）见 error-strategy.md「频控与限流」与本文 FLOW-01。

---

## FLOW-01: OTP 发送

**触发条件**: 用户在登录页提交邮箱点击 "Email me a code"。

**正常路径**:

```mermaid
sequenceDiagram
    actor User
    participant StoreAPI
    participant Svc
    participant Redis
    participant DB
    participant SMTP

    User->>StoreAPI: POST /api/store/auth/otp/send {email, locale}
    Note over StoreAPI: 校验邮箱格式（非法→422 INVALID_EMAIL 40001）
    StoreAPI->>Svc: sendOtp(email, locale)
    Note over Svc,Redis: BE-DIM-8 频控（Redis 窗口计数）
    Svc->>Redis: INCR otp:resend:{email} (TTL otp_resend_seconds)
    alt 间隔未到
        Redis-->>Svc: 命中未过期
        Svc-->>StoreAPI: RESEND_TOO_SOON
        StoreAPI-->>User: 429 {code:42901}
    else 超频次（email 5/h&5/d 或 IP 20/h）
        Svc->>Redis: 校验 otp:count:email:{email}:h / :d / otp:count:ip:{ip}:h
        Svc-->>StoreAPI: RATE_LIMITED
        StoreAPI-->>User: 429 {code:42902}
    else 允许发送
        Svc->>DB: 失效旧 pending OTP, INSERT OtpCode(status=pending, code_hash, attempts=0)
        Svc->>SMTP: send(template=otp, locale, code)  %% 异步, 见 FLOW-15
        SMTP-->>Svc: accepted
        Svc-->>StoreAPI: {resend_after_seconds, length}
        StoreAPI-->>User: 200
    end
```

**异常路径**:
1. 邮箱格式非法 → 422 `40001 INVALID_EMAIL`（不生成 OtpCode）。
2. 重发间隔未到 → 429 `42901 RESEND_TOO_SOON`。
3. 发码超频 → 429 `42902 RATE_LIMITED`。
4. SMTP 发送失败 → 走 FLOW-15 重试；仍失败提示用户重试（不阻塞主流程，OtpCode 已落库）。

---

## FLOW-02: OTP 校验登录（关键事务，BE-DIM-4）

**触发条件**: 用户提交验证码。

```mermaid
sequenceDiagram
    actor User
    participant StoreAPI
    participant Svc
    participant DB
    participant Redis

    User->>StoreAPI: POST /api/store/auth/otp/verify {email, code}
    StoreAPI->>Svc: verifyOtp(email, code)
    Note over Svc,DB: BE-DIM-4 事务边界开始（防并发绕过 attempts）
    Svc->>DB: SELECT OtpCode WHERE email=? AND status=pending FOR UPDATE
    Note over Svc: 行锁 / 乐观锁版本号串行化同一 email 校验
    alt now > expires_at
        Svc->>DB: UPDATE status=expired
        Svc-->>StoreAPI: OTP_EXPIRED
        StoreAPI-->>User: 410 {code:41001}
    else code 错误且 attempts+1 < max
        Svc->>DB: UPDATE attempts=attempts+1
        Svc->>DB: INSERT LoginHistory(result=failed)
        Svc-->>StoreAPI: OTP_INVALID
        StoreAPI-->>User: 401 {code:40101, details:{remaining_attempts}}
    else code 错误且 attempts+1 >= max
        Svc->>DB: UPDATE status=locked
        Svc-->>StoreAPI: OTP_LOCKED
        StoreAPI-->>User: 410 {code:41002}
    else code 正确
        Svc->>DB: UPDATE OtpCode status=consumed
        Svc->>Svc: 定位/归并/新建 User（见 FLOW-03 归并逻辑）
        alt User.status=disabled
            Svc-->>StoreAPI: ACCOUNT_DISABLED
            StoreAPI-->>User: 403 {code:40301}
        else
            Svc->>DB: INSERT UserSession(status=active, token_id=jti)
            Svc->>DB: INSERT LoginHistory(result=success, is_new_device?)
            Note over Svc,DB: BE-DIM-4 事务提交
            Svc->>Redis: SET store:session:valid:{tokenId} (TTL 30s)
            Svc-->>StoreAPI: AuthSession{tokens, user, is_new_account}
            StoreAPI-->>User: 200 (+ 新设备则触发 FLOW-14)
        end
    end
```

**异常路径**: 410 过期/锁定；401 验证码错误（attempts+1，写 failed LoginHistory）；403 账户禁用（不签发会话）。

---

## FLOW-03: OIDC 登录 + 自动归并（外部集成 BE-DIM-5 + 归并事务 BE-DIM-4）

**触发条件**: Google/Apple OIDC 回调。

```mermaid
sequenceDiagram
    actor User
    participant StoreAPI
    participant Svc
    participant OIDC
    participant DB

    User->>StoreAPI: POST /api/store/auth/oidc/{provider}/callback {id_token}
    Note over StoreAPI: 校验 provider 开关（关闭→403 PROVIDER_DISABLED 40303）
    StoreAPI->>Svc: oidcLogin(provider, id_token)
    Note over Svc,OIDC: BE-DIM-5 外部集成（超时 5s/重试 1 次/熔断见 error-strategy）
    Svc->>OIDC: 验证 id_token, 取 sub / email / email_verified
    alt OIDC 超时
        OIDC-->>Svc: timeout
        Svc-->>StoreAPI: OIDC_TIMEOUT
        StoreAPI-->>User: 504 {code:50401} 引导改用 OTP
    else OIDC 不可达
        Svc-->>StoreAPI: OIDC_UNAVAILABLE
        StoreAPI-->>User: 502 {code:50201} 引导改用 OTP
    else 验证成功
        Note over Svc,DB: BE-DIM-4 归并事务开始（单事务迁移 identity + 写日志）
        Svc->>DB: SELECT UserIdentity WHERE provider=? AND provider_uid=sub
        alt 凭证已存在（幂等：重复回调命中既有）
            Svc->>DB: 定位既有 User
        else 凭证不存在
            Svc->>DB: 查同邮箱 User
            alt 同邮箱 User 存在 且 email_verified=true（自动归并）
                Svc->>DB: INSERT UserIdentity 挂到既有 User
                Svc->>DB: INSERT OperationLog(operator=系统, action=账户合并)
            else 同邮箱 User 存在 但 email_verified=false（冲突即拒）
                Svc-->>StoreAPI: EMAIL_CONFLICT_UNVERIFIED
                StoreAPI-->>User: 409 {code:40902} 提示原方式登录后绑定
            else 无同邮箱 User（新建）
                Svc->>DB: INSERT User + UserIdentity(Apple Hide My Email 存 relay_email)
            end
        end
        alt User.status=disabled
            Svc-->>StoreAPI: ACCOUNT_DISABLED
            StoreAPI-->>User: 403 {code:40301}
        else
            Svc->>DB: INSERT UserSession + LoginHistory(success)
            Note over Svc,DB: 归并事务提交
            Svc-->>StoreAPI: AuthSession
            StoreAPI-->>User: 200
        end
    end
```

**异常路径**: 502/504 OIDC 不可用/超时（降级引导 OTP）；409 未验证邮箱冲突（不静默合并）；403 账户禁用/方式关闭。Apple relay 失效（FUNC-029）：标记 `relay_valid=false` 但 sub 仍可登录，不阻断。

---

## FLOW-04: refresh 续期

```mermaid
sequenceDiagram
    actor User
    participant StoreAPI
    participant Svc
    participant Redis
    participant DB

    User->>StoreAPI: POST /api/store/auth/refresh {refresh_token}
    StoreAPI->>Svc: refresh(refresh_token)
    Svc->>DB: SELECT UserSession WHERE refresh_token_id=? AND status=active
    alt 会话不存在/已 revoked/refresh 过期
        Svc-->>StoreAPI: REFRESH_INVALID
        StoreAPI-->>User: 401 {code:40102}
    else 有效
        Svc->>DB: UPDATE access_expires_at, refresh_expires_at（滑动顺延）
        Svc->>Redis: 刷新 store:session:valid:{tokenId} TTL
        Svc-->>StoreAPI: TokenPair（新 access + 顺延 refresh）
        StoreAPI-->>User: 200
    end
```

---

## FLOW-05: 绑定/解绑登录方式（BE-DIM-8 写失效）

```mermaid
sequenceDiagram
    actor User
    participant StoreAPI
    participant Svc
    participant DB
    participant Redis

    User->>StoreAPI: POST /identities/bind  或  DELETE /identities/{id}
    StoreAPI->>Svc: bind / unbind
    alt 绑定
        Svc->>DB: 校验 (provider,provider_uid) 未被占用
        alt 已被占用
            Svc-->>StoreAPI: IDENTITY_TAKEN → 409 {40903}
        else
            Svc->>DB: INSERT/UPDATE UserIdentity connected=true
        end
    else 解绑
        Note over Svc: R2 约束
        alt 目标为 is_primary
            Svc-->>StoreAPI: PRIMARY_EMAIL_REQUIRED → 403 {40304}
        else 解绑后 connected < min_methods
            Svc-->>StoreAPI: MIN_METHODS_REQUIRED → 403 {40305}
        else
            Svc->>DB: UPDATE connected=false
        end
    end
    Svc->>Redis: @CacheInvalidate store:identities:{userId}（两级）
    Svc-->>StoreAPI: 200/204
    StoreAPI-->>User: 结果
```

---

## FLOW-06: 换主邮箱

```mermaid
sequenceDiagram
    actor User
    participant StoreAPI
    participant Svc
    participant DB
    participant SMTP

    User->>StoreAPI: POST /account/email/change-primary {new_email, code}
    StoreAPI->>Svc: changePrimary(new_email, code)
    Svc->>DB: 校验 new_email 未被他人占用
    alt 被占用
        Svc-->>StoreAPI: EMAIL_EXISTS → 409 {40901}
    else 校验 OTP（复用 FLOW-02 校验逻辑）通过
        Note over Svc,DB: 事务：迁移 is_primary 到新邮箱凭证，旧邮箱降为普通已验证方式
        Svc->>DB: UPDATE 旧 is_primary=false, 新 is_primary=true
        Svc->>SMTP: send(template=change_primary, 旧邮箱) %% FLOW-15
        Svc->>Redis: @CacheInvalidate store:identities/user:{userId}
        Svc-->>StoreAPI: IdentityList
        StoreAPI-->>User: 200
    end
```

---

## FLOW-07: 会话撤销（登出他设备，BE-DIM-8 仅 Redis 单级强一致）

```mermaid
sequenceDiagram
    actor User
    participant StoreAPI
    participant Svc
    participant DB
    participant Redis

    User->>StoreAPI: DELETE /sessions/{id}  或  DELETE /sessions/others
    StoreAPI->>Svc: revoke(sessionId | others, currentUserId)
    alt 跨用户撤销（会话属于他人）
        Svc-->>StoreAPI: FORBIDDEN → 403 {40300}
    else
        Note over Svc,DB: 事务：UPDATE UserSession status=revoked
        Svc->>DB: UPDATE status=revoked
        Note over Svc,Redis: 仅远程 Redis 单级，写失效全集群即时生效（无本地残留）
        Svc->>Redis: DEL store:session:valid:{tokenId}, @CacheInvalidate store:sessions:{userId}
        Svc-->>StoreAPI: 204
        StoreAPI-->>User: 204
    end
```

---

## FLOW-08: 账户注销 + 超宽限匿名化

```mermaid
sequenceDiagram
    actor User
    participant StoreAPI
    participant Svc
    participant DB
    participant Redis
    participant SMTP
    participant Sched

    User->>StoreAPI: POST /account/delete {confirm:true}
    StoreAPI->>Svc: deleteAccount(userId)
    Note over Svc,DB: 事务：软删除 + 级联撤销会话
    Svc->>DB: UPDATE User status=deleted, deleted_at=now
    Svc->>DB: UPDATE 全部 UserSession status=revoked
    Svc->>Redis: 清 store:user/identities/sessions/session:valid:* 全部 key
    Svc->>SMTP: send(template=account_deleted) %% FLOW-15
    Svc-->>StoreAPI: 204
    StoreAPI-->>User: 204

    Note over Sched,DB: 每日定时（FLOW-16 子流程，BE-DIM-7 合规）
    Sched->>DB: SELECT User WHERE status=deleted AND deleted_at < now-30d
    Sched->>DB: UPDATE 不可逆匿名化 PII(email/name/phone/identity 标识), status=anonymized, anonymized=true, anonymized_at=now
    Note over Sched,DB: OperationLog/审计日志按保留期保留，不随注销删除（EDGE-026 正当利益）
```

**异常路径**: EDGE-021 注销账户再次登录 → 不复活，按未验证冲突流（409 EMAIL_CONFLICT_UNVERIFIED）。

---

## FLOW-09: 管理员登录（BE-DIM-6 + BE-DIM-7）

```mermaid
sequenceDiagram
    actor Admin
    participant AdminAPI
    participant Svc
    participant DB

    Admin->>AdminAPI: POST /api/admin/auth/login {email, password, redirect}
    AdminAPI->>Svc: adminLogin(email, password)
    Svc->>DB: SELECT AdminUser WHERE email=?
    alt 凭据错误
        Svc-->>AdminAPI: CREDENTIALS_INVALID → 401 {40103}
    else status=disabled
        Svc-->>AdminAPI: ADMIN_DISABLED → 403 {40302}
    else 成功
        Svc->>DB: INSERT AdminSession(token_id=jti)
        Svc->>DB: INSERT OperationLog(action=登录)  %% BE-DIM-7
        Svc-->>AdminAPI: AdminAuthSession（admin JWT 8h, role+permission keys）
        AdminAPI-->>Admin: 200 (前端按 redirect 跳转)
    end
```

---

## FLOW-10: 管理员 CRUD（BE-DIM-6 RBAC + BE-DIM-7 审计）

```mermaid
sequenceDiagram
    actor Admin
    participant AdminAPI
    participant Svc
    participant DB

    Admin->>AdminAPI: POST/PUT/DELETE/PATCH /api/admin/admins/*
    Note over AdminAPI: 鉴权过滤器：admin JWT 校验 + RBAC 守卫(/system/admins)
    Note over AdminAPI: store token 命中 → 401（跨端隔离 BE-DIM-6）
    AdminAPI->>Svc: createAdmin / updateAdmin / deleteAdmin / toggleStatus / resetPassword
    alt 新增邮箱重复
        Svc-->>AdminAPI: EMAIL_EXISTS → 409 {40901}
    else 删除自己
        Svc-->>AdminAPI: CANNOT_DELETE_SELF → 403 {40307}
    else 删除/禁用超级管理员
        Svc-->>AdminAPI: SUPER_ADMIN_PROTECTED → 403 {40306}
    else 合法
        Svc->>DB: 写入变更
        opt 禁用管理员
            Svc->>DB: UPDATE AdminSession status=revoked（级联）
        end
        Svc->>DB: INSERT OperationLog(action + changes before/after) %% FLOW-17 AOP
        Svc-->>AdminAPI: 200/201/204
    end
    AdminAPI-->>Admin: 结果
```

---

## FLOW-11: 角色权限保存

```mermaid
sequenceDiagram
    actor Admin
    participant AdminAPI
    participant Svc
    participant DB

    Admin->>AdminAPI: PUT /api/admin/roles/{id} {name, permission_keys}
    Note over AdminAPI: RBAC 守卫(/system/roles)
    AdminAPI->>Svc: updateRole
    alt 预设超管 is_locked
        Svc-->>AdminAPI: ROLE_LOCKED → 403 {40308}
    else 删除有成员角色
        Svc-->>AdminAPI: ROLE_IN_USE → 409 {40904}
    else 合法保存
        Note over Svc,DB: 事务：全量重写 RolePermission
        Svc->>DB: DELETE+INSERT RolePermission(role_id, keys)
        Svc->>DB: INSERT OperationLog(action=权限变更, changes)
        Svc-->>AdminAPI: Role
    end
    AdminAPI-->>Admin: 结果（前端重渲染菜单/守卫）
```

---

## FLOW-12: 用户身份运营（强制下线/禁用，BE-DIM-8 全集群即时生效）

```mermaid
sequenceDiagram
    actor Admin
    participant AdminAPI
    participant Svc
    participant DB
    participant Redis

    Admin->>AdminAPI: POST /users/{id}/sessions/force-logout {scope}  或  PATCH /users/{id}/status
    Note over AdminAPI: RBAC 守卫(/customers)
    AdminAPI->>Svc: forceLogout / toggleUserStatus
    Note over Svc,DB: 事务
    alt 强制下线
        Svc->>DB: UPDATE UserSession status=revoked (single/all)
    else 禁用账户
        Svc->>DB: UPDATE User status=disabled
        Svc->>DB: UPDATE 全部 UserSession status=revoked（级联）
    end
    Note over Svc,Redis: 仅 Redis 单级写失效，全集群下次请求即判定失效（EDGE-023 无残留窗口）
    Svc->>Redis: DEL store:session:valid:{tokenId}*, 清 store:user/identities/sessions:{userId}
    Svc->>DB: INSERT OperationLog(action=强制下线/禁用管理员? 用户禁用记审计)
    Svc-->>AdminAPI: 204/200
    AdminAPI-->>Admin: 结果
```

---

## FLOW-13: 认证配置保存（BE-DIM-8 写失效 store:authconfig）

```mermaid
sequenceDiagram
    actor Admin
    participant AdminAPI
    participant Svc
    participant DB
    participant Redis

    Admin->>AdminAPI: PUT /api/admin/auth-config {...}
    Note over AdminAPI: RBAC 守卫(/system/auth)
    AdminAPI->>Svc: updateAuthConfig
    Note over Svc: email 主登录恒开；OTP 数值区间校验
    alt 数值越界（ttl>30 / attempts<3 等）
        Svc-->>AdminAPI: CONFIG_OUT_OF_RANGE → 422 {40002}（EDGE-019）
    else 合法
        Svc->>DB: UPDATE AuthConfig
        Svc->>DB: INSERT OperationLog(action=认证配置变更, changes)
        Svc->>Redis: @CacheInvalidate store:authconfig（消费端登录页即读新配置）
        Svc-->>AdminAPI: AuthConfig
    end
    AdminAPI-->>Admin: 结果
```

---

## FLOW-14: 新设备登录通知

```mermaid
sequenceDiagram
    participant Svc
    participant DB
    participant SMTP

    Note over Svc: 登录成功且 is_new_device=true（FLOW-02/03 触发）
    Svc->>SMTP: send(template=new_device, 含设备/IP/时间 + 一键登出链接→signout_others) %% FLOW-15
    SMTP-->>Svc: accepted
    Svc->>DB: UPDATE LoginHistory.notified=true
```

---

## FLOW-15: 邮件发送重试（BE-DIM-5 SMTP 降级）

```mermaid
sequenceDiagram
    participant Svc
    participant SMTP

    Svc->>SMTP: send(template, locale, payload)
    alt 发送成功
        SMTP-->>Svc: accepted
    else 临时失败（连接/超时）
        loop 有限重试（默认 3 次，指数退避）
            Svc->>SMTP: retry
        end
        alt 仍失败
            Svc->>Svc: 记录告警日志 + 标记失败
            Note over Svc: 不阻塞主流程；OTP 场景提示用户重试发送（FLOW-01）
        end
    end
    Note over Svc: 沙箱无网络时切本地 stub（decision 5/15）
```

---

## FLOW-16: 数据保留清理（每日定时，BE-DIM-7 合规）

```mermaid
sequenceDiagram
    participant Sched
    participant DB

    Note over Sched: 每日定时任务
    Sched->>DB: DELETE OtpCode WHERE status in(consumed,expired,locked) AND created_at < now-24h
    Sched->>DB: DELETE UserSession WHERE status=revoked AND created_at < now-30d
    Sched->>DB: DELETE LoginHistory WHERE created_at < now-1y
    Sched->>DB: UPDATE User 匿名化 WHERE status=deleted AND deleted_at < now-30d（见 FLOW-08）
    Note over Sched,DB: OperationLog 保留 1–3 年，按合规配置清理；注销不删（EDGE-026）
```

---

## FLOW-17: 操作审计写入（横切 AOP，BE-DIM-7）

```mermaid
sequenceDiagram
    participant AdminAPI
    participant AOP as 审计切面
    participant Svc
    participant DB

    AdminAPI->>AOP: 关键操作方法调用（创建/编辑/删除/禁用/重置/权限变更/强制下线/配置变更）
    AOP->>Svc: 捕获 before 快照
    Svc->>DB: 执行业务变更
    AOP->>DB: INSERT OperationLog(operator, action, target, ip, user_agent, changes={before,after})
    Note over AOP,DB: 系统自动归并由 Svc 直接写（operator_name=系统）；日志只读不可删（无 delete 接口 EDGE-018）
```

---

## 检查清单

- [x] 所有核心业务流程都有数据流图（17 个 FLOW 覆盖全部 34 FUNC 关键场景）
- [x] 数据流图包含正常路径和异常路径
- [x] 参与者命名清晰（User/Admin/StoreAPI/AdminAPI/Svc/Redis/DB/SMTP/OIDC/Sched/AOP）
- [x] 消息描述具体（含端点、错误码、事务边界）
- [x] 数据流与接口契约一致（端点与 openapi 一一对应）
- [x] 逐条响应 decision.md 后端关键决策（BE-DIM-4/5/6/7/8，见映射表）
