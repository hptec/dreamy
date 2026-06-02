package com.dreamy.identity.common.domain.service;

import com.alicp.jetcache.anno.CacheInvalidate;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.Cached;
import com.dreamy.identity.common.dto.AuthConfigUpdateRequest;
import com.dreamy.identity.common.dto.AuthConfigView;
import com.dreamy.identity.common.dto.mapper.IdentityDtoMapper;
import com.dreamy.identity.common.error.BizException;
import com.dreamy.identity.common.error.ErrorCode;
import com.dreamy.identity.common.repository.entity.AuthConfigEntity;
import com.dreamy.identity.common.repository.mapper.AuthConfigMapper;
import org.springframework.stereotype.Service;

/**
 * 认证配置领域服务（单例 id=1）。
 * 约束: RM-110/111；CV-002 区间双校验（越界 40002）；email_enabled 恒 true（V-CFG）；
 * 缓存 store:authconfig 两级 TTL600s（FLOW-13 @CacheInvalidate）。
 */
@Service
public class AuthConfigService {

    private static final int SINGLETON_ID = 1;

    private final AuthConfigMapper authConfigMapper;
    private final IdentityDtoMapper dtoMapper;

    public AuthConfigService(AuthConfigMapper authConfigMapper, IdentityDtoMapper dtoMapper) {
        this.authConfigMapper = authConfigMapper;
        this.dtoMapper = dtoMapper;
    }

    /** RM-110 读单例（两级缓存 store:authconfig，资料/配置类 600s）。内部用，返回 Entity。 */
    @Cached(name = "store:authconfig", key = "'singleton'", cacheType = CacheType.BOTH,
            expire = 600, localExpire = 600)
    public AuthConfigEntity getConfig() {
        AuthConfigEntity cfg = authConfigMapper.selectById(SINGLETON_ID);
        if (cfg == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
        return cfg;
    }

    /** RM-110 读单例并转 DTO（表示层用，不暴露 Entity）。 */
    public AuthConfigView getConfigView() {
        return dtoMapper.toAuthConfig(getConfig());
    }

    /**
     * RM-111 更新单例（FLOW-13）。接收表示层 Request DTO，返回 View，杜绝 Entity 跨层。
     * 约束: CV-002 区间校验（越界 40002）；email_enabled 强制 true；@CacheInvalidate store:authconfig。
     */
    @CacheInvalidate(name = "store:authconfig", key = "'singleton'")
    public AuthConfigView updateConfig(AuthConfigUpdateRequest request) {
        AuthConfigEntity update = new AuthConfigEntity();
        update.setGoogleEnabled(request.googleEnabled());
        update.setAppleEnabled(request.appleEnabled());
        update.setOtpLength(request.otpLength());
        update.setOtpTtlMinutes(request.otpTtlMinutes());
        update.setOtpResendSeconds(request.otpResendSeconds());
        update.setOtpMaxAttempts(request.otpMaxAttempts());
        update.setMinMethods(request.minMethods());
        update.setGoogleClientId(request.googleClientId());
        update.setAppleServiceId(request.appleServiceId());
        validateRange(update);
        update.setId(SINGLETON_ID);
        update.setEmailEnabled(Boolean.TRUE); // email_enabled 恒 true
        authConfigMapper.updateById(update);
        return dtoMapper.toAuthConfig(authConfigMapper.selectById(SINGLETON_ID));
    }

    /** CV-002：otp_length∈{4,6,8}；ttl 1..30；resend 10..120；max_attempts 3..10；min_methods 1..3 */
    public void validateRange(AuthConfigEntity c) {
        boolean ok = c.getOtpLength() != null && (c.getOtpLength() == 4 || c.getOtpLength() == 6 || c.getOtpLength() == 8)
                && inRange(c.getOtpTtlMinutes(), 1, 30)
                && inRange(c.getOtpResendSeconds(), 10, 120)
                && inRange(c.getOtpMaxAttempts(), 3, 10)
                && inRange(c.getMinMethods(), 1, 3);
        if (!ok) {
            throw new BizException(ErrorCode.CONFIG_OUT_OF_RANGE);
        }
    }

    private boolean inRange(Integer v, int min, int max) {
        return v != null && v >= min && v <= max;
    }
}
