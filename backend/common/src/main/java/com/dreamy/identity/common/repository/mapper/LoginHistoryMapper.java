package com.dreamy.identity.common.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.identity.common.repository.entity.LoginHistoryEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * LoginHistoryMapper —— RM-040~043。表 login_history（追加型）。
 */
@Mapper
public interface LoginHistoryMapper extends BaseMapper<LoginHistoryEntity> {
}
