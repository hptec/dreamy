package com.dreamy.identity.common.infra;

import com.dreamy.identity.common.error.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * OTP 频控（Redis 窗口计数）。
 * 约束: shared-contracts rate_limit（resend / email_h<=5 / email_d<=5 / ip_h<=20）；
 * error frequency 表（42901 RESEND_TOO_SOON / 42902 RATE_LIMITED）；REQ-005-07 窗口到期自动清零。
 * 返回 RateDecision 由调用方按需抛 BizException（携带 details.remaining_resend_seconds）。
 */
@Component
public class OtpRateLimiter {

    private static final int EMAIL_HOURLY = 5;
    private static final int EMAIL_DAILY = 5;
    private static final int IP_HOURLY = 20;

    private final StringRedisTemplate redis;

    public OtpRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** STEP-02 重发间隔：命中未过期 → RESEND_TOO_SOON(42901)，details.remaining_resend_seconds */
    public RateDecision checkResend(String email, int resendSeconds) {
        String key = "otp:resend:" + email;
        Long ttl = redis.getExpire(key);
        if (Boolean.TRUE.equals(redis.hasKey(key)) && ttl != null && ttl > 0) {
            Map<String, Object> details = new HashMap<>();
            details.put("remaining_resend_seconds", ttl);
            return RateDecision.blocked(ErrorCode.RESEND_TOO_SOON, details);
        }
        return RateDecision.allowed();
    }

    /** STEP-03 发码窗口频控：email 5/h&5/d，IP 20/h → RATE_LIMITED(42902) */
    public RateDecision checkSendQuota(String email, String ip) {
        long emailH = current("otp:count:email:" + email + ":h");
        long emailD = current("otp:count:email:" + email + ":d");
        long ipH = current("otp:count:ip:" + ip + ":h");
        if (emailH >= EMAIL_HOURLY || emailD >= EMAIL_DAILY || ipH >= IP_HOURLY) {
            return RateDecision.blocked(ErrorCode.RATE_LIMITED, null);
        }
        return RateDecision.allowed();
    }

    /** 发码成功后：置重发窗口 + 累加发码窗口计数 */
    public void recordSent(String email, String ip, int resendSeconds) {
        redis.opsForValue().set("otp:resend:" + email, "1", Duration.ofSeconds(resendSeconds));
        incrWithTtl("otp:count:email:" + email + ":h", Duration.ofHours(1));
        incrWithTtl("otp:count:email:" + email + ":d", Duration.ofDays(1));
        incrWithTtl("otp:count:ip:" + ip + ":h", Duration.ofHours(1));
    }

    private long current(String key) {
        String v = redis.opsForValue().get(key);
        return v == null ? 0 : Long.parseLong(v);
    }

    private void incrWithTtl(String key, Duration ttl) {
        Long val = redis.opsForValue().increment(key);
        if (val != null && val == 1L) {
            redis.expire(key, ttl);
        }
    }

    /** 频控判定结果 */
    public record RateDecision(boolean permitted, ErrorCode errorCode, Map<String, Object> details) {
        public static RateDecision allowed() {
            return new RateDecision(true, null, null);
        }

        public static RateDecision blocked(ErrorCode code, Map<String, Object> details) {
            return new RateDecision(false, code, details);
        }
    }
}
