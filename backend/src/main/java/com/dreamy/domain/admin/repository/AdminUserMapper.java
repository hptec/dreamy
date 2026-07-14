package com.dreamy.domain.admin.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.admin.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * AdminUserMapper —— RM-050~053。表 admin_user。
 */
@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {

    default AdminUser selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapper<AdminUser>()
                .eq(AdminUser::getId, id)
                .last("FOR UPDATE"));
    }

    default AdminUser selectByEmailForUpdate(String email) {
        return selectOne(new LambdaQueryWrapper<AdminUser>()
                .eq(AdminUser::getEmail, email)
                .last("LIMIT 1 FOR UPDATE"));
    }
}
