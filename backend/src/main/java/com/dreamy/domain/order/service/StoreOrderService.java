package com.dreamy.domain.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.infra.stripe.StripeClient;
import com.dreamy.infra.stripe.StripePaymentIntent;
import com.dreamy.domain.checkout.repository.CheckoutConfigRepository;
import com.dreamy.domain.cart.service.StoreCartService;
import com.dreamy.enums.OrderStatus;
import com.dreamy.enums.RefundStatus;
import com.dreamy.domain.order.entity.Order;
import com.dreamy.domain.order.entity.OrderLine;
import com.dreamy.domain.order.repository.OrderLineRepository;
import com.dreamy.domain.order.repository.OrderRepository;
import com.dreamy.domain.payment.entity.Payment;
import com.dreamy.domain.payment.repository.PaymentRepository;
import com.dreamy.domain.refund.entity.Refund;
import com.dreamy.domain.refund.repository.RefundRepository;
import com.dreamy.domain.refund.service.RefundEligibility;
import com.dreamy.dto.TradingDtos.OrderLineDto;
import com.dreamy.dto.TradingDtos.PaymentCredential;
import com.dreamy.dto.TradingDtos.PaymentSummaryDto;
import com.dreamy.dto.TradingDtos.StoreOrderDetail;
import com.dreamy.dto.TradingDtos.StoreOrderListItem;
import com.dreamy.dto.TradingDtos.StoreRefundDto;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.mq.TradingEventsPublisher;
import com.dreamy.support.TradingFieldErrors;
import com.dreamy.support.TradingPaginatedSupport;
import com.dreamy.support.TradingParams;
import huihao.page.Paginated;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 消费端订单服务（trading-api-detail §5；TASK-051/038）。
 * user_id 强隔离（BE-DIM-6）：跨用户/不存在一律 404601 防探测。
 * 行级退款资格派生（决策 24，RefundEligibility 与 RefundService 同口径三处一致）。
 */
@Service
public class StoreOrderService {

    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final CheckoutConfigRepository checkoutConfigRepository;
    private final OrderCancelService orderCancelService;
    private final StripeClient stripeClient;

    public StoreOrderService(OrderRepository orderRepository, OrderLineRepository orderLineRepository,
                             PaymentRepository paymentRepository, RefundRepository refundRepository,
                             CheckoutConfigRepository checkoutConfigRepository,
                             OrderCancelService orderCancelService, StripeClient stripeClient) {
        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.checkoutConfigRepository = checkoutConfigRepository;
        this.orderCancelService = orderCancelService;
        this.stripeClient = stripeClient;
    }

    /** E-listStoreOrders（V-TRD-030/031 + STEP-TRD-01/02；Paginated 六字段） */
    public Paginated<StoreOrderListItem> listOrders(Long customerId, Integer page, Integer pageSize, Integer status) {
        TradingFieldErrors errors = new TradingFieldErrors();
        int parsedPage = TradingParams.parsePage(page, errors);
        int parsedSize = TradingParams.parsePageSize(pageSize, errors);
        Integer statusFilter = status;
        if (statusFilter != null && OrderStatus.of(statusFilter) == null) {
            errors.reject("status", "invalid_enum");
        }
        errors.throwIfAny();
        OrderStatus statusEnum = statusFilter == null ? null : OrderStatus.of(statusFilter);
        Page<Order> result = orderRepository.pageByCustomer(customerId, statusEnum, parsedPage, parsedSize);
        // STEP-TRD-02 line_count/first_line_img 一次聚合（RM-TRD-032，防 N+1）
        Map<Long, OrderLineRepository.LineAggregate> aggregates =
                orderLineRepository.aggregateByOrderIds(result.getRecords().stream().map(Order::getId).toList());
        return TradingPaginatedSupport.of(result, order -> toListItem(order, aggregates.get(order.getId())));
    }

