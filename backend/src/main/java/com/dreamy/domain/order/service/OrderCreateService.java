package com.dreamy.domain.order.service;

import com.dreamy.domain.product.entity.Sku;
import com.dreamy.infra.stripe.StripeClient;
import com.dreamy.infra.stripe.StripePaymentIntent;
import com.dreamy.domain.coupon.service.CouponDomainService;
import com.dreamy.domain.address.entity.Address;
import com.dreamy.domain.cart.repository.CartItemRepository;
import com.dreamy.enums.OrderStatus;
import com.dreamy.enums.PaymentStatus;
import com.dreamy.domain.order.entity.Order;
import com.dreamy.domain.order.entity.OrderLine;
import com.dreamy.domain.order.repository.OrderLineRepository;
import com.dreamy.domain.order.repository.OrderRepository;
import com.dreamy.domain.payment.entity.Payment;
import com.dreamy.domain.payment.repository.PaymentRepository;
import com.dreamy.dto.TradingDtos.OrderCreateRequest;
import com.dreamy.dto.TradingDtos.OrderCreateResponse;
import com.dreamy.dto.TradingDtos.PaymentCredential;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.infra.TradingTxRunner;
import com.dreamy.port.SkuStockAdapter;
import com.dreamy.support.TradingFieldErrors;
import com.dreamy.support.Money;
import com.dreamy.support.TradingParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 下单服务（FLOW-P06，FUNC-001 核心，BE-DIM-4；TASK-051）。
 * TX-TRD-001 原子事务：① INSERT orders（uk_order_idem/uk_order_no 冲突分流）→ ② batchInsert order_line
 * → ③ 现货行 SKU 乐观锁 CAS×3（失败整体回滚 409601）→ ④ 券核销（失败 4227xx 回滚）→ ⑤ 清车 → COMMIT。
 * Stripe createPaymentIntent 在事务提交后调用（外部调用不入本地事务；失败订单保持 pending —— 502601/504601，
 * 可经 retryOrderPayment 重试，30min 未付走 SCHED-TRD-001 超时取消）。
 */
@Service
public class OrderCreateService {

    private static final Logger log = LoggerFactory.getLogger(OrderCreateService.class);
    private static final int CAS_MAX_ATTEMPTS = 3;
    private static final int ORDER_NO_MAX_ATTEMPTS = 3;
    private static final int EXPIRES_MINUTES = 30;

    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final PaymentRepository paymentRepository;
    private final CartItemRepository cartItemRepository;
    private final CheckoutQuoteService checkoutQuoteService;
    private final OrderNoGenerator orderNoGenerator;
    private final SkuStockAdapter skuStockAdapter;
    private final CouponDomainService couponDomainService;
    private final StripeClient stripeClient;
    private final TradingTxRunner txRunner;
    private final StoreOrderService storeOrderService;

    public OrderCreateService(OrderRepository orderRepository, OrderLineRepository orderLineRepository,
                              PaymentRepository paymentRepository, CartItemRepository cartItemRepository,
                              CheckoutQuoteService checkoutQuoteService, OrderNoGenerator orderNoGenerator,
                              SkuStockAdapter skuStockAdapter, CouponDomainService couponDomainService,
                              StripeClient stripeClient, TradingTxRunner txRunner,
                              StoreOrderService storeOrderService) {
        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
        this.paymentRepository = paymentRepository;
        this.cartItemRepository = cartItemRepository;
        this.checkoutQuoteService = checkoutQuoteService;
        this.orderNoGenerator = orderNoGenerator;
        this.skuStockAdapter = skuStockAdapter;
        this.couponDomainService = couponDomainService;
        this.stripeClient = stripeClient;
        this.txRunner = txRunner;
        this.storeOrderService = storeOrderService;
    }

