package com.dreamy.identity.domain.otp.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dreamy.identity.domain.otp.entity.OtpCodeEntity;
import com.dreamy.identity.domain.otp.repository.OtpCodeMapper;
import com.dreamy.identity.error.BizException;
import com.dreamy.identity.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * OTP 状态写入服务（独立事务）。
 *
 * FND-CODE-001/SEC-001 修复：updateStatus / incrementAttempt 必须在独立事务（REQUIRES_NEW）中执行，
 * 使频控写入在外层 @Transactional 回滚（BizException）前已提交到 DB，防暴力破解约束失效。
 * 参见 OtpService.consumeValidCode 的调用方。
 */
@Service
public class OtpStateWriter {

    private final OtpCodeMapper otpCodeMapper;

    public OtpStateWriter(OtpCodeMapper otpCodeMapper) {
        this.otpCodeMapper = otpCodeMapper;
    }

    /**
     * 更新 OTP 状态（独立提交，不受外层事务回滚影响）。
     * consumed 场景传入 expectedVersion 构成真正的乐观锁 CAS：
     * WHERE version = ? + SET version = version + 1，并发第二次 update 匹配 0 行 → 抛 OTP_EXPIRED。
     * 这是 Redis 分布式锁失效降级时的可靠兜底（防并发双消费）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatus(Long id, String status, Integer expectedVersion) {
        var update = Wrappers.<OtpCodeEntity>lambdaUpdate()
                .eq(OtpCodeEntity::getId, id)
                .set(OtpCodeEntity::getStatus, status);
        if (expectedVersion != null) {
            // 真 CAS：version 条件 + 自增（update(null,wrapper) 不触发 @Version 插件，需手动自增）
            update.eq(OtpCodeEntity::getVersion, expectedVersion)
                    .setSql("version = version + 1");
        }
        int rows = otpCodeMapper.update(null, update);
        if (rows == 0 && expectedVersion != null) {
            // version 不匹配 = 并发请求已抢先 consumed，CAS 失败抛过期语义
            throw new BizException(ErrorCode.OTP_EXPIRED);
        }
    }

    /**
     * attempts 递增（独立提交）。直接 SET attempts = attempts + 1，规避 MyBatis 一级缓存 stale version。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementAttempt(Long id) {
        otpCodeMapper.update(null,
                Wrappers.<OtpCodeEntity>lambdaUpdate()
                        .eq(OtpCodeEntity::getId, id)
                        .setSql("attempts = attempts + 1"));
    }
}
