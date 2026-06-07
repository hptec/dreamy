package com.dreamy.identity.domain.role.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.identity.error.BizException;
import com.dreamy.identity.error.ErrorCode;
import com.dreamy.identity.domain.admin.entity.AdminUser;
import com.dreamy.identity.domain.role.entity.Permission;
import com.dreamy.identity.domain.enums.RoleType;
import com.dreamy.identity.domain.role.entity.Role;
import com.dreamy.identity.domain.role.entity.RolePermission;
import com.dreamy.identity.domain.admin.repository.AdminUserMapper;
import com.dreamy.identity.domain.role.repository.PermissionMapper;
import com.dreamy.identity.domain.role.repository.RoleMapper;
import com.dreamy.identity.domain.role.repository.RolePermissionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 角色权限领域服务（FLOW-11）。
 * 约束: RM-060~071、RM-080；EDGE-015 ROLE_IN_USE 40904；is_locked 40308；DR-06 重名 40000；
 * RISK-03 超管全权限应用层短路（is_locked → 全 22 key）。
 * 主键 Long 迁移：role_permission 内部存 permission_id(Long)，对外维持权限码（perm_code）语义。
 */
@Service
public class RoleService {

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;
    private final AdminUserMapper adminUserMapper;

    public RoleService(RoleMapper roleMapper,
                       RolePermissionMapper rolePermissionMapper,
                       PermissionMapper permissionMapper,
                       AdminUserMapper adminUserMapper) {
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionMapper = permissionMapper;
        this.adminUserMapper = adminUserMapper;
    }

    /** listRoles：全角色 + member_count + permission_keys（MAP-005） */
    public List<Role> listRoles() {
        return roleMapper.selectList(new LambdaQueryWrapper<Role>()
                .orderByAsc(Role::getCreatedAt));
    }

    // ===== 表示层 DTO 组装（Controller 不接触 Entity）=====

    /** Role→RoleDTO（补 member_count + effective permission_keys） */
    public com.dreamy.identity.dto.RoleDTO toRoleDTO(Role role) {
        List<String> keys = effectivePermissionKeys(role);
        long memberCount = countMembers(role.getId());
        return new com.dreamy.identity.dto.RoleDTO(role.getId(), role.getName(),
                role.getType(), role.getIsLocked(), memberCount, keys);
    }

    /** listRoles DTO 视图 */
    public List<com.dreamy.identity.dto.RoleDTO> listRoleDTOs() {
        return listRoles().stream().map(this::toRoleDTO).toList();
    }

    /** createRole 后返回 DTO */
    public com.dreamy.identity.dto.RoleDTO createRoleDTO(String name) {
        return toRoleDTO(createRole(name));
    }

    /** updateRole 后返回 DTO */
    public com.dreamy.identity.dto.RoleDTO updateRoleDTO(Long roleId, String name, List<String> permissionKeys) {
        return toRoleDTO(updateRole(roleId, name, permissionKeys));
    }

    public Role findById(Long roleId) {
        return roleMapper.selectById(roleId);
    }

    /**
     * RM-070 listKeysByRoleId（A3: 分步查询 + 内存聚合，禁 JOIN）。
     * STEP-01: 取 role_permission.permission_id 列表；
     * STEP-02: 按 id IN 批量查 permission，内存提取 perm_code。
     */
    public List<String> permissionKeysOfRole(Long roleId) {
        LambdaQueryWrapper<RolePermission> rpQw = new LambdaQueryWrapper<>();
        rpQw.eq(RolePermission::getRoleId, roleId);
        List<Long> permIds = rolePermissionMapper.selectList(rpQw)
                .stream().map(RolePermission::getPermissionId).toList();
        if (permIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<Permission> permQw = new LambdaQueryWrapper<>();
        permQw.in(Permission::getId, permIds);
        return permissionMapper.selectList(permQw)
                .stream().map(Permission::getPermCode).toList();
    }

    /** RM-052 countByRoleId：角色成员数（member_count + 删除前校验） */
    public long countMembers(Long roleId) {
        LambdaQueryWrapper<AdminUser> qw = new LambdaQueryWrapper<>();
        qw.eq(AdminUser::getRoleId, roleId);
        return adminUserMapper.selectCount(qw);
    }

    /** GUARD-04 / adminMe：超管(is_locked) 短路全 22 key；否则取关联 key（RISK-03 应用层短路方案） */
    public List<String> effectivePermissionKeys(Role role) {
        if (role == null) {
            return List.of();
        }
        if (Boolean.TRUE.equals(role.getIsLocked())) {
            return listAllPermissionKeys();
        }
        return permissionKeysOfRole(role.getId());
    }

    /** RM-080 listAll：22 项权限字典（按 group） */
    public List<Permission> listPermissions() {
        return permissionMapper.selectList(new LambdaQueryWrapper<Permission>()
                .orderByAsc(Permission::getGroup));
    }

    public List<String> listAllPermissionKeys() {
        List<String> keys = new ArrayList<>();
        for (Permission p : permissionMapper.selectList(
                new LambdaQueryWrapper<Permission>().isNotNull(Permission::getId))) {
            keys.add(p.getPermCode());
        }
        return keys;
    }

    /** FUNC-018 createRole：DR-06 重名 40000（字段级 details.field=name）；type=custom */
    @Transactional
    public Role createRole(String name) {
        if (existsByName(name)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR,
                    java.util.Map.of("field", "name")); // DR-06 重名
        }
        Role role = new Role();
        role.setName(name);
        role.setType(RoleType.CUSTOM);
        role.setIsLocked(false);
        role.setVersion(0);
        roleMapper.insert(role); // id/createdAt/updatedAt 由 DB 自增 + 审计基类自动填充
        return role;
    }

