package com.dreamy.domain.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.domain.user.entity.User;
import com.dreamy.domain.checkout.repository.CheckoutConfigRepository;
import com.dreamy.enums.OrderActorType;
import com.dreamy.enums.OrderEventType;
import com.dreamy.enums.OrderStatus;
import com.dreamy.enums.ProductionStage;
import com.dreamy.domain.order.entity.Order;
import com.dreamy.domain.order.repository.OrderLineRepository;
import com.dreamy.domain.order.repository.OrderRepository;
import com.dreamy.domain.payment.repository.PaymentRepository;
import com.dreamy.domain.refund.repository.RefundRepository;
import com.dreamy.domain.refund.service.RefundService;
import com.dreamy.dto.TradingDtos.AdminOrderListItem;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.infra.TradingAfterCommitRunner;
import com.dreamy.infra.TradingAuditRecorder;
import com.dreamy.mq.TradingEventsPublisher;
import com.dreamy.testsupport.TradingImmediateTxRunner;
import huihao.page.Paginated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 后台订单列表扩展列 + CSV 导出单测（admin-prototype-alignment unit_task_be_trd_001）。
 * L2 TRACE: API-TRD-01（country/item_count——RM-TRD-01b/01c）/
 * API-TRD-02（V-101/V-102 + STEP-01~04，ALIGN-012）/
 * API-TRD-03（搜索范围回对客户名——RM-TRD-02，ALIGN-015）。
 */
