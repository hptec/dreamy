package com.dreamy.identity.common.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.identity.common.repository.entity.OtpCodeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * OtpCodeMapper —— RM-020~025。表 otp_code。
 */
@Mapper
public interface OtpCodeMapper extends BaseMapper<OtpCodeEntity> {

    /** RM-020 lockPendingByEmail：SELECT ... FOR UPDATE 行锁串行（TX-001 FLOW-02） */
    @Select("SELECT * FROM otp_code WHERE email = #{email} AND status = 'pending' ORDER BY created_at DESC LIMIT 1 FOR UPDATE")
    OtpCodeEntity lockPendingByEmail(@Param("email") String email);
}
