---
unit_id: unit_backend
unit_name: identity-auth-fullstack Spring Boot 多模块后端
change_name: identity-auth-fullstack
implementer: pd:l3_implementer
codebase_path: /Volumes/MAC/workspace/dreamy/backend
package_root: com.dreamy.identity
generated_at: "2026-05-31T23:30:00Z"
status: implemented
compile:
  command: "JAVA_HOME=graalvm-jdk-25.0.2 ./gradlew compileJava"
  result: SUCCESS
  jdk: "GraalVM 25.0.2+10.1"
  gradle: "9.3.1 (wrapper, aliyun mirror)"
  spring_boot: "3.5.0"
  mybatis_plus: "3.5.10.1 (+ jsqlparser)"
  jetcache: "2.7.7"
  jjwt: "0.12.6"
  mapstruct: "1.6.3"
unit_tests:
  command: "./gradlew :common:test"
  result: SUCCESS
  total: 23
  passed: 23
  failed: 0
coverage:
  operations: "36/36 (store 14 + admin 22)"
  entities: "13/13"
  mappers: "13/13"
  error_codes: "25/25"
  flows: "17/17 (FLOW-01~17)"
  exceptions: "EX-01~30 全覆盖 (30 异常类→25 码)"
  edge_cases: "26/26 (EDGE-001~026)"
  func: "34/34 (FUNC-001~034)"
  coverage_percent: 100.0
---

# Traceability Map — identity-auth-fullstack 后端 (unit_backend)

> L2 设计覆盖率验证：requirements-traceability-matrix.yml coverage_percent=100.0（68 项全覆盖，无 user_approved_skips）。
> 实现前需求验证通过，开始编码。

## 1. 实体层（13/13，对照 identity-ddl.sql）

| constraint_id | status | impl_location | description |
|---|---|---|---|
| ENTITY-user | implemented | common/.../repository/entity/UserEntity.java:18-65 | 表 `user` 聚合根，CHAR(36) 主键 @TableId(INPUT)，@Version 乐观锁，status/tier CHECK 枚举对齐 |
| ENTITY-user_identity | implemented | common/.../repository/entity/UserIdentityEntity.java:14-58 | 表 user_identity，provider/provider_uid/is_primary/relay_valid 字段对齐 uk_identity_provider_uid |
| ENTITY-otp_code | implemented | common/.../repository/entity/OtpCodeEntity.java:16-54 | 表 otp_code，code_hash 仅存哈希，@Version 防并发绕过 attempts |
| ENTITY-user_session | implemented | common/.../repository/entity/UserSessionEntity.java:14-62 | 表 user_session，token_id/refresh_token_id，@Version 滑动续期 |
| ENTITY-login_history | implemented | common/.../repository/entity/LoginHistoryEntity.java:14-50 | 表 login_history 追加型，user_id 弱引用可空，notified 唯一可更新 |
| ENTITY-role | implemented | common/.../repository/entity/RoleEntity.java:15-40 | 表 role，is_locked 超管保护，@Version |
| ENTITY-admin_user | implemented | common/.../repository/entity/AdminUserEntity.java:15-52 | 表 admin_user，password_hash BCrypt，@Version |
| ENTITY-permission | implemented | common/.../repository/entity/PermissionEntity.java:15-32 | 表 permission，业务主键 `key`，`group`/`key` 保留字反引号映射 |
| ENTITY-role_permission | implemented | common/.../repository/entity/RolePermissionEntity.java:13-22 | 表 role_permission 复合主键 (role_id,permission_key) |
| ENTITY-admin_session | implemented | common/.../repository/entity/AdminSessionEntity.java:14-42 | 表 admin_session，access 8h 无 refresh |
| ENTITY-operation_log | implemented | common/.../repository/entity/OperationLogEntity.java:14-46 | 表 operation_log 追加只读，operator_id 弱引用，changes JSON |
| ENTITY-auth_config | implemented | common/.../repository/entity/AuthConfigEntity.java:15-58 | 表 auth_config 单例 id=1，区间 CHECK 字段 |
| ENTITY-email_template | implemented | common/.../repository/entity/EmailTemplateEntity.java:14-38 | 表 email_template 三语×4类，uk_template_code_locale |
| schema.sql | implemented | common/src/main/resources/db/schema.sql | 完整 13 表 DDL（复用 identity-ddl.sql 权威基准） |

## 2. Repository 方法（RM-001~120）

