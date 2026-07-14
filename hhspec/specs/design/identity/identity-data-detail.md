# identity 数据层详细设计（L2）

> 角色: l2_data_designer ｜ change: identity-auth-fullstack ｜ domain: identity
> 五部分方法论：Repository 方法(RM) / DTO↔Entity 映射(MAP) / 查询优化(IDX/NP/QP) / 事务边界(TX/EC) / 数据校验(CV/RI)。
> **权威基线**：已产出 `design/data/identity-ddl.sql`（13 张表可运行 DDL）+ `identity-physical-schema.md` + `identity-retention-anonymization.md`。
> 本文为伪代码级，不绑定 MyBatis-Plus 语法；表名/字段名与 DDL 严格一致，不矛盾。

## 0. 复用声明
- 表结构、主键、唯一键、索引、CHECK 约束、种子数据、保留/匿名化方案以上述三份 data/ 产出为准。
- 本文聚焦 Repository 方法签名、转换映射、事务边界、并发与引用完整性策略。

---

## 1. Repository 方法（RM）

### UserRepository
- RM-001 `findById(id) -> User?`
- RM-002 `findByEmailActive(email) -> User?` —— 命中 uk_user_email；status≠anonymized
- RM-003 `insert(User) -> id`
- RM-004 `updateStatus(id, status, deletedAt?)` —— 乐观锁 @version（FLOW-08/12）
- RM-005 `findDeletedBefore(cutoff) -> List<User>` —— idx_user_status_deleted_at（FLOW-16 匿名化）
- RM-006 `anonymize(id)` —— 清 email/name/phone，status=anonymized（不可逆，事务内级联 identity）
- RM-007 `pageByFilter(status?, tier?, emailLike?, page) -> Page<User>` —— Customers 页

### UserIdentityRepository
- RM-010 `findByProviderUid(provider, providerUid) -> UserIdentity?` —— 命中 uk_identity_provider_uid（FLOW-03 最高频，幂等核心）
- RM-011 `listByUserId(userId) -> List<UserIdentity>` —— idx_identity_user_id
- RM-012 `countConnected(userId) -> int` —— min_methods 校验（R2）
- RM-013 `findPrimary(userId) -> UserIdentity?` —— idx_identity_user_primary
- RM-014 `insert(UserIdentity)` / RM-015 `updateConnected(id, bool)` / RM-016 `setPrimary(userId, identityId)`（事务内迁移）
- RM-017 `markRelayInvalid(id)` —— FUNC-029 relay_valid=false（不锁账户）
- RM-018 `anonymizeByUserId(userId)` —— 清 provider_uid/identifier/relay_email

### OtpCodeRepository
- RM-020 `lockPendingByEmail(email) -> OtpCode?` —— `SELECT ... WHERE email=? AND status=pending FOR UPDATE`（FLOW-02 行锁串行）
- RM-021 `expireAllPending(email)` —— 发码前失效旧码（FLOW-01）
- RM-022 `insert(OtpCode)` / RM-023 `incrementAttempt(id)`（@version）/ RM-024 `updateStatus(id, status)`
- RM-025 `deleteConsumedBefore(cutoff)` —— idx_otp_status_created（FLOW-16 24h）

### UserSessionRepository
- RM-030 `findByRefreshTokenId(rid) -> UserSession?` —— idx_session_refresh（FLOW-04）
- RM-031 `findByTokenId(jti) -> UserSession?` —— uk_session_token_id
- RM-032 `listActiveByUserId(userId) -> List` —— idx_session_user_status
- RM-033 `insert(UserSession)` / RM-034 `slideExpiry(id, accessExp, refreshExp)`（@version FLOW-04）
- RM-035 `revokeById(id)` / RM-036 `revokeAllByUserId(userId, exceptJti?)`（FLOW-07/08/12）
- RM-037 `deleteRevokedBefore(cutoff)` —— idx_session_status_created（FLOW-16 30d）

### LoginHistoryRepository（追加型）
- RM-040 `insert(LoginHistory)`（result success/failed）
- RM-041 `existsDeviceForUser(userId, deviceFingerprint) -> bool` —— is_new_device 判定（idx_login_user_created）
- RM-042 `markNotified(id)` —— FLOW-14 唯一可更新字段
- RM-043 `deleteBefore(cutoff)` —— idx_login_created（FLOW-16 1y）

### AdminUserRepository
- RM-050 `findByEmail(email) -> AdminUser?` —— uk_admin_email（FLOW-09）
- RM-051 `insert/updateProfile/updatePasswordHash/updateStatus`（@version）
- RM-052 `countByRoleId(roleId) -> int` —— idx_admin_role（删角色前校验 ROLE_IN_USE）
- RM-053 `pageByFilter(status?, roleId?, page)`

### RoleRepository / RolePermissionRepository
- RM-060 `findById/listAll/insert/updateName/deleteById`
- RM-061 `existsByName(name) -> bool` —— uk_role_name（重名校验）
- RM-070 `listKeysByRoleId(roleId) -> List<String>` —— PK(role_id,...)（FLOW-09 登录加载权限）
- RM-071 `replaceAll(roleId, keys[])` —— 事务内 DELETE+批量 INSERT（FLOW-11 全量重写）

### PermissionRepository
- RM-080 `listAll() -> List<Permission>`（22 项字典，按 group）

### AdminSessionRepository
- RM-090 `insert/findByTokenId/revokeById/revokeAllByAdminId`（禁用级联 FLOW-10）
- RM-091 `deleteRevokedBefore(cutoff)` —— [INFERRED] 30d 清理

### OperationLogRepository（追加只读）
- RM-100 `insert(OperationLog)`（无 update/delete 接口，EDGE-018）
- RM-101 `pageByFilter(action?, operatorId?, from?, to?, page)` —— idx_oplog_created/operator/action（倒序）
- RM-102 `streamForExport(filter)` —— 导出流式

