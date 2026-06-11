package com.dreamy.trading.domain.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.identity.domain.user.entity.User;
import com.dreamy.trading.domain.enums.OrderStatus;
import com.dreamy.trading.domain.order.entity.Order;
import com.dreamy.trading.domain.order.entity.OrderLine;
import com.dreamy.trading.domain.order.repository.OrderLineRepository;
import com.dreamy.trading.domain.order.repository.OrderRepository;
import com.dreamy.trading.domain.payment.repository.PaymentRepository;
import com.dreamy.trading.domain.refund.entity.Refund;
import com.dreamy.trading.domain.refund.repository.RefundRepository;
import com.dreamy.trading.domain.refund.service.RefundService;
import com.dreamy.trading.domain.checkout.repository.CheckoutConfigRepository;
import com.dreamy.trading.dto.TradingDtos.AdminOrderDetail;
import com.dreamy.trading.dto.TradingDtos.AdminOrderListItem;
import com.dreamy.trading.dto.TradingDtos.AdminRefundDto;
import com.dreamy.trading.dto.TradingDtos.OrderLineDto;
import com.dreamy.trading.error.TradingErrorCode;
import com.dreamy.trading.error.TradingException;
import com.dreamy.trading.infra.TradingAfterCommitRunner;
import com.dreamy.trading.infra.TradingAuditRecorder;
import com.dreamy.trading.infra.TradingTxRunner;
import com.dreamy.trading.mq.TradingEventsPublisher;
import com.dreamy.trading.support.FieldErrors;
import com.dreamy.trading.support.PaginatedSupport;
import com.dreamy.trading.support.TradingParams;
import huihao.page.Paginated;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 后台订单服务（trading-api-detail §9，FLOW-P09，OP-009/s-752；TASK-053）。
 * 发货 TX-TRD-004a：cas(paid→shipped, set carrier/tracking_no/shipped_at) + 审计 → 提交后 MQ order.shipped。
 * 完成 TX-TRD-004b：cas(shipped→completed, set completed_at) + 审计（解锁评价 s-756）。
 * 后台取消：guard pending（paid 取消须先走退款流程 → 409602 hint），事务体复用 OrderCancelService。
 */
@Service
public class AdminOrderService {

    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final CheckoutConfigRepository checkoutConfigRepository;
    private final OrderCancelService orderCancelService;
    private final RefundService refundService;
    private final TradingTxRunner txRunner;
    private final TradingAfterCommitRunner afterCommit;
    private final TradingAuditRecorder audit;
    private final TradingEventsPublisher eventsPublisher;

    public AdminOrderService(OrderRepository orderRepository, OrderLineRepository orderLineRepository,
                             PaymentRepository paymentRepository, RefundRepository refundRepository,
                             CheckoutConfigRepository checkoutConfigRepository,
                             OrderCancelService orderCancelService, RefundService refundService,
                             TradingTxRunner txRunner, TradingAfterCommitRunner afterCommit,
                             TradingAuditRecorder audit, TradingEventsPublisher eventsPublisher) {
        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.checkoutConfigRepository = checkoutConfigRepository;
        this.orderCancelService = orderCancelService;
        this.refundService = refundService;
        this.txRunner = txRunner;
        this.afterCommit = afterCommit;
        this.audit = audit;
        this.eventsPublisher = eventsPublisher;
    }

