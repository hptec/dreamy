package com.dreamy.trading.domain.order.repository;

import com.dreamy.trading.domain.enums.OrderStatus;
import com.dreamy.trading.domain.order.entity.Order;
import com.dreamy.trading.domain.order.entity.OrderLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 订单行聚合仓储单测（admin-prototype-alignment unit_task_be_trd_001 / unit_task_be_cat_001）。
 * L2 TRACE: RM-TRD-01c（item_count 批量聚合：IN 投影行 + Stream.groupingBy 内存求和；缺失 → 0）；
 * RM-CAT-01b（sales_total 批量聚合：两次独立查询 + 内存过滤已支付后五态，spec §9 禁 raw SQL/JOIN）。
 */
@ExtendWith(MockitoExtension.class)
class OrderLineRepositoryTest {

    @Mock
    OrderLineMapper mapper;

    @Mock
    OrderMapper orderMapper;

    OrderLineRepository repository;

    @BeforeEach
    void setUp() {
        repository = new OrderLineRepository(mapper, orderMapper);
    }

    private static OrderLine line(Long orderId, Long productId, int qty) {
        OrderLine line = new OrderLine();
        line.setOrderId(orderId);
        line.setProductId(productId);
        line.setQty(qty);
        return line;
    }

    private static Order order(Long id, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setStatus(status);
        return order;
    }

    @Test
    @DisplayName("RM-TRD-01c：IN 投影行 → groupingBy + summingInt 内存聚合（同订单多行合并）")
    void sumQtyByOrderIdsMergesAggregateRows() {
        when(mapper.selectList(any())).thenReturn(List.of(
                line(1L, 10L, 1), line(1L, 11L, 2), line(2L, 10L, 5)));

        Map<Long, Integer> result = repository.sumQtyByOrderIds(List.of(1L, 2L));

        assertThat(result).containsEntry(1L, 3).containsEntry(2L, 5).hasSize(2);
    }

    @Test
    @DisplayName("RM-TRD-01c：空入参不触发 SQL，返回空 Map（缺失订单由调用方 getOrDefault → 0）")
    void sumQtyByOrderIdsEmptyInputSkipsSql() {
        assertThat(repository.sumQtyByOrderIds(List.of())).isEmpty();
        assertThat(repository.sumQtyByOrderIds(null)).isEmpty();
        verifyNoInteractions(mapper, orderMapper);
    }

    @Test
    @DisplayName("RM-CAT-01b：两次独立查询 + 内存过滤已支付后五态（cancelled/pending 不计）后按商品求和")
    void sumSalesTotalByProductIdsFiltersPaidLikeStatusesInMemory() {
        when(mapper.selectList(any())).thenReturn(List.of(
                line(1L, 10L, 2),   // paid → 计入
                line(2L, 10L, 3),   // cancelled → 不计
                line(3L, 10L, 4),   // refunded → 计入（退款不回溯口径）
                line(4L, 20L, 7))); // pending → 不计
        when(orderMapper.selectList(any())).thenReturn(List.of(
                order(1L, OrderStatus.PAID),
                order(2L, OrderStatus.CANCELLED),
                order(3L, OrderStatus.REFUNDED),
                order(4L, OrderStatus.PENDING)));

        Map<Long, Integer> result = repository.sumSalesTotalByProductIds(List.of(10L, 20L));

        assertThat(result).containsEntry(10L, 6).hasSize(1);
        assertThat(result).doesNotContainKey(20L);
    }

    @Test
    @DisplayName("RM-CAT-01b：空入参/无订单行均不触发 orders 查询，返回空 Map")
    void sumSalesTotalByProductIdsEmptyInputSkipsSql() {
        assertThat(repository.sumSalesTotalByProductIds(List.of())).isEmpty();
        assertThat(repository.sumSalesTotalByProductIds(null)).isEmpty();
        verifyNoInteractions(mapper, orderMapper);

        when(mapper.selectList(any())).thenReturn(List.of());
        assertThat(repository.sumSalesTotalByProductIds(List.of(10L))).isEmpty();
        verifyNoInteractions(orderMapper);
    }
}
