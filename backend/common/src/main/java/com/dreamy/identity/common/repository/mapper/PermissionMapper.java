package com.dreamy.identity.common.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.identity.common.repository.entity.PermissionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * PermissionMapper —— RM-080（22 项字典，按 group）。表 permission。
 */
@Mapper
public interface PermissionMapper extends BaseMapper<PermissionEntity> {
}
