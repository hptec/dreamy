package com.dreamy.infra;

import com.dreamy.enums.UploadScope;
import com.dreamy.dto.PresignDtos.PresignRequest;
import com.dreamy.dto.PresignDtos.PresignResponse;
import com.dreamy.error.CatalogErrorCode;
import com.dreamy.error.CatalogException;
import com.dreamy.support.CatalogFieldErrors;
import com.dreamy.infra.storage.PresignResult;
import com.dreamy.infra.storage.PresignService;
import com.dreamy.infra.storage.StorageUnavailableException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * catalog 域预签名上传服务（E-CAT-35；媒体基建由 catalog 域代管，五 scope 共用——决策 9/FLOW-P17）。
 * 读侧基建：不写 operation_log、不发 MQ、不缓存（STEP-CAT-04）。
 * L2 TRACE: V-CAT-069~071 / E-CAT-35 STEP-CAT-01~04 / TC-CAT-015。
 */
@Service
public class CatalogPresignService {

    /** V-CAT-070 MIME 白名单 */
    static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "video/mp4");

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AtomicLong SEQUENCE = new AtomicLong(RANDOM.nextInt(1 << 12));
    /** 2024-01-01T00:00:00Z 起始纪元（雪花序时间基准） */
    private static final long EPOCH = 1704067200000L;

    private final PresignService presignService;

    public CatalogPresignService(PresignService presignService) {
        this.presignService = presignService;
    }

    public PresignResponse presign(PresignRequest req) {
        CatalogFieldErrors errors = new CatalogFieldErrors();
        // V-CAT-069 file_name 必填 ≤255 + sanitize
        String sanitized = null;
        if (req.fileName() == null || req.fileName().isBlank()) {
            errors.reject("file_name", "required");
        } else if (req.fileName().length() > 255) {
            errors.reject("file_name", "too_long");
        } else {
            sanitized = sanitizeFileName(req.fileName());
            if (sanitized.isEmpty()) {
                errors.reject("file_name", "invalid");
            }
        }
        // V-CAT-070 content_type 白名单
        if (req.contentType() == null || req.contentType().isBlank()) {
            errors.reject("content_type", "required");
        } else if (!ALLOWED_CONTENT_TYPES.contains(req.contentType())) {
            errors.reject("content_type", "unsupported");
        }
        // V-CAT-071 scope 枚举缺省 product
        UploadScope scope = UploadScope.PRODUCT;
        if (req.scope() != null && !req.scope().isBlank()) {
            scope = UploadScope.of(req.scope());
            if (scope == null) {
                errors.reject("scope", "invalid_enum");
            }
        }
        errors.throwIfAny();
        // STEP-CAT-01 对象 key：{scope}/{雪花序id}/{sanitizedFileName}
        String objectKey = scope.getKey() + "/" + nextSnowflakeId() + "/" + sanitized;
        try {
            // STEP-CAT-02/04 预签名 + public_url（infra PresignService 负责签名与 CDN 域拼装）
            PresignResult result = presignService.presign(objectKey, req.contentType());
            return new PresignResponse(result.uploadUrl(), result.objectKey(), result.publicUrl(),
                    result.expiresAt());
        } catch (StorageUnavailableException ex) {
            // STEP-CAT-03 S3 不可达/超时 → 502501（决策 9 降级：表单其余字段可先保存）
            throw new CatalogException(CatalogErrorCode.OBJECT_STORAGE_UNAVAILABLE);
        }
    }

    /** V-CAT-069 sanitize：去路径分隔符/控制字符，仅保留 [A-Za-z0-9._-]；剥离前导点（防 "../.." 穿越残留/dotfile） */
    static String sanitizeFileName(String fileName) {
        StringBuilder sb = new StringBuilder(fileName.length());
        for (char c : fileName.toCharArray()) {
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-') {
                sb.append(c);
            }
        }
        int start = 0;
        while (start < sb.length() && sb.charAt(start) == '.') {
            start++;
        }
        return sb.substring(start);
    }

    /** 雪花序 id（时间偏移 << 22 | 序列，单调趋势递增，进程内唯一） */
    static long nextSnowflakeId() {
        long timePart = (System.currentTimeMillis() - EPOCH) << 22;
        long seqPart = SEQUENCE.incrementAndGet() & 0x3FFFFF;
        return timePart | seqPart;
    }
}
