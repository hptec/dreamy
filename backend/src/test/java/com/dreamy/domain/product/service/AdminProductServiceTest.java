package com.dreamy.domain.product.service;

import com.dreamy.domain.attribute.repository.AttributeDefRepository;
import com.dreamy.domain.attribute.service.ProductAttributeConfigService;
import com.dreamy.domain.category.entity.Category;
import com.dreamy.domain.category.repository.CategoryRepository;
import com.dreamy.domain.category.service.CategoryTreeService;
import com.dreamy.enums.ProductStatus;
import com.dreamy.domain.product.entity.Product;
import com.dreamy.domain.product.entity.Sku;
import com.dreamy.domain.product.repository.ProductAttributeValueRepository;
import com.dreamy.domain.product.repository.ProductImageRepository;
import com.dreamy.domain.product.repository.ProductRepository;
import com.dreamy.domain.product.repository.ProductCollectionRepository;
import com.dreamy.domain.product.repository.ProductTranslationRepository;
import com.dreamy.domain.product.repository.SizeChartRowRepository;
import com.dreamy.domain.product.repository.SkuRepository;
import com.dreamy.domain.collection.repository.CollectionRepository;
import com.dreamy.dto.AdminProductUpsert;
import com.dreamy.dto.SkuDto;
import com.dreamy.error.CatalogErrorCode;
import com.dreamy.error.CatalogException;
import com.dreamy.event.ContentInvalidatedPublisher;
import com.dreamy.infra.CatalogAfterCommitRunner;
import com.dreamy.infra.CatalogAuditRecorder;
import com.dreamy.infra.CatalogCacheService;
import com.dreamy.port.TradingQueryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 后台商品服务单元测试（CAS 防丢失 / 状态机 guard / 幂等短路 / flags minProperties）。
 * STUB_SCOPE: repository_io + 基建（cache/audit/MQ/事务模板）。
 * L2 TRACE: TC-CAT-018（CAS 409508 单测面）/ TC-CAT-033~035（product_lifecycle）/ V-CAT-040~042。
 */
@ExtendWith(MockitoExtension.class)
class AdminProductServiceTest {

    @Mock
    ProductRepository productRepository;
    @Mock
    ProductTranslationRepository translationRepository;
    @Mock
    ProductImageRepository imageRepository;
    @Mock
    SkuRepository skuRepository;
    @Mock
    SizeChartRowRepository sizeChartRepository;
    @Mock
    ProductCollectionRepository productCollectionRepository;
    @Mock
    ProductAttributeValueRepository attributeValueRepository;
    @Mock
    AttributeDefRepository attributeDefRepository;
    @Mock
    ProductAttributeConfigService attributeConfigService;
    @Mock
    CollectionRepository collectionRepository;
    @Mock
    CategoryRepository categoryRepository;
    @Mock
    CategoryTreeService treeService;
    @Mock
    CatalogCacheService cache;
    @Mock
    CatalogAuditRecorder audit;
    @Mock
    CatalogAfterCommitRunner afterCommit;
    @Mock
    ContentInvalidatedPublisher invalidatedPublisher;
    @Mock
    TradingQueryPort tradingQueryPort;
    @Mock
    TransactionTemplate transactionTemplate;

    AdminProductService service;

    @BeforeEach
    void setUp() {
        service = new AdminProductService(productRepository, translationRepository, imageRepository,
                skuRepository, sizeChartRepository, productCollectionRepository, attributeValueRepository,
                attributeDefRepository, attributeConfigService, collectionRepository, categoryRepository,
                treeService, cache, audit, afterCommit, invalidatedPublisher, tradingQueryPort,
                transactionTemplate, new ObjectMapper());
    }

    private static Product published(long id) {
        Product p = new Product();
        p.setId(id);
        p.setName("Aurelia");
        p.setSlug("aurelia-gown");
        p.setStatus(ProductStatus.PUBLISHED);
        p.setCategoryId(1L);
        return p;
    }

    private static AdminProductUpsert upsertWithSku(SkuDto sku) {
        return new AdminProductUpsert("Aurelia", "aurelia-gown", 1L, null, null, null,
                new BigDecimal("1280"), null, null, null, 2, null, null, null, 0, 45, null, null,
                null, null, null, null, null, List.of(sku), null, null, null, null,
                null, null, null);
    }

