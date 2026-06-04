package com.dreamy.identity.domain.user.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.identity.domain.user.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * UserMapper —— RM-001~007。表 user。
 * RM-002/RM-005 已迁至 Service 层用 LambdaQueryWrapper 实现（DEC-004/A2）。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}
