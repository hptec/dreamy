package com.dreamy.domain.cart.service;

import com.dreamy.error.CatalogErrorCode;
import com.dreamy.error.CatalogException;
import com.dreamy.domain.cart.entity.CartItem;
import com.dreamy.domain.cart.repository.CartItemRepository;
import com.dreamy.domain.cart.repository.CartMergeRecordRepository;
import com.dreamy.dto.TradingDtos.CartItemCreate;
import com.dreamy.dto.TradingDtos.CartItemDto;
import com.dreamy.dto.TradingDtos.CartResponse;
import com.dreamy.dto.TradingDtos.CustomSizeData;
import com.dreamy.dto.TradingDtos.SkuView;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.infra.TradingTxRunner;
import com.dreamy.port.TradingCatalogSnapshotPort;
import com.dreamy.port.TradingCatalogSnapshotPort.ProductBrief;
import com.dreamy.port.TradingCatalogSnapshotPort.SkuBrief;
import com.dreamy.port.TradingDyeLotPort;
import com.dreamy.support.TradingFieldErrors;
import com.dreamy.support.TradingParams;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 消费端购物车服务（trading-api-detail §1，FLOW-P04，决策 8）。
 * 双模式 guard（V-TRD-004/CV-TRD-007）：现货必填 sku_id 且属于该商品；定制必填完整 custom_size_data
 * 且商品开放定制（XOR，二者同传 → 422604）。
 * 不预占库存：加车/改量仅校验提示（409601）；扣减发生在下单事务（决策 6）。
 */
@Service
public class StoreCartService {

    private static final int TRUNCATE_KEEP_MIN = 1;

    private final CartItemRepository cartItemRepository;
    private final CartMergeRecordRepository cartMergeRecordRepository;
    private final TradingCatalogSnapshotPort catalogSnapshotPort;
    private final TradingDyeLotPort dyeLotPort;
    private final TradingTxRunner txRunner;

    public StoreCartService(CartItemRepository cartItemRepository,
                            CartMergeRecordRepository cartMergeRecordRepository,
                            TradingCatalogSnapshotPort catalogSnapshotPort, TradingDyeLotPort dyeLotPort,
                            TradingTxRunner txRunner) {
        this.cartItemRepository = cartItemRepository;
        this.cartMergeRecordRepository = cartMergeRecordRepository;
        this.catalogSnapshotPort = catalogSnapshotPort;
        this.dyeLotPort = dyeLotPort;
        this.txRunner = txRunner;
    }

    /** E-getCart（STEP-TRD-01~03） */
    public CartResponse getCart(Long customerId, String locale) {
        return assembleCart(customerId, locale, null);
    }

    /** E-addCartItem（V-TRD-002~005 + STEP-TRD-01~03） */
    public CartResponse addItem(Long customerId, CartItemCreate request, String locale) {
        ValidatedItem item = validateItem(customerId, request);
        // STEP-TRD-01 现货库存校验提示（既有同 SKU 条目 qty + 本次 qty > stock → 409601）
        if (item.sku() != null) {
            CartItem existing = cartItemRepository.findMergeTarget(customerId, item.sku().id(), null);
            int existingQty = existing == null ? 0 : existing.getQty();
            int stock = item.sku().stock() == null ? 0 : item.sku().stock();
            if (existingQty + item.qty() > stock) {
                throw new TradingException(TradingErrorCode.STOCK_INSUFFICIENT,
                        Map.of("sku_id", item.sku().id()));
            }
        }
        // STEP-TRD-02 合并或插入
        txRunner.inTx(() -> upsertItem(customerId, item));
        // STEP-TRD-03 返回整车
        return assembleCart(customerId, locale, null);
    }