    /** E-listAdminOrders（V-TRD-043~047 + STEP-TRD-01/02） */
    public Paginated<AdminOrderListItem> list(Integer page, Integer pageSize, String status, String search,
                                              String currency, LocalDateTime from, LocalDateTime to) {
        FieldErrors errors = new FieldErrors();
        int parsedPage = TradingParams.parsePage(page, errors);
        int parsedSize = TradingParams.parsePageSize(pageSize, errors);
        String statusFilter = (status == null || status.isBlank()) ? "all" : status;
        if (!TradingParams.ORDER_STATUS_FILTER.contains(statusFilter)) {
            errors.reject("status", "invalid_enum");
        }
        String keyword = TradingParams.checkMaxLength(search, 80, "search", errors);
        if (currency != null && !currency.isBlank() && !TradingParams.isSupportedCurrency(currency)) {
            errors.reject("currency", "invalid_enum");
        }
        // V-TRD-047 from ≤ to
        if (from != null && to != null && from.isAfter(to)) {
            errors.reject("from", "range_invalid");
        }
        errors.throwIfAny();
        OrderStatus statusEnum = "all".equals(statusFilter) ? null : OrderStatus.of(statusFilter);
        String currencyFilter = (currency == null || currency.isBlank()) ? null : currency;
        // 客户邮箱搜索：identity 先解析 customer_ids 再回本域 IN（避免跨域 join）
        List<Long> customerIds = keyword == null ? null : refundService.findUserIdsByEmailLike(keyword);
        Page<Order> result = orderRepository.pageByAdminFilter(statusEnum, currencyFilter, from, to,
                keyword, customerIds, parsedPage, parsedSize);
        // STEP-TRD-02 customer_name/customer_email 批量联取（防 N+1）
        Map<Long, User> users = refundService.loadUsers(
                result.getRecords().stream().map(Order::getCustomerId).distinct().toList());
        return PaginatedSupport.of(result, order -> toListItem(order, users.get(order.getCustomerId())));
    }

