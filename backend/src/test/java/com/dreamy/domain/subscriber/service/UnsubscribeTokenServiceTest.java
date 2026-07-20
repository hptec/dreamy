package com.dreamy.domain.subscriber.service;

import com.dreamy.config.MarketingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UnsubscribeTokenService 单元测试（固定 Clock；生成/解析/过期/篡改/畸形输入统一 InvalidTokenException）。 */
class UnsubscribeTokenServiceTest {

    private static final String SECRET = "test-unsubscribe-secret-32chars-min";
    private static final Instant NOW = Instant.parse("2026-07-18T08:00:00Z");
    private static final long GEN = UnsubscribeTokenService.toEpochMillis(
            LocalDateTime.of(2026, 7, 1, 12, 30, 45, 123_000_000));

    private UnsubscribeTokenService service;

    @BeforeEach
    void setUp() {
        service = new UnsubscribeTokenService(properties(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("生成→解析回环：email/exp/gen 一致，exp=now+30天")
    void roundTrip() {
        String token = service.generate("user@dreamy.test", GEN);
        UnsubscribeTokenService.ParsedToken parsed = service.parse(token);
        assertThat(parsed.email()).isEqualTo("user@dreamy.test");
        assertThat(parsed.genEpochMillis()).isEqualTo(GEN);
        assertThat(parsed.expEpochMillis()).isEqualTo(NOW.toEpochMilli() + 30L * 24 * 3600 * 1000);
    }

    @Test
    @DisplayName("过期 token → InvalidTokenException")
    void expiredTokenRejected() {
        String token = service.generate("user@dreamy.test", GEN);
        UnsubscribeTokenService later = new UnsubscribeTokenService(properties(),
                Clock.fixed(NOW.plusMillis(30L * 24 * 3600 * 1000 + 1), ZoneOffset.UTC));
        assertThatThrownBy(() -> later.parse(token))
                .isInstanceOf(UnsubscribeTokenService.InvalidTokenException.class);
    }

    @Test
    @DisplayName("篡改任意段（payload 或签名）→ InvalidTokenException")
    void tamperedTokenRejected() {
        String token = service.generate("user@dreamy.test", GEN);
        String[] parts = token.split("\\.");
        // 篡改 email 段
        parts[1] = parts[1].substring(0, parts[1].length() - 1) + (parts[1].endsWith("A") ? "B" : "A");
        assertThatThrownBy(() -> service.parse(String.join(".", parts)))
                .isInstanceOf(UnsubscribeTokenService.InvalidTokenException.class);
        // 篡改签名段（首字符——末字符可能只落在 base64 填充位，解码后不变）
        String[] parts2 = token.split("\\.");
        parts2[4] = (parts2[4].startsWith("A") ? "B" : "A") + parts2[4].substring(1);
        assertThatThrownBy(() -> service.parse(String.join(".", parts2)))
                .isInstanceOf(UnsubscribeTokenService.InvalidTokenException.class);
    }

    @Test
    @DisplayName("段数错误/版本前缀错误/非数字时间戳/超长 token → 统一 InvalidTokenException")
    void malformedTokensRejected() {
        String valid = service.generate("user@dreamy.test", GEN);
        String[] parts = valid.split("\\.");
        // 段数 ≠ 5
        assertThatThrownBy(() -> service.parse(parts[0] + "." + parts[1]))
                .isInstanceOf(UnsubscribeTokenService.InvalidTokenException.class);
        // 版本前缀错误
        assertThatThrownBy(() -> service.parse("u9." + parts[1] + "." + parts[2] + "." + parts[3] + "." + parts[4]))
                .isInstanceOf(UnsubscribeTokenService.InvalidTokenException.class);
        // exp 非数字
        assertThatThrownBy(() -> service.parse("u1." + parts[1] + ".YWJj." + parts[3] + "." + parts[4]))
                .isInstanceOf(UnsubscribeTokenService.InvalidTokenException.class);
        // 超长 token
        assertThatThrownBy(() -> service.parse(valid + "x".repeat(1024)))
                .isInstanceOf(UnsubscribeTokenService.InvalidTokenException.class);
        // null / 空
        assertThatThrownBy(() -> service.parse(null))
                .isInstanceOf(UnsubscribeTokenService.InvalidTokenException.class);
        assertThatThrownBy(() -> service.parse(""))
                .isInstanceOf(UnsubscribeTokenService.InvalidTokenException.class);
        // 非法 base64 段
        assertThatThrownBy(() -> service.parse("u1.***.***.***.***"))
                .isInstanceOf(UnsubscribeTokenService.InvalidTokenException.class);
    }

    @Test
    @DisplayName("异密钥签名 → InvalidTokenException（域隔离）")
    void differentSecretRejected() {
        String token = service.generate("user@dreamy.test", GEN);
        MarketingProperties other = properties();
        other.setUnsubscribeSecret("another-secret-key-32chars-minimum");
        UnsubscribeTokenService otherService = new UnsubscribeTokenService(other,
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> otherService.parse(token))
                .isInstanceOf(UnsubscribeTokenService.InvalidTokenException.class);
    }

    @Test
    @DisplayName("toEpochMillis：DATETIME(3) 毫秒精度 UTC 换算")
    void toEpochMillisPrecision() {
        long millis = UnsubscribeTokenService.toEpochMillis(
                LocalDateTime.of(2026, 1, 2, 3, 4, 5, 678_000_000));
        assertThat(millis % 1000).isEqualTo(678);
        assertThat(millis).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4, 5, 678_000_000)
                .atZone(ZoneOffset.UTC).toInstant().toEpochMilli());
    }

    private static MarketingProperties properties() {
        MarketingProperties p = new MarketingProperties();
        p.setUnsubscribeSecret(SECRET);
        p.setUnsubscribeTokenTtlSeconds(30L * 24 * 3600);
        return p;
    }
}