### AuthConfigRepository（单例）
- RM-110 `getSingleton() -> AuthConfig`（id=1）
- RM-111 `update(AuthConfig)`（CHECK + 应用层区间双校验）

### EmailTemplateRepository
- RM-120 `findByCodeLocale(code, locale) -> EmailTemplate?` —— uk_template_code_locale（缺失回退默认 locale）

---

## 2. DTO ↔ Entity 映射（MAP）
- MAP-001 User→UserProfileDTO：snake_case 出参；email 匿名化态返回 null；不含 password 类字段（user 本无密码）
- MAP-002 UserIdentity→IdentityDTO：暴露 {id,provider,identifier,is_primary,verified,hidden_email,relay_valid,last_login_at}；隐藏 provider_uid（防枚举）
- MAP-003 UserSession→SessionDTO：附 is_current(jti 匹配)；隐藏 token_id/refresh_token_id
- MAP-004 AdminUser→AdminDTO：隐藏 password_hash；附 role_name
- MAP-005 Role→RoleDTO：附 member_count + permission_keys
- MAP-006 OperationLog→LogDTO：changes JSON 原样；operator_name 快照
- MAP-007 时间字段统一 OffsetDateTime↔ISO8601 UTC（边界转换）
- MAP-008 enum 字段：Java enum↔字符串（与 CHECK 取值一致）

---

## 3. 查询优化（IDX/NP/QP）
- IDX-* 索引以 identity-physical-schema.md 第 15 节矩阵为准（14/14 高频路径全覆盖）。
- NP-001 防 N+1：getUserDetail 用单次批量查 identities/sessions/login_history（按 user_id IN/= 一次取，不循环）
- NP-002 listUsers 的 identity_count 用聚合子查询或单独批量 count，避免逐行查询
- QP-001 listOperationLogs 倒序分页走 idx_oplog_created；大表建议 keyset 分页（created_at < lastCursor）替代 deep OFFSET（[INFERRED] 长保留表）
- QP-002 OIDC 登录走 uk_identity_provider_uid 唯一索引点查（FLOW-03 热路径）
- QP-003 会话有效性始终以 DB session.status 为授权依据；Redis store:session:valid:{jti}（单级）仅作正缓存提示

---

## 4. 事务边界（TX/EC）
- TX-001 verifyOtp（FLOW-02）：行锁 SELECT FOR UPDATE → attempts/status 更新 → 归并/建号 → session+history → 提交后写 Redis。EC：code 错误也需提交 attempts+1（独立短事务或同事务回滚仅业务异常）
- TX-002 oidcCallback 归并（FLOW-03）：单事务内 INSERT identity 挂既有 user + INSERT operation_log(账户合并)；唯一索引冲突=重复回调→幂等返回既有
- TX-003 changePrimaryEmail（FLOW-06）：单事务迁移 is_primary（旧 false→新 true），保证恒一个 is_primary 不变量
- TX-004 updateRole（FLOW-11）：单事务 DELETE+批量 INSERT role_permission（全量重写原子）
- TX-005 forceLogout/toggleUserStatus（FLOW-12）：单事务 revoke sessions + (禁用时)user status；DB 提交后全集群即时拒绝，并尽力清 Redis 兼容键
- TX-006 deleteAccount（FLOW-08）：单事务 user.deleted + 全 session revoked；提交后清全部 redis 键
- EC-001 乐观锁冲突（@version on user/otp_code/user_session）→ 重试 1 次或抛 409/重新读
- EC-002 Redis 失效失败不回滚 DB并记告警；DB revoked 状态即时拒绝，提示键由 TTL 30s 自然回收

---

## 5. 数据校验与引用完整性（CV/RI）
- CV-001 枚举值落库前校验 ∈ CHECK 取值（双保险）
- CV-002 AuthConfig 区间校验（ttl 1..30 / resend 10..120 / attempts 3..10 / min_methods 1..3 / length∈{4,6,8}）→ 越界 40002
- CV-003 email 长度 ≤255、name ≤80、phone ≤32、role.name ≤40
- CV-004 password 明文 ≥6 位后 BCrypt（仅存 hash）；OTP 仅存 code_hash
- RI-001 逻辑外键：插入 user_identity/user_session 前校验 user 存在；role_permission 前校验 role+permission_key 存在；admin_user.role_id 前校验 role 存在
- RI-002 删 role 前 RM-052 countByRoleId=0（ROLE_IN_USE）
- RI-003 审计弱引用：operation_log.operator_id / login_history.user_id 可空，主体匿名化/删除后保留记录（EDGE-026 正当利益）
- RI-004 匿名化级联：user.anonymize() 事务内级联 user_identity.anonymizeByUserId（不删行，清 PII 字段）

---

## 6. 数据保留（引用既有方案）
保留/清理/匿名化全策略见 `design/data/identity-retention-anonymization.md`：
- otp_code 24h（RM-025）/ user_session revoked 30d（RM-037）/ admin_session 30d（RM-091, [INFERRED]）/ login_history 1y（RM-043）/ operation_log 1-3y 注销不删 / user deleted 30d 宽限后匿名化（RM-005/006）。

## 7. 自检
- [x] 13 实体全部有 Repository 方法且字段/表名与 DDL 一致
- [x] DTO 映射隐藏敏感字段（provider_uid/token_id/password_hash/code_hash）
- [x] 事务边界覆盖全部 5 个事务 FLOW
- [x] 乐观锁列与并发策略对齐 physical-schema
- [x] 逻辑外键引用完整性策略明确（无物理 FK）
- [x] 保留匿名化引用既有方案不重复造
