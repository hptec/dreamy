# String→Long 主键迁移映射表

> 阶段 D 全链路适配依据。所有标注字段从 String 改为 Long。

## 主键（全部 → Long AUTO_INCREMENT，由 LongAuditableEntity 提供）

| 实体 | 表 | 原主键 | 新主键 | 特殊处理 |
|------|-----|--------|--------|---------|
| UserEntity | user | String id | Long id | - |
| UserIdentityEntity | user_identity | String id | Long id | - |
| RoleEntity | role | String id | Long id | - |
| PermissionEntity | permission | String key | Long id（新增代理主键）| key→perm_code 唯一索引 uk_permission_perm_code |
| RolePermissionEntity | role_permission | 复合(roleId,permissionKey) | Long id（新增代理主键）| (role_id,permission_id) 唯一索引 uk_role_permission |
| AdminUserEntity | admin_user | String id | Long id | - |
| UserSessionEntity | user_session | String id | Long id | - |
| AdminSessionEntity | admin_session | String id | Long id | - |
| OtpCodeEntity | otp_code | String id | Long id | - |
| AuthConfigEntity | auth_config | Integer id | Long id | 单例固定 id=1 |
| EmailTemplateEntity | email_template | String id | Long id | - |
| LoginHistoryEntity | login_history | String id | Long id | - |
| OperationLogEntity | operation_log | String id | Long id | - |

## 外键字段（String → Long）

| 实体.字段 | 引用 | 可空 |
|----------|------|------|
| UserIdentityEntity.userId | user.id | 否 |
| UserSessionEntity.userId | user.id | 否 |
| LoginHistoryEntity.userId | user.id | 是（弱引用） |
| AdminUserEntity.roleId | role.id | 否 |
| AdminSessionEntity.adminId | admin_user.id | 否 |
| RolePermissionEntity.roleId | role.id | 否 |
| RolePermissionEntity.permissionKey → **permissionId** | permission.id | 否（字段改名+改型） |
| OperationLogEntity.operatorId | admin_user.id | 是（弱引用） |

## 时间类型：所有 OffsetDateTime → LocalDateTime（实体+DTO+service+mapper）

## 应用层 IdGenerator：废弃。改由 DB 自增，insert 后 entity.getId() 取回。

## 不变字段（保持 String，业务语义非主键）

- UserIdentityEntity.providerUid / identifier（渠道唯一标识，非 FK）
- PermissionEntity.permCode（原 key，业务码，改唯一索引）
- 所有 tokenId / refreshTokenId（JWT jti，业务字符串非 FK）
- 所有枚举状态字段（status/type/provider/method 等）
