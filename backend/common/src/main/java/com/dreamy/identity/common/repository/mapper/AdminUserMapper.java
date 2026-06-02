package com.dreamy.identity.common.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.identity.common.repository.entity.AdminUserEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AdminUserMapper —— RM-050~053。表 admin_user。
 */
@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUserEntity> {
}
