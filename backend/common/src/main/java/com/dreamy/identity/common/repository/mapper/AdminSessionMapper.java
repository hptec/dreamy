package com.dreamy.identity.common.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.identity.common.repository.entity.AdminSessionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AdminSessionMapper —— RM-090/091。表 admin_session。
 */
@Mapper
public interface AdminSessionMapper extends BaseMapper<AdminSessionEntity> {
}
