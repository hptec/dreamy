package com.dreamy.domain.order.service;

import com.dreamy.domain.product.entity.Sku;
import com.dreamy.infra.stripe.StripeClient;
import com.dreamy.infra.stripe.StripePaymentIntent;
import com.dreamy.infra.stripe.StripeUnavailableException;
import com.dreamy.domain.coupon.service.CouponDomainService;
import com.dreamy.domain.coupon.service.CouponDomainService.CouponQuote;
import com.dreamy.domain.address.entity.Address;
import com.dreamy.domain.cart.entity.CartItem;
import com.dreamy.domain.cart.repository.CartItemRepository;
import com.dreamy.domain.order.entity.Order;
import com.dreamy.domain.order.repository.OrderLineRepository;
import com.dreamy.domain.order.repository.OrderRepository;
import com.dreamy.domain.payment.entity.Payment;
import com.dreamy.domain.payment.repository.PaymentRepository;
import com.dreamy.dto.TradingDtos.OrderCreateRequest;
import com.dreamy.dto.TradingDtos.OrderCreateResponse;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.port.TradingCatalogSnapshotPort.ProductBrief;
import com.dreamy.port.TradingCatalogSnapshotPort.SkuBrief;
import com.dreamy.port.SkuStockAdapter;
import com.dreamy.testsupport.TradingImmediateTxRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 下单服务单测（TX-TRD-001 事务体逻辑面；DB 原子性由 IT 层验证）。
 * L2 TRACE: TC-TRD-021/022 [P0]（幂等键预检/竞态唯一冲突 → 409603 + details.order_id）/
 * TC-TRD-023 [P0]（CAS×3 失败 → 409601）/ TC-TRD-024 [P1]（version 变更重读重试成功）/
 * TC-TRD-020 [P0] 单测面（订单+行+扣减+核销+清车调用链）/ TC-TRD-080 [P0] 单测面（Stripe 失败订单保持 pending）。
 */
@ExtendWith(MockitoExtension.class)
class OrderCreateServiceTest {

    private static final long CUSTOMER = 7L;
    private static final long PRODUCT = 11L;
    private static final long SKU = 21L;

    @Mock
    OrderRepository orderRepository;
    @Mock
    OrderLineRepository orderLineRepository;
    @Mock
    PaymentRepository paymentRepository;
    @Mock
    CartItemRepository cartItemRepository;
    @Mock
    CheckoutQuoteService checkoutQuoteService;
    @Mock
    OrderNoGenerator orderNoGenerator;
    @Mock
    SkuStockAdapter skuStockAdapter;
    @Mock
    CouponDomainService couponDomainService;
    @Mock
    StripeClient stripeClient;
    @Mock
    StoreOrderService storeOrderService;

    OrderCreateService service;

    @BeforeEach
    void setUp() {
        service = new OrderCreateService(orderRepository, orderLineRepository, paymentRepository,
                cartItemRepository, checkoutQuoteService, orderNoGenerator, skuStockAdapter,
                couponDomainService, stripeClient, new TradingImmediateTxRunner(), storeOrderService);
        lenient().when(orderNoGenerator.nextOrderNo()).thenReturn("DRM-20260610-0001");
        lenient().when(checkoutQuoteService.compute(anyLong(), anyLong(), isNull(), anyString(), anyString(),
                any(), anyBoolean(), any(), anyString(), eq(true))).thenReturn(computation(null));
        lenient().when(skuStockAdapter.deduct(eq(SKU), anyInt(), anyLong())).thenReturn(1);
        lenient().when(stripeClient.createPaymentIntent(anyLong(), anyString(), anyString(), anyMap()))
                .thenReturn(new StripePaymentIntent("pi_1", "pi_1_secret", "requires_payment_method",
                        22200L, "usd"));
        lenient().doAnswer(inv -> {
            Order order = inv.getArgument(0);
            order.setId(100L);
            return null;
        }).when(orderRepository).insert(any(Order.class));
    }

