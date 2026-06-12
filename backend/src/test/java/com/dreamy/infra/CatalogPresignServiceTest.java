package com.dreamy.infra;

import com.dreamy.dto.PresignDtos.PresignRequest;
import com.dreamy.dto.PresignDtos.PresignResponse;
import com.dreamy.error.CatalogErrorCode;
import com.dreamy.error.CatalogException;
import com.dreamy.infra.storage.PresignResult;
import com.dreamy.infra.storage.PresignService;
import com.dreamy.infra.storage.StorageUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预签名入参校验/降级单元测试（决策 9）。
 * L2 TRACE: TC-CAT-015 / TC-CAT-073（502501 单测面）/ V-CAT-069~071 / E-CAT-35。
 */
@ExtendWith(MockitoExtension.class)
class CatalogPresignServiceTest {

    @Mock
    PresignService presignService;
    @InjectMocks
    CatalogPresignService service;

    @Test
    @DisplayName("TC-CAT-015 [P1]: file_name sanitize 剥离路径穿越；MIME 白名单外拒绝；scope 缺省 product")
    void sanitizeAndWhitelist() {
        // sanitize 静态校验
        assertThat(CatalogPresignService.sanitizeFileName("../../etc/passwd")).isEqualTo("etcpasswd");
        assertThat(CatalogPresignService.sanitizeFileName("my photo (1).jpg")).isEqualTo("myphoto1.jpg");
        assertThat(CatalogPresignService.sanitizeFileName("///")).isEmpty();
        // MIME 白名单外 → 422501 fields.content_type=unsupported
        assertThatThrownBy(() -> service.presign(new PresignRequest("a.svg", "image/svg+xml", null)))
                .isInstanceOf(CatalogException.class)
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("content_type", "unsupported"));
        // file_name sanitize 后为空 → 422501
        assertThatThrownBy(() -> service.presign(new PresignRequest("///", "image/jpeg", null)))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("file_name", "invalid"));
        // scope 枚举外 → 422501
        assertThatThrownBy(() -> service.presign(new PresignRequest("a.jpg", "image/jpeg", "avatar")))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("scope", "invalid_enum"));
        // 合法请求：scope 缺省 product → 对象 key 前缀 product/
        when(presignService.presign(anyString(), eq("image/jpeg")))
                .thenAnswer(inv -> new PresignResult("http://upload", inv.getArgument(0),
                        "http://cdn/" + inv.getArgument(0), OffsetDateTime.now().plusMinutes(10)));
        PresignResponse resp = service.presign(new PresignRequest("a.jpg", "image/jpeg", null));
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(presignService).presign(keyCaptor.capture(), eq("image/jpeg"));
        assertThat(keyCaptor.getValue()).startsWith("product/").endsWith("/a.jpg");
        assertThat(resp.uploadUrl()).isEqualTo("http://upload");
        assertThat(resp.publicUrl()).startsWith("http://cdn/product/");
    }

    @Test
    @DisplayName("TC-CAT-073（单测面）[P0]: S3 不可达 → 502501 OBJECT_STORAGE_UNAVAILABLE（决策 9 降级）")
    void storageUnavailable() {
        when(presignService.presign(anyString(), eq("image/png")))
                .thenThrow(new StorageUnavailableException("timeout"));
        assertThatThrownBy(() -> service.presign(new PresignRequest("b.png", "image/png", "banner")))
                .isInstanceOf(CatalogException.class)
                .satisfies(ex -> assertThat(((CatalogException) ex).getErrorCode())
                        .isEqualTo(CatalogErrorCode.OBJECT_STORAGE_UNAVAILABLE));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> fields(Throwable ex) {
        return (Map<String, String>) ((CatalogException) ex).getDetails().get("fields");
    }
}