| constraint_id | status | impl_location | description |
|---|---|---|---|
| RM-001/003/004 | implemented | repository/mapper/UserMapper.java (BaseMapper) | findById/insert/updateById(@version) |
| RM-002 | implemented | repository/mapper/UserMapper.java:21-22 | findByEmailActive（status≠anonymized） |
| RM-005 | implemented | repository/mapper/UserMapper.java:25-26 | findDeletedBefore（idx_user_status_deleted_at，FLOW-16） |
| RM-006/018 | implemented | domain/service/RetentionScheduler.java:139-165 | anonymize + anonymizeByUserId 级联（RI-004） |
| RM-007 | implemented | domain/service/UserOpsService.java:38-52 | pageByFilter（status/tier/emailLike） |
| RM-010 | implemented | domain/service/MergeService.java:96-102 | findByProviderUid（幂等核心 QP-002） |
| RM-011 | implemented | domain/service/IdentityService.java:140-144 | listByUserId |
| RM-012 | implemented | domain/service/IdentityService.java:289-294 | countConnected（min_methods R2） |
| RM-014/015/016 | implemented | domain/service/IdentityService.java:158-210 | insert/updateConnected/setPrimary（事务迁移） |
| RM-017 | implemented | domain/service/RetentionScheduler.java:155-163 | markRelayInvalid（匿名化级联含 relay 清理） |
| RM-020 | implemented | repository/mapper/OtpCodeMapper.java:18-19 | lockPendingByEmail（SELECT FOR UPDATE，TX-001） |
| RM-021 | implemented | domain/service/OtpService.java:95-100 | expireAllPending（发码前失效旧码） |
| RM-022/023/024 | implemented | domain/service/OtpService.java:103-120,174-185 | insert/incrementAttempt(@version)/updateStatus |
| RM-025 | implemented | domain/service/RetentionScheduler.java:88-94 | deleteConsumedBefore（24h） |
| RM-030 | implemented | domain/service/SessionService.java:155-159 | findByRefreshTokenId（FLOW-04） |
| RM-031 | implemented | domain/service/SessionService.java (LambdaQuery tokenId) | findByTokenId |
| RM-032 | implemented | domain/service/SessionService.java:178-184 | listActiveByUserId |
| RM-033/034 | implemented | domain/service/SessionService.java:78-118,165-176 | insert/slideExpiry(@version FLOW-04) |
| RM-035/036 | implemented | domain/service/SessionService.java:191-220 | revokeById/revokeAllByUserId(exceptJti) |
| RM-037 | implemented | domain/service/RetentionScheduler.java:97-103 | deleteRevokedBefore（30d） |
| RM-040 | implemented | domain/service/SessionService.java:121-135 | login_history insert（success/failed） |
| RM-041 | implemented | domain/service/SessionService.java:60-70 | existsDeviceForUser（is_new_device 判定） |
| RM-042 | implemented | domain/service/SessionService.java:240-245 | markNotified（FLOW-14） |
| RM-043 | implemented | domain/service/RetentionScheduler.java:115-120 | deleteBefore（1y） |
| RM-050 | implemented | domain/service/AdminService.java:135-139 | findByEmail（FLOW-09 uk_admin_email） |
| RM-051 | implemented | domain/service/AdminService.java (insert/update/updateStatus) | insert/updateProfile/updatePasswordHash/updateStatus(@version) |
| RM-052 | implemented | domain/service/RoleService.java:80-84 | countByRoleId（删角色前 ROLE_IN_USE） |
| RM-053 | implemented | domain/service/AdminService.java:142-153 | pageByFilter |
| RM-060/061 | implemented | domain/service/RoleService.java:58-66,176-181 | findById/listAll/insert/updateName/deleteById/existsByName |
| RM-070 | implemented | repository/mapper/RolePermissionMapper.java:20-21 | listKeysByRoleId（FLOW-09） |
| RM-071 | implemented | domain/service/RoleService.java:127-148 | replaceAll（DELETE+批量INSERT TX-004 全量重写） |
| RM-080 | implemented | domain/service/RoleService.java:91-95 | listAll（22 项字典 by group） |
| RM-090 | implemented | domain/service/AdminService.java:113-127,191-200 | insert/findByTokenId/revokeById/revokeAllByAdminId |
| RM-091 | implemented | domain/service/RetentionScheduler.java:106-112 | admin_session revoked 30d（[INFERRED]） |
| RM-100 | implemented | domain/service/AuditService.java:30-43 | insert（无 update/delete EDGE-018） |
| RM-101 | implemented | domain/service/AuditService.java:46-49 | pageByFilter（倒序 idx_oplog_created） |
| RM-102 | implemented | domain/service/AuditService.java:52-55 | streamForExport |
| RM-110 | implemented | domain/service/AuthConfigService.java:33-41 | getSingleton（id=1，两级缓存） |
| RM-111 | implemented | domain/service/AuthConfigService.java:48-56 | update（CHECK+应用层双校验） |
| RM-120 | implemented | infra/mail/EmailTemplateRenderer.java:38-44 | findByCodeLocale（缺失回退默认 locale） |

## 3. API 操作（36/36：store 14 + admin 22）