    /** E-createOrder（V-TRD-021~027 + STEP-TRD-01~07） */
    public OrderCreateResponse createOrder(Long customerId, OrderCreateRequest request) {
        // ==== 入参验证 ====
        TradingFieldErrors errors = new TradingFieldErrors();
        String idemKey = TradingParams.requireText(request.idempotencyKey(), 64, "idempotency_key", errors);
        if (request.addressId() == null) {
            errors.reject("address_id", "required");
        }
        if (!TradingParams.isSupportedCarrier(request.carrier())) {
            errors.reject("carrier", request.carrier() == null ? "required" : "invalid_enum");
        }
        // V-TRD-025 payment_method（PayPal 置灰不产生数据，决策 25）
        if (request.paymentMethod() == null || !TradingParams.PAYMENT_METHODS.contains(request.paymentMethod())) {
            errors.reject("payment_method", "invalid_enum");
        }
        // V-TRD-027 locale 缺省 en
        String locale = TradingParams.parseLocale(request.locale(), errors);
        errors.throwIfAny();
        // V-TRD-023 currency（422605 在 compute 内）

        // STEP-TRD-01 幂等预检（uk_order_idem）
        Order existing = orderRepository.findByIdempotencyKey(idemKey);
        if (existing != null) {
            throw new TradingException(TradingErrorCode.DUPLICATE_SUBMISSION, Map.of("order_id", existing.getId()));
        }

        // STEP-TRD-02/03 锁汇 + 全量服务端重算（不信任前端金额；V-TRD-022/026 在 compute 严格模式内）
        CheckoutQuoteService.Computation quote = checkoutQuoteService.compute(customerId,
                request.addressId(), null, request.currency(), request.carrier(), request.couponCode(),
                Boolean.TRUE.equals(request.giftWrap()), request.weddingDate(), locale, true);

        // STEP-TRD-05 原子事务（TX-TRD-001；订单号 STEP-TRD-04 预生成 + uk_order_no 冲突重取 ×3）
        Order order = txRunner.inTx(() -> createOrderTx(customerId, idemKey, request, quote));

        // STEP-TRD-06 事务外 Stripe PaymentIntent（失败 → 502601/504601，订单保持 pending）
        // metadata 携带 locale：webhook 侧 order.paid 事件渲染语言来源（orders 表无 locale 列，决策 16 口径）
        StripePaymentIntent intent = stripeClient.createPaymentIntent(
                Money.toMinor(order.getTotalAmount()), order.getCurrency().toLowerCase(), order.getOrderNo(),
                Map.of("order_no", order.getOrderNo(), "order_id", String.valueOf(order.getId()),
                        "locale", locale));
        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setProvider("stripe");
        payment.setPaymentIntentId(intent.id());
        payment.setAmount(order.getTotalAmount());
        payment.setCurrency(order.getCurrency());
        payment.setStatus(PaymentStatus.CREATED);
        paymentRepository.insert(payment);

        // STEP-TRD-07 出参（client_secret 即取即用不落库）
        return new OrderCreateResponse(storeOrderService.getOrderDetail(customerId, order.getId()),
                new PaymentCredential(intent.id(), intent.clientSecret()));
    }

    /** TX-TRD-001 事务体（READ_COMMITTED；任一步失败整体回滚） */
    private Order createOrderTx(Long customerId, String idemKey, OrderCreateRequest request,
                                CheckoutQuoteService.Computation quote) {
        // ① INSERT orders（订单号冲突重取 ×3；幂等键冲突 → 409603 + details.order_id）
        Order order = buildOrder(customerId, idemKey, request, quote);
        insertWithOrderNoRetry(order);

        // ② INSERT order_line[]（快照）
        List<OrderLine> lines = new ArrayList<>();
        for (CheckoutQuoteService.PricedLine priced : quote.lines()) {
            lines.add(buildLine(order.getId(), priced));
        }
        orderLineRepository.batchInsert(lines);

        // ③ 现货行逐行乐观锁扣减（决策 6：定制行不扣减；CAS ×3 仍失败 → 409601 整体回滚）
        for (CheckoutQuoteService.PricedLine priced : quote.lines()) {
            if (priced.sku() != null) {
                deductWithRetry(priced.sku().id(), priced.qty(), priced.sku().version());
            }
        }

        // ④ 券核销（marketing 领域服务参与本事务，affected=0 → 422703 等向上抛回滚）
        if (quote.couponQuote() != null && quote.couponQuote().valid()) {
            Long couponId = couponDomainService.redeem(
                    request.couponCode() == null ? null : request.couponCode().trim(), quote.subtotalUsd());
            order.setCouponId(couponId);
        }

        // ⑤ 清车
        cartItemRepository.deleteAllByCustomerId(customerId);
        return order;
    }

