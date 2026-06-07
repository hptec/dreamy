package com.dreamy.identity.domain.audit.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.identity.domain.audit.entity.LoginHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * LoginHistoryMapper —— RM-040~043。表 login_history（追加型）。
 */
@Mapper
public interface LoginHistoryMapper extends BaseMapper<LoginHistory> {
}