| constraint_id | status | impl_location | description |
|---|---|---|---|
| L1-API-sendOtp | implemented | store/.../StoreAuthController.java:60-68 | FLOW-01 V-001/002 STEP-01~07 |
| L1-API-verifyOtp | implemented | store/.../StoreAuthController.java:71-77 + OtpService.verifyOtp | FLOW-02 单事务行锁 V-003/004 STEP-01~10 |
| L1-API-oidcCallback | implemented | store/.../StoreAuthController.java:80-89 + IdentityService.oidcLogin | FLOW-03 V-005~007 归并单事务 |
| L1-API-refreshToken | implemented | store/.../StoreAuthController.java:92-103 | FLOW-04 滑动续期 |
| L1-API-getStoreAuthConfig | implemented | store/.../StoreAuthController.java:106-114 | FUNC-003 登录方式开关 |
| L1-API-getProfile | implemented | store/.../AccountController.java:50-55 | FUNC-007 缓存 store:user |
| L1-API-listIdentities | implemented | store/.../AccountController.java:58-64 | FUNC-010 缓存 store:identities |
| L1-API-bindIdentity | implemented | store/.../AccountController.java:67-75 | FLOW-05 FUNC-008 40903 |
| L1-API-unbindIdentity | implemented | store/.../AccountController.java:78-83 | FLOW-05 FUNC-009 40304/40305 R2 |
| L1-API-changePrimaryEmail | implemented | store/.../AccountController.java:86-92 | FLOW-06 FUNC-026 40901 TX-003 |
| L1-API-listSessions | implemented | store/.../AccountController.java:95-107 | FUNC-011/013 is_current(MAP-003) |
| L1-API-revokeSession | implemented | store/.../AccountController.java:110-119 | FLOW-07 FUNC-012 40300 |
| L1-API-revokeOtherSessions | implemented | store/.../AccountController.java:122-127 | FLOW-07 FUNC-012 |
| L1-API-deleteAccount | implemented | store/.../AccountController.java:130-137 | FLOW-08 FUNC-027 TX-006 |
| L1-API-adminLogin | implemented | admin/.../AdminAuthController.java:43-58 | FLOW-09 FUNC-014 40103/40302 |
| L1-API-adminLogout | implemented | admin/.../AdminAuthController.java:61-65 | 撤销当前 jti |
| L1-API-adminMe | implemented | admin/.../AdminAuthController.java:68-80 | FUNC-021 守卫数据源 + 超管短路 |
| L1-API-listAdmins | implemented | admin/.../AdminAuthController.java:83-96 | RBAC /system/admins 分页 |
| L1-API-createAdmin | implemented | admin/.../AdminAuthController.java:99-106 | FLOW-10 FUNC-015 40901 |
| L1-API-updateAdmin | implemented | admin/.../AdminAuthController.java:109-118 | FLOW-10 FUNC-016 40306 |
| L1-API-deleteAdmin | implemented | admin/.../AdminAuthController.java:121-126 | FLOW-10 FUNC-017 40307/40306 |
| L1-API-toggleAdminStatus | implemented | admin/.../AdminAuthController.java:129-136 | EDGE-014 40306 级联 revoke |
| L1-API-resetAdminPassword | implemented | admin/.../AdminAuthController.java:139-144 | CV-004 ≥6 |
| L1-API-listRoles | implemented | admin/.../RoleController.java:33-37 | MAP-005 member_count+keys |
| L1-API-createRole | implemented | admin/.../RoleController.java:41-46 | FUNC-018 DR-06 重名 40000 |
| L1-API-updateRole | implemented | admin/.../RoleController.java:49-55 | FLOW-11 FUNC-018/019 40308 TX-004 |
| L1-API-deleteRole | implemented | admin/.../RoleController.java:58-63 | FLOW-11 FUNC-020 40308/40904 |
| L1-API-listPermissions | implemented | admin/.../RoleController.java:66-71 | RM-080 22 项 |
| L1-API-listUsers | implemented | admin/.../UserOpsController.java:34-44 | RM-007 分页筛选 |
| L1-API-getUserDetail | implemented | admin/.../UserOpsController.java:47-60 | NP-001 防 N+1 批量查 |
| L1-API-toggleUserStatus | implemented | admin/.../UserOpsController.java:63-69 | FLOW-12 FUNC-022 TX-005 |
| L1-API-forceLogoutUserSessions | implemented | admin/.../UserOpsController.java:72-78 | FLOW-12 FUNC-022 EDGE-023 |
| L1-API-getAuthConfig | implemented | admin/.../AuthConfigController.java:38-41 | AuthSettings 页 |
| L1-API-updateAuthConfig | implemented | admin/.../AuthConfigController.java:45-49 | FLOW-13 FUNC-023 CV-002 40002 @CacheInvalidate |
| L1-API-listOperationLogs | implemented | admin/.../AuthConfigController.java:52-66 | FUNC-024 倒序分页只读 |
| L1-API-exportOperationLogs | implemented | admin/.../AuthConfigController.java:69-90 | CSV 流式 RM-102 |

## 4. DTO 映射（MAP-001~008，MapStruct）