    /**
     * FLOW-11 updateRole（FUNC-018/019）。
     * 约束: STEP-01 is_locked 40308；STEP-02 TX-004 全量重写 role_permission（DELETE+批量 INSERT，校验 keys 存在）。
     */
    @Transactional
    public Role updateRole(Long roleId, String name, List<String> permissionKeys) {
        Role role = requireExist(roleId);
        if (Boolean.TRUE.equals(role.getIsLocked())) {
            throw new BizException(ErrorCode.ROLE_LOCKED); // 40308
        }
        if (name != null && !name.equals(role.getName())) {
            if (existsByName(name)) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, java.util.Map.of("field", "name"));
            }
            role.setName(name);
            roleMapper.updateById(role);
        }
        if (permissionKeys != null) {
            // RI-001 校验 keys 均存在
            Set<String> valid = new HashSet<>(listAllPermissionKeys());
            for (String key : permissionKeys) {
                if (!valid.contains(key)) {
                    throw new BizException(ErrorCode.VALIDATION_ERROR, java.util.Map.of("field", "permission_keys"));
                }
            }
            // RM-071 全量重写（TX-004）：权限码 → permission_id 后写入关联表
            LambdaQueryWrapper<RolePermission> del = new LambdaQueryWrapper<>();
            del.eq(RolePermission::getRoleId, roleId);
            rolePermissionMapper.delete(del);
            for (String key : permissionKeys) {
                // A4: LambdaQueryWrapper 替代 @Select
                LambdaQueryWrapper<Permission> permQw = new LambdaQueryWrapper<>();
                permQw.eq(Permission::getPermCode, key);
                Permission perm = permissionMapper.selectOne(permQw);
                if (perm == null) {
                    throw new BizException(ErrorCode.VALIDATION_ERROR, java.util.Map.of("field", "permission_keys"));
                }
                RolePermission rp = new RolePermission();
                rp.setRoleId(roleId);
                rp.setPermissionId(perm.getId());
                rolePermissionMapper.insert(rp);
            }
        }
        return role;
    }

    /** FLOW-11 deleteRole（FUNC-020 EDGE-015）：STEP-01 is_locked 40308；STEP-02 有成员 40904；STEP-03 DELETE */
    @Transactional
    public void deleteRole(Long roleId) {
        Role role = requireExist(roleId);
        if (Boolean.TRUE.equals(role.getIsLocked())) {
            throw new BizException(ErrorCode.ROLE_LOCKED); // 40308
        }
        if (countMembers(roleId) > 0) {
            throw new BizException(ErrorCode.ROLE_IN_USE); // 40904
        }
        LambdaQueryWrapper<RolePermission> del = new LambdaQueryWrapper<>();
        del.eq(RolePermission::getRoleId, roleId);
        rolePermissionMapper.delete(del);
        roleMapper.deleteById(roleId);
    }

    /** RM-061 existsByName */
    public boolean existsByName(String name) {
        LambdaQueryWrapper<Role> qw = new LambdaQueryWrapper<>();
        qw.eq(Role::getName, name);
        return roleMapper.selectCount(qw) > 0;
    }

    private Role requireExist(Long roleId) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return role;
    }
}
