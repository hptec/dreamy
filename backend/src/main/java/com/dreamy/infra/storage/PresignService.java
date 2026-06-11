package com.dreamy.infra.storage;

/**
 * S3 兼容 PUT 预签名端口（决策 9 / FLOW-P17；review §0 StoragePresignPort 的基建落点）。
 * 失败 → StorageUnavailableException（域层映射 502501 catalog / 502801 review，表单其余字段可先保存）。
 * 对象 key 生成与 MIME/scope 校验归各域端点（V-CAT-069~071 / V-REV-013），本端口只负责签名。
 */
public interface PresignService {

    /**
     * 生成 PUT 预签名 URL。
     *
     * @param objectKey   对象 key（如 product/{id}/{sanitizedFileName}，调用方已 sanitize）
     * @param contentType 上传 MIME（绑定进签名头，客户端须原样发送 Content-Type）
     */
    PresignResult presign(String objectKey, String contentType);
}
