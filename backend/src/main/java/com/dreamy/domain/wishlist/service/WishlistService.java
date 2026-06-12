package com.dreamy.domain.wishlist.service;

import com.dreamy.error.CatalogErrorCode;
import com.dreamy.error.CatalogException;
import com.dreamy.domain.cart.service.StoreCartService;
import com.dreamy.domain.wishlist.entity.WishlistItem;
import com.dreamy.domain.wishlist.repository.WishlistItemRepository;
import com.dreamy.dto.TradingDtos.CartItemCreate;
import com.dreamy.dto.TradingDtos.CartResponse;
import com.dreamy.dto.TradingDtos.CustomSizeData;
import com.dreamy.dto.TradingDtos.WishlistItemDto;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.infra.TradingTxRunner;
import com.dreamy.port.TradingCatalogSnapshotPort;
import com.dreamy.port.TradingCatalogSnapshotPort.ProductBrief;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 收藏服务（trading-api-detail §6，FLOW-P13，决策 18；derived_scope host TASK-051）。
 * 幂等：uk_wishlist_customer_product（重复 add → 200 既有，js_guard）；
 * moveWishlistToCart 为 TX-TRD-006 单事务（加车 + 删收藏同滚同成）。
 */
@Service
public class WishlistService {

    /** addWishlistItem 结果（first=true → 201；false → 幂等 200） */
    public record AddResult(WishlistItemDto item, boolean first) {
    }

    private final WishlistItemRepository wishlistItemRepository;
    private final TradingCatalogSnapshotPort catalogSnapshotPort;
    private final StoreCartService storeCartService;
    private final TradingTxRunner txRunner;

    public WishlistService(WishlistItemRepository wishlistItemRepository, TradingCatalogSnapshotPort catalogSnapshotPort,
                           StoreCartService storeCartService, TradingTxRunner txRunner) {
        this.wishlistItemRepository = wishlistItemRepository;
        this.catalogSnapshotPort = catalogSnapshotPort;
        this.storeCartService = storeCartService;
        this.txRunner = txRunner;
    }

    /** E-listWishlist（V-TRD-036；含 status=draft 不可购买标记） */
    public List<WishlistItemDto> list(Long customerId, String locale) {
        List<WishlistItem> items = wishlistItemRepository.listByCustomerId(customerId);
        Map<Long, ProductBrief> products = catalogSnapshotPort.getProductBriefs(
                items.stream().map(WishlistItem::getProductId).toList(), locale);
        return items.stream()
                .map(item -> new WishlistItemDto(item.getId(), item.getProductId(),
                        products.get(item.getProductId())))
                .toList();
    }

    /** E-addWishlistItem（V-TRD-037 + STEP-TRD-01/02：insertIgnore 幂等） */
    public AddResult add(Long customerId, Long productId, String locale) {
        if (productId == null) {
            throw TradingException.fieldValidation("product_id", "required");
        }
        ProductBrief product = catalogSnapshotPort.getProductBrief(productId, locale);
        if (product == null || !product.published()) {
            throw new CatalogException(CatalogErrorCode.PRODUCT_NOT_FOUND);
        }
        boolean first = wishlistItemRepository.insertIgnore(customerId, productId) > 0;
        WishlistItem item = wishlistItemRepository.findByCustomerAndProduct(customerId, productId);
        return new AddResult(new WishlistItemDto(item.getId(), item.getProductId(), product), first);
    }

    /** E-removeWishlistItem（V-TRD-038；affected=0 → 404604） */
    public void remove(Long customerId, Long productId) {
        if (wishlistItemRepository.deleteByCustomerAndProduct(customerId, productId) == 0) {
            throw new TradingException(TradingErrorCode.WISHLIST_ITEM_NOT_FOUND);
        }
    }

    /** E-moveWishlistToCart（V-TRD-039/040 + STEP-TRD-01~04；TX-TRD-006 单事务） */
    public CartResponse moveToCart(Long customerId, Long productId, Long skuId, Integer qty,
                                   CustomSizeData customSizeData, String locale) {
        int parsedQty = qty == null ? 1 : qty;
        if (parsedQty < 1) {
            throw TradingException.fieldValidation("qty", "range_invalid");
        }
        WishlistItem item = wishlistItemRepository.findByCustomerAndProduct(customerId, productId);
        if (item == null) {
            throw new TradingException(TradingErrorCode.WISHLIST_ITEM_NOT_FOUND);
        }
        // TX-TRD-006：加车（双模式 guard/库存校验复用 addCartItem 全链路）+ 删收藏 同事务
        return txRunner.inTx(() -> {
            CartResponse cart = storeCartService.addItem(customerId,
                    new CartItemCreate(productId, skuId, parsedQty, customSizeData), locale);
            wishlistItemRepository.deleteByCustomerAndProduct(customerId, productId);
            return cart;
        });
    }
}
