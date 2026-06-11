package com.dreamy.infra.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 预签名 stub 实现（dev 缺省，dreamy.storage.mode=stub）：返回本地假 URL，不做任何网络/签名计算，
 * 保证 dev 零外部依赖可启动（前端可对该 URL PUT 到本地 mock 或忽略上传结果）。
 */
@Component
@ConditionalOnProperty(name = "dreamy.storage.mode", havingValue = "stub", matchIfMissing = true)
public class StubPresignService implements PresignService {

    private static final Logger log = LoggerFactory.getLogger(StubPresignService.class);

    private final StorageProperties props;

    public StubPresignService(StorageProperties props) {
        this.props = props;
    }

    @Override
    public PresignResult presign(String objectKey, String contentType) {
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC)
                .plusSeconds(props.getPresignTtlSeconds());
        String uploadUrl = trimTrailingSlash(props.getEndpoint()) + "/" + props.getBucket() + "/" + objectKey
                + "?X-Stub-Presign=PUT&X-Stub-Expires=" + props.getPresignTtlSeconds();
        String publicUrl = trimTrailingSlash(props.getPublicBaseUrl()) + "/" + objectKey;
        log.info("[STORAGE-STUB] presign object_key={} content_type={}", objectKey, contentType);
        return new PresignResult(uploadUrl, objectKey, publicUrl, expiresAt);
    }

    private String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
