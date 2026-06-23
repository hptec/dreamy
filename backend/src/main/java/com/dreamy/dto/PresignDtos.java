package com.dreamy.dto;

import java.time.OffsetDateTime;

/**
 * 预签名上传入参/出参（E-CAT-35，决策 9/FLOW-P17）。
 * L2 TRACE: openapi PresignRequest/PresignResponse / V-CAT-069~071。
 */
public final class PresignDtos {

    private PresignDtos() {
    }

    /** scope ∈ {product,category,banner,content} 缺省 product（V-CAT-071） */
    public record PresignRequest(String fileName, String contentType, String scope) {
    }

    public record PresignResponse(String uploadUrl, String objectKey, String publicUrl, OffsetDateTime expiresAt) {
    }
}
