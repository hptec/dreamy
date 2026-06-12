package com.dreamy.controller;

import com.dreamy.i18n.RequestLocaleContext;
import com.dreamy.domain.order.service.CheckoutQuoteService;
import com.dreamy.domain.order.service.OrderCreateService;
import com.dreamy.dto.TradingDtos.CheckoutQuoteRequest;
import com.dreamy.dto.TradingDtos.CheckoutQuoteResponse;
import com.dreamy.dto.TradingDtos.OrderCreateRequest;
import com.dreamy.dto.TradingDtos.OrderCreateResponse;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * 消费端结算控制器（trading-api-detail §3：quoteCheckout/createOrder；FLOW-P05/P06）。
 * locale 取 Accept-Language（RequestLocaleContext，StoreJwtFilter 注入）做行文案解析；
 * createOrder.locale 入参为交易邮件渲染语言（V-TRD-027，决策 16）。
 */
@RestController
public class StoreCheckoutController {

    private final CheckoutQuoteService checkoutQuoteService;
    private final OrderCreateService orderCreateService;

    public StoreCheckoutController(CheckoutQuoteService checkoutQuoteService,
                                   OrderCreateService orderCreateService) {
        this.checkoutQuoteService = checkoutQuoteService;
        this.orderCreateService = orderCreateService;
    }

    /** E-quoteCheckout（只读试算，不落库不缓存） */
    @PostMapping("/api/store/checkout/quote")
    public ResponseEntity<R<CheckoutQuoteResponse>> quote(@RequestBody CheckoutQuoteRequest request) {
        return ResponseEntity.ok(R.ok(checkoutQuoteService.quote(StoreAuth.customerId(), request,
                requestLocale())));
    }

    /** E-createOrder（201；TX-TRD-001 原子事务 + 事务外 PaymentIntent） */
    @PostMapping("/api/store/checkout/orders")
    public ResponseEntity<R<OrderCreateResponse>> createOrder(@RequestBody OrderCreateRequest request) {
        return ResponseEntity.status(201)
                .body(R.ok(orderCreateService.createOrder(StoreAuth.customerId(), request)));
    }

    private String requestLocale() {
        Locale locale = RequestLocaleContext.get();
        return locale == null ? "en" : locale.getLanguage();
    }
}
