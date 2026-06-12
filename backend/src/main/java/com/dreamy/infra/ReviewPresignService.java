package com.dreamy.infra;

import com.dreamy.infra.storage.PresignResult;
import com.dreamy.infra.storage.PresignService;
import com.dreamy.infra.storage.StorageUnavailableException;
import com.dreamy.dto.ReviewDtos.PresignRequest;
import com.dreamy.dto.ReviewDtos.PresignResponse;
import com.dreamy.error.ReviewErrorCode;
import com.dreamy.error.ReviewException;
import com.dreamy.support.ReviewFieldErrors;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 买家秀预签名服务（E-REV-05；决策 9 / FLOW-P17，复用 catalog 代管的 infra PresignService 基建）。
 * scope 固定 review（对象 key `review/{雪花序id}/{sanitizedFileName}`，V-REV-007 校验依据）；
 * MIME 仅图片三类（V-REV-013，**不含 video/mp4**——区别于 admin presign）。
 * 读侧基建：不写 operation_log、不发 MQ、不缓存（STEP-REV-04）。
 * L2 TRACE: V-REV-012/013 / E-REV-05 STEP-REV-01~04 / TC-REV-007/041/049。
 */
@Service
public class ReviewPresignService {

    /** V-REV-013 MIME 白名单（买家秀仅图片） */
    static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    /** scope 固定 review（决策 9 预签名归类） */
    static final String SCOPE = "review";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AtomicLong SEQUENCE = new AtomicLong(RANDOM.nextInt(1 << 12));
    /** 2024-01-01T00:00:00Z 起始纪元（雪花序时间基准，与 catalog 同口径） */
    private static final long EPOCH = 1704067200000L;

    private final PresignService presignService;

    public ReviewPresignService(PresignService presignService) {
        this.presignService = presignService;
    }

    public PresignResponse presign(PresignRequest req) {
        ReviewFieldErrors errors = new ReviewFieldErrors();
        // V-REV-012 file_name 必填 ≤255 + sanitize（与 catalog V-CAT-069 同口径）
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
        // V-REV-013 content_type ∈ {image/jpeg, image/png, image/webp}
        if (req.contentType() == null || req.contentType().isBlank()) {
            errors.reject("content_type", "required");
        } else if (!ALLOWED_CONTENT_TYPES.contains(req.contentType())) {
            errors.reject("content_type", "unsupported");
        }
        errors.throwIfAny();
        // STEP-REV-01 对象 key：review/{雪花序id}/{sanitizedFileName}
        String objectKey = SCOPE + "/" + nextSnowflakeId() + "/" + sanitized;
        try {
            // STEP-REV-02/04 预签名（有效期 600s；超时 3s 由 infra 收口）+ public_url 拼装
            PresignResult result = presignService.presign(objectKey, req.contentType());
            return new PresignResponse(result.uploadUrl(), result.objectKey(), result.publicUrl(),
                    result.expiresAt());
        } catch (StorageUnavailableException ex) {
            // STEP-REV-03 S3 不可达/超时 → 502801（决策 9 降级：评价可先不带图提交）
            throw new ReviewException(ReviewErrorCode.OBJECT_STORAGE_UNAVAILABLE);
        }
    }

    /** V-REV-012 sanitize：去路径分隔符/控制字符，仅保留 [A-Za-z0-9._-]；剥离前导点（防穿越残留/dotfile） */
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
