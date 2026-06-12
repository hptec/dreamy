package com.dreamy.domain.admin.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.admin.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * AdminUserMapper —— RM-050~053。表 admin_user。
 */
@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {
}