| constraint_id | status | impl_location | description |
|---|---|---|---|
| MAP-001 | implemented | dto/mapper/IdentityDtoMapper.java:36 + dto/UserProfileDTO.java | User→UserProfileDTO，无密码字段，匿名化态 email=null |
| MAP-002 | implemented | dto/mapper/IdentityDtoMapper.java:39 + dto/IdentityDTO.java | UserIdentity→IdentityDTO，隐藏 provider_uid |
| MAP-003 | implemented | dto/mapper/IdentityDtoMapper.java:42-43 + AccountController.java:98-105 | UserSession→SessionDTO，is_current 据 jti 补，隐藏 token_id |
| MAP-004 | implemented | dto/mapper/IdentityDtoMapper.java:46-47 | AdminUser→AdminDTO，隐藏 password_hash，role_name 补 |
| MAP-005 | implemented | dto/RoleDTO.java + RoleController.java:73-78 | Role→RoleDTO，member_count+permission_keys |
| MAP-006 | implemented | dto/mapper/IdentityDtoMapper.java:50 + dto/OperationLogDTO.java | OperationLog→LogDTO，changes JSON 原样 |
| MAP-007 | implemented | app/.../application.yml (jackson write-dates-as-timestamps:false) + OffsetDateTime | 时间 ISO8601 UTC 边界转换 |
| MAP-008 | implemented | 全 entity enum 字段为 String（与 CHECK 取值一致） | enum↔字符串 |

> IMP-06：MapStruct 实现，禁止手写 getter/setter 逐字段赋值（IdentityDtoMapper @Mapper(componentModel="spring")）。

## 5. 事务边界（TX-001~006，EC-001/002）

| constraint_id | status | impl_location | description |
|---|---|---|---|
| TX-001 | implemented | OtpService.java:140-172 (@Transactional) + SessionService.openStoreSession afterCommit | verifyOtp 行锁 SELECT FOR UPDATE → attempts/status → 归并 → session+history → 提交后写 Redis |
| TX-002 | implemented | MergeService.java:50-94 (@Transactional) | oidcCallback 归并单事务 INSERT identity + operation_log(账户合并) |
| TX-003 | implemented | IdentityService.java:218-280 (@Transactional) | changePrimaryEmail 单事务迁移 is_primary 恒一个 |
| TX-004 | implemented | RoleService.java:106-150 (@Transactional) | updateRole DELETE+批量 INSERT role_permission 原子 |
| TX-005 | implemented | UserOpsService.java:88-99 (@Transactional) + SessionService.revokeAll | forceLogout/toggleUserStatus revoke + Redis 单级失效 |
| TX-006 | implemented | IdentityService.java:286-310 (@Transactional) | deleteAccount user.deleted + 全 session revoked + 清 redis |
| EC-001 | implemented | UserEntity/OtpCodeEntity/UserSessionEntity @Version + MyBatisPlusConfig OptimisticLockerInnerInterceptor | 乐观锁冲突 |
| EC-002 | implemented | SessionValidityCache.java:35-42,55-61 | Redis 失效失败不回滚 DB，兜底 TTL30s 收敛 |

## 6. 缓存（JetCache 分级，BE-DIM-8）

| constraint_id | status | impl_location | description |
|---|---|---|---|
| CACHE-session-valid | implemented | infra/SessionValidityCache.java:1-62 | store:session:valid:{tokenId} 仅 Redis 单级 TTL30s（QP-003/EDGE-023） |
| CACHE-authconfig | implemented | AuthConfigService.java:34-41 @Cached BOTH 600s | store:authconfig 两级，FLOW-13 @CacheInvalidate |
| CACHE-user | implemented | IdentityService.java:115-122 @Cached BOTH 300s | store:user:{userId}，FLOW-08/12 失效 |
| CACHE-identities | implemented | IdentityService.java:126-133 @Cached BOTH 300s | store:identities:{userId}，FLOW-05/06 失效 |
| CACHE-invalidate | implemented | IdentityService/UserOpsService @CacheInvalidate 各写操作 | write_invalidate_rule 事务提交后失效 |

## 7. 频控/限流（rate_limit，REQ-005）

| constraint_id | status | impl_location | description |
|---|---|---|---|
| RL-resend | implemented | infra/OtpRateLimiter.java:33-43 | otp:resend:{email} 命中 42901 + remaining_resend_seconds |
| RL-email-h/d | implemented | infra/OtpRateLimiter.java:46-54 | otp:count:email:{email}:h/:d 超阈 42902 |
| RL-ip-h | implemented | infra/OtpRateLimiter.java:46-54 | otp:count:ip:{ip}:h 超阈 42902 |
| RL-record | implemented | infra/OtpRateLimiter.java:57-62 | recordSent 置窗口计数 + TTL（窗口到期自动清零 REQ-005-07） |

## 8. 安全 / 双 JWT 隔离（BE-DIM-6）