    private Order buildOrder(Long customerId, String idemKey, OrderCreateRequest request,
                             CheckoutQuoteService.Computation quote) {
        Order order = new Order();
        order.setOrderNo(orderNoGenerator.nextOrderNo());
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.PENDING);
        order.setCurrency(quote.currency());
        // FUNC-020 / 决策13：下单语言环境快照（邮件三语用），缺省 en
        order.setLocaleSnapshot(request.locale() != null ? request.locale() : "en");
        order.setExchangeRate(quote.rate());
        order.setWeddingDate(request.weddingDate());
        order.setSubtotal(quote.subtotal());
        order.setShippingFee(quote.shippingFee());
        order.setGiftWrap(quote.giftWrap());
        order.setGiftWrapFee(quote.giftWrapFee());
        order.setDiscountAmount(quote.discountAmount());
        order.setTotalAmount(quote.totalAmount());
        order.setCouponId(quote.couponQuote() != null && quote.couponQuote().valid()
                ? quote.couponQuote().couponId() : null);
        order.setPaymentMethod(request.paymentMethod());
        order.setAddressSnapshot(addressSnapshot(quote.address()));
        order.setCarrier(quote.selectedCarrier() != null ? quote.selectedCarrier() : request.carrier());
        order.setIdempotencyKey(idemKey);
        order.setExpiresAt(LocalDateTime.now().plusMinutes(EXPIRES_MINUTES));
        return order;
    }

    /** ① 唯一冲突分流：幂等键命中 → 409603；订单号撞号 → 重取 ×3（uk_order_no 兜底，TC-TRD-012/083） */
    private void insertWithOrderNoRetry(Order order) {
        for (int attempt = 1; ; attempt++) {
            try {
                orderRepository.insert(order);
                return;
            } catch (DuplicateKeyException ex) {
                Order existing = orderRepository.findByIdempotencyKey(order.getIdempotencyKey());
                if (existing != null) {
                    // 并发双击竞态：恰一单创建，本线程回滚返回既有单（TC-TRD-022）
                    throw new TradingException(TradingErrorCode.DUPLICATE_SUBMISSION,
                            Map.of("order_id", existing.getId()));
                }
                if (attempt >= ORDER_NO_MAX_ATTEMPTS) {
                    throw ex;
                }
                log.warn("[ORDER-CREATE] order_no collision, regenerate (attempt {})", attempt);
                order.setOrderNo(orderNoGenerator.nextOrderNo());
            }
        }
    }

    /** ③ CAS 扣减（重读 version 重试 ≤3，TC-TRD-023/024） */
    private void deductWithRetry(Long skuId, int qty, Long initialVersion) {
        Long version = initialVersion;
        for (int attempt = 1; attempt <= CAS_MAX_ATTEMPTS; attempt++) {
            if (version != null && skuStockAdapter.deduct(skuId, qty, version) > 0) {
                return;
            }
            Sku latest = skuStockAdapter.reload(skuId);
            if (latest == null || latest.getStock() == null || latest.getStock() < qty) {
                break;
            }
            version = latest.getVersion();
        }
        throw new TradingException(TradingErrorCode.STOCK_INSUFFICIENT, Map.of("sku_id", skuId));
    }

    private OrderLine buildLine(Long orderId, CheckoutQuoteService.PricedLine priced) {
        OrderLine line = new OrderLine();
        line.setOrderId(orderId);
        line.setProductId(priced.product().id());
        line.setProductName(priced.product().name());
        line.setQty(priced.qty());
        line.setUnitPrice(priced.unitPrice());
        line.setImg(priced.product().imageUrl());
        if (priced.sku() != null) {
            line.setSkuId(priced.sku().id());
            line.setSkuCode(priced.sku().skuCode());
            line.setColor(priced.sku().color());
            line.setSize(priced.sku().size());
        }
        line.setCustomSizeData(priced.item().getCustomSizeData());
        return line;
    }

    /** 地址 JSON 快照（AddressUpsert 字段形状，订单与地址解耦——deleteAddress 不波及） */
    static Map<String, Object> addressSnapshot(Address address) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("receiver", address.getReceiver());
        if (address.getPhone() != null) {
            snapshot.put("phone", address.getPhone());
        }
        snapshot.put("line", address.getLine());
        snapshot.put("city", address.getCity());
        if (address.getState() != null) {
            snapshot.put("state", address.getState());
        }
        snapshot.put("zip", address.getZip());
        snapshot.put("country", address.getCountry());
        return snapshot;
    }
}
