package com.dreamy.identity.common.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.identity.common.error.BizException;
import com.dreamy.identity.common.error.ErrorCode;
import com.dreamy.identity.common.repository.entity.AdminUserEntity;
import com.dreamy.identity.common.repository.entity.PermissionEntity;
import com.dreamy.identity.common.repository.entity.RoleEntity;
import com.dreamy.identity.common.repository.entity.RolePermissionEntity;
import com.dreamy.identity.common.repository.mapper.AdminUserMapper;
import com.dreamy.identity.common.repository.mapper.PermissionMapper;
import com.dreamy.identity.common.repository.mapper.RoleMapper;
import com.dreamy.identity.common.repository.mapper.RolePermissionMapper;
import com.dreamy.identity.common.util.IdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 角色权限领域服务（FLOW-11）。
 * 约束: RM-060~071、RM-080；EDGE-015 ROLE_IN_USE 40904；is_locked 40308；DR-06 重名 40000；
 * RISK-03 超管全权限应用层短路（is_locked → 全 22 key）。
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
    public List<RoleEntity> listRoles() {
        return roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>()
                .orderByAsc(RoleEntity::getCreatedAt));
    }

    // ===== 表示层 DTO 组装（Controller 不接触 Entity）=====

    /** RoleEntity→RoleDTO（补 member_count + effective permission_keys） */
    public com.dreamy.identity.common.dto.RoleDTO toRoleDTO(RoleEntity role) {
        List<String> keys = effectivePermissionKeys(role);
        long memberCount = countMembers(role.getId());
        return new com.dreamy.identity.common.dto.RoleDTO(role.getId(), role.getName(),
                role.getType(), role.getIsLocked(), memberCount, keys);
    }

    /** listRoles DTO 视图 */
    public List<com.dreamy.identity.common.dto.RoleDTO> listRoleDTOs() {
        return listRoles().stream().map(this::toRoleDTO).toList();
    }

    /** createRole 后返回 DTO */
    public com.dreamy.identity.common.dto.RoleDTO createRoleDTO(String name) {
        return toRoleDTO(createRole(name));
    }

    /** updateRole 后返回 DTO */
    public com.dreamy.identity.common.dto.RoleDTO updateRoleDTO(String roleId, String name, List<String> permissionKeys) {
        return toRoleDTO(updateRole(roleId, name, permissionKeys));
    }

    public RoleEntity findById(String roleId) {
        return roleMapper.selectById(roleId);
    }

    /** RM-070 listKeysByRoleId */
    public List<String> permissionKeysOfRole(String roleId) {
        return rolePermissionMapper.listKeysByRoleId(roleId);
    }

    /** RM-052 countByRoleId：角色成员数（member_count + 删除前校验） */
    public long countMembers(String roleId) {
        LambdaQueryWrapper<AdminUserEntity> qw = new LambdaQueryWrapper<>();
        qw.eq(AdminUserEntity::getRoleId, roleId);
        return adminUserMapper.selectCount(qw);
    }

    /** GUARD-04 / adminMe：超管(is_locked) 短路全 22 key；否则取关联 key（RISK-03 应用层短路方案） */
    public List<String> effectivePermissionKeys(RoleEntity role) {
        if (role == null) {
            return List.of();
        }
        if (Boolean.TRUE.equals(role.getIsLocked())) {
            return listAllPermissionKeys();
        }
        return permissionKeysOfRole(role.getId());
    }

    /** RM-080 listAll：22 项权限字典（按 group） */
    public List<PermissionEntity> listPermissions() {
        return permissionMapper.selectList(new LambdaQueryWrapper<PermissionEntity>()
                .orderByAsc(PermissionEntity::getGroup));
    }

    public List<String> listAllPermissionKeys() {
        List<String> keys = new ArrayList<>();
        for (PermissionEntity p : permissionMapper.selectList(null)) {
            keys.add(p.getKey());
        }
        return keys;
    }

    /** FUNC-018 createRole：DR-06 重名 40000（字段级 details.field=name）；type=custom */
    @Transactional
    public RoleEntity createRole(String name) {
        if (existsByName(name)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR,
                    java.util.Map.of("field", "name")); // DR-06 重名
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        RoleEntity role = new RoleEntity();
        role.setId(IdGenerator.uuid());
        role.setName(name);
        role.setType("custom");
        role.setIsLocked(false);
        role.setVersion(0);
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        roleMapper.insert(role);
        return role;
    }

    /**
     * FLOW-11 updateRole（FUNC-018/019）。
     * 约束: STEP-01 is_locked 40308；STEP-02 TX-004 全量重写 role_permission（DELETE+批量 INSERT，校验 keys 存在）。
     */
    @Transactional
    public RoleEntity updateRole(String roleId, String name, List<String> permissionKeys) {
        RoleEntity role = requireExist(roleId);
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
            // RM-071 全量重写（TX-004）
            LambdaQueryWrapper<RolePermissionEntity> del = new LambdaQueryWrapper<>();
            del.eq(RolePermissionEntity::getRoleId, roleId);
            rolePermissionMapper.delete(del);
            for (String key : permissionKeys) {
                RolePermissionEntity rp = new RolePermissionEntity();
                rp.setRoleId(roleId);
                rp.setPermissionKey(key);
                rolePermissionMapper.insert(rp);
            }
        }
        return role;
    }

    /** FLOW-11 deleteRole（FUNC-020 EDGE-015）：STEP-01 is_locked 40308；STEP-02 有成员 40904；STEP-03 DELETE */
    @Transactional
    public void deleteRole(String roleId) {
        RoleEntity role = requireExist(roleId);
        if (Boolean.TRUE.equals(role.getIsLocked())) {
            throw new BizException(ErrorCode.ROLE_LOCKED); // 40308
        }
        if (countMembers(roleId) > 0) {
            throw new BizException(ErrorCode.ROLE_IN_USE); // 40904
        }
        LambdaQueryWrapper<RolePermissionEntity> del = new LambdaQueryWrapper<>();
        del.eq(RolePermissionEntity::getRoleId, roleId);
        rolePermissionMapper.delete(del);
        roleMapper.deleteById(roleId);
    }

    /** RM-061 existsByName */
    public boolean existsByName(String name) {
        LambdaQueryWrapper<RoleEntity> qw = new LambdaQueryWrapper<>();
        qw.eq(RoleEntity::getName, name);
        return roleMapper.selectCount(qw) > 0;
    }

    private RoleEntity requireExist(String roleId) {
        RoleEntity role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return role;
    }
}
