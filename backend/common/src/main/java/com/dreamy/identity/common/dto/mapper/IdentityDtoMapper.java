package com.dreamy.identity.common.dto.mapper;

import com.dreamy.identity.common.dto.AdminDTO;
import com.dreamy.identity.common.dto.AuthConfigView;
import com.dreamy.identity.common.dto.IdentityDTO;
import com.dreamy.identity.common.dto.OperationLogDTO;
import com.dreamy.identity.common.dto.PermissionDTO;
import com.dreamy.identity.common.dto.SessionDTO;
import com.dreamy.identity.common.dto.UserProfileDTO;
import com.dreamy.identity.common.repository.entity.AdminUserEntity;
import com.dreamy.identity.common.repository.entity.AuthConfigEntity;
import com.dreamy.identity.common.repository.entity.OperationLogEntity;
import com.dreamy.identity.common.repository.entity.PermissionEntity;
import com.dreamy.identity.common.repository.entity.UserEntity;
import com.dreamy.identity.common.repository.entity.UserIdentityEntity;
import com.dreamy.identity.common.repository.entity.UserSessionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Entity↔DTO 映射（MapStruct，MAP-001~006/008）。
 * 约束: MAP-001（资料隐藏密码类）/MAP-002（隐藏 provider_uid）/MAP-003（隐藏 token_id，is_current 由 Service 补）/
 * MAP-004（隐藏 password_hash，role_name 补）/MAP-006（changes 原样）/MAP-008（enum↔字符串）。
 * 禁止手写 getter/setter 逐字段赋值（IMP-06）。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface IdentityDtoMapper {

    // MAP-001 User→UserProfileDTO（匿名化态 email 已在库为 null，自然映射 null）
    UserProfileDTO toProfile(UserEntity entity);

    // MAP-002 UserIdentity→IdentityDTO（隐藏 provider_uid，不在目标字段即不暴露）
    IdentityDTO toIdentity(UserIdentityEntity entity);

    // MAP-003 UserSession→SessionDTO（is_current 由 @Mapping 忽略，Service 层据 jti 补）
    @Mapping(target = "isCurrent", ignore = true)
    SessionDTO toSession(UserSessionEntity entity);

    // MAP-004 AdminUser→AdminDTO（隐藏 password_hash；role_name 由 @Mapping 忽略，Service 补）
    @Mapping(target = "roleName", ignore = true)
    AdminDTO toAdmin(AdminUserEntity entity);

    // MAP-006 OperationLog→LogDTO（changes JSON 原样，operator_name 快照）
    OperationLogDTO toOperationLog(OperationLogEntity entity);

    // 权限字典
    PermissionDTO toPermission(PermissionEntity entity);

    // MAP-009 AuthConfig→AuthConfigView（隐藏单例 id/updatedAt 等内部字段）
    AuthConfigView toAuthConfig(AuthConfigEntity entity);

    // MAP-007 LoginHistory→LoginHistoryDTO（用户详情登录记录）
    com.dreamy.identity.common.dto.LoginHistoryDTO toLoginHistory(
            com.dreamy.identity.common.repository.entity.LoginHistoryEntity entity);
}
