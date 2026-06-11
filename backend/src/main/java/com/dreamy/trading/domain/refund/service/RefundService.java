package com.dreamy.trading.domain.refund.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.identity.domain.user.entity.User;
import com.dreamy.identity.domain.user.repository.UserMapper;
import com.dreamy.infra.stripe.StripeClient;
import com.dreamy.infra.stripe.StripeRefund;
import com.dreamy.trading.domain.checkout.repository.CheckoutConfigRepository;
import com.dreamy.trading.domain.enums.OrderStatus;
import com.dreamy.trading.domain.enums.PaymentStatus;
import com.dreamy.trading.domain.enums.RefundStatus;
import com.dreamy.trading.domain.order.entity.Order;
import com.dreamy.trading.domain.order.entity.OrderLine;
import com.dreamy.trading.domain.order.repository.OrderLineRepository;
import com.dreamy.trading.domain.order.repository.OrderRepository;
import com.dreamy.trading.domain.order.service.OrderNoGenerator;
import com.dreamy.trading.domain.payment.entity.Payment;
import com.dreamy.trading.domain.payment.repository.PaymentRepository;
import com.dreamy.trading.domain.refund.entity.Refund;
import com.dreamy.trading.domain.refund.repository.RefundRepository;
import com.dreamy.trading.dto.TradingDtos.AdminRefundDto;
import com.dreamy.trading.dto.TradingDtos.StoreRefundDto;
import com.dreamy.trading.error.TradingErrorCode;
import com.dreamy.trading.error.TradingException;
import com.dreamy.trading.infra.TradingAfterCommitRunner;
import com.dreamy.trading.infra.TradingAuditRecorder;
import com.dreamy.trading.infra.TradingTxRunner;
import com.dreamy.trading.mq.TradingEventsPublisher;
import com.dreamy.trading.port.SkuStockAdapter;
import com.dreamy.trading.support.FieldErrors;
import com.dreamy.trading.support.Money;
import com.dreamy.trading.support.PaginatedSupport;
import com.dreamy.trading.support.TradingParams;
import huihao.page.Paginated;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 退款流服务（FLOW-P10，决策 24/31，s-755；TASK-041/054）。
 * - 申请（消费端 TX-TRD-009a / 后台代客 TX-TRD-009b）：INSERT refund + orders→refunding（条件更新）。
 * - 审核通过（TX-TRD-003，BE-DIM-4 核心）：Stripe 置于事务内，失败整体回滚——「钱动账必须动」
 *   （error-strategy 事务一致性约束第二式）；超时假成功由 charge.refunded webhook 对账告警闭环。
 * - 拒绝（TX-TRD-009c）：casReject + orders refunding→paid|shipped 按 shipped_at 还原。
 * - 登记退货单号（决策 31）：pending 工单可单独登记，不触发状态机不发 MQ。
 */
@Service
public class RefundService {

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
    private final UserMapper userMapper;

    public RefundService(RefundRepository refundRepository, OrderRepository orderRepository,
                         OrderLineRepository orderLineRepository, PaymentRepository paymentRepository,
                         CheckoutConfigRepository checkoutConfigRepository, OrderNoGenerator orderNoGenerator,
                         SkuStockAdapter skuStockAdapter, StripeClient stripeClient, TradingTxRunner txRunner,
                         TradingAfterCommitRunner afterCommit, TradingAuditRecorder audit,
                         TradingEventsPublisher eventsPublisher, UserMapper userMapper) {
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
        this.userMapper = userMapper;
    }