| constraint_id | status | impl_location | description |
|---|---|---|---|
| JWT-store | implemented | security/JwtTokenProvider.java:62-103 | access 2h + refresh 30d，独立 STORE_JWT_SECRET，claims typ=store |
| JWT-admin | implemented | security/JwtTokenProvider.java:120-137 | access 8h 无 refresh，独立 ADMIN_JWT_SECRET，claims typ=admin |
| JWT-cross-use | implemented | JwtTokenProvider.java:155-175 + StoreJwtFilter/AdminJwtFilter | 跨端误用 40100（EDGE-024，requireType 校验 typ） |
| FILTER-store | implemented | store/filter/StoreJwtFilter.java:1-120 | /api/store/* 前缀 + 公开端点白名单 + Accept-Language |
| FILTER-admin | implemented | admin/filter/AdminJwtFilter.java:1-95 | /api/admin/* 前缀 + 固定 zh + RBAC 数据源 |
| BCRYPT | implemented | config/CommonConfig.java:36-38 | BCryptPasswordEncoder（CV-004 密码/OTP 哈希） |

## 9. RBAC（菜单级，21 permission keys）

| constraint_id | status | impl_location | description |
|---|---|---|---|
| RBAC-super-shortcircuit | implemented | RoleService.java:69-77 effectivePermissionKeys | 超管 is_locked → 全 permission key 短路（RISK-03 应用层方案） |
| RBAC-permission-seed | implemented | common/src/main/resources/db/schema.sql + seed-supplement.sql | permission 22 项菜单 key（RISK-02，来源 portal-admin router） |
| RBAC-role-locked | implemented | RoleService.java:110-113,156-159 | is_locked 角色不可改权限/删 40308 |

## 10. 错误处理（EX-01~30 → 25 码，PATH-01~04，i18n）

| constraint_id | status | impl_location | description |
|---|---|---|---|
| ERR-codes | implemented | error/ErrorCode.java:1-72 | 25 错误码枚举（数字码+HTTP 状态+i18n key） |
| EX-01~25 | implemented | error/BizException.java + 各 Service throw BizException(ErrorCode,details) | 表示层/应用层/领域异常 |
| EX-26~29 | implemented | error/InfraException.java + RealOidcVerifier/SmtpMailSender | 基础设施异常（50001/50002/50201/50401） |
| EX-30 | implemented | error/GlobalExceptionHandler.java:56-60 | 兜底 50000 不暴露细节（PATH-04） |
| PATH-01 | implemented | GlobalExceptionHandler.java:40-44 | 领域/应用异常透传映射 {code,message,details} |
| PATH-02 | implemented | GlobalExceptionHandler.java:33-37 + InfraException | 基础设施异常不泄漏堆栈 |
| PATH-03 | implemented | StoreJwtFilter/AdminJwtFilter writeUnauthorized | 鉴权过滤器直接短路 401 |
| ERR-i18n | implemented | i18n/MessageResolver.java + messages_{en,es,fr,zh}.properties | store en/es/fr by Accept-Language，admin zh |
| ERR-25-must-test | implemented | OtpServiceTest/MergeServiceTest/JwtTokenProviderTest/RoleAndAdminServiceTest/AuthConfigServiceTest | 关键错误码单元测试覆盖（40101/41001/41002/40902/40100/40306/40308/40904/40002 等） |

## 11. 降级与重试（RT/CB/DG）

| constraint_id | status | impl_location | description |
|---|---|---|---|
| RT-001 | implemented | infra/mail/SmtpMailSender.java:38-58 | SMTP 3 次指数退避 1s/2s/4s，仍失败 50002 |
| RT-002 | implemented | infra/oidc/RealOidcVerifier.java:52-72 | OIDC 超时 5s 重试 1 次 |
| CB-001 | implemented | infra/oidc/RealOidcVerifier.java:74-82 | OIDC 熔断窗口快速失败 50201 |
| DG-001 | implemented | RealOidcVerifier 抛 50201/50401（前端切 OTP） | OIDC 不可用降级引导 OTP |
| DG-002 | implemented | StubMailSender/StubOidcVerifier @ConditionalOnProperty stub | 沙箱默认 stub（identity.mail.mode/oidc.mode=stub） |
| DG-003 | implemented | SessionValidityCache.java:45-52 | Redis 不可用降级（返回 false 由调用方查 DB，记 ERROR 告警，[INFERRED] CFL-18） |

## 12. 数据保留与匿名化（FLOW-16，FUNC-032/033）

| constraint_id | status | impl_location | description |
|---|---|---|---|
| FLOW-16-otp | implemented | RetentionScheduler.java:88-94 | OTP 24h 清 |
| FLOW-16-session | implemented | RetentionScheduler.java:97-103 | user_session revoked 30d 清 |
| FLOW-16-admin-session | implemented | RetentionScheduler.java:106-112 | admin_session revoked 30d 清 |
| FLOW-16-login | implemented | RetentionScheduler.java:115-120 | login_history 1y 清 |
| FUNC-033-anonymize | implemented | RetentionScheduler.java:128-165 | 注销 30d 宽限后不可逆匿名化 PII + 级联 identity（RI-004） |
| EDGE-026 | implemented | operation_log 无 delete 接口（AuditService 仅 insert/query） | 注销不删审计日志 |

## 13. 审计（FLOW-17，BE-DIM-7）

| constraint_id | status | impl_location | description |
|---|---|---|---|
| FLOW-17-aop | implemented | admin/aspect/AuditAspect.java:1-75 + AuditLog.java | @AuditLog 注解 AOP @Around 写 operation_log |
| AUDIT-15-actions | implemented | 各 admin Controller @AuditLog(action=...) | 15 种 action 枚举（登录/创建管理员/.../认证配置变更/账户合并/强制下线） |
| AUDIT-login-history | implemented | SessionService.java:78-135 | LoginHistory success/failed 落库（FLOW-02/03） |

## 14. 脱敏（redaction，强制）

| constraint_id | status | impl_location | description |
|---|---|---|---|
| REDACT-otp | implemented | OtpService（仅存 code_hash）+ StubMailSender body suppressed | OTP 明文不记录 |
| REDACT-password | implemented | AdminService/CommonConfig BCrypt（仅 password_hash） | 密码不记录 |
| REDACT-token | implemented | SessionValidityCache/日志仅记 jti/tokenId | JWT/refresh 不记原文 |
| REDACT-email | implemented | StubMailSender.java:35-41 mask(j***@email) | 邮箱掩码 |
| REDACT-5xx | implemented | GlobalExceptionHandler.java:56-60 | 5xx 不暴露 SQL/堆栈 |

## 15. 测试覆盖（test_coverage，TC-UNIT-*）

```yaml
test_coverage:
  - tc_id: TC-UNIT-001~005
    test_file: common/src/test/java/com/dreamy/identity/common/domain/service/OtpServiceTest.java
    test_method: verifyOtp_correct/wrongCode/maxAttempts/expired/noPending
    constraint_ids: [UT-01, FLOW-02, "40101", "41001", "41002", STEP-01~03]
  - tc_id: TC-UNIT-010~013
    test_file: common/src/test/java/com/dreamy/identity/common/domain/service/MergeServiceTest.java
    test_method: resolveOrMerge_existingProviderUid/sameEmailVerified/sameEmailUnverified/noExisting
    constraint_ids: [UT-04, DR-02, "40902", FUNC-025, EDGE-017, TX-002]
  - tc_id: TC-UNIT-020~025
    test_file: common/src/test/java/com/dreamy/identity/common/security/JwtTokenProviderTest.java
    test_method: issueAndParseStore/Refresh/Admin/crossUse/tampered
    constraint_ids: [UT-06, JWT-store, JWT-admin, EDGE-024, "40100"]
  - tc_id: TC-UNIT-030~033
    test_file: common/src/test/java/com/dreamy/identity/common/domain/service/RoleAndAdminServiceTest.java
    test_method: updateRole_locked/deleteRole_locked/deleteRole_hasMembers/deleteAdmin_self
    constraint_ids: [UT-05, UT-07, "40308", "40904", "40307", EDGE-013/014/015]
  - tc_id: TC-UNIT-040~043
    test_file: common/src/test/java/com/dreamy/identity/common/domain/service/AuthConfigServiceTest.java
    test_method: validateRange_invalidLength/ttlZero/resendTooLow/valid
    constraint_ids: [UT-06, CV-002, "40002", EDGE-019]
```

> 集成测试（IT-01~09 Testcontainers MySQL/Redis）、契约/API/韧性/NBT 测试由 pd:l3_test_engineer 后续阶段实现，不在本单元范围。

## 16. 验收核对（FUNC-* 对照 acceptance-baseline）

全部 36 操作 status=implemented，impl_location 指向真实代码（非 mock/占位符）。后端无前端 mock 问题（前端由 unit_frontend 负责）。无 uncovered_acceptance 条目。

uncovered_acceptance: []

## 17. 风险/后续复核项（传递）

- RISK-02: permission 22 项 key 已按 portal-admin router/menu.js 补全为 21 个菜单 key（路由表实际 21 项菜单级路由，详情页继承父级 permKey）。seed-supplement.sql 提供完整种子。
- RISK-03: 超管全权限采用应用层短路方案（RoleService.effectivePermissionKeys is_locked→全 key）。
- CFL-18 (DG-003): Redis 不可用降级查 DB 已实现告警日志，强制下线即时性在 Redis 恢复前有窗口，需运维监控。
- 集成/运行时验证：编译通过 + 单元测试 23/23 通过；完整启动需 MySQL/Redis（沙箱用 Testcontainers，由 test_engineer 阶段执行）。

## 18. L3 审查 blocking 修复轮（5 类真实安全/功能/性能 blocking）

> 修复轮：基于上一轮代码增量修复，未从头重写。`./gradlew clean compileJava test` 全绿（61 活跃测试通过 + 4 IT @Disabled 无 Docker，0 失败 0 错误）。
> 编译命令: `JAVA_HOME=graalvm-jdk-25.0.2 ./gradlew clean compileJava test` → BUILD SUCCESSFUL。

### BLOCKER-1（安全）：会话撤销/强制下线在请求链路实际生效（EDGE-023 即时失效）

| constraint_id | status | impl_location | description |
|---|---|---|---|
| BLK-FIX-1-validator | implemented | common/.../infra/SessionValidator.java:1-78 | 新增请求链路会话校验器：Redis 命中即有效（QP-003），未命中降级查 DB session.status='active' 并回填 Redis（DG-003 自愈） |
| BLK-FIX-1-admincache | implemented | common/.../infra/AdminSessionValidityCache.java:1-58 | 新增 admin 会话有效性缓存（admin:session:valid:{tokenId} 单级 TTL30s），复用 SessionValidityCache 模式 |
| BLK-FIX-1-storefilter | implemented | store/.../filter/StoreJwtFilter.java:71-91 | 解析 token 后调用 sessionValidator.isStoreSessionValid(jti)，无效→401（40100）；激活原死代码 SessionValidityCache.isValid |
| BLK-FIX-1-adminfilter | implemented | admin/.../filter/AdminJwtFilter.java:64-91 | 解析 token 后调用 sessionValidator.isAdminSessionValid(jti)，无效→401（40100） |
| BLK-FIX-1-adminlogin | implemented | common/.../domain/service/AdminService.java:90-118 | login afterCommit markValid(jti)；logout afterCommit invalidate(jti) |
| BLK-FIX-1-admindisable | implemented | common/.../domain/service/AdminService.java:230-248 | 禁用管理员级联撤销 afterCommit invalidate 每个 active 会话 jti（EDGE-014） |
| BLK-FIX-1-storerevoke | implemented | common/.../domain/service/SessionService.java:114-115,162-163,235 | openStoreSession markValid；refresh invalidate旧+markValid新；revokeEntity invalidate（撤销/登出/强制下线/禁用全经此路径） |

### BLOCKER-2（安全）：admin 服务端 RBAC（方案A 注解+AOP）

| constraint_id | status | impl_location | description |
|---|---|---|---|
| BLK-FIX-2-annotation | implemented | admin/.../aspect/RequirePermission.java:1-40 | 新增 @RequirePermission("menuKey") 方法注解 |
| BLK-FIX-2-aspect | implemented | admin/.../aspect/PermissionAspect.java:1-50 | @Before 切面校验 AuthContext.permissionKeys 含 key，缺失→BizException(FORBIDDEN/40300)；超管全 22 key 自然通过 |
| BLK-FIX-2-admins | implemented | admin/.../controller/AdminAuthController.java:64-117 | listAdmins/createAdmin/updateAdmin/deleteAdmin/toggleStatus/resetPassword 全标 /system/admins |
| BLK-FIX-2-roles | implemented | admin/.../controller/RoleController.java:41-59 | createRole/updateRole/deleteRole 标 /system/roles |
| BLK-FIX-2-auth | implemented | admin/.../controller/AuthConfigController.java:41-72 | auth-config 标 /system/auth；operation-logs+export 标 /system/logs |
| BLK-FIX-2-customers | implemented | admin/.../controller/UserOpsController.java:28-60 | listUsers/getUserDetail/toggleStatus/forceLogout 全标 /customers |

### BLOCKER-3（安全）：OIDC JWKS 验签（real 模式）

| constraint_id | status | impl_location | description |
|---|---|---|---|
| BLK-FIX-3-dep | implemented | common/build.gradle:30-31, gradle.properties:10 | 新增 com.nimbusds:nimbus-jose-jwt:9.37.3（阿里云镜像已成功下载 JAR 验证可用） |
| BLK-FIX-3-verify | implemented | common/.../infra/oidc/RealOidcVerifier.java:106-156 | RemoteJWKSet 按 kid 取 RS256 公钥验签 + 校验 iss(google/apple)/aud(client_id)/exp/nonce；验签失败→50201（PATH-02 不泄漏 token）；StubOidcVerifier 保持不变 |
| BLK-FIX-3-cb | implemented | common/.../infra/oidc/RealOidcVerifier.java:67-103 | 保留 RT-002 重试1次/CB-001 熔断/超时→50401/不可达→50201 语义 |

### BLOCKER-4（功能）：绑定/换主邮箱改用纯校验码 verifyCodeOnly

| constraint_id | status | impl_location | description |
|---|---|---|---|
| BLK-FIX-4-method | implemented | common/.../domain/service/OtpService.java:147-205 | 抽出 verifyCodeOnly（仅 STEP-01~03 行锁/过期/hash/attempts/locked，不归并不开会话）；verifyOtp 复用同一 consumeValidCode 私有方法保留全管线 |
| BLK-FIX-4-bind | implemented | common/.../domain/service/IdentityService.java:166-173 | bindIdentity(email 分支) 改调 verifyCodeOnly，不再凭空建 user 致 40903 孤立账户 |
| BLK-FIX-4-changeprimary | implemented | common/.../domain/service/IdentityService.java:259-263 | changePrimaryEmail 改调 verifyCodeOnly，避免新建同 email user 违反 uk_user_email 回滚 |

### BLOCKER-5（性能）：操作日志导出真流式 + 时间窗上限

| constraint_id | status | impl_location | description |
|---|---|---|---|
| BLK-FIX-5-mapper | implemented | common/.../repository/mapper/OperationLogMapper.java:21-46 | 新增 streamByFilter（@Options fetchSize=Integer.MIN_VALUE FORWARD_ONLY + ResultHandler 游标逐行回调，MySQL 行级流式） |
| BLK-FIX-5-service | implemented | common/.../domain/service/AuditService.java:51-90 | streamForExport(@Transactional readOnly) 边查边写 Consumer；enforceExportWindow 强制 from/to 必传且跨度≤92天，越界→40000（RM-102 流式契约） |
| BLK-FIX-5-controller | implemented | admin/.../controller/AuthConfigController.java:74-86 | export 改用 streamForExport 逐行写 CSV，移除原 exportAll 全量 selectList（OOM 根因） |

### 修复轮新增/修改测试

```yaml
test_coverage:
  - tc_id: TC-WEB-STORE-013
    test_file: store/src/test/java/com/dreamy/identity/store/controller/StoreAuthControllerTest.java
    test_method: accountProfile_revokedSession_returns401
    constraint_ids: [BLOCKER-1, EDGE-023, "40100"]
  - tc_id: TC-WEB-ADMIN-009
    test_file: admin/src/test/java/com/dreamy/identity/admin/controller/AdminAuthControllerTest.java
    test_method: createAdmin_withoutPermission_returns403
    constraint_ids: [BLOCKER-2, "40300", "/system/admins"]
  - tc_id: TC-WEB-ADMIN-010
    test_file: admin/src/test/java/com/dreamy/identity/admin/controller/AdminAuthControllerTest.java
    test_method: createAdmin_withPermission_returns201
    constraint_ids: [BLOCKER-2, "/system/admins"]
  - tc_id: TC-WEB-ADMIN-011
    test_file: admin/src/test/java/com/dreamy/identity/admin/controller/AdminAuthControllerTest.java
    test_method: adminMe_revokedSession_returns401
    constraint_ids: [BLOCKER-1, "40100"]
  - tc_id: TC-UNIT-006~008
    test_file: common/src/test/java/com/dreamy/identity/common/domain/service/OtpServiceTest.java
    test_method: verifyCodeOnly_correct_consumesWithoutLoginPipeline/wrongCode_throwsAndNoSession/expired_throwsOtpExpired
    constraint_ids: [BLOCKER-4, "40101", "41001"]
  - tc_id: TC-UNIT-060~062
    test_file: common/src/test/java/com/dreamy/identity/common/domain/service/AuditServiceTest.java
    test_method: streamForExport_missingWindow_rejected/windowTooWide_rejected/validWindow_streamsRowByRow
    constraint_ids: [BLOCKER-5, RM-102, "40000"]
```

### 残留风险（修复轮）

- BLOCKER-3 验签库：nimbus-jose-jwt:9.37.3 已通过阿里云镜像成功下载 JAR 并编译，验签完整实现（完整实现，无遗留标记）。RemoteJWKSet(URL) 构造在 9.37.3 标记 deprecated 但功能正常；real 模式需配置 identity.oidc.{google,apple}.client-id 否则 aud 校验拒绝全部 token（安全默认：未配置即拒）。沙箱默认 stub 模式不受影响。
- BLOCKER-1 DG-003：Redis 不可用时每请求降级查 DB（user_session/admin_session），DB active 命中回填 Redis 自愈。性能在 Redis 故障期下降但保可用，需运维监控（沿用既有风险）。
- BLOCKER-5 流式：streamForExport 用 @Transactional(readOnly) 持有连接确保 MySQL streaming ResultSet 不被提前归还；H2（沙箱测试）不支持 fetchSize=MIN_VALUE 游标语义但 ResultHandler 回调路径已单测覆盖，生产 MySQL 行为以 @Options 为准。
- IT（ForceLogoutIT/OidcMergeIT/OtpConcurrencyIT）仍 @Disabled（无 Docker），解除不在本任务范围；BLOCKER-1 撤销→401 已由 web 切片测试 TC-WEB-STORE-013/TC-WEB-ADMIN-011 覆盖过滤器逻辑。
