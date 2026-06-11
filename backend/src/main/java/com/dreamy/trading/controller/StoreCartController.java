package com.dreamy.trading.controller;

import com.dreamy.trading.domain.cart.service.StoreCartService;
import com.dreamy.trading.dto.TradingDtos.CartItemCreate;
import com.dreamy.trading.dto.TradingDtos.CartItemUpdate;
import com.dreamy.trading.dto.TradingDtos.CartMergeRequest;
import com.dreamy.trading.dto.TradingDtos.CartResponse;
import com.dreamy.trading.support.FieldErrors;
import com.dreamy.trading.support.TradingParams;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消费端购物车控制器（trading-api-detail §1：getCart/addCartItem/updateCartItem/removeCartItem/mergeCart；
 * 全部 StoreBearerAuth，不缓存——决策 4 第 3 层）。
 */
@RestController
public class StoreCartController {

    private final StoreCartService storeCartService;

    public StoreCartController(StoreCartService storeCartService) {
        this.storeCartService = storeCartService;
    }

    /** E-getCart */
    @GetMapping("/api/store/cart")
    public ResponseEntity<R<CartResponse>> getCart(@RequestParam(required = false) String locale) {
        return ResponseEntity.ok(R.ok(storeCartService.getCart(StoreAuth.customerId(), parseLocale(locale))));
    }

    /** E-addCartItem（201） */
    @PostMapping("/api/store/cart/items")
    public ResponseEntity<R<CartResponse>> addItem(@RequestBody CartItemCreate request,
                                                   @RequestParam(required = false) String locale) {
        return ResponseEntity.status(201)
                .body(R.ok(storeCartService.addItem(StoreAuth.customerId(), request, parseLocale(locale))));
    }

    /** E-updateCartItem */
    @PatchMapping("/api/store/cart/items/{id}")
    public ResponseEntity<R<CartResponse>> updateItem(@PathVariable Long id, @RequestBody CartItemUpdate request,
                                                      @RequestParam(required = false) String locale) {
        return ResponseEntity.ok(R.ok(storeCartService.updateItem(StoreAuth.customerId(), id,
                request == null ? null : request.qty(), parseLocale(locale))));
    }

    /** E-removeCartItem（204） */
    @DeleteMapping("/api/store/cart/items/{id}")
    public ResponseEntity<Void> removeItem(@PathVariable Long id) {
        storeCartService.removeItem(StoreAuth.customerId(), id);
        return ResponseEntity.noContent().build();
    }

    /** E-mergeCart（TX-TRD-007 幂等合并） */
    @PostMapping("/api/store/cart/merge")
    public ResponseEntity<R<CartResponse>> merge(@RequestBody CartMergeRequest request,
                                                 @RequestParam(required = false) String locale) {
        return ResponseEntity.ok(R.ok(storeCartService.merge(StoreAuth.customerId(),
                request == null ? null : request.anonToken(),
                request == null ? null : request.items(), parseLocale(locale))));
    }

    /** V-TRD-001 locale 解析（非法 → 422601） */
    private String parseLocale(String locale) {
        FieldErrors errors = new FieldErrors();
        String parsed = TradingParams.parseLocale(locale, errors);
        errors.throwIfAny();
        return parsed;
    }
}