    /** E-updateCartItem（V-TRD-006/007 + STEP-TRD-01~03） */
    public CartResponse updateItem(Long customerId, Long id, Integer qty, String locale) {
        TradingFieldErrors errors = new TradingFieldErrors();
        if (qty == null || qty < 1) {
            errors.reject("qty", "range_invalid");
        }
        errors.throwIfAny();
        CartItem item = cartItemRepository.findByIdAndCustomerId(id, customerId);
        if (item == null) {
            throw new TradingException(TradingErrorCode.CART_ITEM_NOT_FOUND);
        }
        if (item.getSkuId() != null) {
            SkuBrief sku = catalogSnapshotPort.getSku(item.getSkuId());
            int stock = sku == null || sku.stock() == null ? 0 : sku.stock();
            if (qty > stock) {
                throw new TradingException(TradingErrorCode.STOCK_INSUFFICIENT, Map.of("sku_id", item.getSkuId()));
            }
        }
        cartItemRepository.updateQty(id, qty);
        return assembleCart(customerId, locale, null);
    }

    /** E-removeCartItem（affected=0 → 404603） */
    public void removeItem(Long customerId, Long id) {
        if (cartItemRepository.deleteByIdAndCustomerId(id, customerId) == 0) {
            throw new TradingException(TradingErrorCode.CART_ITEM_NOT_FOUND);
        }
    }

    /** E-mergeCart（TX-TRD-007 单事务；V-TRD-009/010；STEP-TRD-01~04） */
    public CartResponse merge(Long customerId, String anonToken, List<CartItemCreate> items, String locale) {
        TradingFieldErrors errors = new TradingFieldErrors();
        String token = TradingParams.requireText(anonToken, 64, "anon_token", errors);
        if (items == null) {
            errors.reject("items", "required");
        }
        errors.throwIfAny();

        // V-TRD-010 逐项校验：单项非法不阻断整批（跳过）；全部非法 → 422601 details.invalid_items
        List<ValidatedItem> validItems = new ArrayList<>();
        List<Integer> invalidIndexes = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            try {
                validItems.add(validateItem(customerId, items.get(i)));
            } catch (RuntimeException ex) {
                invalidIndexes.add(i);
            }
        }
        if (!items.isEmpty() && validItems.isEmpty()) {
            throw new TradingException(TradingErrorCode.FIELD_VALIDATION_FAILED,
                    Map.of("invalid_items", invalidIndexes));
        }

