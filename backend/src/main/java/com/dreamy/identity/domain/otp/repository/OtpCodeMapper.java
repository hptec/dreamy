package com.dreamy.identity.domain.otp.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.identity.domain.otp.entity.OtpCodeEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * OtpCodeMapper —— RM-020~025。表 otp_code。
 *
 * DEC-001（ORM 合规重构）：原 RM-020 lockPendingByEmail（{@code @Select ... FOR UPDATE} 行锁串行）已删除。
 * 并发控制改由 OtpService 用 huihao-redis 分布式锁（IdLockSupport.onIdLock("otp:verify", email)）+
 * OtpCodeEntity @Version 乐观锁兜底；pending OTP 查询改用 LambdaQueryWrapper（消除 native SQL）。
 */
@Mapper
public interface OtpCodeMapper extends BaseMapper<OtpCodeEntity> {
}
