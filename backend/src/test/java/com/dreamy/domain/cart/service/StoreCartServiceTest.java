package com.dreamy.domain.cart.service;

import com.dreamy.error.CatalogException;
import com.dreamy.domain.cart.entity.CartItem;
import com.dreamy.domain.cart.repository.CartItemRepository;
import com.dreamy.domain.cart.repository.CartMergeRecordRepository;
import com.dreamy.dto.TradingDtos.CartItemCreate;
import com.dreamy.dto.TradingDtos.CartResponse;
import com.dreamy.dto.TradingDtos.CustomSizeData;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.port.TradingCatalogSnapshotPort;
import com.dreamy.port.TradingCatalogSnapshotPort.ProductBrief;
import com.dreamy.port.TradingCatalogSnapshotPort.SkuBrief;
import com.dreamy.port.TradingDyeLotPort;
import com.dreamy.testsupport.TradingImmediateTxRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 购物车服务单测（FLOW-P04，决策 8）。
 * L2 TRACE: TC-TRD-003 [P0]（双模式 guard：现货缺 sku → 422604；定制缺数据 → 422604；二者同传 422604）/
 * TC-TRD-038 [P1] 单测面（同 anon_token 幂等短路不重复累加 + 超库存截断标记）/ 409601 加车提示。
 */
@ExtendWith(MockitoExtension.class)
class StoreCartServiceTest {

    private static final long CUSTOMER = 7L;
    private static final long PRODUCT = 11L;
    private static final long SKU = 21L;

    @Mock
    CartItemRepository cartItemRepository;
    @Mock
    CartMergeRecordRepository cartMergeRecordRepository;
    @Mock
    TradingCatalogSnapshotPort catalogSnapshotPort;
    @Mock
    TradingDyeLotPort dyeLotPort;

    StoreCartService service;

    @BeforeEach
    void setUp() {
        service = new StoreCartService(cartItemRepository, cartMergeRecordRepository, catalogSnapshotPort,
                dyeLotPort, new TradingImmediateTxRunner());
        lenient().when(catalogSnapshotPort.getProductBrief(eq(PRODUCT), anyString()))
                .thenReturn(product(true, "published"));
        lenient().when(catalogSnapshotPort.getSku(SKU))
                .thenReturn(new SkuBrief(SKU, PRODUCT, "AUR-IV-2", "Ivory", "2", 5, 0L));
        lenient().when(catalogSnapshotPort.getProductBriefs(anyCollection(), anyString())).thenReturn(Map.of());
        lenient().when(catalogSnapshotPort.getSkus(anyCollection())).thenReturn(Map.of());
        lenient().when(dyeLotPort.hintProductIds(anyLong(), anyList())).thenReturn(List.of());
        lenient().when(cartItemRepository.listByCustomerId(CUSTOMER)).thenReturn(List.of());
    }

    private ProductBrief product(boolean customAvailable, String status) {
        return new ProductBrief(PRODUCT, "aurelia-gown", "Aurelia Gown", null, new BigDecimal("100.00"),
                null, null, null, 30, false, customAvailable, status);
    }

    private CustomSizeData customSize() {
        return new CustomSizeData(new BigDecimal("36"), new BigDecimal("28"), new BigDecimal("38"),
                new BigDecimal("58"), null);
    }

