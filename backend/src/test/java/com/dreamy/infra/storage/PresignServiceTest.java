package com.dreamy.infra.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S3 兼容预签名单元测试（决策 9 / E-CAT-38 STEP-CAT-02：PUT 预签名 600s）。
 * stub：本地假 URL 零外部依赖；real：SigV4 query 参数完备 + 配置缺失 fail（502501/502801 由域层映射）。
 */
class PresignServiceTest {

    private StorageProperties props(String mode) {
        StorageProperties p = new StorageProperties();
        p.setMode(mode);
        p.setEndpoint("https://acc123.r2.cloudflarestorage.com");
        p.setRegion("auto");
        p.setBucket("dreamy-media");
        p.setAccessKey("AKIA_TEST_KEY");
        p.setSecretKey("test-secret");
        p.setPublicBaseUrl("https://cdn.dreamy.com");
        p.setPresignTtlSeconds(600);
        return p;
    }

    @Test
    @DisplayName("stub：返回本地假 URL + public_url + expires_at（dev 零外部依赖）")
    void stubPresignReturnsFakeUrl() {
        StorageProperties p = props("stub");
        p.setEndpoint("http://localhost:9000");
        p.setPublicBaseUrl("http://localhost:9000/dreamy-media");
        StubPresignService service = new StubPresignService(p);

        PresignResult result = service.presign("product/123/dress.webp", "image/webp");
        assertThat(result.uploadUrl())
                .startsWith("http://localhost:9000/dreamy-media/product/123/dress.webp?")
                .contains("X-Stub-Presign=PUT");
        assertThat(result.objectKey()).isEqualTo("product/123/dress.webp");
        assertThat(result.publicUrl()).isEqualTo("http://localhost:9000/dreamy-media/product/123/dress.webp");
        assertThat(result.expiresAt()).isNotNull();
    }

    @Test
    @DisplayName("real：SigV4 query 预签名参数完备（Algorithm/Credential/Date/Expires/SignedHeaders/Signature）")
    void realPresignProducesSigV4Url() {
        Clock fixed = Clock.fixed(Instant.parse("2026-06-10T08:30:00Z"), ZoneOffset.UTC);
        S3PresignService service = new S3PresignService(props("real"), fixed);

        PresignResult result = service.presign("review/456/photo.jpg", "image/jpeg");
        assertThat(result.uploadUrl())
                .startsWith("https://acc123.r2.cloudflarestorage.com/dreamy-media/review/456/photo.jpg?")
                .contains("X-Amz-Algorithm=AWS4-HMAC-SHA256")
                .contains("X-Amz-Credential=AKIA_TEST_KEY%2F20260610%2Fauto%2Fs3%2Faws4_request")
                .contains("X-Amz-Date=20260610T083000Z")
                .contains("X-Amz-Expires=600")
                .contains("X-Amz-SignedHeaders=content-type%3Bhost");
        assertThat(result.uploadUrl()).matches(".*X-Amz-Signature=[0-9a-f]{64}$");
        assertThat(result.publicUrl()).isEqualTo("https://cdn.dreamy.com/review/456/photo.jpg");
        assertThat(result.expiresAt()).isEqualTo(Instant.parse("2026-06-10T08:40:00Z").atOffset(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("real：同输入同时刻签名确定（纯本地计算可重放）；不同 content-type 签名不同（绑定生效）")
    void realPresignDeterministicAndContentTypeBound() {
        Clock fixed = Clock.fixed(Instant.parse("2026-06-10T08:30:00Z"), ZoneOffset.UTC);
        S3PresignService service = new S3PresignService(props("real"), fixed);
        String first = service.presign("a/b.png", "image/png").uploadUrl();
        String second = service.presign("a/b.png", "image/png").uploadUrl();
        String differentType = service.presign("a/b.png", "image/webp").uploadUrl();
        assertThat(first).isEqualTo(second);
        assertThat(first).isNotEqualTo(differentType);
    }

    @Test
    @DisplayName("real：凭证未配置 → StorageUnavailableException（域层映射 502501/502801）")
    void realPresignMissingCredentialsFails() {
        StorageProperties p = props("real");
        p.setAccessKey("");
        S3PresignService service = new S3PresignService(p, Clock.systemUTC());
        assertThatThrownBy(() -> service.presign("a/b.png", "image/png"))
                .isInstanceOf(StorageUnavailableException.class);
    }

    @Test
    @DisplayName("real：object key 特殊字符按 RFC3986 编码且保留路径分隔符")
    void realPresignEncodesObjectKey() {
        Clock fixed = Clock.fixed(Instant.parse("2026-06-10T08:30:00Z"), ZoneOffset.UTC);
        S3PresignService service = new S3PresignService(props("real"), fixed);
        PresignResult result = service.presign("banner/2026/hero image+v2.webp", "image/webp");
        assertThat(result.uploadUrl())
                .contains("/dreamy-media/banner/2026/hero%20image%2Bv2.webp?");
    }
}
