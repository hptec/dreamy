package com.dreamy.domain.session.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.session.entity.AdminSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * AdminSessionMapper —— RM-090/091。表 admin_session。
 */
@Mapper
public interface AdminSessionMapper extends BaseMapper<AdminSession> {
}
