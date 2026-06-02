package com.dreamy.identity.common.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.identity.common.repository.entity.UserSessionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * UserSessionMapper —— RM-030~037。表 user_session。
 */
@Mapper
public interface UserSessionMapper extends BaseMapper<UserSessionEntity> {
}