    private CheckoutQuoteService.Computation computation(CouponQuote couponQuote) {
        CartItem item = new CartItem();
        item.setId(1L);
        item.setCustomerId(CUSTOMER);
        item.setProductId(PRODUCT);
        item.setSkuId(SKU);
        item.setQty(2);
        ProductBrief product = new ProductBrief(PRODUCT, "aurelia-gown", "Aurelia Gown",
                new BigDecimal("100.00"), null, null, "https://img/a.jpg", 30, false, true, 2);
        SkuBrief sku = new SkuBrief(SKU, PRODUCT, "AUR-IV-2", "Ivory", "2", 5, 3L);
        Address address = new Address();
        address.setId(31L);
        address.setCustomerId(CUSTOMER);
        address.setReceiver("Emma");
        address.setLine("1 Main St");
        address.setCity("NY");
        address.setZip("10001");
        address.setCountry("US");
        CheckoutQuoteService.PricedLine line = new CheckoutQuoteService.PricedLine(item, product, sku,
                new BigDecimal("100.00"), new BigDecimal("100.00"), 2);
        return new CheckoutQuoteService.Computation("USD", BigDecimal.ONE, List.of(line),
                new BigDecimal("200.00"), new BigDecimal("200.00"), List.of(), "UPS Worldwide Express",
                new BigDecimal("22.00"), false, new BigDecimal("0.00"), couponQuote,
                couponQuote != null && couponQuote.valid() ? new BigDecimal("20.00") : new BigDecimal("0.00"),
                couponQuote != null && couponQuote.valid() ? new BigDecimal("202.00") : new BigDecimal("222.00"),
                30, false, List.of(), address, "US");
    }

    private OrderCreateRequest request(String coupon) {
        return new OrderCreateRequest("idem-key-1", 31L, "USD", "UPS Worldwide Express", coupon, false,
                null, "Stripe", "en");
    }

    @Test
    @DisplayName("TC-TRD-020 [P0] 单测面: 订单+行+CAS 扣减+清车+事务外 PaymentIntent 调用链全通")
    void happyPathChain() {
        OrderCreateResponse resp = service.createOrder(CUSTOMER, request(null));
        verify(orderRepository).insert(any(Order.class));
        verify(orderLineRepository).batchInsert(any());
        verify(skuStockAdapter).deduct(SKU, 2, 3L);
        verify(cartItemRepository).deleteAllByCustomerId(CUSTOMER);
        // Stripe 金额 = 222.00×100；币种小写（决策 14）
        verify(stripeClient).createPaymentIntent(eq(22200L), eq("usd"), eq("DRM-20260610-0001"), anyMap());
        verify(paymentRepository).insert(any(Payment.class));
        assertThat(resp.payment().paymentIntentId()).isEqualTo("pi_1");
        assertThat(resp.payment().clientSecret()).isEqualTo("pi_1_secret");
        verify(couponDomainService, never()).redeem(any(), any());
    }

