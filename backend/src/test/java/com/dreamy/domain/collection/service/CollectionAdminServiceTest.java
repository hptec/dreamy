package com.dreamy.domain.collection.service;

import com.dreamy.domain.product.repository.ProductCollectionRepository;
import com.dreamy.domain.product.repository.ProductRepository;
import com.dreamy.domain.product.repository.ProductImageRepository;
import com.dreamy.domain.collection.entity.Collection;
import com.dreamy.domain.collection.entity.CollectionGroup;
import com.dreamy.domain.collection.repository.CollectionGroupRepository;
import com.dreamy.domain.collection.repository.CollectionRepository;
import com.dreamy.dto.AdminCatalogDtos.CollectionUpsert;
import com.dreamy.dto.AdminCatalogDtos.CollectionGroupUpsert;
import com.dreamy.dto.TranslationDtos.CollectionTranslationDto;
import com.dreamy.error.CatalogErrorCode;
import com.dreamy.error.CatalogException;
import com.dreamy.infra.CatalogAfterCommitRunner;
import com.dreamy.infra.CatalogAuditRecorder;
import com.dreamy.infra.CatalogCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 集合分组/集合生命周期守卫单元测试（TASK-035/036）。
 * L2 TRACE: TC-CAT-037/038（单测面）/ TC-CAT-059（守卫与级联）/ V-CAT-063~068 / CV-CAT-006。
 */
@ExtendWith(MockitoExtension.class)
class CollectionAdminServiceTest {

    @Mock
    CollectionGroupRepository groupRepository;
    @Mock
    CollectionRepository collectionRepository;
    @Mock
    ProductCollectionRepository productCollectionRepository;
    @Mock
    ProductRepository productRepository;
    @Mock
    ProductImageRepository productImageRepository;
    @Mock
    CatalogCacheService cache;
    @Mock
    CatalogAuditRecorder audit;
    @Mock
    CatalogAfterCommitRunner afterCommit;
    @Mock
    com.dreamy.domain.cache.service.CacheInvalidationTaskService cacheTasks;

    CollectionAdminService service;

    @BeforeEach
    void setUp() {
        service = new CollectionAdminService(groupRepository, collectionRepository, productCollectionRepository,
                productRepository, productImageRepository, audit, cacheTasks, new ObjectMapper());
    }

    @Test
    @DisplayName("TC-CAT-038 [P0]: 分组下仍有集合 → 409506 COLLECTION_GROUP_IN_USE（details.collection_count）")
    void groupDeleteGuard() {
        CollectionGroup group = new CollectionGroup();
        group.setId(1L);
        group.setName("Color");
        when(groupRepository.findById(1L)).thenReturn(group);
        when(collectionRepository.countByGroupId(1L)).thenReturn(3L);
        assertThatThrownBy(() -> service.deleteGroup(1L))
                .satisfies(ex -> {
                    CatalogException ce = (CatalogException) ex;
                    assertThat(ce.getErrorCode()).isEqualTo(CatalogErrorCode.COLLECTION_GROUP_IN_USE);
                    assertThat(ce.getDetails()).containsEntry("collection_count", 3L);
                });
        verify(groupRepository, never()).deleteById(anyLong());
        // 清空集合后可删
        when(collectionRepository.countByGroupId(1L)).thenReturn(0L);
        service.deleteGroup(1L);
        verify(groupRepository).deleteById(1L);
    }

    @Test
    @DisplayName("E-CAT-28 [P0]: 创建集合分组提交后立即失效消费端集合缓存")
    void createGroupInvalidatesCollectionsAfterCommit() {
        service.createGroup(new CollectionGroupUpsert("Style", null, null));

        verify(cacheTasks).enqueue(anyString(), anyString(), anyString(),
                nullable(Object.class), nullable(String.class), anyList(), nullable(java.time.LocalDateTime.class),
                anyMap(), nullable(String.class));
    }

    @Test
    @DisplayName("TC-CAT-059 [P0]: 删除集合级联摘除 product_collection 挂载（RM-CAT-144，无前置 guard）")
    void collectionDeleteCascadesProductCollection() {
        Collection collection = new Collection();
        collection.setId(7L);
        collection.setName("Sage");
        when(collectionRepository.findById(7L)).thenReturn(collection);
        service.deleteCollection(7L);
        InOrder cleanup = inOrder(productCollectionRepository, collectionRepository);
        cleanup.verify(productCollectionRepository).deleteByCollectionId(7L);
        cleanup.verify(collectionRepository).deleteTranslationsByCollectionId(7L);
        cleanup.verify(collectionRepository).deleteById(7L);
        verify(audit).record(org.mockito.ArgumentMatchers.eq("删除集合"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("V-CAT-063~066 [P0]: 分组不存在；name/status/translations 校验")
    void collectionValidation() {
        when(groupRepository.findById(9L)).thenReturn(null);
        assertThatThrownBy(() -> service.createCollection(new CollectionUpsert(9L, "Sage", 1, null)))
                .satisfies(ex -> assertThat(((CatalogException) ex).getErrorCode())
                        .isEqualTo(CatalogErrorCode.COLLECTION_NOT_FOUND));
        CollectionGroup group = new CollectionGroup();
        group.setId(1L);
        when(groupRepository.findById(1L)).thenReturn(group);
        assertThatThrownBy(() -> service.createCollection(new CollectionUpsert(1L, "  ", 1, null)))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("name", "required"));
        assertThatThrownBy(() -> service.createCollection(new CollectionUpsert(1L, "Sage", 3, null)))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("status", "invalid_enum"));
        assertThatThrownBy(() -> service.createCollection(new CollectionUpsert(1L, "Sage", 1,
                List.of(new CollectionTranslationDto("es", "x".repeat(65))))))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("translations", "too_long"));
    }

    @Test
    @DisplayName("TC-CAT-037（单测面）[P0]: enabled↔disabled 流转走编辑端点，审计 changes 含 status 流转")
    void collectionToggleViaUpdate() {
        CollectionGroup group = new CollectionGroup();
        group.setId(1L);
        when(groupRepository.findById(1L)).thenReturn(group);
        Collection collection = new Collection();
        collection.setId(7L);
        collection.setName("Sage");
        collection.setStatus(com.dreamy.enums.CollectionStatus.ENABLED);
        when(collectionRepository.findById(7L)).thenReturn(collection);
        when(productCollectionRepository.countByCollections(false)).thenReturn(Map.of());
        var dto = service.updateCollection(7L, new CollectionUpsert(1L, "Sage", 2, null));
        assertThat(dto.status()).isEqualTo(2);
        verify(audit).record(org.mockito.ArgumentMatchers.eq("编辑集合"),
                org.mockito.ArgumentMatchers.eq("Sage"),
                org.mockito.ArgumentMatchers.contains("\"status\":2"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> fields(Throwable ex) {
        return (Map<String, String>) ((CatalogException) ex).getDetails().get("fields");
    }
}
