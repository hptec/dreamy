package com.dreamy.domain.session.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.session.entity.UserSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * UserSessionMapper —— RM-030~037。表 user_session。
 */
@Mapper
public interface UserSessionMapper extends BaseMapper<UserSession> {
}
