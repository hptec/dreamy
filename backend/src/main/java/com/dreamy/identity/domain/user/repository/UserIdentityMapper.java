package com.dreamy.identity.domain.user.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.identity.domain.user.entity.UserIdentityEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * UserIdentityMapper —— RM-010~018。表 user_identity。
 * 复杂查询由 Repository 用 LambdaQueryWrapper 实现，避免散落 SQL。
 */
@Mapper
public interface UserIdentityMapper extends BaseMapper<UserIdentityEntity> {
}
