package com.dreamy.domain.gateway.service;

import com.dreamy.error.GatewayErrorCode;
import com.dreamy.error.GatewayException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 网关 API Key 加解密/掩码服务（AES-256-GCM，决策 6.1 / FUNC-007 / EDGE-010）。
 * L2 TRACE: i18n-backend-api-detail.md §6.1（MAP-001 encryptApiKey / decryptApiKey / maskApiKey）。
 *
 * 密钥来源：环境变量 DREAMY_GATEWAY_AES_KEY（Base64 编码 256-bit / 32 字节）。
 * 启动 fail-fast：key 为空或解码后非 32 字节 → IllegalStateException，禁止硬编码兜底。
 * 密文格式：keyVersion + ':' + base64(IV(12B) + cipher + GCM tag(16B))，首版前缀 v1:。
 */
@Service
public class GatewayCryptoService {

    private static final Logger log = LoggerFactory.getLogger(GatewayCryptoService.class);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ALGORITHM = "AES";
    private static final int IV_LENGTH = 12;          // AES-GCM 推荐 96-bit IV
    private static final int GCM_TAG_BITS = 128;       // 128-bit auth tag
    private static final int KEY_LENGTH = 32;          // 256-bit
    private static final String KEY_VERSION = "v1";
    private static final String VERSION_SEP = ":";

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${dreamy.gateway.aes-key:}")
    private String aesKeyBase64;

    private byte[] secretKey;

    @PostConstruct
    void initKey() {
        if (aesKeyBase64 == null || aesKeyBase64.isBlank()) {
            throw new IllegalStateException("DREAMY_GATEWAY_AES_KEY 未配置，启动失败（网关 API Key 加密依赖此密钥）");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(aesKeyBase64.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("DREAMY_GATEWAY_AES_KEY 非合法 Base64，启动失败", ex);
        }
        if (decoded.length != KEY_LENGTH) {
            throw new IllegalStateException(
                    "DREAMY_GATEWAY_AES_KEY 长度非法(需 256-bit/32 字节，实际 " + decoded.length + " 字节)，启动失败");
        }
        this.secretKey = decoded;
        log.info("[GATEWAY-CRYPTO] AES-256-GCM 密钥加载成功（version={}）", KEY_VERSION);
    }

    /** 加密明文 API Key → keyVersion:base64(IV+cipher)（创建/更新时调用，MAP-001）。 */
    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secretKey, ALGORITHM),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return KEY_VERSION + VERSION_SEP + Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            log.error("[GATEWAY-CRYPTO] API Key 加密失败", ex);
            throw new GatewayException(GatewayErrorCode.GATEWAY_VALIDATION);
        }
    }

    /** 解密密文（调用网关时）。剥 keyVersion 前缀，分离 IV，GCM 解密。失败 → 422202 + ERROR 日志告警。 */
    public String decrypt(String encrypted) {
        if (encrypted == null) {
            return null;
        }
        try {
            String payload = encrypted;
            int sepIdx = encrypted.indexOf(VERSION_SEP);
            if (sepIdx >= 0) {
                // 轮转预留：当前仅 v1，按前缀 resolveKeyByVersion 后续扩展
                payload = encrypted.substring(sepIdx + 1);
            }
            byte[] combined = Base64.getDecoder().decode(payload);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            byte[] cipherBytes = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, cipherBytes, 0, cipherBytes.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secretKey, ALGORITHM),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.error("[GATEWAY-CRYPTO] API Key 解密失败（密文损坏或密钥不匹配）", ex);
            throw new GatewayException(GatewayErrorCode.INVALID_API_KEY_FORMAT);
        }
    }

    /**
     * 掩码展示（响应时，EDGE-010）：解密后取前 4 + **** + 后 4。
     * 不足 8 位 → 全 ****。解密失败兜底 ****（避免详情接口因极罕见密文损坏整体 500）。
     */
    public String mask(String encrypted) {
        if (encrypted == null) {
            return null;
        }
        String plain;
        try {
            plain = decrypt(encrypted);
        } catch (GatewayException ex) {
            return "****";
        }
        if (plain == null || plain.length() <= 8) {
            return "****";
        }
        return plain.substring(0, 4) + "****" + plain.substring(plain.length() - 4);
    }

    /** 判断是否为掩码格式（更新场景：传掩码 → 保持原密文，不重新加密）。 */
    public boolean isMasked(String value) {
        return value != null && value.contains("****");
    }
}
