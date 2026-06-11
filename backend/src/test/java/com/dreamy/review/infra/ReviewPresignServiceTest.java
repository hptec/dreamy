package com.dreamy.review.infra;

import com.dreamy.infra.storage.PresignResult;
import com.dreamy.infra.storage.PresignService;
import com.dreamy.infra.storage.StorageUnavailableException;
import com.dreamy.review.dto.ReviewDtos.PresignRequest;
import com.dreamy.review.dto.ReviewDtos.PresignResponse;
import com.dreamy.review.error.ReviewErrorCode;
import com.dreamy.review.error.ReviewException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.when;

/**
 * E-REV-05 买家秀预签名单元测试。
 * L2 TRACE: TC-REV-007 [P1]（sanitize 路径穿越 / MIME 三白名单，store 与 admin 白名单差异：
 * 不含 video/mp4）/ TC-REV-041 单测面（object_key 含 review/ 前缀）/ TC-REV-049 [P0]（502801 降级）。
 */
@ExtendWith(MockitoExtension.class)
class ReviewPresignServiceTest {

    @Mock
    PresignService presignService;

    ReviewPresignService service;

    @BeforeEach
    void setUp() {
        service = new ReviewPresignService(presignService);
    }

    @Test
    @DisplayName("TC-REV-041 [P1]: 合法 MIME → 四字段齐全且 object_key 以 review/ 归类（V-REV-007 校验依据）")
    void presignHappyPath() {
        when(presignService.presign(startsWith("review/"), anyString())).thenAnswer(inv ->
                new PresignResult("https://s3/upload", inv.getArgument(0),
                        "http://localhost:9000/dreamy-media/" + inv.getArgument(0),
                        OffsetDateTime.now().plusSeconds(600)));
        PresignResponse resp = service.presign(new PresignRequest("photo.jpg", "image/jpeg"));
        assertThat(resp.uploadUrl()).isNotBlank();
        assertThat(resp.objectKey()).startsWith("review/").endsWith("/photo.jpg");
        assertThat(resp.publicUrl()).contains("/review/");
        assertThat(resp.expiresAt()).isNotNull();
    }

    @Test
    @DisplayName("TC-REV-007 [P1]: MIME 白名单——jpeg/png/webp 通过；video/mp4 与 image/gif 拒绝（store/admin 差异）")
    void mimeWhitelist() {
        assertThat(ReviewPresignService.ALLOWED_CONTENT_TYPES)
                .containsExactlyInAnyOrder("image/jpeg", "image/png", "image/webp");
        for (String bad : new String[]{"video/mp4", "image/gif"}) {
            assertThatThrownBy(() -> service.presign(new PresignRequest("a.bin", bad)))
                    .isInstanceOfSatisfying(ReviewException.class, ex ->
                            assertThat(ex.getErrorCode()).isEqualTo(ReviewErrorCode.FIELD_VALIDATION_FAILED));
        }
    }

    @Test
    @DisplayName("TC-REV-007 [P1]: file_name sanitize——路径穿越剥离；超长/空结果 422801")
    void fileNameSanitize() {
        assertThat(ReviewPresignService.sanitizeFileName("../../etc/passwd")).isEqualTo("etcpasswd");
        assertThat(ReviewPresignService.sanitizeFileName("my photo (1).jpg")).isEqualTo("myphoto1.jpg");
        assertThatThrownBy(() -> service.presign(new PresignRequest("///", "image/jpeg")))
                .isInstanceOf(ReviewException.class);
        assertThatThrownBy(() -> service.presign(new PresignRequest("a".repeat(256), "image/jpeg")))
                .isInstanceOf(ReviewException.class);
        assertThatThrownBy(() -> service.presign(new PresignRequest(null, "image/jpeg")))
                .isInstanceOf(ReviewException.class);
    }

    @Test
    @DisplayName("TC-REV-049 [P0]: S3 不可达/超时 → 502801 OBJECT_STORAGE_UNAVAILABLE（决策 9 降级）")
    void storageUnavailable() {
        when(presignService.presign(anyString(), anyString()))
                .thenThrow(new StorageUnavailableException("timeout"));
        assertThatThrownBy(() -> service.presign(new PresignRequest("photo.jpg", "image/png")))
                .isInstanceOfSatisfying(ReviewException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ReviewErrorCode.OBJECT_STORAGE_UNAVAILABLE));
    }
}
