package com.dreamy.domain.gateway.service;

import com.dreamy.error.GatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 网关 API Key 加解密/掩码单元测试（FUNC-007 / EDGE-010 / §6.1）。
 * 覆盖：AES-256-GCM 往返、v1: 前缀、掩码格式、密钥长度 fail-fast、解密失败兜底。
 */
class GatewayCryptoServiceTest {

    private GatewayCryptoService service;

    @BeforeEach
    void setUp() {
        service = new GatewayCryptoService();
        // 32 字节随机 256-bit 密钥（Base64）
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) i;
        }
        ReflectionTestUtils.setField(service, "aesKeyBase64", Base64.getEncoder().encodeToString(key));
        ReflectionTestUtils.invokeMethod(service, "initKey");
    }

    @Test
    @DisplayName("TC-UNIT-GW-001: 加解密往返还原明文且密文带 v1: 前缀")
    void encryptDecryptRoundTrip() {
        String plain = "sk-or-v1-abcdef0123456789";
        String encrypted = service.encrypt(plain);
        assertThat(encrypted).startsWith("v1:");
        assertThat(service.decrypt(encrypted)).isEqualTo(plain);
    }

    @Test
    @DisplayName("TC-UNIT-GW-002: 相同明文两次加密 IV 随机 → 密文不同")
    void encryptUsesRandomIv() {
        String plain = "sk-test-key-value";
        assertThat(service.encrypt(plain)).isNotEqualTo(service.encrypt(plain));
    }

    @Test
    @DisplayName("TC-UNIT-GW-003: 掩码取前4+****+后4（EDGE-010）")
    void maskFormat() {
        String encrypted = service.encrypt("sk-abcdef1234");
        assertThat(service.mask(encrypted)).isEqualTo("sk-a****1234");
    }

    @Test
    @DisplayName("TC-UNIT-GW-004: 不足 8 位掩码全 ****")
    void maskShortKey() {
        String encrypted = service.encrypt("short");
        assertThat(service.mask(encrypted)).isEqualTo("****");
    }

    @Test
    @DisplayName("TC-UNIT-GW-005: 掩码格式识别 isMasked")
    void isMasked() {
        assertThat(service.isMasked("sk-a****1234")).isTrue();
        assertThat(service.isMasked("sk-plaintext")).isFalse();
    }

    @Test
    @DisplayName("TC-UNIT-GW-006: 密文损坏解密 → 422202 GatewayException")
    void decryptCorruptedThrows() {
        assertThatThrownBy(() -> service.decrypt("v1:bm90LXZhbGlkLWNpcGhlcg=="))
                .isInstanceOf(GatewayException.class);
    }

    @Test
    @DisplayName("TC-UNIT-GW-007: 密钥长度非 32 字节 → fail-fast IllegalStateException")
    void shortKeyFailFast() {
        GatewayCryptoService bad = new GatewayCryptoService();
        ReflectionTestUtils.setField(bad, "aesKeyBase64",
                Base64.getEncoder().encodeToString(new byte[16]));
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(bad, "initKey"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("长度非法");
    }

    @Test
    @DisplayName("TC-UNIT-GW-008: 空密钥 → fail-fast IllegalStateException")
    void blankKeyFailFast() {
        GatewayCryptoService bad = new GatewayCryptoService();
        ReflectionTestUtils.setField(bad, "aesKeyBase64", "");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(bad, "initKey"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未配置");
    }
}
