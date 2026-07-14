package com.dreamy.infra.storage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * S3 兼容预签名 real 实现（dreamy.storage.mode=real）：AWS SigV4 query 预签名 PUT URL
 * （纯本地 HMAC 计算，不出网络、不引入 AWS SDK；R2 等 S3 兼容服务通用）。
 * - path-style：{endpoint}/{bucket}/{key}；有效期 X-Amz-Expires=presign-ttl-seconds（E-CAT-38：600s）；
 * - 签名头绑定 content-type + host（客户端 PUT 须原样携带 Content-Type，配合 MIME 白名单校验）；
 * - payload UNSIGNED-PAYLOAD（预签名直传标准形态）。
 * 配置缺失（access/secret key 空）→ StorageUnavailableException（域层映射 502501/502801）。
 */
@Component
@ConditionalOnProperty(name = "dreamy.storage.mode", havingValue = "real")
public class S3PresignService implements PresignService {

    private static final DateTimeFormatter AMZ_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD";

    private final StorageProperties props;
    private final Clock clock;

    @Autowired
    public S3PresignService(StorageProperties props) {
        this(props, Clock.systemUTC());
    }

    public S3PresignService(StorageProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    @Override
    public PresignResult presign(String objectKey, String contentType) {
        if (isBlank(props.getAccessKey()) || isBlank(props.getSecretKey())
                || isBlank(props.getEndpoint()) || isBlank(props.getBucket())) {
            throw new StorageUnavailableException("object storage credentials/endpoint not configured");
        }
        Instant now = clock.instant();
        String amzDate = AMZ_DATE.format(now);
        String dateStamp = amzDate.substring(0, 8);
        String credentialScope = dateStamp + "/" + props.getRegion() + "/s3/aws4_request";

        URI endpoint = URI.create(props.getEndpoint());
        String hostHeader = endpoint.getHost() + (endpoint.getPort() != -1 ? ":" + endpoint.getPort() : "");
        String canonicalUri = "/" + props.getBucket() + "/" + uriEncode(objectKey, true);

        Map<String, String> query = new LinkedHashMap<>();
        // 按字典序排列（X-Amz-Algorithm < Credential < Date < Expires < SignedHeaders）
        query.put("X-Amz-Algorithm", ALGORITHM);
        query.put("X-Amz-Credential", props.getAccessKey() + "/" + credentialScope);
        query.put("X-Amz-Date", amzDate);
        query.put("X-Amz-Expires", String.valueOf(props.getPresignTtlSeconds()));
        query.put("X-Amz-SignedHeaders", "content-type;host");
        String canonicalQuery = canonicalQuery(query);

        String canonicalHeaders = "content-type:" + contentType.trim() + "\n"
                + "host:" + hostHeader + "\n";
        String canonicalRequest = "PUT\n" + canonicalUri + "\n" + canonicalQuery + "\n"
                + canonicalHeaders + "\ncontent-type;host\n" + UNSIGNED_PAYLOAD;
        String stringToSign = ALGORITHM + "\n" + amzDate + "\n" + credentialScope + "\n"
                + hexSha256(canonicalRequest);

        byte[] signingKey = hmac(hmac(hmac(hmac(
                        ("AWS4" + props.getSecretKey()).getBytes(StandardCharsets.UTF_8), dateStamp),
                props.getRegion()), "s3"), "aws4_request");
        String signature = hex(hmac(signingKey, stringToSign));

        String baseEndpoint = props.getEndpoint().endsWith("/")
                ? props.getEndpoint().substring(0, props.getEndpoint().length() - 1)
                : props.getEndpoint();
        String uploadUrl = baseEndpoint + canonicalUri + "?" + canonicalQuery + "&X-Amz-Signature=" + signature;
        String publicBase = props.getPublicBaseUrl().endsWith("/")
                ? props.getPublicBaseUrl().substring(0, props.getPublicBaseUrl().length() - 1)
                : props.getPublicBaseUrl();
        OffsetDateTime expiresAt = OffsetDateTime.ofInstant(now, ZoneOffset.UTC)
                .plusSeconds(props.getPresignTtlSeconds());
        return new PresignResult(uploadUrl, objectKey, publicBase + "/" + objectKey, expiresAt);
    }

    private String canonicalQuery(Map<String, String> query) {
        StringJoiner joiner = new StringJoiner("&");
        query.forEach((k, v) -> joiner.add(uriEncode(k, false) + "=" + uriEncode(v, false)));
        return joiner.toString();
    }

    /** RFC3986 编码（SigV4 口径：unreserved 不编码；keepSlash=true 时保留路径分隔符） */
    private String uriEncode(String value, boolean keepSlash) {
        StringBuilder sb = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xFF);
            boolean unreserved = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~';
            if (unreserved || (keepSlash && c == '/')) {
                sb.append(c);
            } else {
                sb.append('%').append(String.format("%02X", b & 0xFF));
            }
        }
        return sb.toString();
    }

    private String hexSha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return hex(digest.digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new StorageUnavailableException("SHA-256 unavailable", ex);
        }
    }

    private byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new StorageUnavailableException("HmacSHA256 unavailable", ex);
        }
    }

    private String hex(byte[] raw) {
        StringBuilder sb = new StringBuilder(raw.length * 2);
        for (byte b : raw) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
