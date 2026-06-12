package com.dreamy.domain.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.domain.user.entity.User;
import com.dreamy.domain.checkout.repository.CheckoutConfigRepository;
import com.dreamy.enums.OrderStatus;
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

    AdminOrderService service;

    @BeforeEach
    void setUp() {
        service = new AdminOrderService(orderRepository, orderLineRepository, paymentRepository, refundRepository,
                checkoutConfigRepository, orderCancelService, refundService,
                new TradingImmediateTxRunner(), new TradingAfterCommitRunner(), audit, eventsPublisher);
        lenient().when(refundService.loadUsers(any())).thenReturn(Map.of());
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
        when(orderRepository.pageByAdminFilter(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
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
                anyInt(), anyInt())).thenReturn(pageOf(List.of()));

        service.list(1, 20, null, "Alice", null, null, null);

        verify(refundService).findUserIdsByNameOrEmailLike("Alice");
        verify(orderRepository).pageByAdminFilter(any(), any(), any(), any(), eq("Alice"), eq(List.of(7L)),
                anyInt(), anyInt());
    }

    @Test
    @DisplayName("API-TRD-03：search 为空时不触发 identity 解析")
    void listWithoutSearchSkipsCustomerResolution() {
        when(orderRepository.pageByAdminFilter(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
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
}