    @Test
    @DisplayName("TC-TRD-021 [P0]: 幂等键预检命中 → 409603 + details.order_id=首单；不再开事务")
    void idempotencyPrecheck() {
        Order existing = new Order();
        existing.setId(55L);
        when(orderRepository.findByIdempotencyKey("idem-key-1")).thenReturn(existing);
        assertThatThrownBy(() -> service.createOrder(CUSTOMER, request(null)))
                .isInstanceOfSatisfying(TradingException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.DUPLICATE_SUBMISSION);
                    assertThat(ex.getDetails()).containsEntry("order_id", 55L);
                });
        verify(orderRepository, never()).insert(any());
    }

    @Test
    @DisplayName("TC-TRD-022 [P0]: 并发竞态唯一冲突 → 重查既有单返回 409603（恰一单创建）")
    void concurrentDuplicateKey() {
        Order existing = new Order();
        existing.setId(56L);
        // 预检未命中（竞态窗口），INSERT 撞 uk_order_idem，重查命中
        when(orderRepository.findByIdempotencyKey("idem-key-1")).thenReturn(null).thenReturn(existing);
        doThrow(new DuplicateKeyException("uk_order_idem")).when(orderRepository).insert(any(Order.class));
        assertThatThrownBy(() -> service.createOrder(CUSTOMER, request(null)))
                .isInstanceOfSatisfying(TradingException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.DUPLICATE_SUBMISSION);
                    assertThat(ex.getDetails()).containsEntry("order_id", 56L);
                });
        verify(stripeClient, never()).createPaymentIntent(anyLong(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("TC-TRD-012 [P1]: 订单号撞号（幂等键未命中）→ 重取 ×3 后成功")
    void orderNoCollisionRetry() {
        when(orderNoGenerator.nextOrderNo()).thenReturn("DRM-20260610-0001", "DRM-20260610-0002");
        doThrow(new DuplicateKeyException("uk_order_no"))
                .doAnswer(inv -> {
                    Order order = inv.getArgument(0);
                    order.setId(100L);
                    return null;
                })
                .when(orderRepository).insert(any(Order.class));
        OrderCreateResponse resp = service.createOrder(CUSTOMER, request(null));
        assertThat(resp).isNotNull();
        verify(stripeClient).createPaymentIntent(anyLong(), anyString(), eq("DRM-20260610-0002"), anyMap());
    }

    @Test
    @DisplayName("TC-TRD-024 [P1]: CAS 遭遇 version 变更（非缺货）→ 重读重试 ≤3 次后成功")
    void casRetrySucceeds() {
        when(skuStockAdapter.deduct(eq(SKU), eq(2), anyLong())).thenReturn(0).thenReturn(1);
        Sku latest = new Sku();
        latest.setId(SKU);
        latest.setStock(5);
        latest.setVersion(4L);
        when(skuStockAdapter.reload(SKU)).thenReturn(latest);
        OrderCreateResponse resp = service.createOrder(CUSTOMER, request(null));
        assertThat(resp).isNotNull();
        verify(skuStockAdapter).deduct(SKU, 2, 3L);
        verify(skuStockAdapter).deduct(SKU, 2, 4L);
    }

    @Test
    @DisplayName("TC-TRD-023 [P0]: 重读缺货 → 409601 STOCK_INSUFFICIENT details.sku_id（整体回滚由 TX 承载）")
    void casFailsStockInsufficient() {
        when(skuStockAdapter.deduct(eq(SKU), eq(2), anyLong())).thenReturn(0);
        Sku latest = new Sku();
        latest.setId(SKU);
        latest.setStock(1);
        latest.setVersion(4L);
        when(skuStockAdapter.reload(SKU)).thenReturn(latest);
        assertThatThrownBy(() -> service.createOrder(CUSTOMER, request(null)))
                .isInstanceOfSatisfying(TradingException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.STOCK_INSUFFICIENT);
                    assertThat(ex.getDetails()).containsEntry("sku_id", SKU);
                });
        verify(stripeClient, never()).createPaymentIntent(anyLong(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("STEP-TRD-05 ④: 有效券事务内核销（redeem 以 USD 小计调用，couponId 入单）")
    void couponRedeemedInTx() {
        CouponQuote quote = new CouponQuote(true, 5L, new BigDecimal("20.00"), false, null, null);
        when(checkoutQuoteService.compute(anyLong(), anyLong(), isNull(), anyString(), anyString(), any(),
                anyBoolean(), any(), anyString(), eq(true))).thenReturn(computation(quote));
        when(couponDomainService.redeem(eq("BRIDE10"), any())).thenReturn(5L);
        service.createOrder(CUSTOMER, request("BRIDE10"));
        verify(couponDomainService).redeem("BRIDE10", new BigDecimal("200.00"));
        verify(stripeClient).createPaymentIntent(eq(20200L), eq("usd"), anyString(), anyMap());
    }

    @Test
    @DisplayName("TC-TRD-080 [P0] 单测面: Stripe 失败 → 502601 异常透出；payment 不落库（订单保持 pending 可重试）")
    void stripeFailureLeavesPending() {
        when(stripeClient.createPaymentIntent(anyLong(), anyString(), anyString(), anyMap()))
                .thenThrow(new StripeUnavailableException("down", null));
        assertThatThrownBy(() -> service.createOrder(CUSTOMER, request(null)))
                .isInstanceOf(StripeUnavailableException.class);
        verify(paymentRepository, never()).insert(any());
        // 事务体已完成（订单/清车已提交）
        verify(cartItemRepository).deleteAllByCustomerId(CUSTOMER);
    }

    @Test
    @DisplayName("V-TRD-024/025: carrier/payment_method 枚举校验 → 422601 字段级")
    void enumValidation() {
        assertThatThrownBy(() -> service.createOrder(CUSTOMER, new OrderCreateRequest("k", 31L, "USD",
                "FedEx", null, false, null, "Stripe", "en")))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.FIELD_VALIDATION_FAILED));
        assertThatThrownBy(() -> service.createOrder(CUSTOMER, new OrderCreateRequest("k", 31L, "USD",
                "UPS Worldwide Express", null, false, null, "PayPal", "en")))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.FIELD_VALIDATION_FAILED));
    }
}