        List<Long> truncatedIds = txRunner.inTx(() -> {
            // STEP-TRD-01 幂等闸（uk_merge_customer_token；冲突 → 已合并，返回现车）
            if (cartMergeRecordRepository.insertIgnore(customerId, token) == 0) {
                return null;
            }
            // STEP-TRD-02/03 逐项 UPSERT + 现货超库存截断
            List<Long> truncated = new ArrayList<>();
            for (ValidatedItem item : validItems) {
                CartItem row = upsertItem(customerId, item);
                if (item.sku() != null) {
                    int stock = item.sku().stock() == null ? 0 : item.sku().stock();
                    if (row.getQty() > stock) {
                        int clamped = Math.max(stock, TRUNCATE_KEEP_MIN);
                        if (clamped < row.getQty()) {
                            cartItemRepository.updateQty(row.getId(), clamped);
                        }
                        truncated.add(row.getId());
                    }
                }
            }
            return truncated;
        });
        // STEP-TRD-04 事务提交后组装（幂等短路 truncatedIds=null → 不带截断标记）
        return assembleCart(customerId, locale, truncatedIds == null || truncatedIds.isEmpty() ? null : truncatedIds);
    }

    // ==================== 内部 ====================

    /** 校验后的待入车条目（sku=null 即定制行） */
    record ValidatedItem(Long productId, SkuBrief sku, int qty, CustomSizeData customSizeData, String customHash) {
    }

    /**
     * V-TRD-002~005 单条目校验（addItem 抛 422601/422604/404501；merge 模式同口径由调用方捕获跳过）。
     */
    private ValidatedItem validateItem(Long customerId, CartItemCreate request) {
        TradingFieldErrors errors = new TradingFieldErrors();
        if (request == null || request.productId() == null) {
            throw TradingException.fieldValidation("product_id", "required");
        }
        // V-TRD-003 qty ≥1
        if (request.qty() == null || request.qty() < 1) {
            errors.reject("qty", "range_invalid");
        }
        // V-TRD-005 定制数据完整性
        TradingParams.validateCustomSize(request.customSizeData(), errors);
        errors.throwIfAny();

        // V-TRD-002 商品存在且 published → 否则 404501（透传 catalog）
        ProductBrief product = catalogSnapshotPort.getProductBrief(request.productId(), "en");
        if (product == null || !product.published()) {
            throw new CatalogException(CatalogErrorCode.PRODUCT_NOT_FOUND);
        }

        // V-TRD-004 双模式 guard（决策 6；CV-TRD-007 XOR）
        boolean customMode = request.customSizeData() != null;
        if (customMode) {
            if (request.skuId() != null || !Boolean.TRUE.equals(product.customSizeAvailable())) {
                throw new TradingException(TradingErrorCode.SKU_REQUIRED);
            }
            return new ValidatedItem(product.id(), null, request.qty(), request.customSizeData(),
                    TradingParams.customSizeHash(request.customSizeData()));
        }
        if (request.skuId() == null) {
            throw new TradingException(TradingErrorCode.SKU_REQUIRED);
        }
        SkuBrief sku = catalogSnapshotPort.getSku(request.skuId());
        if (sku == null || !Objects.equals(sku.productId(), product.id())) {
            throw new TradingException(TradingErrorCode.SKU_REQUIRED);
        }
        return new ValidatedItem(product.id(), sku, request.qty(), null, null);
    }

    /** addCartItem.STEP-TRD-02 合并判定 + UPSERT（同 sku / 同定制哈希 → 累加 qty） */
    private CartItem upsertItem(Long customerId, ValidatedItem item) {
        Long skuId = item.sku() == null ? null : item.sku().id();
        CartItem target = cartItemRepository.findMergeTarget(customerId, skuId, item.customHash());
        if (target != null) {
            int merged = target.getQty() + item.qty();
            cartItemRepository.updateQty(target.getId(), merged);
            target.setQty(merged);
            return target;
        }
        CartItem row = new CartItem();
        row.setCustomerId(customerId);
        row.setProductId(item.productId());
        row.setSkuId(skuId);
        row.setQty(item.qty());
        if (item.customSizeData() != null) {
            row.setCustomSizeData(toMap(item.customSizeData()));
            row.setCustomSizeHash(item.customHash());
        }
        cartItemRepository.insert(row);
        return row;
    }

    /** CartResponse 装配（getCart.STEP-TRD-02/03：批量快照防 N+1 + dye lot） */
    private CartResponse assembleCart(Long customerId, String locale, List<Long> truncatedIds) {
        List<CartItem> items = cartItemRepository.listByCustomerId(customerId);
        List<Long> productIds = items.stream().map(CartItem::getProductId).distinct().toList();
        Map<Long, ProductBrief> products = catalogSnapshotPort.getProductBriefs(productIds, locale);
        List<Long> skuIds = items.stream().map(CartItem::getSkuId).filter(Objects::nonNull).toList();
        Map<Long, SkuBrief> skus = catalogSnapshotPort.getSkus(skuIds);
        List<CartItemDto> dtos = new ArrayList<>();
        for (CartItem item : items) {
            SkuBrief sku = item.getSkuId() == null ? null : skus.get(item.getSkuId());
            dtos.add(new CartItemDto(item.getId(), item.getProductId(), item.getSkuId(), item.getQty(),
                    fromMap(item.getCustomSizeData()), products.get(item.getProductId()),
                    sku == null ? null
                            : new SkuView(sku.id(), sku.skuCode(), sku.color(), sku.size(), sku.stock())));
        }
        List<Long> dyeLot = dyeLotPort.hintProductIds(customerId, productIds);
        return new CartResponse(dtos, dyeLot, truncatedIds);
    }

    /** CustomSizeData ↔ JSON Map（实体落库形态） */
    public static Map<String, BigDecimal> toMap(CustomSizeData data) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        map.put("bust", data.bust());
        map.put("waist", data.waist());
        map.put("hips", data.hips());
        map.put("hollow_to_floor", data.hollowToFloor());
        if (data.height() != null) {
            map.put("height", data.height());
        }
        return map;
    }

    public static CustomSizeData fromMap(Map<String, BigDecimal> map) {
        if (map == null) {
            return null;
        }
        return new CustomSizeData(num(map.get("bust")), num(map.get("waist")), num(map.get("hips")),
                num(map.get("hollow_to_floor")), num(map.get("height")));
    }

    private static BigDecimal num(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(value.toString());
    }
}
