package com.dreamy.trading.controller;

import com.dreamy.trading.domain.browse.service.BrowseHistoryService;
import com.dreamy.trading.domain.wishlist.service.WishlistService;
import com.dreamy.trading.dto.TradingDtos.BrowseHistoryListResponse;
import com.dreamy.trading.dto.TradingDtos.BrowseHistoryRecordRequest;
import com.dreamy.trading.dto.TradingDtos.CartResponse;
import com.dreamy.trading.dto.TradingDtos.WishlistAddRequest;
import com.dreamy.trading.dto.TradingDtos.WishlistItemDto;
import com.dreamy.trading.dto.TradingDtos.WishlistListResponse;
import com.dreamy.trading.dto.TradingDtos.WishlistMoveToCartRequest;
import com.dreamy.trading.support.FieldErrors;
import com.dreamy.trading.support.TradingParams;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消费端收藏 + 浏览历史控制器（trading-api-detail §6/§7，FLOW-P13，决策 18/23）。
 */
@RestController
public class StoreWishlistController {

    private final WishlistService wishlistService;
    private final BrowseHistoryService browseHistoryService;

    public StoreWishlistController(WishlistService wishlistService, BrowseHistoryService browseHistoryService) {
        this.wishlistService = wishlistService;
        this.browseHistoryService = browseHistoryService;
    }

    /** E-listWishlist */
    @GetMapping("/api/store/wishlists")
    public ResponseEntity<R<WishlistListResponse>> list(@RequestParam(required = false) String locale) {
        return ResponseEntity.ok(R.ok(new WishlistListResponse(
                wishlistService.list(StoreAuth.customerId(), parseLocale(locale)))));
    }

    /** E-addWishlistItem（首次 201 / 重复幂等 200，js_guard） */
    @PostMapping("/api/store/wishlists")
    public ResponseEntity<R<WishlistItemDto>> add(@RequestBody WishlistAddRequest request,
                                                  @RequestParam(required = false) String locale) {
        WishlistService.AddResult result = wishlistService.add(StoreAuth.customerId(),
                request == null ? null : request.productId(), parseLocale(locale));
        return ResponseEntity.status(result.first() ? 201 : 200).body(R.ok(result.item()));
    }

    /** E-removeWishlistItem（204；affected=0 → 404604） */
    @DeleteMapping("/api/store/wishlists/{productId}")
    public ResponseEntity<Void> remove(@PathVariable Long productId) {
        wishlistService.remove(StoreAuth.customerId(), productId);
        return ResponseEntity.noContent().build();
    }

    /** E-moveWishlistToCart（TX-TRD-006 单事务） */
    @PostMapping("/api/store/wishlists/{productId}/move-to-cart")
    public ResponseEntity<R<CartResponse>> moveToCart(@PathVariable Long productId,
                                                      @RequestBody WishlistMoveToCartRequest request,
                                                      @RequestParam(required = false) String locale) {
        return ResponseEntity.ok(R.ok(wishlistService.moveToCart(StoreAuth.customerId(), productId,
                request == null ? null : request.skuId(),
                request == null ? null : request.qty(),
                request == null ? null : request.customSizeData(), parseLocale(locale))));
    }

    /** E-listBrowseHistory（V-TRD-041） */
    @GetMapping("/api/store/browse-history")
    public ResponseEntity<R<BrowseHistoryListResponse>> listBrowseHistory(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String locale) {
        return ResponseEntity.ok(R.ok(new BrowseHistoryListResponse(
                browseHistoryService.list(StoreAuth.customerId(), limit, parseLocale(locale)))));
    }

    /** E-recordBrowseHistory（204；upsert + 50 条滚动） */
    @PostMapping("/api/store/browse-history")
    public ResponseEntity<Void> recordBrowseHistory(@RequestBody BrowseHistoryRecordRequest request) {
        browseHistoryService.record(StoreAuth.customerId(), request == null ? null : request.productId());
        return ResponseEntity.noContent().build();
    }

    private String parseLocale(String locale) {
        FieldErrors errors = new FieldErrors();
        String parsed = TradingParams.parseLocale(locale, errors);
        errors.throwIfAny();
        return parsed;
    }
}