    @Test
    @DisplayName("TC-TRD-003 [P0]: 现货缺 sku_id → 422604 SKU_REQUIRED")
    void spotWithoutSkuRejected() {
        assertThatThrownBy(() -> service.addItem(CUSTOMER, new CartItemCreate(PRODUCT, null, 1, null), "en"))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.SKU_REQUIRED));
    }

    @Test
    @DisplayName("TC-TRD-003 [P0]: 定制数据 + sku_id 同传（XOR 违反）→ 422604")
    void bothModesRejected() {
        assertThatThrownBy(() -> service.addItem(CUSTOMER,
                new CartItemCreate(PRODUCT, SKU, 1, customSize()), "en"))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.SKU_REQUIRED));
    }

    @Test
    @DisplayName("TC-TRD-003 [P0]: 商品未开放定制却携带定制数据 → 422604")
    void customNotAvailableRejected() {
        when(catalogSnapshotPort.getProductBrief(eq(PRODUCT), anyString()))
                .thenReturn(product(false, "published"));
        assertThatThrownBy(() -> service.addItem(CUSTOMER,
                new CartItemCreate(PRODUCT, null, 1, customSize()), "en"))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.SKU_REQUIRED));
    }

    @Test
    @DisplayName("V-TRD-002: 商品不存在/未发布 → 404501 透传 catalog")
    void productNotPublished() {
        when(catalogSnapshotPort.getProductBrief(eq(PRODUCT), anyString())).thenReturn(product(true, "draft"));
        assertThatThrownBy(() -> service.addItem(CUSTOMER, new CartItemCreate(PRODUCT, SKU, 1, null), "en"))
                .isInstanceOf(CatalogException.class);
    }

    @Test
    @DisplayName("STEP-TRD-01: 既有同 SKU qty + 本次 qty > stock → 409601（仅提示不预占）")
    void stockHintOnAdd() {
        CartItem existing = new CartItem();
        existing.setId(1L);
        existing.setQty(4);
        when(cartItemRepository.findMergeTarget(CUSTOMER, SKU, null)).thenReturn(existing);
        assertThatThrownBy(() -> service.addItem(CUSTOMER, new CartItemCreate(PRODUCT, SKU, 2, null), "en"))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.STOCK_INSUFFICIENT));
        verify(cartItemRepository, never()).insert(any());
    }

    @Test
    @DisplayName("STEP-TRD-02: 同 SKU 合并累加 qty（不重复插行）")
    void mergeSameSkuQty() {
        CartItem existing = new CartItem();
        existing.setId(1L);
        existing.setQty(2);
        when(cartItemRepository.findMergeTarget(CUSTOMER, SKU, null)).thenReturn(existing);
        service.addItem(CUSTOMER, new CartItemCreate(PRODUCT, SKU, 2, null), "en");
        verify(cartItemRepository).updateQty(1L, 4);
        verify(cartItemRepository, never()).insert(any());
    }

    @Test
    @DisplayName("TC-TRD-038 [P1]: 同 anon_token 二次 merge → 幂等短路（不重复累加，返回现车）")
    void mergeIdempotentShortCircuit() {
        when(cartMergeRecordRepository.insertIgnore(CUSTOMER, "anon-1")).thenReturn(0);
        CartResponse resp = service.merge(CUSTOMER, "anon-1",
                List.of(new CartItemCreate(PRODUCT, SKU, 2, null)), "en");
        assertThat(resp.mergedTruncatedItemIds()).isNull();
        verify(cartItemRepository, never()).insert(any());
        verify(cartItemRepository, never()).updateQty(anyLong(), eq(2));
    }

    @Test
    @DisplayName("TC-TRD-038 [P1]: 现货超库存按 stock 截断并标记 merged_truncated_item_ids")
    void mergeTruncatesOverStock() {
        when(cartMergeRecordRepository.insertIgnore(CUSTOMER, "anon-2")).thenReturn(1);
        when(cartItemRepository.findMergeTarget(CUSTOMER, SKU, null)).thenReturn(null);
        org.mockito.Mockito.doAnswer(inv -> {
            CartItem row = inv.getArgument(0);
            row.setId(33L);
            return null;
        }).when(cartItemRepository).insert(any(CartItem.class));
        CartResponse resp = service.merge(CUSTOMER, "anon-2",
                List.of(new CartItemCreate(PRODUCT, SKU, 9, null)), "en");
        // stock=5 < 9 → 截断到 5 并标记
        verify(cartItemRepository).updateQty(33L, 5);
        assertThat(resp.mergedTruncatedItemIds()).containsExactly(33L);
    }

    @Test
    @DisplayName("V-TRD-010: 全部条目非法 → 422601 details.invalid_items（单项非法跳过不阻断）")
    void mergeAllInvalidRejected() {
        assertThatThrownBy(() -> service.merge(CUSTOMER, "anon-3",
                List.of(new CartItemCreate(PRODUCT, null, 1, null)), "en"))
                .isInstanceOfSatisfying(TradingException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.FIELD_VALIDATION_FAILED);
                    assertThat(ex.getDetails()).containsEntry("invalid_items", List.of(0));
                });
        verify(cartMergeRecordRepository, never()).insertIgnore(anyLong(), anyString());
    }

    @Test
    @DisplayName("E-removeCartItem: affected=0 → 404603（跨用户防探测同码）")
    void removeNotFound() {
        when(cartItemRepository.deleteByIdAndCustomerId(99L, CUSTOMER)).thenReturn(0);
        assertThatThrownBy(() -> service.removeItem(CUSTOMER, 99L))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.CART_ITEM_NOT_FOUND));
    }

    @Test
    @DisplayName("定制行合并判定走 custom_size_hash（同定制数据合并）")
    void customLineMergeByHash() {
        when(cartItemRepository.findMergeTarget(eq(CUSTOMER), isNull(), anyString())).thenReturn(null);
        service.addItem(CUSTOMER, new CartItemCreate(PRODUCT, null, 1, customSize()), "en");
        verify(cartItemRepository).insert(org.mockito.ArgumentMatchers.argThat((CartItem row) ->
                row.getSkuId() == null && row.getCustomSizeHash() != null && row.getCustomSizeData() != null));
    }
}
