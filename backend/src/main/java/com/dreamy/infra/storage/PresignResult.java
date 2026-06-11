package com.dreamy.infra.storage;

import java.time.OffsetDateTime;

/**
 * 预签名结果（E-CAT-35 / E-REV 上传出参形状：{upload_url, object_key, public_url, expires_at}）。
 */
public record PresignResult(
        String uploadUrl,
        String objectKey,
        String publicUrl,
        OffsetDateTime expiresAt
) {
}
