package com.dreamy.domain.refund.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.domain.checkout.repository.CheckoutConfigRepository;
import com.dreamy.domain.order.entity.Order;
import com.dreamy.domain.order.entity.OrderLine;
import com.dreamy.domain.order.repository.OrderLineRepository;
import com.dreamy.domain.order.repository.OrderRepository;
import com.dreamy.domain.order.service.OrderEventRecorder;
import com.dreamy.domain.order.service.OrderNoGenerator;
import com.dreamy.domain.payment.entity.Payment;
import com.dreamy.domain.payment.repository.PaymentRepository;
import com.dreamy.domain.refund.entity.Refund;
import com.dreamy.domain.refund.repository.RefundRepository;
import com.dreamy.infra.grpc.CustomerInfoPort;
import com.dreamy.infra.grpc.IdentityGateClient;
import com.dreamy.dto.TradingDtos.AdminRefundDto;
import com.dreamy.dto.TradingDtos.StoreRefundDto;
import com.dreamy.enums.OrderActorType;
import com.dreamy.enums.OrderEventType;
import com.dreamy.enums.OrderStatus;
import com.dreamy.enums.ProductionStage;
import com.dreamy.enums.RefundStatus;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.infra.TradingAfterCommitRunner;
import com.dreamy.infra.TradingAuditRecorder;
import com.dreamy.infra.TradingTxRunner;
import com.dreamy.infra.stripe.StripeClient;
import com.dreamy.infra.stripe.StripeRefund;
import com.dreamy.mq.TradingEventsPublisher;
import com.dreamy.port.SkuStockAdapter;
import com.dreamy.support.Money;
import com.dreamy.support.TradingFieldErrors;
import com.dreamy.support.TradingPaginatedSupport;
import com.dreamy.support.TradingParams;
import huihao.page.Paginated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 退款流服务（FLOW-P10，决策 24/31，s-755；TASK-041/054；order-flow-complete H 部分退款语义）。
 * - 申请（消费端 TX-TRD-009a / 后台代客 TX-TRD-009b）：事务内先 CAS from∈{PAID,SHIPPED,DELIVERED}→REFUNDING
 *   （affected=0 → 409907，由 DB 保证同一订单同一时刻最多一张挂起工单），再 INSERT refund（写 from_status/from_stage 快照）
 *   + order_event(REFUND) + MQ refund.requested。
 * - 审核通过（TX-TRD-003）：casApprove → orders.refunded_amount += amount（条件 ≤ total，否则 422908）→ Stripe 仅退
 *   本次 delta（事务内，失败整体回滚——「钱动账必须动」）→ payment 累计（<total PARTIALLY_REFUNDED / ≥total REFUNDED）
 *   → 单条 UPDATE 判定：累计 ≥ total → REFUNDED，否则还原 from_status（PAID 回写 from_stage）→ 全额时现货回补。
 * - 拒绝（TX-TRD-009c）：casReject + 还原 from_status（PAID 回写 from_stage）。
 * - 后台取消已支付（STATE-7）：同事务创建 + 批准全额剩余退款 → CAS REFUNDING→CANCELLED（语义为商家取消）。
 * - 登记退货单号（决策 31）：pending 工单可单独登记，不触发状态机不发 MQ。
 */
@Service
public class RefundService {

    public static final String ADMIN_CANCEL_REASON = "admin_cancel";

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final PaymentRepository paymentRepository;
    private final CheckoutConfigRepository checkoutConfigRepository;
    private final OrderNoGenerator orderNoGenerator;
    private final SkuStockAdapter skuStockAdapter;
    private final StripeClient stripeClient;
    private final TradingTxRunner txRunner;
    private final TradingAfterCommitRunner afterCommit;
    private final TradingAuditRecorder audit;
    private final TradingEventsPublisher eventsPublisher;
    private final CustomerInfoPort customerInfoPort;
    private final IdentityGateClient identityGateClient;
    private final OrderEventRecorder orderEventRecorder;