    /** E-getAdminOrder（V-TRD-048 + STEP-TRD-01/02） */
    public AdminOrderDetail getDetail(Long orderId) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            throw new TradingException(TradingErrorCode.ORDER_NOT_FOUND);
        }
        return assembleDetail(order);
    }

    /** E-shipAdminOrder（V-TRD-049/050 + STEP-TRD-01~03；TX-TRD-004a） */
    public AdminOrderDetail ship(Long orderId, String carrier, String trackingNo) {
        FieldErrors errors = new FieldErrors();
        if (!TradingParams.isSupportedCarrier(carrier)) {
            errors.reject("carrier", carrier == null ? "required" : "invalid_enum");
        }
        String parsedTrackingNo = TradingParams.requireText(trackingNo, 64, "tracking_no", errors);
        errors.throwIfAny();
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            throw new TradingException(TradingErrorCode.ORDER_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();
        txRunner.inTx(() -> {
            // 状态机 guard：paid→shipped（affected=0 → 409602，无变更无需回滚动作）
            int affected = orderRepository.casUpdateStatus(orderId, OrderStatus.PAID, OrderStatus.SHIPPED,
                    uw -> uw.set(Order::getCarrier, carrier)
                            .set(Order::getTrackingNo, parsedTrackingNo)
                            .set(Order::getShippedAt, now));
            if (affected == 0) {
                throw TradingException.orderStateInvalid();
            }
            // 同事务审计（action=订单发货）
            audit.record(TradingAuditRecorder.ACTION_ORDER_SHIP, order.getOrderNo(),
                    "{\"carrier\":\"" + carrier + "\",\"tracking_no\":\"" + parsedTrackingNo + "\"}");
            // 提交后 MQ order.shipped（EVT-TRD-002 → 发货邮件 FLOW-P11）
            afterCommit.run(() -> {
                Order shipped = orderRepository.findById(orderId);
                eventsPublisher.publishOrderShipped(shipped, "en");
            });
        });
        return assembleDetail(orderRepository.findById(orderId));
    }

    /** E-patchAdminOrderStatus（V-TRD-051 + STEP-TRD-01~04；TX-TRD-004b） */
    public AdminOrderDetail patchStatus(Long orderId, String status) {
        if (!"completed".equals(status) && !"cancelled".equals(status)) {
            throw TradingException.fieldValidation("status", "invalid_enum");
        }
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            throw new TradingException(TradingErrorCode.ORDER_NOT_FOUND);
        }
        if ("completed".equals(status)) {
            // shipped→completed（解锁评价提交 s-756，review 域按 completed 订单校验购买资格）
            LocalDateTime now = LocalDateTime.now();
            txRunner.inTx(() -> {
                if (orderRepository.casUpdateStatus(orderId, OrderStatus.SHIPPED, OrderStatus.COMPLETED,
                        uw -> uw.set(Order::getCompletedAt, now)) == 0) {
                    throw TradingException.orderStateInvalid();
                }
                audit.record(TradingAuditRecorder.ACTION_ORDER_STATUS, order.getOrderNo(),
                        "{\"status\":\"completed\"}");
            });
        } else {
            // cancelled：guard pending（paid 取消须先走退款流程）
            if (order.getStatus() != OrderStatus.PENDING) {
                throw new TradingException(TradingErrorCode.ORDER_STATE_INVALID,
                        Map.of("hint", "paid 订单请走退款"));
            }
            if (!orderCancelService.cancelPending(order, TradingEventsPublisher.CANCEL_REASON_ADMIN)) {
                throw TradingException.orderStateInvalid();
            }
            audit.record(TradingAuditRecorder.ACTION_ORDER_STATUS, order.getOrderNo(),
                    "{\"status\":\"cancelled\"}");
        }
        return assembleDetail(orderRepository.findById(orderId));
    }

    // ==================== 装配（MAP-TRD-005） ====================

    private AdminOrderListItem toListItem(Order o, User user) {
        return new AdminOrderListItem(o.getId(), o.getOrderNo(), o.getStatus().getKey(), o.getCurrency(),
                o.getExchangeRate(), o.getWeddingDate(), o.getSubtotal(), o.getShippingFee(), o.getGiftWrap(),
                o.getGiftWrapFee(), o.getDiscountAmount(), o.getTotalAmount(), o.getCouponId(),
                o.getPaymentMethod(), o.getCarrier(), o.getTrackingNo(), o.getExpiresAt(), o.getPaidAt(),
                o.getShippedAt(), o.getCompletedAt(), o.getCreatedAt(), o.getCustomerId(),
                user == null ? null : user.getName(), user == null ? null : user.getEmail());
    }

    /** AdminOrderDetail 装配（客户信息/定制明细/支付摘要/地址快照/礼品包装/工单/状态时间线） */
    public AdminOrderDetail assembleDetail(Order order) {
        List<OrderLine> lines = orderLineRepository.listByOrderId(order.getId());
        List<Refund> refunds = refundRepository.listByOrderId(order.getId());
        Map<Long, User> users = refundService.loadUsers(List.of(order.getCustomerId()));
        User user = users.get(order.getCustomerId());
        int graceHours = checkoutConfigRepository.getSingleton().getCustomRefundGraceHours();
        LocalDateTime now = LocalDateTime.now();
        List<OrderLineDto> lineDtos = lines.stream()
                .map(line -> StoreOrderService.toLineDto(line, order, graceHours, now)).toList();
        List<AdminRefundDto> refundDtos = refunds.stream()
                .map(r -> refundService.toAdminDto(r, order, users)).toList();
        return new AdminOrderDetail(order.getId(), order.getOrderNo(), order.getStatus().getKey(),
                order.getCurrency(), order.getExchangeRate(), order.getWeddingDate(), order.getSubtotal(),
                order.getShippingFee(), order.getGiftWrap(), order.getGiftWrapFee(), order.getDiscountAmount(),
                order.getTotalAmount(), order.getCouponId(), order.getPaymentMethod(), order.getCarrier(),
                order.getTrackingNo(), order.getExpiresAt(), order.getPaidAt(), order.getShippedAt(),
                order.getCompletedAt(), order.getCreatedAt(), order.getCustomerId(),
                user == null ? null : user.getName(), user == null ? null : user.getEmail(),
                user == null ? null : user.getPhone(), lineDtos, order.getAddressSnapshot(),
                StoreOrderService.toPaymentSummary(paymentRepository.findByOrderId(order.getId())),
                refundDtos);
    }
}
