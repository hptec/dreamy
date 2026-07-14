package com.dreamy.domain.attribute.service;

import com.dreamy.domain.attribute.entity.AttributeSet;
import com.dreamy.domain.attribute.repository.AttributeDefRepository;
import com.dreamy.domain.attribute.repository.AttributeSetRepository;
import com.dreamy.domain.category.repository.CategoryRepository;
import com.dreamy.error.CatalogErrorCode;
import com.dreamy.error.CatalogException;
import com.dreamy.infra.CatalogAfterCommitRunner;
import com.dreamy.infra.CatalogAuditRecorder;
import com.dreamy.infra.CatalogCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 属性集删除守卫与物理删除顺序单元测试（TX-CAT-011）。 */
@ExtendWith(MockitoExtension.class)
class AttributeSetServiceTest {

    @Mock
    AttributeSetRepository setRepository;
    @Mock
    AttributeDefRepository defRepository;
    @Mock
    CategoryRepository categoryRepository;
    @Mock
    CatalogAuditRecorder audit;
    @Mock
    CatalogCacheService cache;
    @Mock
    CatalogAfterCommitRunner afterCommit;
    @InjectMocks
    AttributeSetService service;

    @Test
    @DisplayName("TX-CAT-011 [P0]: 被分类引用时返回 409503，且不清关联、不删除主记录")
    void deleteInUseGuard() {
        AttributeSet existing = attributeSet(1L, "Wedding Dress");
        when(setRepository.findById(1L)).thenReturn(existing);
        when(categoryRepository.countByAttributeSetId(1L)).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(CatalogException.class)
                .satisfies(ex -> {
                    CatalogException ce = (CatalogException) ex;
                    assertThat(ce.getErrorCode()).isEqualTo(CatalogErrorCode.ATTRIBUTE_SET_IN_USE);
                    assertThat(ce.getDetails()).containsEntry("category_count", 2L);
                });

        verify(setRepository, never()).replaceItems(anyLong(), anyList());
        verify(setRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("TX-CAT-011 [P0]: 未被引用时先清关联项，再物理删除属性集")
    void deletePhysicallyAfterItems() {
        AttributeSet existing = attributeSet(1L, "Wedding Dress");
        when(setRepository.findById(1L)).thenReturn(existing);
        when(categoryRepository.countByAttributeSetId(1L)).thenReturn(0L);

        service.delete(1L);

        InOrder deletion = inOrder(setRepository);
        deletion.verify(setRepository).replaceItems(1L, List.of());
        deletion.verify(setRepository).deleteById(1L);
    }

    private static AttributeSet attributeSet(long id, String label) {
        AttributeSet set = new AttributeSet();
        set.setId(id);
        set.setLabel(label);
        return set;
    }
}