@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {

    @Mock
    OrderRepository orderRepository;
    @Mock
    OrderLineRepository orderLineRepository;
    @Mock
    PaymentRepository paymentRepository;
    @Mock
    RefundRepository refundRepository;
    @Mock
    CheckoutConfigRepository checkoutConfigRepository;
    @Mock
    OrderCancelService orderCancelService;
    @Mock
    RefundService refundService;
    @Mock
    TradingAuditRecorder audit;
    @Mock
    TradingEventsPublisher eventsPublisher;
    @Mock
    OrderEventRecorder orderEventRecorder;
    @Mock
    com.dreamy.domain.shipment.service.ShipmentService shipmentService;
    @Mock
    com.dreamy.domain.shipment.service.ShipmentQueryService shipmentQueryService;
    @Mock
    com.dreamy.domain.shipment.repository.ShipmentRepository shipmentRepository;

    AdminOrderService service;

    @BeforeEach
    void setUp() {
        service = new AdminOrderService(orderRepository, orderLineRepository, paymentRepository, refundRepository,
                checkoutConfigRepository, orderCancelService, refundService,
                new TradingImmediateTxRunner(), new TradingAfterCommitRunner(), audit, eventsPublisher,
                orderEventRecorder, shipmentService, shipmentQueryService, shipmentRepository);
        lenient().when(refundService.loadUsers(any())).thenReturn(Map.of());
        lenient().when(shipmentQueryService.listByOrder(anyLong())).thenReturn(List.of());
        lenient().when(orderLineRepository.sumQtyByOrderIds(any())).thenReturn(Map.of());
    }

    private Order order(long id, String country) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNo("DRM-20260610-" + String.format("%04d", id));
        order.setCustomerId(7L);
        order.setStatus(OrderStatus.PAID);
        order.setCurrency("USD");
        order.setExchangeRate(BigDecimal.ONE);
        order.setSubtotal(new BigDecimal("200.00"));
        order.setShippingFee(new BigDecimal("37.00"));
        order.setGiftWrap(Boolean.FALSE);
        order.setGiftWrapFee(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotalAmount(new BigDecimal("237.00"));
        order.setPaymentMethod("Stripe");
        if (country != null) {
            order.setAddressSnapshot(Map.of("country", country, "city", "New York"));
        }
        order.setCreatedAt(LocalDateTime.of(2026, 6, 10, 12, 0));
        return order;
    }

    private User user(long id, String name, String email) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(email);
        return user;
    }

    private Page<Order> pageOf(List<Order> records) {
        Page<Order> page = new Page<>(1, 20);
        page.setRecords(records);
        page.setTotal(records.size());
        return page;
    }

    // ==================== API-TRD-01 列表扩展列 ====================

    @Test
    @DisplayName("API-TRD-01/RM-TRD-01b/01c：列表派生 country=address_snapshot.country、item_count=SUM(qty)，缺失聚合 → 0")
    void listDerivesCountryAndItemCount() {
        when(orderRepository.pageByAdminFilter(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(pageOf(List.of(order(1L, "US"), order(2L, null))));
        when(refundService.loadUsers(any())).thenReturn(Map.of(7L, user(7L, "Alice", "alice@example.com")));
        when(orderLineRepository.sumQtyByOrderIds(any())).thenReturn(Map.of(1L, 3));

        Paginated<AdminOrderListItem> result = service.list(1, 20, null, null, null, null, null);

        AdminOrderListItem first = result.getData().get(0);
        assertThat(first.country()).isEqualTo("US");
        assertThat(first.itemCount()).isEqualTo(3);
        assertThat(first.customerName()).isEqualTo("Alice");
        AdminOrderListItem second = result.getData().get(1);
        assertThat(second.country()).isNull();
        assertThat(second.itemCount()).isZero();
    }

    // ==================== API-TRD-03 搜索范围扩展 ====================

    @Test
    @DisplayName("API-TRD-03/RM-TRD-02：search 经 identity 客户名/邮箱模糊解析 customer_ids（不再仅邮箱）")
    void listSearchResolvesCustomerIdsByNameOrEmail() {
        when(refundService.findUserIdsByNameOrEmailLike("Alice")).thenReturn(List.of(7L));
        when(orderRepository.pageByAdminFilter(any(), any(), any(), any(), eq("Alice"), eq(List.of(7L)),
                any(), any(), anyInt(), anyInt())).thenReturn(pageOf(List.of()));

        service.list(1, 20, null, "Alice", null, null, null);

        verify(refundService).findUserIdsByNameOrEmailLike("Alice");
        verify(orderRepository).pageByAdminFilter(any(), any(), any(), any(), eq("Alice"), eq(List.of(7L)),
                any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("API-TRD-03：search 为空时不触发 identity 解析")
    void listWithoutSearchSkipsCustomerResolution() {
        when(orderRepository.pageByAdminFilter(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(pageOf(List.of()));

        service.list(1, 20, null, null, null, null, null);

        verify(refundService, never()).findUserIdsByNameOrEmailLike(any());
    }

    // ==================== API-TRD-02 导出：V-101/V-102 ====================

    @Test
    @DisplayName("V-102：status 枚举外值 → 422601 字段校验失败（与 listAdminOrders 同口径，V-101）")
    void exportRejectsInvalidStatus() {
        assertThatThrownBy(() -> service.export(99, null, null, null, null))
                .isInstanceOfSatisfying(TradingException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.FIELD_VALIDATION_FAILED));
        verify(audit, never()).record(any(), any(), any());
    }

    @Test
    @DisplayName("V-102：from > to → 422601 字段校验失败")
    void exportRejectsInvertedTimeWindow() {
        LocalDateTime from = LocalDateTime.of(2026, 6, 10, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 6, 1, 0, 0);
        assertThatThrownBy(() -> service.export(null, null, null, from, to))
                .isInstanceOfSatisfying(TradingException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.FIELD_VALIDATION_FAILED));
    }

    @Test
    @DisplayName("V-101：currency 枚举外值 → 422601（query 与 listAdminOrders 完全一致）")
    void exportRejectsInvalidCurrency() {
        assertThatThrownBy(() -> service.export(null, null, "JPY", null, null))
                .isInstanceOfSatisfying(TradingException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.FIELD_VALIDATION_FAILED));
    }

    // ==================== API-TRD-02 导出：STEP-01~04 ====================

    @Test
    @DisplayName("TC-ALIGN-012「仅 paid 行」：status=paid 解析为枚举并透传 keyset 查询（行过滤由 SQL 端执行，与列表同口径）")
    void exportPassesStatusFilterToQuery() {
        when(orderRepository.listByAdminFilterAfterId(eq(OrderStatus.PAID), any(), any(), any(), any(), any(),
                anyLong(), anyInt())).thenReturn(List.of());

        service.export(2, null, null, null, null);

        verify(orderRepository).listByAdminFilterAfterId(eq(OrderStatus.PAID), any(), any(), any(), any(), any(),
                eq(0L), eq(AdminOrderService.EXPORT_BATCH_SIZE));
    }

    @Test
    @DisplayName("STEP-01/02 + RM-TRD-01b/01c：CSV 含 BOM/表头/派生列行；STEP-04 审计含行数")
    void exportWritesCsvWithDerivedColumnsAndAudit() {
        when(orderRepository.listByAdminFilterAfterId(any(), any(), any(), any(), any(), any(),
                anyLong(), anyInt())).thenReturn(List.of(order(1L, "US"))).thenReturn(List.of());
        when(refundService.loadUsers(any())).thenReturn(Map.of(7L, user(7L, "Alice, Smith", "alice@example.com")));
        when(orderLineRepository.sumQtyByOrderIds(any())).thenReturn(Map.of(1L, 3));

        AdminOrderService.OrderExport export = service.export(null, null, null, null, null);

        assertThat(export.truncated()).isFalse();
        assertThat(export.rowCount()).isEqualTo(1);
        assertThat(export.csv()).startsWith("\uFEFF"
                + "order_no,customer_name,customer_email,country,item_count,total_amount,currency,payment_method,status,created_at\n");
        // CSV 转义：客户名含逗号 → 双引号包裹
        assertThat(export.csv()).contains(
                "DRM-20260610-0001,\"Alice, Smith\",alice@example.com,US,3,237.00,USD,Stripe,2,2026-06-10T12:00\n");
        verify(audit).record(eq(TradingAuditRecorder.ACTION_ORDER_EXPORT), eq("orders"),
                contains("\"rows\":1"));
    }

    @Test
    @DisplayName("STEP-02：keyset 游标按 id ASC 推进（满批 500 续读，末批 < 500 终止）")
    void exportAdvancesKeysetCursor() {
        List<Order> fullBatch = new ArrayList<>();
        for (long id = 1; id <= AdminOrderService.EXPORT_BATCH_SIZE; id++) {
            fullBatch.add(order(id, "US"));
        }
        when(orderRepository.listByAdminFilterAfterId(any(), any(), any(), any(), any(), any(),
                eq(0L), eq(AdminOrderService.EXPORT_BATCH_SIZE))).thenReturn(fullBatch);
        when(orderRepository.listByAdminFilterAfterId(any(), any(), any(), any(), any(), any(),
                eq((long) AdminOrderService.EXPORT_BATCH_SIZE), eq(AdminOrderService.EXPORT_BATCH_SIZE)))
                .thenReturn(List.of(order(501L, "US")));

        AdminOrderService.OrderExport export = service.export(null, null, null, null, null);

        assertThat(export.rowCount()).isEqualTo(501);
        assertThat(export.truncated()).isFalse();
        verify(orderRepository).listByAdminFilterAfterId(any(), any(), any(), any(), any(), any(),
                eq((long) AdminOrderService.EXPORT_BATCH_SIZE), eq(AdminOrderService.EXPORT_BATCH_SIZE));
    }

    @Test
    @DisplayName("CSV 公式注入中和（L4 security 修复）：顾客可控列以 =/+/-/@/\\t 开头 → 前置 '；金额/系统列不中和")
    void exportNeutralizesFormulaInjectionInCustomerControlledCells() {
        Order order = order(1L, "=HYPERLINK(\"http://evil\")");
        when(orderRepository.listByAdminFilterAfterId(any(), any(), any(), any(), any(), any(),
                anyLong(), anyInt())).thenReturn(List.of(order)).thenReturn(List.of());
        when(refundService.loadUsers(any())).thenReturn(
                Map.of(7L, user(7L, "=SUM(A1:A9)", "+alice@example.com")));
        when(orderLineRepository.sumQtyByOrderIds(any())).thenReturn(Map.of(1L, 3));

        AdminOrderService.OrderExport export = service.export(null, null, null, null, null);

        // customer_name/customer_email/country 三列前置 '（country 含逗号场景已由 csvCell 引号包裹兜底）
        assertThat(export.csv()).contains(
                "DRM-20260610-0001,'=SUM(A1:A9),'+alice@example.com,\"'=HYPERLINK(\"\"http://evil\"\")\","
                        + "3,237.00,USD,Stripe,2,2026-06-10T12:00\n");
        // 金额列（total_amount=237.00）与系统列未被中和：行内不出现 '237.00 / 'USD
        assertThat(export.csv()).doesNotContain("'237.00").doesNotContain("'USD");
        // 直接锁定中和函数行为：- 开头 / tab 开头 / 非触发字符 / null
        assertThat(AdminOrderService.csvCellUntrusted("-2+3")).isEqualTo("'-2+3");
        assertThat(AdminOrderService.csvCellUntrusted("\t=cmd")).isEqualTo("'\t=cmd");
        assertThat(AdminOrderService.csvCellUntrusted("@user")).isEqualTo("'@user");
        assertThat(AdminOrderService.csvCellUntrusted("Alice")).isEqualTo("Alice");
        assertThat(AdminOrderService.csvCellUntrusted(null)).isEmpty();
    }

    @Test
    @DisplayName("STEP-03：行数达 10000 → 截断，truncated=true + CSV 末行 # TRUNCATED AT 10000 ROWS")
    void exportTruncatesAtMaxRows() {
        int total = AdminOrderService.EXPORT_MAX_ROWS + 1;
        when(orderRepository.listByAdminFilterAfterId(any(), any(), any(), any(), any(), any(),
                anyLong(), anyInt())).thenAnswer(invocation -> {
            long lastId = invocation.getArgument(6);
            List<Order> batch = new ArrayList<>();
            for (long id = lastId + 1; id <= Math.min(lastId + AdminOrderService.EXPORT_BATCH_SIZE, total); id++) {
                batch.add(order(id, null));
            }
            return batch;
        });

        AdminOrderService.OrderExport export = service.export(null, null, null, null, null);

        assertThat(export.truncated()).isTrue();
        assertThat(export.rowCount()).isEqualTo(AdminOrderService.EXPORT_MAX_ROWS);
        assertThat(export.csv()).endsWith(AdminOrderService.EXPORT_TRUNCATED_LINE + "\n");
        verify(audit).record(eq(TradingAuditRecorder.ACTION_ORDER_EXPORT), eq("orders"),
                contains("\"truncated\":true"));
    }

    @Test
    @DisplayName("STEP-04：空结果导出仅表头，审计 detail 含筛选条件与 rows=0")
    void exportEmptyResultStillAudits() {
        when(orderRepository.listByAdminFilterAfterId(any(), any(), any(), any(), any(), any(),
                anyLong(), anyInt())).thenReturn(List.of());
        when(refundService.findUserIdsByNameOrEmailLike("alice")).thenReturn(List.of());

        AdminOrderService.OrderExport export = service.export(2, "alice", "USD", null, null);

        assertThat(export.rowCount()).isZero();
        assertThat(export.truncated()).isFalse();
        assertThat(export.csv()).isEqualTo("\uFEFF"
                + "order_no,customer_name,customer_email,country,item_count,total_amount,currency,payment_method,status,created_at\n");
        verify(audit).record(eq(TradingAuditRecorder.ACTION_ORDER_EXPORT), eq("orders"),
                contains("\"status\":\"2\",\"search\":\"alice\",\"currency\":\"USD\""));
    }

    // ==================== order-flow-complete B/J ====================

    private void stubDetailDeps(Order returned) {
        lenient().when(orderRepository.findById(returned.getId())).thenReturn(returned);
        lenient().when(orderLineRepository.listByOrderId(returned.getId())).thenReturn(List.of());
        lenient().when(refundRepository.listByOrderId(returned.getId())).thenReturn(List.of());
        lenient().when(paymentRepository.findByOrderId(returned.getId())).thenReturn(null);
        lenient().when(orderEventRecorder.listAdmin(returned.getId())).thenReturn(List.of());
        com.dreamy.domain.checkout.entity.CheckoutConfig config = new com.dreamy.domain.checkout.entity.CheckoutConfig();
        config.setCustomRefundGraceHours(24);
        lenient().when(checkoutConfigRepository.getSingleton()).thenReturn(config);
    }

    private Order withStatus(Order o, OrderStatus status) {
        o.setStatus(status);
        return o;
    }

    @Test
    @DisplayName("PATCH status=8：SHIPPED→DELIVERED（delivered_at）+ 事件(ADMIN) + order.delivered")
    void patchStatusDelivered() {
        Order shipped = withStatus(order(1L, "US"), OrderStatus.SHIPPED);
        Order delivered = withStatus(order(1L, "US"), OrderStatus.DELIVERED);
        when(orderRepository.findById(1L)).thenReturn(shipped, delivered);
        stubDetailDeps(delivered);
        when(orderRepository.findById(1L)).thenReturn(shipped, delivered);
        when(orderRepository.casUpdateStatus(eq(1L), eq(OrderStatus.SHIPPED), eq(OrderStatus.DELIVERED), any()))
                .thenReturn(1);

        var detail = service.patchStatus(1L, 8);

        assertThat(detail.status()).isEqualTo(8);
        verify(orderEventRecorder).statusChanged(eq(1L), eq(OrderStatus.SHIPPED), eq(OrderStatus.DELIVERED),
                eq(OrderActorType.ADMIN), any(), any(), eq(true));
        verify(eventsPublisher).publishOrderDelivered(any(Order.class), eq("en"));
        verify(audit).record(eq(TradingAuditRecorder.ACTION_ORDER_STATUS), eq(shipped.getOrderNo()),
                contains("delivered"));
    }

    @Test
    @DisplayName("PATCH status=4：DELIVERED→COMPLETED（不再写 delivered_at）；SHIPPED→COMPLETED（同写 delivered_at）；事件 ADMIN")
    void patchStatusCompleted() {
        Order delivered = withStatus(order(1L, "US"), OrderStatus.DELIVERED);
        Order completed = withStatus(order(1L, "US"), OrderStatus.COMPLETED);
        stubDetailDeps(completed);
        when(orderRepository.findById(1L)).thenReturn(delivered, completed);
        when(orderRepository.casUpdateStatus(eq(1L), eq(OrderStatus.DELIVERED), eq(OrderStatus.COMPLETED), any()))
                .thenReturn(1);
        service.patchStatus(1L, 4);
        verify(orderEventRecorder).statusChanged(eq(1L), eq(OrderStatus.DELIVERED), eq(OrderStatus.COMPLETED),
                eq(OrderActorType.ADMIN), any(), any(), eq(true));

        Order shipped = withStatus(order(1L, "US"), OrderStatus.SHIPPED);
        when(orderRepository.findById(1L)).thenReturn(shipped, completed);
        when(orderRepository.casUpdateStatus(eq(1L), eq(OrderStatus.SHIPPED), eq(OrderStatus.COMPLETED), any()))
                .thenReturn(1);
        service.patchStatus(1L, 4);
        verify(orderEventRecorder).statusChanged(eq(1L), eq(OrderStatus.SHIPPED), eq(OrderStatus.COMPLETED),
                eq(OrderActorType.ADMIN), any(), any(), eq(true));
    }

    @Test
    @DisplayName("PATCH status=5：PENDING → OrderCancelService(admin, operatorId)；PAID → RefundService.adminCancelPaidOrder；SHIPPED → 409602；status=2/3 → 422601")
    void patchStatusCancelled() {
        Order pending = withStatus(order(1L, "US"), OrderStatus.PENDING);
        Order cancelled = withStatus(order(1L, "US"), OrderStatus.CANCELLED);
        stubDetailDeps(cancelled);
        when(orderRepository.findById(1L)).thenReturn(pending, cancelled);
        when(orderCancelService.cancelPending(eq(pending), eq(TradingEventsPublisher.CANCEL_REASON_ADMIN), any()))
                .thenReturn(true);
        service.patchStatus(1L, 5);
        verify(orderCancelService).cancelPending(eq(pending), eq("admin"), any());
        verify(refundService, never()).adminCancelPaidOrder(any());

        Order paid = withStatus(order(1L, "US"), OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(paid, cancelled);
        service.patchStatus(1L, 5);
        verify(refundService).adminCancelPaidOrder(paid);

        Order shipped = withStatus(order(1L, "US"), OrderStatus.SHIPPED);
        when(orderRepository.findById(1L)).thenReturn(shipped);
        assertThatThrownBy(() -> service.patchStatus(1L, 5))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.ORDER_STATE_INVALID));
        assertThatThrownBy(() -> service.patchStatus(1L, 2))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.FIELD_VALIDATION_FAILED));
        assertThatThrownBy(() -> service.patchStatus(1L, 3))
                .isInstanceOf(TradingException.class);
    }

    @Test
    @DisplayName("production-stage：仅 PAID；1→2 递进发 order.production + PRODUCTION 事件；3→2 回退一档允许；1→3 跳级 409602；非 PAID 409602；非法枚举 422601")
    void patchProductionStage() {
        Order paid = withStatus(order(1L, "US"), OrderStatus.PAID);
        paid.setProductionStage(ProductionStage.PENDING_REVIEW);
        Order after = withStatus(order(1L, "US"), OrderStatus.PAID);
        after.setProductionStage(ProductionStage.IN_PRODUCTION);
        stubDetailDeps(after);
        when(orderRepository.findById(1L)).thenReturn(paid, after);
        when(orderRepository.casUpdateProductionStage(1L, ProductionStage.PENDING_REVIEW, ProductionStage.IN_PRODUCTION))
                .thenReturn(1);

        var detail = service.patchProductionStage(1L, 2);

        assertThat(detail.productionStage()).isEqualTo(2);
        verify(orderEventRecorder).productionStageChanged(eq(1L), eq(ProductionStage.PENDING_REVIEW),
                eq(ProductionStage.IN_PRODUCTION), eq(OrderActorType.ADMIN), any(), isNull());
        verify(eventsPublisher).publishOrderProduction(any(Order.class), eq(ProductionStage.IN_PRODUCTION), eq("en"));

        // 回退一档 3→2：允许，不发 order.production（delta<0）
        Order qc = withStatus(order(1L, "US"), OrderStatus.PAID);
        qc.setProductionStage(ProductionStage.QUALITY_CHECK);
        when(orderRepository.findById(1L)).thenReturn(qc, after);
        when(orderRepository.casUpdateProductionStage(1L, ProductionStage.QUALITY_CHECK, ProductionStage.IN_PRODUCTION))
                .thenReturn(1);
        service.patchProductionStage(1L, 2);
        verify(orderEventRecorder).productionStageChanged(eq(1L), eq(ProductionStage.QUALITY_CHECK),
                eq(ProductionStage.IN_PRODUCTION), eq(OrderActorType.ADMIN), any(), eq("rolled back one stage"));
        verify(eventsPublisher, org.mockito.Mockito.times(1)).publishOrderProduction(any(), any(), any());

        // 跳级 1→3
        when(orderRepository.findById(1L)).thenReturn(paid);
        assertThatThrownBy(() -> service.patchProductionStage(1L, 3))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.ORDER_STATE_INVALID));
        // 非 PAID
        when(orderRepository.findById(1L)).thenReturn(withStatus(order(1L, "US"), OrderStatus.SHIPPED));
        assertThatThrownBy(() -> service.patchProductionStage(1L, 2))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.ORDER_STATE_INVALID));
        // 非法枚举
        assertThatThrownBy(() -> service.patchProductionStage(1L, 9))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.FIELD_VALIDATION_FAILED));
    }

    @Test
    @DisplayName("ship（兼容别名）：委托 ShipmentService.create(lines=null 全部未发行；carrier 可为 name)，返回最新详情")
    void shipDelegatesToShipmentService() {
        Order paid = withStatus(order(1L, "US"), OrderStatus.PAID);
        Order shipped = withStatus(order(1L, "US"), OrderStatus.SHIPPED);
        stubDetailDeps(shipped);
        when(orderRepository.findById(1L)).thenReturn(paid, shipped);

        var detail = service.ship(1L, "DHL Express", "DHL123");

        verify(shipmentService).create(eq(1L), argThat(req -> "DHL Express".equals(req.carrierCode())
                && "DHL123".equals(req.trackingNo()) && req.lines() == null), isNull());
        assertThat(detail.status()).isEqualTo(3);
        // 旧契约字段名映射：carrier_code 校验失败 → 422601 fields.carrier
        when(shipmentService.create(anyLong(), any(), isNull()))
                .thenThrow(TradingException.fieldValidation("carrier_code", "invalid_enum"));
        assertThatThrownBy(() -> service.ship(1L, "Nope", "X1"))
                .isInstanceOfSatisfying(TradingException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.FIELD_VALIDATION_FAILED);
                    assertThat(((Map<?, ?>) ex.getDetails().get("fields")).containsKey("carrier")).isTrue();
                });
        // tracking_no 必填
        assertThatThrownBy(() -> service.ship(1L, "DHL Express", " "))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.FIELD_VALIDATION_FAILED));
    }

    @Test
    @DisplayName("notes：content 必填 ≤512；customer_visible 缺省 false → 'Internal note'；返回 OrderEventDto")
    void addNote() {
        Order paid = withStatus(order(1L, "US"), OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(paid);
        com.dreamy.domain.order.entity.OrderEvent saved = new com.dreamy.domain.order.entity.OrderEvent();
        saved.setId(77L);
        saved.setType(OrderEventType.NOTE);
        saved.setActorType(OrderActorType.ADMIN);
        saved.setTitle("Internal note");
        saved.setDetail("call customer");
        saved.setCustomerVisible(false);
        when(orderEventRecorder.record(eq(1L), eq(OrderEventType.NOTE), eq(OrderActorType.ADMIN), any(),
                eq("Internal note"), eq("call customer"), isNull(), eq(false))).thenReturn(saved);
        when(orderEventRecorder.toDtos(eq(List.of(saved)), eq(true))).thenReturn(List.of(
                new com.dreamy.dto.TradingDtos.OrderEventDto(77L, 2, 3, null, "Ops", "Internal note",
                        "call customer", null, false, null)));

        var dto = service.addNote(1L, "call customer", null);

        assertThat(dto.id()).isEqualTo(77L);
        assertThat(dto.customerVisible()).isFalse();
        assertThatThrownBy(() -> service.addNote(1L, "  ", true))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.FIELD_VALIDATION_FAILED));
    }

    @Test
    @DisplayName("列表：production_stage/wedding_before 筛选透传；wedding_days_left 派生；production_stage 非法 → 422601")
    void listWithNewFilters() {
        Order o = withStatus(order(1L, "US"), OrderStatus.PAID);
        o.setProductionStage(ProductionStage.QUALITY_CHECK);
        o.setWeddingDate(LocalDate.now().plusDays(10));
        when(orderRepository.pageByAdminFilter(any(), any(), any(), any(), any(), any(),
                eq(ProductionStage.QUALITY_CHECK), eq(LocalDate.of(2026, 12, 31)), anyInt(), anyInt()))
                .thenReturn(pageOf(List.of(o)));

        var result = service.list(1, 20, null, null, null, null, null, 3, LocalDate.of(2026, 12, 31));

        AdminOrderListItem item = result.getData().get(0);
        assertThat(item.productionStage()).isEqualTo(3);
        assertThat(item.weddingDaysLeft()).isEqualTo(10L);
        assertThatThrownBy(() -> service.list(1, 20, null, null, null, null, null, 9, null))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.FIELD_VALIDATION_FAILED));
    }

    @Test
    @DisplayName("详情：events 全量（listAdmin）+ 新字段 + shipments 空 + wedding_days_left/locale_snapshot")
    void detailAssemblyNewFields() {
        Order o = withStatus(order(1L, "US"), OrderStatus.DELIVERED);
        o.setProductionStage(null);
        o.setDeliveredAt(LocalDateTime.of(2026, 9, 7, 9, 0));
        o.setRefundedAmount(new BigDecimal("37.00"));
        o.setAmountVersion(2);
        o.setLocaleSnapshot("es");
        stubDetailDeps(o);
        when(orderEventRecorder.listAdmin(1L)).thenReturn(List.of(
                new com.dreamy.dto.TradingDtos.OrderEventDto(1L, 2, 3, 3L, "Ops", "Internal note", null, null,
                        false, null)));

        var detail = service.getDetail(1L);

        assertThat(detail.status()).isEqualTo(8);
        assertThat(detail.deliveredAt()).isEqualTo(LocalDateTime.of(2026, 9, 7, 9, 0));
        assertThat(detail.refundedAmount()).isEqualByComparingTo("37.00");
        assertThat(detail.amountVersion()).isEqualTo(2);
        assertThat(detail.localeSnapshot()).isEqualTo("es");
        assertThat(detail.events()).hasSize(1);
        assertThat(detail.events().get(0).customerVisible()).isFalse();
        assertThat(detail.shipments()).isEmpty();
    }
}
