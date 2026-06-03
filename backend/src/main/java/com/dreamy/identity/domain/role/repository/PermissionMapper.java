package com.dreamy.identity.domain.role.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.identity.domain.role.entity.PermissionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * PermissionMapper —— RM-080（22 项字典，按 group）。表 permission。
 */
@Mapper
public interface PermissionMapper extends BaseMapper<PermissionEntity> {

    /** 按业务权限码取代理主键 id（perm_code → id，写入 role_permission 用） */
    @Select("SELECT id FROM permission WHERE perm_code = #{permCode} LIMIT 1")
    Long findIdByPermCode(@Param("permCode") String permCode);
}
