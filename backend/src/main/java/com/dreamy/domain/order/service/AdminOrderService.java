package com.dreamy.domain.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.domain.user.entity.User;
import com.dreamy.enums.OrderStatus;
import com.dreamy.domain.order.entity.Order;
import com.dreamy.domain.order.entity.OrderLine;
import com.dreamy.domain.order.repository.OrderLineRepository;
import com.dreamy.domain.order.repository.OrderRepository;
import com.dreamy.domain.payment.repository.PaymentRepository;
import com.dreamy.domain.refund.entity.Refund;
import com.dreamy.domain.refund.repository.RefundRepository;
import com.dreamy.domain.refund.service.RefundService;
import com.dreamy.domain.checkout.repository.CheckoutConfigRepository;
import com.dreamy.dto.TradingDtos.AdminOrderDetail;
import com.dreamy.dto.TradingDtos.AdminOrderListItem;
import com.dreamy.dto.TradingDtos.AdminRefundDto;
import com.dreamy.dto.TradingDtos.OrderLineDto;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.infra.TradingAfterCommitRunner;
import com.dreamy.infra.TradingAuditRecorder;
import com.dreamy.infra.TradingTxRunner;
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

    /** E-listAdminOrders（V-TRD-043~047 + STEP-TRD-01/02；API-TRD-03 搜索范围含客户名——ALIGN-015） */
    public Paginated<AdminOrderListItem> list(Integer page, Integer pageSize, Integer status, String search,
                                              String currency, LocalDateTime from, LocalDateTime to) {
        TradingFieldErrors errors = new TradingFieldErrors();
        int parsedPage = TradingParams.parsePage(page, errors);
        int parsedSize = TradingParams.parsePageSize(pageSize, errors);
        AdminOrderFilter filter = parseFilter(status, search, currency, from, to, errors);
        // RM-TRD-02 客户名/邮箱搜索：identity 先解析 customer_ids 再回本域 IN（避免跨域 join）
        List<Long> customerIds = filter.keyword() == null ? null
                : refundService.findUserIdsByNameOrEmailLike(filter.keyword());
        Page<Order> result = orderRepository.pageByAdminFilter(filter.status(), filter.currency(),
                filter.from(), filter.to(), filter.keyword(), customerIds, parsedPage, parsedSize);
        // STEP-TRD-02 customer_name/customer_email 批量联取（防 N+1）
        Map<Long, User> users = refundService.loadUsers(
                result.getRecords().stream().map(Order::getCustomerId).distinct().toList());
        // RM-TRD-01c item_count 批量聚合（SUM(qty) GROUP BY，缺失 → 0）
        Map<Long, Integer> itemCounts = orderLineRepository.sumQtyByOrderIds(
                result.getRecords().stream().map(Order::getId).toList());
        return TradingPaginatedSupport.of(result,
                order -> toListItem(order, users.get(order.getCustomerId()), itemCounts));
    }

    // ==================== 订单导出（API-TRD-02） ====================

    /** STEP-02：keyset 游标批大小 */
    static final int EXPORT_BATCH_SIZE = 500;
    /** STEP-03：导出行数上限（BE-DIM-8） */
    static final int EXPORT_MAX_ROWS = 10000;
    /** STEP-03：截断标记末行 */
    static final String EXPORT_TRUNCATED_LINE = "# TRUNCATED AT 10000 ROWS";
    /** API-TRD-02 出参列序 */
    private static final String EXPORT_CSV_HEADER =
            "order_no,customer_name,customer_email,country,item_count,total_amount,currency,payment_method,status,created_at";

    /** E-exportAdminOrders（API-TRD-02：V-101/102 + STEP-01~04；CSV ≤10000 行） */
    public OrderExport export(Integer status, String search, String currency,
                              LocalDateTime from, LocalDateTime to) {
        // V-101 query 与 listAdminOrders 完全一致（不含分页）；V-102 枚举外值/from>to 非法（既有 422601 校验口径）
        AdminOrderFilter filter = parseFilter(status, search, currency, from, to, new TradingFieldErrors());
        // STEP-01 组装列表同款查询条件（RM-TRD-02 客户名/邮箱 → customer_ids）
        List<Long> customerIds = filter.keyword() == null ? null
                : refundService.findUserIdsByNameOrEmailLike(filter.keyword());
        StringBuilder csv = new StringBuilder("\uFEFF").append(EXPORT_CSV_HEADER).append('\n');
        int rows = 0;
        boolean truncated = false;
        long lastId = 0L;
        // STEP-02 keyset 游标流式读取（id ASC，批 500），每批做 RM-TRD-01b/01c 派生
        while (!truncated) {
            List<Order> batch = orderRepository.listByAdminFilterAfterId(filter.status(), filter.currency(),
                    filter.from(), filter.to(), filter.keyword(), customerIds, lastId, EXPORT_BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            Map<Long, User> users = refundService.loadUsers(
                    batch.stream().map(Order::getCustomerId).distinct().toList());
            Map<Long, Integer> itemCounts = orderLineRepository.sumQtyByOrderIds(
                    batch.stream().map(Order::getId).toList());
            for (Order order : batch) {
                // STEP-03 行数达 10000 → 截断
                if (rows >= EXPORT_MAX_ROWS) {
                    truncated = true;
                    break;
                }
                csv.append(csvLine(order, users.get(order.getCustomerId()), itemCounts)).append('\n');
                rows++;
            }
            lastId = batch.get(batch.size() - 1).getId();
            if (batch.size() < EXPORT_BATCH_SIZE) {
                break;
            }
        }
        if (truncated) {
            csv.append(EXPORT_TRUNCATED_LINE).append('\n');
        }
        // STEP-04 OperationLog（action=导出订单，detail 含筛选条件、行数；PII 导出审计——BE-DIM-8）
        audit.record(TradingAuditRecorder.ACTION_ORDER_EXPORT, "orders",
                "{\"status\":\"" + (status == null ? "all" : String.valueOf(status))
                        + "\",\"search\":" + jsonText(filter.keyword())
                        + ",\"currency\":" + jsonText(filter.currency())
                        + ",\"from\":" + jsonText(from == null ? null : from.toString())
                        + ",\"to\":" + jsonText(to == null ? null : to.toString())
                        + ",\"rows\":" + rows + ",\"truncated\":" + truncated + "}");
        return new OrderExport(csv.toString(), rows, truncated);
    }

    /** V-TRD-043~047 / V-101~102：列表与导出共用筛选参数解析（导出不含分页） */
    private AdminOrderFilter parseFilter(Integer status, String search, String currency,
                                         LocalDateTime from, LocalDateTime to, TradingFieldErrors errors) {
        Integer statusFilter = status;
        if (statusFilter != null && OrderStatus.of(statusFilter) == null) {
            errors.reject("status", "invalid_enum");
        }
        String keyword = TradingParams.checkMaxLength(search, 80, "search", errors);
        if (currency != null && !currency.isBlank() && !TradingParams.isSupportedCurrency(currency)) {
            errors.reject("currency", "invalid_enum");
        }
        // V-TRD-047 / V-102 from ≤ to
        if (from != null && to != null && from.isAfter(to)) {
            errors.reject("from", "range_invalid");
        }
        errors.throwIfAny();
        OrderStatus statusEnum = statusFilter == null ? null : OrderStatus.of(statusFilter);
        String currencyFilter = (currency == null || currency.isBlank()) ? null : currency;
        return new AdminOrderFilter(statusEnum, currencyFilter, from, to, keyword);
    }

    /** 列表/导出共用筛选载体（V-101：query 与 listAdminOrders 一致，不含分页） */
    private record AdminOrderFilter(OrderStatus status, String currency, LocalDateTime from,
                                    LocalDateTime to, String keyword) {
    }

    /** 导出结果载体（csv 含 UTF-8 BOM；truncated → 响应头 X-Export-Truncated——STEP-03） */
    public record OrderExport(String csv, int rowCount, boolean truncated) {
    }

    /** CSV 行装配（列序见 API-TRD-02 出参；country=RM-TRD-01b / item_count=RM-TRD-01c 派生；
     * customer_name/customer_email/country 为顾客侧可控文本 → 公式注入中和（L4 security 修复），
     * 金额/数字/系统枚举列不做中和） */
    private String csvLine(Order order, User user, Map<Long, Integer> itemCounts) {
        return String.join(",",
                csvCell(order.getOrderNo()),
                csvCellUntrusted(user == null ? null : user.getName()),
                csvCellUntrusted(user == null ? null : user.getEmail()),
                csvCellUntrusted(extractCountry(order)),
                String.valueOf(itemCounts.getOrDefault(order.getId(), 0)),
                csvCell(order.getTotalAmount() == null ? null : order.getTotalAmount().toPlainString()),
                csvCell(order.getCurrency()),
                csvCell(order.getPaymentMethod()),
                csvCell(String.valueOf(order.getStatus().getKey())),
                csvCell(order.getCreatedAt() == null ? null : order.getCreatedAt().toString()));
    }

    /** CSV 转义（含逗号/引号/换行 → 双引号包裹 + 引号翻倍；null → 空串） */
    private static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * 顾客可控文本列的 CSV 公式注入中和（L4 security ISS 修复，OWASP CSV Injection）：
     * 值以 = + - @ \t 开头 → 前置 ' 使电子表格按文本解析，再走常规 csvCell 转义。
     */
    static String csvCellUntrusted(String value) {
        if (value != null && !value.isEmpty()) {
            char first = value.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t') {
                value = "'" + value;
            }
        }
        return csvCell(value);
    }

    /** 审计 detail JSON 文本值（null → null 字面量） */
    private static String jsonText(String value) {
        return value == null ? "null" : "\"" + value.replace("\"", "\\\"") + "\"";
    }

    /** RM-TRD-01b：country 取 orders.address_snapshot JSON 列 country 键（实体已反序列化为 Map，无额外 join） */
    private static String extractCountry(Order order) {
        if (order.getAddressSnapshot() == null) {
            return null;
        }
        Object country = order.getAddressSnapshot().get("country");
        return country == null ? null : country.toString();
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
        TradingFieldErrors errors = new TradingFieldErrors();
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
    public AdminOrderDetail patchStatus(Long orderId, Integer status) {
        if (!OrderStatus.COMPLETED.getKey().equals(status) && !OrderStatus.CANCELLED.getKey().equals(status)) {
            throw TradingException.fieldValidation("status", "invalid_enum");
        }
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            throw new TradingException(TradingErrorCode.ORDER_NOT_FOUND);
        }
        if (OrderStatus.COMPLETED.getKey().equals(status)) {
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

    // ==================== 装配（MAP-TRD-005 + API-TRD-01 扩展列） ====================

    /** MAP-TRD-005：country（RM-TRD-01b）/ item_count（RM-TRD-01c，缺失 → 0）追加 */
    private AdminOrderListItem toListItem(Order o, User user, Map<Long, Integer> itemCounts) {
        return new AdminOrderListItem(o.getId(), o.getOrderNo(), o.getStatus().getKey(), o.getCurrency(),
                o.getExchangeRate(), o.getWeddingDate(), o.getSubtotal(), o.getShippingFee(), o.getGiftWrap(),
                o.getGiftWrapFee(), o.getDiscountAmount(), o.getTotalAmount(), o.getCouponId(),
                o.getPaymentMethod(), o.getCarrier(), o.getTrackingNo(), o.getExpiresAt(), o.getPaidAt(),
                o.getShippedAt(), o.getCompletedAt(), o.getCreatedAt(), o.getCustomerId(),
                user == null ? null : user.getName(), user == null ? null : user.getEmail(),
                extractCountry(o), itemCounts.getOrDefault(o.getId(), 0));
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
