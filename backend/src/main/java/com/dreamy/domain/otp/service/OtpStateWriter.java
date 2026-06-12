package com.dreamy.domain.otp.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dreamy.enums.OtpStatus;
import com.dreamy.domain.otp.entity.OtpCode;
import com.dreamy.domain.otp.repository.OtpCodeMapper;
import com.dreamy.error.BizException;
import com.dreamy.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OtpStateWriter {

    private final OtpCodeMapper otpCodeMapper;

    public OtpStateWriter(OtpCodeMapper otpCodeMapper) {
        this.otpCodeMapper = otpCodeMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatus(Long id, OtpStatus status, Integer expectedVersion) {
        var update = Wrappers.<OtpCode>lambdaUpdate()
                .eq(OtpCode::getId, id)
                .set(OtpCode::getStatus, status);
        if (expectedVersion != null) {
            update.eq(OtpCode::getVersion, expectedVersion)
                    .setSql("version = version + 1");
        }
        int rows = otpCodeMapper.update(null, update);
        if (rows == 0 && expectedVersion != null) {
            throw new BizException(ErrorCode.OTP_EXPIRED);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementAttempt(Long id) {
        otpCodeMapper.update(null,
                Wrappers.<OtpCode>lambdaUpdate()
                        .eq(OtpCode::getId, id)
                        .setSql("attempts = attempts + 1"));
    }
}