    @Test
    @DisplayName("TC-CAT-018（单测面）[P0]: SKU CAS affected=0 → 409508 PRODUCT_VERSION_CONFLICT（EC-CAT-001 不重试）")
    void skuCasConflict() {
        Product existing = published(10L);
        when(productRepository.findById(10L)).thenReturn(existing);
        Sku owned = new Sku();
        owned.setId(5L);
        owned.setVersion(2L);
        when(skuRepository.listByProductId(10L)).thenReturn(List.of(owned));
        when(categoryRepository.findById(1L)).thenReturn(new Category());
        when(productRepository.existsBySlugExcept("aurelia-gown", 10L)).thenReturn(false);
        when(skuRepository.existsBySkuCodes(anyCollection(), eq(10L))).thenReturn(List.of());
        // 携带过期 version=1 → CAS affected=0
        when(skuRepository.casUpdate(any(Sku.class), eq(1L))).thenReturn(0);
        assertThatThrownBy(() -> service.update(10L,
                upsertWithSku(new SkuDto(5L, "AUR-IVORY-2", "Ivory", "US 2", 5, 1L))))
                .isInstanceOf(CatalogException.class)
                .satisfies(ex -> assertThat(((CatalogException) ex).getErrorCode())
                        .isEqualTo(CatalogErrorCode.PRODUCT_VERSION_CONFLICT));
        // 主表更新未发生（整体回滚语义由 @Transactional 承载，此处断言短路）
        verify(productRepository, never()).update(any());
    }

    @Test
    @DisplayName("TC-CAT-034 [P0]: published 直删被拒 → 409509；不存在 → 404501")
    void deleteGuards() {
        when(productRepository.findById(10L)).thenReturn(published(10L));
        assertThatThrownBy(() -> service.delete(10L))
                .satisfies(ex -> assertThat(((CatalogException) ex).getErrorCode())
                        .isEqualTo(CatalogErrorCode.PRODUCT_NOT_DELETABLE));
        verify(productRepository, never()).deleteById(anyLong());
        when(productRepository.findById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(99L))
                .satisfies(ex -> assertThat(((CatalogException) ex).getErrorCode())
                        .isEqualTo(CatalogErrorCode.PRODUCT_NOT_FOUND));
    }

    @Test
    @DisplayName("TC-CAT-033 [P0]: draft 删除成功 → 七表级联物理删除（含 EAV，TX-CAT-003）")
    void draftDeleteCascades() {
        Product draft = published(11L);
        draft.setStatus(ProductStatus.DRAFT);
        when(productRepository.findById(11L)).thenReturn(draft);
        service.delete(11L);
        verify(productRepository).deleteById(11L);
        verify(imageRepository).deleteByProductId(11L);
        verify(skuRepository).deleteByProductId(11L);
        verify(sizeChartRepository).deleteByProductId(11L);
        verify(productCollectionRepository).deleteByProductId(11L);
        verify(translationRepository).deleteByProductId(11L);
        verify(attributeValueRepository).deleteByProductId(11L);
        verify(audit).record(eq("删除商品"), any(), any());
    }