    /** E-applyStoreRefund（V-TRD-035 + STEP-TRD-01~06；TX-TRD-009a） */
    public StoreRefundDto applyStoreRefund(Long customerId, Long orderId, String reason) {
        FieldErrors errors = new FieldErrors();
        String parsedReason = TradingParams.requireText(reason, 255, "reason", errors);
        errors.throwIfAny();
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId);
        if (order == null) {
            throw new TradingException(TradingErrorCode.ORDER_NOT_FOUND);
        }
        // 全额含 gift_wrap_fee（决策 28）
        Refund refund = createRefundTx(order, order.getTotalAmount(), parsedReason, false);
        return new StoreRefundDto(refund.getId(), refund.getRefundNo(), refund.getOrderId(), refund.getAmount(),
                refund.getCurrency(), refund.getReason(), refund.getStatus().getKey(), refund.getAppliedAt());
    }

    /** E-createAdminRefund（V-TRD-052/053 + STEP-TRD-01~05；TX-TRD-009b，校验同消费端） */
    public AdminRefundDto createAdminRefund(Long orderId, BigDecimal amount, String reason) {
        FieldErrors errors = new FieldErrors();
        if (amount == null || amount.signum() < 0) {
            errors.reject("amount", amount == null ? "required" : "range_invalid");
        }
        String parsedReason = TradingParams.requireText(reason, 255, "reason", errors);
        errors.throwIfAny();
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            throw new TradingException(TradingErrorCode.ORDER_NOT_FOUND);
        }
        // STEP-TRD-04 金额上限（决策 28，含礼品包装费）→ 422603
        if (amount.compareTo(order.getTotalAmount()) > 0) {
            throw new TradingException(TradingErrorCode.REFUND_AMOUNT_EXCEEDED,
                    Map.of("max_refundable", order.getTotalAmount()));
        }
        Refund refund = createRefundTx(order, amount, parsedReason, true);
        return toAdminDto(refund, order, loadUsers(List.of(order.getCustomerId())));
    }

    /** 申请共用事务体（状态 guard / 进行中工单 / 定制投产判定 / INSERT + refunding） */
    private Refund createRefundTx(Order order, BigDecimal amount, String reason, boolean adminInitiated) {
        // 状态 guard：paid|shipped
        if (order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.SHIPPED) {
            throw TradingException.orderStateInvalid();
        }
        // 进行中工单（409605）
        if (refundRepository.existsPendingByOrderId(order.getId())) {
            throw new TradingException(TradingErrorCode.REFUND_ALREADY_EXISTS);
        }
        // 定制投产判定（决策 24 后端双重校验；后台同样生效）→ 422602 + grace_deadline
        int graceHours = checkoutConfigRepository.getSingleton().getCustomRefundGraceHours();
        LocalDateTime now = LocalDateTime.now();
        if (orderLineRepository.existsCustomLine(order.getId())
                && RefundEligibility.customProduced(order.getPaidAt(), graceHours, now)) {
            throw new TradingException(TradingErrorCode.CUSTOM_ITEM_NOT_REFUNDABLE,
                    Map.of("grace_deadline", RefundEligibility.graceDeadline(order.getPaidAt(), graceHours)));
        }
        return txRunner.inTx(() -> {
            Refund refund = new Refund();
            refund.setRefundNo(orderNoGenerator.nextRefundNo());
            refund.setOrderId(order.getId());
            refund.setCustomerId(order.getCustomerId());
            refund.setAmount(amount);
            refund.setCurrency(order.getCurrency());
            refund.setReason(reason);
            refund.setStatus(RefundStatus.PENDING);
            refund.setAppliedAt(LocalDateTime.now());
            refundRepository.insert(refund);
            // orders → refunding（条件更新，affected=0 → 回滚 409602）
            int affected = orderRepository.casUpdateStatus(order.getId(), order.getStatus(),
                    OrderStatus.REFUNDING, null);
            if (affected == 0) {
                throw TradingException.orderStateInvalid();
            }
            if (adminInitiated) {
                audit.record(TradingAuditRecorder.ACTION_REFUND_CREATE, refund.getRefundNo(),
                        "{\"order_no\":\"" + order.getOrderNo() + "\",\"amount\":\"" + amount + "\"}");
            }
            return refund;
        });
    }

    /** E-approveAdminRefund（V-TRD-055 + STEP-TRD-01~04；TX-TRD-003 Stripe 事务内整体回滚） */
    public AdminRefundDto approve(Long refundId, String returnTrackingNo) {
        FieldErrors errors = new FieldErrors();
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
        txRunner.inTx(() -> {
            // ① 条件更新防并发双审（affected=0 → 409604 回滚）
            if (refundRepository.casApprove(refundId, trackingNo) == 0) {
                throw new TradingException(TradingErrorCode.REFUND_STATE_INVALID);
            }
            // ② Stripe Refund 事务内（原币种原金额，决策 14）：失败/超时 → 异常向上 → 全量回滚（502601/504601）
            StripeRefund stripeRefund = stripeClient.createRefund(
                    payment == null ? null : payment.getPaymentIntentId(),
                    Money.toMinor(refund.getAmount()), "requested_by_customer");
            // ③ 记 stripe_refund_id
            refundRepository.updateStripeRefundId(refundId, stripeRefund.id());
            // ④ orders refunding→refunded
            if (orderRepository.casUpdateStatus(order.getId(), OrderStatus.REFUNDING, OrderStatus.REFUNDED,
                    null) == 0) {
                throw TradingException.orderStateInvalid();
            }
            // ⑤ 现货行库存回补（定制行不回补，决策 6）
            for (OrderLine line : orderLineRepository.listSpotLines(order.getId())) {
                skuStockAdapter.restock(line.getSkuId(), line.getQty());
            }
            // ⑥ payment succeeded→refunded
            if (payment != null) {
                paymentRepository.casUpdateStatus(payment.getId(),
                        List.of(PaymentStatus.SUCCEEDED), PaymentStatus.REFUNDED, null, null);
            }
            // ⑦ operation_log（事务内）
            audit.record(TradingAuditRecorder.ACTION_REFUND_APPROVE, refund.getRefundNo(),
                    "{\"amount\":\"" + refund.getAmount() + "\",\"stripe_refund_id\":\"" + stripeRefund.id()
                            + "\",\"return_tracking_no\":" + (trackingNo == null ? "null" : "\"" + trackingNo + "\"")
                            + "}");
            // STEP-TRD-04 提交后 MQ refund.resolved（EVT-TRD-004）
            afterCommit.run(() -> eventsPublisher.publishRefundResolved(refund, order.getOrderNo(),
                    "approved", null));
        });
        return toAdminDto(refundRepository.findById(refundId), order, loadUsers(List.of(refund.getCustomerId())));
    }

    /** E-rejectAdminRefund（V-TRD-056 + STEP-TRD-01~04；TX-TRD-009c） */
    public AdminRefundDto reject(Long refundId, String reason) {
        FieldErrors errors = new FieldErrors();
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
        txRunner.inTx(() -> {
            if (refundRepository.casReject(refundId, rejectReason) == 0) {
                throw new TradingException(TradingErrorCode.REFUND_STATE_INVALID);
            }
            // STEP-TRD-03 订单回原状态（refunding→paid|shipped 按 shipped_at）
            OrderStatus restoreTo = order.getShippedAt() == null ? OrderStatus.PAID : OrderStatus.SHIPPED;
            orderRepository.casRestoreFromRefunding(order.getId(), restoreTo);
            audit.record(TradingAuditRecorder.ACTION_REFUND_REJECT, refund.getRefundNo(),
                    "{\"reject_reason\":\"" + rejectReason + "\"}");
            afterCommit.run(() -> eventsPublisher.publishRefundResolved(refund, order.getOrderNo(),
                    "rejected", rejectReason));
        });
        return toAdminDto(refundRepository.findById(refundId), order, loadUsers(List.of(refund.getCustomerId())));
    }

    /** E-patchAdminRefund（V-TRD-057；决策 31 登记类操作，不发 MQ 不写状态机） */
    public AdminRefundDto patchReturnTrackingNo(Long refundId, String returnTrackingNo) {
        FieldErrors errors = new FieldErrors();
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
    public Paginated<AdminRefundDto> pageAdmin(Integer page, Integer pageSize, String status, String search) {
        FieldErrors errors = new FieldErrors();
        int parsedPage = TradingParams.parsePage(page, errors);
        int parsedSize = TradingParams.parsePageSize(pageSize, errors);
        String statusFilter = (status == null || status.isBlank()) ? "all" : status;
        if (!TradingParams.REFUND_STATUS_FILTER.contains(statusFilter)) {
            errors.reject("status", "invalid_enum");
        }
        String keyword = TradingParams.checkMaxLength(search, 80, "search", errors);
        errors.throwIfAny();
        RefundStatus statusEnum = "all".equals(statusFilter) ? null : RefundStatus.of(statusFilter);
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
        Map<Long, User> users = loadUsers(result.getRecords().stream().map(Refund::getCustomerId).distinct().toList());
        return PaginatedSupport.of(result, r -> toAdminDto(r, orders.get(r.getOrderId()), users));
    }

    /** MAP-TRD-008：AdminRefund 视图（order_no/customer 派生 + stripe_refund_id/return_tracking_no） */
    public AdminRefundDto toAdminDto(Refund refund, Order order, Map<Long, User> users) {
        User user = users == null ? null : users.get(refund.getCustomerId());
        return new AdminRefundDto(refund.getId(), refund.getRefundNo(), refund.getOrderId(), refund.getAmount(),
                refund.getCurrency(), refund.getReason(), refund.getRejectReason(), refund.getStatus().getKey(),
                refund.getAppliedAt(), order == null ? null : order.getOrderNo(), refund.getCustomerId(),
                user == null ? null : user.getName(), user == null ? null : user.getEmail(),
                refund.getStripeRefundId(), refund.getReturnTrackingNo());
    }

    /** identity 用户快照批量联取（进程内只读，防 N+1；查询优化补充：先 identity 后 IN 回查口径同源） */
    public Map<Long, User> loadUsers(List<Long> userIds) {
        Map<Long, User> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        for (User user : userMapper.selectByIds(userIds)) {
            result.put(user.getId(), user);
        }
        return result;
    }

    /** 客户邮箱模糊 → user ids（identity 自有索引，避免跨域 join——IDX-TRD 查询优化补充） */
    public List<Long> findUserIdsByEmailLike(String emailLike) {
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                        .like(User::getEmail, emailLike)
                        .select(User::getId)
                        .last("LIMIT 500"))
                .stream().map(User::getId).toList();
    }

    /**
     * RM-TRD-02：客户名/邮箱模糊 → user ids（API-TRD-03 listAdminOrders 搜索范围扩展，ALIGN-015；
     * QP-TRD-01：LIKE '%kw%' 不走索引，与既有邮箱搜索同量级，管理端低频可接受，不引入额外索引）。
     */
    public List<Long> findUserIdsByNameOrEmailLike(String keyword) {
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                        .and(w -> w.like(User::getName, keyword).or().like(User::getEmail, keyword))
                        .select(User::getId)
                        .last("LIMIT 500"))
                .stream().map(User::getId).toList();
    }
}
