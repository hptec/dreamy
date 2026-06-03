# 包重组 + Long 迁移契约

> 阶段 C+D 执行依据。所有非实体 Java 文件遵循此契约。

## 一、实体新包路径（旧 → 新）

旧：`com.dreamy.identity.common.repository.entity.XxxEntity`
新（按 domain）：
- user：UserEntity, UserIdentityEntity → `com.dreamy.identity.domain.user.entity`
- role：RoleEntity, PermissionEntity, RolePermissionEntity → `com.dreamy.identity.domain.role.entity`
- admin：AdminUserEntity → `com.dreamy.identity.domain.admin.entity`
- session：UserSessionEntity, AdminSessionEntity → `com.dreamy.identity.domain.session.entity`
- otp：OtpCodeEntity → `com.dreamy.identity.domain.otp.entity`
- authconfig：AuthConfigEntity, EmailTemplateEntity → `com.dreamy.identity.domain.authconfig.entity`
- audit：LoginHistoryEntity, OperationLogEntity → `com.dreamy.identity.domain.audit.entity`

## 二、mapper 新包路径（旧 → 新，按 domain，与 entity 同 domain）

旧：`com.dreamy.identity.common.repository.mapper.XxxMapper`
新：`com.dreamy.identity.domain.<domain>.repository.XxxMapper`
（UserMapper/UserIdentityMapper→user；RoleMapper/PermissionMapper/RolePermissionMapper→role；AdminUserMapper→admin；UserSessionMapper/AdminSessionMapper→session；OtpCodeMapper→otp；AuthConfigMapper/EmailTemplateMapper→authconfig；LoginHistoryMapper/OperationLogMapper→audit）

## 三、service 新包路径（按 domain）

旧：`com.dreamy.identity.common.domain.service.XxxService`
新：`com.dreamy.identity.domain.<domain>.service.XxxService`
映射：
- IdentityService, UserOpsService, MergeService → user
- RoleService → role
- AdminService → admin
- SessionService → session
- OtpService → otp
- AuthConfigService → authconfig
- AuditService, RetentionScheduler → audit
- LoginContext, LoginResult（domain/model）→ `com.dreamy.identity.domain.user.model`

## 四、dto 新包路径

旧 `common.dto.*` → `com.dreamy.identity.dto`（共享 DTO 平铺，跨 domain 复用）
旧 `common.dto.mapper.IdentityDtoMapper` → `com.dreamy.identity.dto.mapper`
admin.dto.* → 保留在 `com.dreamy.identity.controller.pojo`（视图对象，归 controller 层）
PageResult/PageQuery → 删除 PageResult，改用 huihao.page.Paginated（阶段 E）；PageQuery 保留到 `com.dreamy.identity.dto`
ErrorBody → 删除，改用 huihao.web.R（阶段 E）

## 五、其余包平铺到工程根包

- config → `com.dreamy.identity.config`（合并 common/store/admin 的 config）
- error → `com.dreamy.identity.error`
- i18n → `com.dreamy.identity.i18n`
- infra → `com.dreamy.identity.infra`（含 mail/oidc 子包）
- security → `com.dreamy.identity.security`
- util → `com.dreamy.identity.util`
- aspect（原 admin/aspect）→ `com.dreamy.identity.aspect`
- filter（原 store/admin filter）→ `com.dreamy.identity.security`（与 security 合并）
- controller（合并 store/admin controller）→ `com.dreamy.identity.controller`

## 六、类型迁移规则（Long）

1. **方法参数/返回/字段**：所有代表实体主键或外键的 `String id/userId/roleId/adminId/sessionId/operatorId/permissionKey` → `Long`
2. **permissionKey → permissionId**（RolePermission，String→Long）
3. **DTO record**：id 及外键字段 String→Long（SessionDTO/AdminDTO.roleId/UserProfileDTO/OperationLogDTO/IdentityDTO/RoleDTO/LoginHistoryDTO 的 id）。注意 IdentityDTO.identifier 保持 String（非 id）
4. **insert 逻辑**：删除 `entity.setId(IdGenerator.uuid())` 和 `entity.setCreatedAt(...)`/`setUpdatedAt(...)`（基类自动）。insert 后用 `entity.getId()`（已回填 Long）
5. **OffsetDateTime → LocalDateTime**（全部：实体已改，service/dto/mapper 参数同步）。`OffsetDateTime.now(ZoneOffset.UTC)` → `LocalDateTime.now()`
6. **JWT 边界转换**：JwtTokenProvider.issueStoreTokens/issueAdminToken 的 userId/adminId/roleId 参数保持 String（JWT sub claim 是字符串）。调用处 `jwtTokenProvider.issueStoreTokens(String.valueOf(user.getId()), ...)`。解析 AuthPrincipal.subject() 后用 `Long.parseLong(principal.subject())` 得到 Long id
7. **AuthPrincipal / AuthContext / TokenPair / infra 缓存**：不改（基于 tokenId=JWT jti=String，与主键无关）
8. **IdGenerator**：删除该类及所有 import 和调用

## 七、mapper @Select 中的列名/类型

- 自定义 @Select SQL 里 `WHERE id = #{id}` 参数 String→Long
- @Param OffsetDateTime → LocalDateTime
- permission 表查询：`WHERE \`key\`=` → `WHERE perm_code=`；role_permission 的 permission_key → permission_id
