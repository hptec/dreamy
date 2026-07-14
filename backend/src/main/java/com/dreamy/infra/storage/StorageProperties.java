package com.dreamy.infra.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * S3 兼容对象存储配置（决策 9，Cloudflare R2 类；tech-profile integrations.object_storage）。
 * 凭证仅后端配置（任何响应与日志不出现，error-strategy 脱敏规则）；
 * mode=stub（dev 缺省，返回本地假 URL，零外部依赖）/ real（SigV4 预签名）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "dreamy.storage")
public class StorageProperties {

    /** stub | real */
    private String mode = "stub";

    /** S3 兼容 endpoint（R2: https://{account}.r2.cloudflarestorage.com） */
    private String endpoint = "http://localhost:9000";

    /** SigV4 region（R2 固定 auto） */
    private String region = "auto";

    private String bucket = "dreamy-media";

    private String accessKey = "";

    private String secretKey = "";

    /** public_url 前缀（CDN 域名；落库存 public_url，CDN 直出） */
    private String publicBaseUrl = "http://localhost:9000/dreamy-media";

    /** 预签名有效期（E-CAT-38 STEP-CAT-02：600s） */
    private long presignTtlSeconds = 600L;
}