    @Test
    @DisplayName("TC-CAT-035 [P1]: 同态幂等——published 重复 publish → 短路不开事务不写审计不发事件")
    void toggleIdempotentShortCircuit() {
        Product existing = published(10L);
        when(productRepository.findById(10L)).thenReturn(existing);
        when(skuRepository.sumStockByProductIds(anyCollection())).thenReturn(java.util.Map.of());
        when(tradingQueryPort.sumSalesTotalByProductIds(anyCollection())).thenReturn(java.util.Map.of());
        when(imageRepository.listByProductIds(anyCollection())).thenReturn(List.of());
        when(categoryRepository.listAll()).thenReturn(List.of());
        var item = service.toggleStatus(10L, 2);
        assertThat(item.status()).isEqualTo(2);
        verify(transactionTemplate, never()).executeWithoutResult(any());
        verify(audit, never()).record(any(), any(), any());
        verify(invalidatedPublisher, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("RM-CAT-01b/c [P0]: 列表行 sales_total 端口批量聚合合并；缺失 product_id → 0（API-CAT-03）")
    void salesTotalMergedFromTradingPort() {
        Product existing = published(10L);
        when(productRepository.findById(10L)).thenReturn(existing);
        when(skuRepository.sumStockByProductIds(anyCollection())).thenReturn(java.util.Map.of());
        when(imageRepository.listByProductIds(anyCollection())).thenReturn(List.of());
        when(categoryRepository.listAll()).thenReturn(List.of());
        // RM-CAT-01b 一次聚合命中 → 合并到 DTO
        when(tradingQueryPort.sumSalesTotalByProductIds(anyCollection())).thenReturn(java.util.Map.of(10L, 7));
        assertThat(service.toggleStatus(10L, 2).salesTotal()).isEqualTo(7);
        // RM-CAT-01c 缺失 product_id → sales_total = 0
        when(tradingQueryPort.sumSalesTotalByProductIds(anyCollection())).thenReturn(java.util.Map.of());
        assertThat(service.toggleStatus(10L, 2).salesTotal()).isZero();
    }

    @Test
    @DisplayName("V-CAT-040 [P0]: status 必填/枚举外 → 422501")
    void toggleStatusValidation() {
        assertThatThrownBy(() -> service.toggleStatus(10L, null))
                .satisfies(ex -> assertThat(((CatalogException) ex).getErrorCode())
                        .isEqualTo(CatalogErrorCode.FIELD_VALIDATION_FAILED));
        assertThatThrownBy(() -> service.toggleStatus(10L, 3))
                .satisfies(ex -> assertThat(((CatalogException) ex).getErrorCode())
                        .isEqualTo(CatalogErrorCode.FIELD_VALIDATION_FAILED));
    }

    @Test
    @DisplayName("V-CAT-041/042 [P0]: flags 空对象 → 422501 fields._body=empty；sort<0 → range_invalid")
    void flagsValidation() {
        assertThatThrownBy(() -> service.patchFlags(10L, null, null, null, null))
                .satisfies(ex -> assertThat(((CatalogException) ex).getErrorCode())
                        .isEqualTo(CatalogErrorCode.FIELD_VALIDATION_FAILED));
        assertThatThrownBy(() -> service.patchFlags(10L, true, null, null, -1))
                .satisfies(ex -> assertThat(((CatalogException) ex).getErrorCode())
                        .isEqualTo(CatalogErrorCode.FIELD_VALIDATION_FAILED));
        verify(productRepository, never()).patchFlags(anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("E-CAT-09 STEP-CAT-01/02 [P0]: slug 冲突 → 409501；sku_code 冲突 → 409504（details.sku_codes）")
    void uniquenessConflicts() {
        lenient().when(categoryRepository.findById(1L)).thenReturn(new Category());
        when(productRepository.existsBySlugExcept("aurelia-gown", null)).thenReturn(true);
        AdminProductUpsert req = upsertWithSku(new SkuDto(null, "AUR-IVORY-2", "Ivory", "US 2", 5, null));
        assertThatThrownBy(() -> service.create(req))
                .satisfies(ex -> assertThat(((CatalogException) ex).getErrorCode())
                        .isEqualTo(CatalogErrorCode.SLUG_EXISTS));
        when(productRepository.existsBySlugExcept("aurelia-gown", null)).thenReturn(false);
        when(skuRepository.existsBySkuCodes(anyCollection(), eq((Long) null)))
                .thenReturn(List.of("AUR-IVORY-2"));
        assertThatThrownBy(() -> service.create(req))
                .satisfies(ex -> {
                    CatalogException ce = (CatalogException) ex;
                    assertThat(ce.getErrorCode()).isEqualTo(CatalogErrorCode.SKU_CODE_EXISTS);
                    assertThat(ce.getDetails()).containsEntry("sku_codes", List.of("AUR-IVORY-2"));
                });
    }

    @Test
    @DisplayName("E-CAT-11 STEP-CAT-03 [P0]: 无 SKU 回传 updated_at 与 DB 不一致 → 409508")
    void updatedAtConflictForCustomOnlyProduct() {
        Product existing = published(10L);
        existing.setUpdatedAt(java.time.LocalDateTime.of(2026, 6, 1, 10, 0, 0));
        when(productRepository.findById(10L)).thenReturn(existing);
        when(skuRepository.listByProductId(10L)).thenReturn(List.of());
        when(categoryRepository.findById(1L)).thenReturn(new Category());
        when(productRepository.existsBySlugExcept("aurelia-gown", 10L)).thenReturn(false);
        AdminProductUpsert req = new AdminProductUpsert("Aurelia", "aurelia-gown", 1L, null, null,
                null, new BigDecimal("1280"), null, null, null, 2, null, null, null, 0, 45,
                null, null, null, null, null, null, null, null, null, null, null, "2026-06-01T09:00:00",
                null, null, null);
        assertThatThrownBy(() -> service.update(10L, req))
                .satisfies(ex -> assertThat(((CatalogException) ex).getErrorCode())
                        .isEqualTo(CatalogErrorCode.PRODUCT_VERSION_CONFLICT));
    }
}
