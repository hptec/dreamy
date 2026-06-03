package com.dreamy.identity.util;

import java.security.SecureRandom;

/**
 * OTP 明文生成器（仅生成，绝不持久化明文，仅存 code_hash）。
 * 约束: STEP-06（生成 length 位明文）；CV-004 / redaction.fully_redacted。
 */
public final class OtpGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private OtpGenerator() {
    }

    /** 生成 length 位纯数字验证码 */
    public static String numeric(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
