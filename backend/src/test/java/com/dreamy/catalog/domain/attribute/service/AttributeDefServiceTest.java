package com.dreamy.catalog.domain.attribute.service;

import com.dreamy.catalog.domain.attribute.entity.AttributeDef;
import com.dreamy.catalog.domain.attribute.repository.AttributeDefRepository;
import com.dreamy.catalog.domain.attribute.repository.AttributeSetRepository;
import com.dreamy.catalog.domain.enums.AttributeType;
import com.dreamy.catalog.dto.AdminCatalogDtos.AttributeDefUpsert;
import com.dreamy.catalog.dto.TranslationDtos.AttributeDefTranslationDto;
import com.dreamy.catalog.error.CatalogErrorCode;
import com.dreamy.catalog.error.CatalogException;
import com.dreamy.catalog.infra.CatalogAuditRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 属性定义 options guard 单元测试。
 * L2 TRACE: TC-CAT-012 / V-CAT-053~058 / CV-CAT-007。
 */
@ExtendWith(MockitoExtension.class)
class AttributeDefServiceTest {

    @Mock
    AttributeDefRepository defRepository;
    @Mock
    AttributeSetRepository setRepository;
    @Mock
    CatalogAuditRecorder audit;
    @InjectMocks
    AttributeDefService service;

    @SuppressWarnings("unchecked")
    private static Map<String, String> fields(Throwable ex) {
        return (Map<String, String>) ((CatalogException) ex).getDetails().get("fields");
    }

    @Test
    @DisplayName("TC-CAT-012 [P1]: select/multiselect options 必填非空且去重；text/toggle 禁止提交 options")
    void optionsGuard() {
        lenient().when(defRepository.existsByKey("silhouette")).thenReturn(false);
        // select 缺 options
        assertThatThrownBy(() -> service.create(new AttributeDefUpsert("silhouette", "Silhouette",
                "select", null, null)))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("options", "required"));
        // select options 重复
        assertThatThrownBy(() -> service.create(new AttributeDefUpsert("silhouette", "Silhouette",
                "select", List.of("A-Line", "A-Line"), null)))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("options", "duplicated"));
        // text 提交 options
        assertThatThrownBy(() -> service.create(new AttributeDefUpsert("care", "Care",
                "text", List.of("x"), null)))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("options", "not_allowed"));
        // type 枚举外
        assertThatThrownBy(() -> service.create(new AttributeDefUpsert("care", "Care",
                "checkbox", null, null)))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("type", "invalid_enum"));
    }

    @Test
    @DisplayName("TC-CAT-012 [P1]: 译文 options 与主表等长校验（V-CAT-058 错位 → options_length_mismatch）")
    void translationOptionsLengthMismatch() {
        lenient().when(defRepository.existsByKey("silhouette")).thenReturn(false);
        assertThatThrownBy(() -> service.create(new AttributeDefUpsert("silhouette", "Silhouette",
                "select", List.of("A-Line", "Mermaid"),
                List.of(new AttributeDefTranslationDto("es", "Silueta", List.of("Línea A"))))))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("translations", "options_length_mismatch"));
    }

    @Test
    @DisplayName("V-CAT-053 [P0]: key 必填/pattern/重复 → 422501（fields.key=exists，契约无 409）")
    void keyValidation() {
        assertThatThrownBy(() -> service.create(new AttributeDefUpsert(null, "L", "text", null, null)))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("key", "required"));
        assertThatThrownBy(() -> service.create(new AttributeDefUpsert("Bad-Key", "L", "text", null, null)))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("key", "pattern"));
        when(defRepository.existsByKey("dup_key")).thenReturn(true);
        assertThatThrownBy(() -> service.create(new AttributeDefUpsert("dup_key", "L", "text", null, null)))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("key", "exists"));
    }

    @Test
    @DisplayName("V-CAT-057 [P0]: 编辑场景 key/type 不可变更 → 422501 immutable；不存在 → 404504")
    void updateImmutability() {
        AttributeDef existing = new AttributeDef();
        existing.setId(1L);
        existing.setKey("silhouette");
        existing.setType(AttributeType.SELECT);
        existing.setLabel("Silhouette");
        when(defRepository.findById(1L)).thenReturn(existing);
        assertThatThrownBy(() -> service.update(1L, new AttributeDefUpsert("renamed", "L", "select",
                List.of("A"), null)))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("key", "immutable"));
        assertThatThrownBy(() -> service.update(1L, new AttributeDefUpsert("silhouette", "L", "text",
                null, null)))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("type", "immutable"));
        when(defRepository.findById(9L)).thenReturn(null);
        assertThatThrownBy(() -> service.update(9L, new AttributeDefUpsert("k", "L", "text", null, null)))
                .satisfies(ex -> assertThat(((CatalogException) ex).getErrorCode())
                        .isEqualTo(CatalogErrorCode.ATTRIBUTE_DEF_NOT_FOUND));
    }

    @Test
    @DisplayName("TC-CAT-058（单测面）[P0]: 被属性集引用删除 → 409507（details.attribute_set_count）")
    void deleteInUseGuard() {
        AttributeDef existing = new AttributeDef();
        existing.setId(1L);
        existing.setLabel("Silhouette");
        when(defRepository.findById(1L)).thenReturn(existing);
        when(setRepository.countItemsByAttributeId(1L)).thenReturn(2L);
        assertThatThrownBy(() -> service.delete(1L))
                .satisfies(ex -> {
                    CatalogException ce = (CatalogException) ex;
                    assertThat(ce.getErrorCode()).isEqualTo(CatalogErrorCode.ATTRIBUTE_DEF_IN_USE);
                    assertThat(ce.getDetails()).containsEntry("attribute_set_count", 2L);
                });
    }
}
