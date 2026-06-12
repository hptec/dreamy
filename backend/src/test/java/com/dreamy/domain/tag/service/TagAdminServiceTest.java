package com.dreamy.domain.tag.service;

import com.dreamy.domain.product.repository.ProductTagRepository;
import com.dreamy.domain.tag.entity.Tag;
import com.dreamy.domain.tag.entity.TagDimension;
import com.dreamy.domain.tag.repository.TagDimensionRepository;
import com.dreamy.domain.tag.repository.TagRepository;
import com.dreamy.dto.AdminCatalogDtos.TagUpsert;
import com.dreamy.error.CatalogErrorCode;
import com.dreamy.error.CatalogException;
import com.dreamy.event.ContentInvalidatedPublisher;
import com.dreamy.infra.CatalogAfterCommitRunner;
import com.dreamy.infra.CatalogAuditRecorder;
import com.dreamy.infra.CatalogCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 标签维度/标签生命周期守卫单元测试（TASK-035/036）。
 * L2 TRACE: TC-CAT-037/038（单测面）/ TC-CAT-059（守卫与级联）/ V-CAT-063~068 / CV-CAT-006。
 */
@ExtendWith(MockitoExtension.class)
class TagAdminServiceTest {

    @Mock
    TagDimensionRepository dimensionRepository;
    @Mock
    TagRepository tagRepository;
    @Mock
    ProductTagRepository productTagRepository;
    @Mock
    CatalogCacheService cache;
    @Mock
    CatalogAuditRecorder audit;
    @Mock
    CatalogAfterCommitRunner afterCommit;
    @Mock
    ContentInvalidatedPublisher invalidatedPublisher;

    TagAdminService service;

    @BeforeEach
    void setUp() {
        service = new TagAdminService(dimensionRepository, tagRepository, productTagRepository, cache,
                audit, afterCommit, invalidatedPublisher, new ObjectMapper());
    }

    @Test
    @DisplayName("TC-CAT-038 [P0]: 维度下仍有标签 → 409506 TAG_DIMENSION_IN_USE（details.tag_count）")
    void dimensionDeleteGuard() {
        TagDimension dim = new TagDimension();
        dim.setId(1L);
        dim.setName("Color");
        when(dimensionRepository.findById(1L)).thenReturn(dim);
        when(tagRepository.countByDimensionId(1L)).thenReturn(3L);
        assertThatThrownBy(() -> service.deleteDimension(1L))
                .satisfies(ex -> {
                    CatalogException ce = (CatalogException) ex;
                    assertThat(ce.getErrorCode()).isEqualTo(CatalogErrorCode.TAG_DIMENSION_IN_USE);
                    assertThat(ce.getDetails()).containsEntry("tag_count", 3L);
                });
        verify(dimensionRepository, never()).deleteById(anyLong());
        // 清空标签后可删
        when(tagRepository.countByDimensionId(1L)).thenReturn(0L);
        service.deleteDimension(1L);
        verify(dimensionRepository).deleteById(1L);
    }

    @Test
    @DisplayName("TC-CAT-059 [P0]: 删除标签级联摘除 product_tag 挂载（RM-CAT-144，无前置 guard）")
    void tagDeleteCascadesProductTag() {
        Tag tag = new Tag();
        tag.setId(7L);
        tag.setName("Sage");
        when(tagRepository.findById(7L)).thenReturn(tag);
        service.deleteTag(7L);
        verify(tagRepository).deleteById(7L);
        verify(tagRepository).deleteTranslationsByTagId(7L);
        verify(productTagRepository).deleteByTagId(7L);
        verify(audit).record(org.mockito.ArgumentMatchers.eq("删除标签"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("V-CAT-063~065 [P0]: 维度不存在 → 404505；name 必填；status 枚举外 → 422501")
    void tagValidation() {
        when(dimensionRepository.findById(9L)).thenReturn(null);
        assertThatThrownBy(() -> service.createTag(new TagUpsert(9L, "Sage", null, "enabled", null)))
                .satisfies(ex -> assertThat(((CatalogException) ex).getErrorCode())
                        .isEqualTo(CatalogErrorCode.TAG_NOT_FOUND));
        TagDimension dim = new TagDimension();
        dim.setId(1L);
        when(dimensionRepository.findById(1L)).thenReturn(dim);
        assertThatThrownBy(() -> service.createTag(new TagUpsert(1L, "  ", null, "enabled", null)))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("name", "required"));
        assertThatThrownBy(() -> service.createTag(new TagUpsert(1L, "Sage", null, "archived", null)))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("status", "invalid_enum"));
        assertThatThrownBy(() -> service.createTag(new TagUpsert(1L, "Sage", "x".repeat(513), "enabled", null)))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("cover", "too_long"));
    }

    @Test
    @DisplayName("TC-CAT-037（单测面）[P0]: enabled↔disabled 流转走编辑端点，审计 changes 含 status 流转")
    void tagToggleViaUpdate() {
        TagDimension dim = new TagDimension();
        dim.setId(1L);
        when(dimensionRepository.findById(1L)).thenReturn(dim);
        Tag tag = new Tag();
        tag.setId(7L);
        tag.setName("Sage");
        tag.setStatus(com.dreamy.enums.TagStatus.ENABLED);
        when(tagRepository.findById(7L)).thenReturn(tag);
        when(productTagRepository.countByTags(false)).thenReturn(Map.of());
        var dto = service.updateTag(7L, new TagUpsert(1L, "Sage", null, "disabled", null));
        assertThat(dto.status()).isEqualTo("disabled");
        verify(audit).record(org.mockito.ArgumentMatchers.eq("编辑标签"),
                org.mockito.ArgumentMatchers.eq("Sage"),
                org.mockito.ArgumentMatchers.contains("disabled"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> fields(Throwable ex) {
        return (Map<String, String>) ((CatalogException) ex).getDetails().get("fields");
    }
}