    /** E-getStoreOrder（STEP-TRD-01~04；404601 防探测） */
    public StoreOrderDetail getOrderDetail(Long customerId, Long orderId) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId);
        if (order == null) {
            throw new TradingException(TradingErrorCode.ORDER_NOT_FOUND);
        }
        return assembleDetail(order);
    }

    /** E-cancelStoreOrder（STEP-TRD-01~04；TX-TRD-005） */
    public StoreOrderDetail cancelOrder(Long customerId, Long orderId) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId);
        if (order == null) {
            throw new TradingException(TradingErrorCode.ORDER_NOT_FOUND);
        }
        // STEP-TRD-02 js_guard（条件更新为终防线）
        if (order.getStatus() != OrderStatus.PENDING) {
            throw TradingException.orderStateInvalid();
        }
        if (!orderCancelService.cancelPending(order, TradingEventsPublisher.CANCEL_REASON_CUSTOMER)) {
            // 与 webhook 竞态：重读返回 409602
            throw TradingException.orderStateInvalid();
        }
        return assembleDetail(orderRepository.findById(orderId));
    }

    /** E-retryOrderPayment（V-TRD-034 + STEP-TRD-01~05；TC-TRD-063 矩阵） */
    public PaymentCredential retryPayment(Long customerId, Long orderId) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId);
        if (order == null) {
            throw new TradingException(TradingErrorCode.ORDER_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();
        // STEP-TRD-02 cancelled 且超时取消 → 410601；其余非 pending → 409602
        if (order.getStatus() == OrderStatus.CANCELLED) {
            if (order.getExpiresAt() != null && order.getExpiresAt().isBefore(now)) {
                throw new TradingException(TradingErrorCode.ORDER_EXPIRED);
            }
            throw TradingException.orderStateInvalid();
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw TradingException.orderStateInvalid();
        }
        // STEP-TRD-03 pending 已过期（调度器未及时取消）→ 内联超时取消 → 410601
        if (order.getExpiresAt() != null && !order.getExpiresAt().isAfter(now)) {
            orderCancelService.cancelPending(order, TradingEventsPublisher.CANCEL_REASON_TIMEOUT);
            throw new TradingException(TradingErrorCode.ORDER_EXPIRED);
        }
        // STEP-TRD-04 复用或重建 PI（RM-TRD-044）
        Payment payment = paymentRepository.findByOrderId(orderId);
        if (payment != null && payment.getPaymentIntentId() != null) {
            try {
                StripePaymentIntent intent = stripeClient.retrievePaymentIntent(payment.getPaymentIntentId());
                String piStatus = intent.status();
                if ("requires_payment_method".equals(piStatus) || "requires_confirmation".equals(piStatus)
                        || "requires_action".equals(piStatus)) {
                    return new PaymentCredential(intent.id(), intent.clientSecret());
                }
            } catch (com.dreamy.infra.stripe.StripeException ex) {
                // 检索失败 → 走重建路径（STEP-TRD-04 口径）
            }
        }
        StripePaymentIntent rebuilt = stripeClient.createPaymentIntent(
                com.dreamy.support.Money.toMinor(order.getTotalAmount()),
                order.getCurrency().toLowerCase(), order.getOrderNo(),
                Map.of("order_no", order.getOrderNo(), "order_id", String.valueOf(order.getId())));
        if (payment == null) {
            payment = new Payment();
            payment.setOrderId(order.getId());
            payment.setProvider("stripe");
            payment.setPaymentIntentId(rebuilt.id());
            payment.setAmount(order.getTotalAmount());
            payment.setCurrency(order.getCurrency());
            payment.setStatus(com.dreamy.enums.PaymentStatus.CREATED);
            paymentRepository.insert(payment);
        } else {
            paymentRepository.rebindPaymentIntent(payment.getId(), rebuilt.id());
        }
        return new PaymentCredential(rebuilt.id(), rebuilt.clientSecret());
    }

    // ==================== 装配（MAP-TRD-003/004） ====================

    private StoreOrderListItem toListItem(Order o, OrderLineRepository.LineAggregate aggregate) {
        return new StoreOrderListItem(o.getId(), o.getOrderNo(), o.getStatus().getKey(), o.getCurrency(),
                o.getExchangeRate(), o.getWeddingDate(), o.getSubtotal(), o.getShippingFee(), o.getGiftWrap(),
                o.getGiftWrapFee(), o.getDiscountAmount(), o.getTotalAmount(), o.getCouponId(),
                o.getPaymentMethod(), o.getCarrier(), o.getTrackingNo(), o.getExpiresAt(), o.getPaidAt(),
                o.getShippedAt(), o.getCompletedAt(), o.getCreatedAt(),
                aggregate == null ? 0 : aggregate.lineCount(),
                aggregate == null ? null : aggregate.firstLineImg());
    }

    /** StoreOrderDetail 装配（行级 refundable + 整单 refund_eligible 派生——决策 24） */
    public StoreOrderDetail assembleDetail(Order order) {
        List<OrderLine> lines = orderLineRepository.listByOrderId(order.getId());
        Payment payment = paymentRepository.findByOrderId(order.getId());
        List<Refund> refunds = refundRepository.listByOrderId(order.getId());
        int graceHours = checkoutConfigRepository.getSingleton().getCustomRefundGraceHours();
        LocalDateTime now = LocalDateTime.now();

        boolean hasCustomLine = lines.stream().anyMatch(l -> l.getCustomSizeData() != null);
        boolean hasPending = refunds.stream().anyMatch(r -> r.getStatus() == RefundStatus.PENDING);
        boolean eligible = RefundEligibility.orderEligible(order.getStatus(), hasPending, hasCustomLine,
                order.getPaidAt(), graceHours, now);
        Integer blockReason = null;
        if (!eligible && hasCustomLine
                && RefundEligibility.customProduced(order.getPaidAt(), graceHours, now)) {
            blockReason = TradingErrorCode.CUSTOM_ITEM_NOT_REFUNDABLE.getCode();
        }

        List<OrderLineDto> lineDtos = lines.stream().map(line -> toLineDto(line, order, graceHours, now)).toList();
        List<StoreRefundDto> refundDtos = refunds.stream().map(StoreOrderService::toStoreRefund).toList();
        return new StoreOrderDetail(order.getId(), order.getOrderNo(), order.getStatus().getKey(),
                order.getCurrency(), order.getExchangeRate(), order.getWeddingDate(), order.getSubtotal(),
                order.getShippingFee(), order.getGiftWrap(), order.getGiftWrapFee(), order.getDiscountAmount(),
                order.getTotalAmount(), order.getCouponId(), order.getPaymentMethod(), order.getCarrier(),
                order.getTrackingNo(), order.getExpiresAt(), order.getPaidAt(), order.getShippedAt(),
                order.getCompletedAt(), order.getCreatedAt(), lineDtos, order.getAddressSnapshot(),
                toPaymentSummary(payment), eligible, blockReason, refundDtos);
    }

    static OrderLineDto toLineDto(OrderLine line, Order order, int graceHours, LocalDateTime now) {
        boolean custom = line.getCustomSizeData() != null;
        boolean refundable = RefundEligibility.lineRefundable(custom, order.getStatus(),
                order.getPaidAt(), graceHours, now);
        return new OrderLineDto(line.getId(), line.getProductId(), line.getSkuId(), line.getProductName(),
                line.getSkuCode(), line.getColor(), line.getSize(), line.getQty(), line.getUnitPrice(),
                line.getImg(), StoreCartService.fromMap(line.getCustomSizeData()), refundable);
    }

    static PaymentSummaryDto toPaymentSummary(Payment payment) {
        if (payment == null) {
            return null;
        }
        return new PaymentSummaryDto(payment.getProvider(), payment.getPaymentIntentId(), payment.getAmount(),
                payment.getCurrency(), payment.getStatus().getKey(), payment.getCardSummary(), payment.getPaidAt());
    }

    /** MAP-TRD-007：StoreRefund 视图隐藏 stripe_refund_id/return_tracking_no/customer_* */
    static StoreRefundDto toStoreRefund(Refund refund) {
        return new StoreRefundDto(refund.getId(), refund.getRefundNo(), refund.getOrderId(), refund.getAmount(),
                refund.getCurrency(), refund.getReason(), refund.getStatus().getKey(), refund.getAppliedAt());
    }
}