    public RefundService(RefundRepository refundRepository, OrderRepository orderRepository,
                         OrderLineRepository orderLineRepository, PaymentRepository paymentRepository,
                         CheckoutConfigRepository checkoutConfigRepository, OrderNoGenerator orderNoGenerator,
                         SkuStockAdapter skuStockAdapter, StripeClient stripeClient, TradingTxRunner txRunner,
                         TradingAfterCommitRunner afterCommit, TradingAuditRecorder audit,
                         TradingEventsPublisher eventsPublisher, CustomerInfoPort customerInfoPort,
                         IdentityGateClient identityGateClient,
                         OrderEventRecorder orderEventRecorder) {
        this.refundRepository = refundRepository;
        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
        this.paymentRepository = paymentRepository;
        this.checkoutConfigRepository = checkoutConfigRepository;
        this.orderNoGenerator = orderNoGenerator;
        this.skuStockAdapter = skuStockAdapter;
        this.stripeClient = stripeClient;
        this.txRunner = txRunner;
        this.afterCommit = afterCommit;
        this.audit = audit;
        this.eventsPublisher = eventsPublisher;
        this.customerInfoPort = customerInfoPort;
        this.identityGateClient = identityGateClient;
        this.orderEventRecorder = orderEventRecorder;
    }

    /** E-applyStoreRefund（V-TRD-035 + STEP-TRD-01~06；TX-TRD-009a）：申请金额 = 剩余可退额（含 gift_wrap_fee，决策 28） */
    public StoreRefundDto applyStoreRefund(Long customerId, Long orderId, String reason) {
        TradingFieldErrors errors = new TradingFieldErrors();
        String parsedReason = TradingParams.requireText(reason, 255, "reason", errors);
        errors.throwIfAny();
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId);
        if (order == null) {
            throw new TradingException(TradingErrorCode.ORDER_NOT_FOUND);
        }
        BigDecimal amount = RefundEligibility.remainingRefundable(order.getTotalAmount(), order.getRefundedAmount());
        Refund refund = createRefundTx(order, amount, parsedReason, false, OrderActorType.CUSTOMER, customerId);
        return toStoreDto(refund);
    }

    /** E-createAdminRefund（V-TRD-052/053 + STEP-TRD-01~05；TX-TRD-009b，校验同消费端；部分退款 amount ≤ 剩余可退额） */
    public AdminRefundDto createAdminRefund(Long orderId, BigDecimal amount, String reason) {
        TradingFieldErrors errors = new TradingFieldErrors();
        if (amount == null || amount.signum() <= 0) {
            errors.reject("amount", amount == null ? "required" : "range_invalid");
        }
        String parsedReason = TradingParams.requireText(reason, 255, "reason", errors);
        errors.throwIfAny();
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            throw new TradingException(TradingErrorCode.ORDER_NOT_FOUND);
        }
        checkAmountCap(order, amount);
        Refund refund = createRefundTx(order, amount, parsedReason, true, OrderActorType.ADMIN,
                audit.currentOperatorId());
        return toAdminDto(refund, order, loadUsers(List.of(order.getCustomerId())));
    }

    /** 金额上限：> total → 422603（存量契约）；> 剩余可退额 → 422908（order-flow-complete） */
    private void checkAmountCap(Order order, BigDecimal amount) {
        if (amount.compareTo(order.getTotalAmount()) > 0) {
            throw new TradingException(TradingErrorCode.REFUND_AMOUNT_EXCEEDED,
                    Map.of("max_refundable", order.getTotalAmount()));
        }
        BigDecimal remaining = RefundEligibility.remainingRefundable(order.getTotalAmount(), order.getRefundedAmount());
        if (amount.compareTo(remaining) > 0) {
            throw new TradingException(TradingErrorCode.REFUND_TOTAL_EXCEEDED,
                    Map.of("max_refundable", remaining));
        }
    }

    /**
     * 申请共用事务体（STATE-6）：js_guard → 定制投产判定 → 事务内 CAS →REFUNDING（409907）→ INSERT refund（快照）
     * → order_event(REFUND) → MQ refund.requested（outbox）。
     */
    private Refund createRefundTx(Order order, BigDecimal amount, String reason, boolean adminInitiated,
                                  OrderActorType actor, Long actorId) {
        // 状态 guard：REFUNDING = 已有挂起工单 → 409907；其余非 paid|shipped|delivered → 409602
        if (order.getStatus() == OrderStatus.REFUNDING) {
            throw new TradingException(TradingErrorCode.REFUND_PENDING_EXISTS);
        }
        if (order.getStatus() == null || !order.getStatus().isPostPaymentActive()) {
            throw TradingException.orderStateInvalid();
        }
        if (amount == null || amount.signum() <= 0) {
            throw new TradingException(TradingErrorCode.REFUND_TOTAL_EXCEEDED, Map.of("max_refundable",
                    RefundEligibility.remainingRefundable(order.getTotalAmount(), order.getRefundedAmount())));
        }
        // 定制投产判定（决策 24 后端双重校验；后台同样生效）→ 422602 + grace_deadline
        int graceHours = checkoutConfigRepository.getSingleton().getCustomRefundGraceHours();
        LocalDateTime now = LocalDateTime.now();
        if (orderLineRepository.existsCustomLine(order.getId())
                && RefundEligibility.customProduced(order.getPaidAt(), graceHours, now)) {
            throw new TradingException(TradingErrorCode.CUSTOM_ITEM_NOT_REFUNDABLE,
                    Map.of("grace_deadline", RefundEligibility.graceDeadline(order.getPaidAt(), graceHours)));
        }
        OrderStatus fromStatus = order.getStatus();
        ProductionStage fromStage = order.getProductionStage();
        return txRunner.inTx(() -> {
            // ① 先 CAS →REFUNDING（DB 保证单张挂起工单；affected=0 → 409907）
            int affected = orderRepository.casUpdateStatus(order.getId(), fromStatus, OrderStatus.REFUNDING, null);
            if (affected == 0) {
                throw new TradingException(TradingErrorCode.REFUND_PENDING_EXISTS);
            }
            // ② INSERT refund（from_status / from_stage 快照）
            Refund refund = insertRefundRow(order, amount, reason, fromStatus, fromStage);
            // ③ order_event(REFUND, actor, 可见)
            Map<String, Object> payload = refundEventPayload(refund);
            payload.put("from", fromStatus.getKey());
            payload.put("to", OrderStatus.REFUNDING.getKey());
            orderEventRecorder.record(order.getId(), OrderEventType.REFUND, actor, actorId,
                    "Refund requested", reason, payload, true);
            if (adminInitiated) {
                audit.record(TradingAuditRecorder.ACTION_REFUND_CREATE, refund.getRefundNo(),
                        "{\"order_no\":\"" + order.getOrderNo() + "\",\"amount\":\"" + amount + "\"}");
            }
            // ④ MQ refund.requested（outbox 事务内落表，提交后投递）
            eventsPublisher.publishRefundRequested(refund, order.getOrderNo());
            return refund;
        });
    }

    private Refund insertRefundRow(Order order, BigDecimal amount, String reason,
                                   OrderStatus fromStatus, ProductionStage fromStage) {
        Refund refund = new Refund();
        refund.setRefundNo(orderNoGenerator.nextRefundNo());
        refund.setOrderId(order.getId());
        refund.setCustomerId(order.getCustomerId());
        refund.setAmount(amount);
        refund.setCurrency(order.getCurrency());
        refund.setReason(reason);
        refund.setStatus(RefundStatus.PENDING);
        refund.setAppliedAt(LocalDateTime.now());
        refund.setFromStatus(fromStatus);
        refund.setFromStage(fromStage);
        refundRepository.insert(refund);
        return refund;
    }

    /** E-approveAdminRefund（V-TRD-055 + STEP-TRD-01~04；TX-TRD-003 Stripe 事务内整体回滚；部分退款累计） */
    public AdminRefundDto approve(Long refundId, String returnTrackingNo) {
        TradingFieldErrors errors = new TradingFieldErrors();
        String trackingNo = TradingParams.checkMaxLength(returnTrackingNo, 64, "return_tracking_no", errors);
        errors.throwIfAny();
        Refund refund = refundRepository.findById(refundId);
        if (refund == null) {
            throw new TradingException(TradingErrorCode.REFUND_NOT_FOUND);
        }
        // STEP-TRD-02 js_guard（终防线为 casApprove）
        if (refund.getStatus() != RefundStatus.PENDING) {
            throw new TradingException(TradingErrorCode.REFUND_STATE_INVALID);
        }
        Order order = orderRepository.findById(refund.getOrderId());
        Payment payment = paymentRepository.findByOrderId(refund.getOrderId());
        Long operatorId = audit.currentOperatorId();
        txRunner.inTx(() -> {
            // ① 条件更新防并发双审（affected=0 → 409604 回滚）
            if (refundRepository.casApprove(refundId, trackingNo) == 0) {
                throw new TradingException(TradingErrorCode.REFUND_STATE_INVALID);
            }
            // ②~④ 累计 + Stripe delta + payment 累计
            StripeRefund stripeRefund = settle(refund, order, payment);
            // ⑤ 单条 UPDATE 判定：累计 ≥ total → REFUNDED；否则还原 from_status（PAID 回写 from_stage）
            OrderStatus fromStatus = resolveFromStatus(refund, order);
            if (orderRepository.casResolveRefunding(order.getId(), fromStatus, refund.getFromStage()) == 0) {
                throw TradingException.orderStateInvalid();
            }
            Order resolved = orderRepository.findById(order.getId());
            OrderStatus finalStatus = resolved == null ? null : resolved.getStatus();
            // ⑥ 全额（REFUNDED）时现货行库存回补（定制行不回补，决策 6）
            if (finalStatus == OrderStatus.REFUNDED) {
                restockSpotLines(order.getId());
            }
            // ⑦ order_event(REFUND, ADMIN, 可见)
            Map<String, Object> payload = refundEventPayload(refund);
            payload.put("result", "approved");
            payload.put("stripe_refund_id", stripeRefund.id());
            payload.put("from", OrderStatus.REFUNDING.getKey());
            payload.put("to", finalStatus == null ? null : finalStatus.getKey());
            payload.put("refunded_amount", resolved == null ? null : resolved.getRefundedAmount());
            orderEventRecorder.record(order.getId(), OrderEventType.REFUND, OrderActorType.ADMIN, operatorId,
                    finalStatus == OrderStatus.REFUNDED ? "Refund approved (full)" : "Refund approved (partial)",
                    null, payload, true);
            // ⑧ operation_log（事务内）
            audit.record(TradingAuditRecorder.ACTION_REFUND_APPROVE, refund.getRefundNo(),
                    "{\"amount\":\"" + refund.getAmount() + "\",\"stripe_refund_id\":\"" + stripeRefund.id()
                            + "\",\"return_tracking_no\":" + (trackingNo == null ? "null" : "\"" + trackingNo + "\"")
                            + ",\"order_status\":" + (finalStatus == null ? "null" : finalStatus.getKey()) + "}");
            // STEP-TRD-04 MQ refund.resolved（EVT-TRD-004，outbox）
            eventsPublisher.publishRefundResolved(refund, order.getOrderNo(), "approved", null);
        });
        return toAdminDto(refundRepository.findById(refundId), orderRepository.findById(order.getId()),
                loadUsers(List.of(refund.getCustomerId())));
    }

    /**
     * 账务结算（事务内）：orders.refunded_amount += amount（超额 422908）→ Stripe 仅退本次 delta（失败/超时 → 异常
     * 向上 → 全量回滚 502601/504601）→ 记 stripe_refund_id → payment 累计 + 状态。
     */
    private StripeRefund settle(Refund refund, Order order, Payment payment) {
        if (orderRepository.addRefundedAmount(order.getId(), refund.getAmount()) == 0) {
            throw new TradingException(TradingErrorCode.REFUND_TOTAL_EXCEEDED, Map.of("max_refundable",
                    RefundEligibility.remainingRefundable(order.getTotalAmount(), order.getRefundedAmount())));
        }
        // Idempotency-Key = 工单号：远端已退而本地回滚/超时重试时 Stripe 返回同一 refund，不二次扣款
        StripeRefund stripeRefund = stripeClient.createRefund(
                payment == null ? null : payment.getPaymentIntentId(),
                Money.toMinor(refund.getAmount()), "requested_by_customer", "refund:" + refund.getRefundNo());
        refundRepository.updateStripeRefundId(refund.getId(), stripeRefund.id());
        if (payment != null && paymentRepository.applyRefund(payment.getId(), refund.getAmount()) == 0) {
            log.warn("[REFUND][ALERT] payment refund accumulate skipped (status={}) refund_no={}",
                    payment.getStatus(), refund.getRefundNo());
        }
        return stripeRefund;
    }

    private void restockSpotLines(Long orderId) {
        for (OrderLine line : orderLineRepository.listSpotLines(orderId)) {
            skuStockAdapter.restock(line.getSkuId(), line.getQty());
        }
    }

    /** from_status 快照；存量工单无快照时按 delivered_at/shipped_at 推断 */
    static OrderStatus resolveFromStatus(Refund refund, Order order) {
        if (refund.getFromStatus() != null) {
            return refund.getFromStatus();
        }
        if (order.getDeliveredAt() != null) {
            return OrderStatus.DELIVERED;
        }
        return order.getShippedAt() == null ? OrderStatus.PAID : OrderStatus.SHIPPED;
    }

    /** E-rejectAdminRefund（V-TRD-056 + STEP-TRD-01~04；TX-TRD-009c 还原 from_status 快照） */
    public AdminRefundDto reject(Long refundId, String reason) {
        TradingFieldErrors errors = new TradingFieldErrors();
        String rejectReason = TradingParams.requireText(reason, 255, "reason", errors);
        errors.throwIfAny();
        Refund refund = refundRepository.findById(refundId);
        if (refund == null) {
            throw new TradingException(TradingErrorCode.REFUND_NOT_FOUND);
        }
        if (refund.getStatus() != RefundStatus.PENDING) {
            throw new TradingException(TradingErrorCode.REFUND_STATE_INVALID);
        }
        Order order = orderRepository.findById(refund.getOrderId());
        Long operatorId = audit.currentOperatorId();
        txRunner.inTx(() -> {
            if (refundRepository.casReject(refundId, rejectReason) == 0) {
                throw new TradingException(TradingErrorCode.REFUND_STATE_INVALID);
            }
            // STEP-TRD-03 订单还原 from_status 快照（PAID 回写 from_stage）
            OrderStatus restoreTo = resolveFromStatus(refund, order);
            orderRepository.casRestoreFromRefunding(order.getId(), restoreTo, refund.getFromStage());
            Map<String, Object> payload = refundEventPayload(refund);
            payload.put("result", "rejected");
            payload.put("reject_reason", rejectReason);
            payload.put("from", OrderStatus.REFUNDING.getKey());
            payload.put("to", restoreTo.getKey());
            orderEventRecorder.record(order.getId(), OrderEventType.REFUND, OrderActorType.ADMIN, operatorId,
                    "Refund rejected", rejectReason, payload, true);
            audit.record(TradingAuditRecorder.ACTION_REFUND_REJECT, refund.getRefundNo(),
                    "{\"reject_reason\":\"" + rejectReason + "\"}");
            eventsPublisher.publishRefundResolved(refund, order.getOrderNo(), "rejected", rejectReason);
        });
        return toAdminDto(refundRepository.findById(refundId), orderRepository.findById(order.getId()),
                loadUsers(List.of(refund.getCustomerId())));
    }

    /**
     * STATE-7 后台取消已支付订单（AdminOrderService.patchStatus 2→5 委托）：同事务
     * 创建 Refund(amount=total−refunded) → 批准（Stripe delta / 累计 / payment）→ CAS REFUNDING→CANCELLED（语义为商家取消）
     * → 现货回补 → 事件（STATUS_CHANGED + REFUND）→ MQ order.cancelled(admin) + refund.resolved。
     * 调用方须先校验 order.status=PAID；本方法不开外层 js_guard 之外的事务边界（自含 txRunner）。
     */
    public Refund adminCancelPaidOrder(Order order) {
        BigDecimal amount = RefundEligibility.remainingRefundable(order.getTotalAmount(), order.getRefundedAmount());
        Payment payment = paymentRepository.findByOrderId(order.getId());
        Long operatorId = audit.currentOperatorId();
        ProductionStage fromStage = order.getProductionStage();
        return txRunner.inTx(() -> {
            // ① PAID→REFUNDING（affected=0 → 已有挂起工单/状态漂移 → 409602）
            if (orderRepository.casUpdateStatus(order.getId(), OrderStatus.PAID, OrderStatus.REFUNDING, null) == 0) {
                throw TradingException.orderStateInvalid();
            }
            Refund refund = insertRefundRow(order, amount, ADMIN_CANCEL_REASON, OrderStatus.PAID, fromStage);
            // ② 批准 + 结算（金额为 0 时仅记录，不触达 Stripe）
            if (refundRepository.casApprove(refund.getId(), null) == 0) {
                throw new TradingException(TradingErrorCode.REFUND_STATE_INVALID);
            }
            String stripeRefundId = null;
            if (amount.signum() > 0) {
                stripeRefundId = settle(refund, order, payment).id();
            }
            // ③ REFUNDING→CANCELLED（商家取消语义；production_stage 清空）
            if (orderRepository.casUpdateStatus(order.getId(), OrderStatus.REFUNDING, OrderStatus.CANCELLED,
                    uw -> OrderRepository.setProductionStage(uw, null)) == 0) {
                throw TradingException.orderStateInvalid();
            }
            // ④ 现货回补（沿用退款批准逻辑）
            restockSpotLines(order.getId());
            // ⑤ 事件：STATUS_CHANGED + REFUND（ADMIN，可见）
            orderEventRecorder.statusChanged(order.getId(), OrderStatus.PAID, OrderStatus.CANCELLED,
                    OrderActorType.ADMIN, operatorId, "cancelled by admin with full refund", true);
            Map<String, Object> payload = refundEventPayload(refund);
            payload.put("result", "approved");
            payload.put("stripe_refund_id", stripeRefundId);
            payload.put("to", OrderStatus.CANCELLED.getKey());
            orderEventRecorder.record(order.getId(), OrderEventType.REFUND, OrderActorType.ADMIN, operatorId,
                    "Refund approved (admin cancel)", null, payload, true);
            audit.record(TradingAuditRecorder.ACTION_REFUND_APPROVE, refund.getRefundNo(),
                    "{\"amount\":\"" + amount + "\",\"stripe_refund_id\":"
                            + (stripeRefundId == null ? "null" : "\"" + stripeRefundId + "\"")
                            + ",\"order_status\":" + OrderStatus.CANCELLED.getKey() + "}");
            // ⑥ MQ
            eventsPublisher.publishOrderCancelled(order, TradingEventsPublisher.CANCEL_REASON_ADMIN);
            eventsPublisher.publishRefundResolved(refund, order.getOrderNo(), "approved", null);
            return refund;
        });
    }

    /** E-patchAdminRefund（V-TRD-057；决策 31 登记类操作，不发 MQ 不写状态机） */
    public AdminRefundDto patchReturnTrackingNo(Long refundId, String returnTrackingNo) {
        TradingFieldErrors errors = new TradingFieldErrors();
        String trackingNo = TradingParams.requireText(returnTrackingNo, 64, "return_tracking_no", errors);
        errors.throwIfAny();
        Refund refund = refundRepository.findById(refundId);
        if (refund == null) {
            throw new TradingException(TradingErrorCode.REFUND_NOT_FOUND);
        }
        if (refund.getStatus() != RefundStatus.PENDING) {
            throw new TradingException(TradingErrorCode.REFUND_STATE_INVALID);
        }
        refundRepository.updateReturnTrackingNo(refundId, trackingNo);
        Refund updated = refundRepository.findById(refundId);
        Order order = orderRepository.findById(updated.getOrderId());
        return toAdminDto(updated, order, loadUsers(List.of(updated.getCustomerId())));
    }

    /** E-listAdminRefunds（V-TRD-054 + STEP-TRD-01；联取 order_no / customer 派生） */
    public Paginated<AdminRefundDto> pageAdmin(Integer page, Integer pageSize, Integer status, String search) {
        TradingFieldErrors errors = new TradingFieldErrors();
        int parsedPage = TradingParams.parsePage(page, errors);
        int parsedSize = TradingParams.parsePageSize(pageSize, errors);
        Integer statusFilter = status;
        if (statusFilter != null && RefundStatus.of(statusFilter) == null) {
            errors.reject("status", "invalid_enum");
        }
        String keyword = TradingParams.checkMaxLength(search, 80, "search", errors);
        errors.throwIfAny();
        RefundStatus statusEnum = statusFilter == null ? null : RefundStatus.of(statusFilter);
        List<Long> orderIds = null;
        List<Long> customerIds = null;
        if (keyword != null) {
            orderIds = orderRepository.findIdsByOrderNoLike(keyword);
            customerIds = findUserIdsByEmailLike(keyword);
        }
        Page<Refund> result = refundRepository.pageByAdminFilter(statusEnum, keyword, orderIds, customerIds,
                parsedPage, parsedSize);
        Map<Long, Order> orders = orderRepository.listByIds(
                        result.getRecords().stream().map(Refund::getOrderId).distinct().toList()).stream()
                .collect(java.util.stream.Collectors.toMap(Order::getId, o -> o));
        Map<Long, CustomerInfoPort.CustomerInfo> users = loadUsers(
                result.getRecords().stream().map(Refund::getCustomerId).distinct().toList());
        return TradingPaginatedSupport.of(result, r -> toAdminDto(r, orders.get(r.getOrderId()), users));
    }

    /** MAP-TRD-008：AdminRefund 视图（order_no/customer 派生 + stripe_refund_id/return_tracking_no + 快照） */
    public AdminRefundDto toAdminDto(Refund refund, Order order, Map<Long, CustomerInfoPort.CustomerInfo> users) {
        CustomerInfoPort.CustomerInfo user = users == null ? null : users.get(refund.getCustomerId());
        return new AdminRefundDto(refund.getId(), refund.getRefundNo(), refund.getOrderId(), refund.getAmount(),
                refund.getCurrency(), refund.getReason(), refund.getRejectReason(), refund.getStatus().getKey(),
                refund.getAppliedAt(), order == null ? null : order.getOrderNo(), refund.getCustomerId(),
                user == null ? null : user.name(), user == null ? null : user.email(),
                refund.getStripeRefundId(), refund.getReturnTrackingNo(),
                refund.getFromStatus() == null ? null : refund.getFromStatus().getKey(),
                refund.getFromStage() == null ? null : refund.getFromStage().getKey(),
                refund.getUpdatedAt());
    }

    /** MAP-TRD-007：StoreRefund 视图隐藏 stripe_refund_id/return_tracking_no/customer_* */
    public static StoreRefundDto toStoreDto(Refund refund) {
        return new StoreRefundDto(refund.getId(), refund.getRefundNo(), refund.getOrderId(), refund.getAmount(),
                refund.getCurrency(), refund.getReason(), refund.getStatus().getKey(), refund.getAppliedAt(),
                refund.getRejectReason(),
                refund.getFromStatus() == null ? null : refund.getFromStatus().getKey(),
                refund.getUpdatedAt());
    }

    private static Map<String, Object> refundEventPayload(Refund refund) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("refund_id", refund.getId());
        payload.put("refund_no", refund.getRefundNo());
        payload.put("amount", refund.getAmount());
        payload.put("currency", refund.getCurrency());
        return payload;
    }

    /** identity 用户快照批量联取(CustomerInfoPort gRPC,防 N+1;gRPC 不可达返回空集由装配侧降级) */
    public Map<Long, CustomerInfoPort.CustomerInfo> loadUsers(List<Long> userIds) {
        return customerInfoPort.byIds(userIds);
    }

    /** 客户邮箱前缀 → user ids(IdentityGate ListUsers LIKE_PREFIX;协议纪律:仅前缀匹配防全表扫描) */
    public List<Long> findUserIdsByEmailLike(String emailLike) {
        String prefix = emailLike == null ? "" : emailLike.trim();
        if (prefix.isEmpty()) {
            return List.of();
        }
        dreamy.identity.v1.ListUsersResponse resp = identityGateClient.listUsers(
                List.of(IdentityGateClient.likePrefix(
                        dreamy.identity.v1.UserColumn.USER_COLUMN_EMAIL, prefix)),
                "id asc", 1, 100);
        return resp.getItemsList().stream().map(dreamy.identity.v1.UserRecord::getId).toList();
    }

    /**
     * RM-TRD-02：客户名/邮箱搜索 → user ids(API-TRD-03 listAdminOrders 搜索范围扩展,ALIGN-015)。
     * 协议无 OR 条件 → name 与 email 各一次 LIKE_PREFIX 取并集(前缀匹配语义,防全表扫描)。
     */
    public List<Long> findUserIdsByNameOrEmailLike(String keyword) {
        String prefix = keyword == null ? "" : keyword.trim();
        if (prefix.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>();
        for (dreamy.identity.v1.UserColumn col : new dreamy.identity.v1.UserColumn[]{
                dreamy.identity.v1.UserColumn.USER_COLUMN_NAME,
                dreamy.identity.v1.UserColumn.USER_COLUMN_EMAIL}) {
            dreamy.identity.v1.ListUsersResponse resp = identityGateClient.listUsers(
                    List.of(IdentityGateClient.likePrefix(col, prefix)), "id asc", 1, 100);
            resp.getItemsList().forEach(r -> ids.add(r.getId()));
        }
        return List.copyOf(ids);
    }
}
