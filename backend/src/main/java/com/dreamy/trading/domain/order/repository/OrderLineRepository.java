package com.dreamy.trading.domain.order.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.trading.domain.enums.OrderStatus;
import com.dreamy.trading.domain.order.entity.Order;
import com.dreamy.trading.domain.order.entity.OrderLine;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 订单行仓储（RM-TRD-030~034）。
 * L2 TRACE: trading-data-detail §1 OrderLineRepository / IDX-TRD-013。
 */
@Repository
public class OrderLineRepository {

    /** 销量计入口径（RM-CAT-01）：已支付后五态；cancelled/pending 不计 */
    private static final Set<OrderStatus> SALES_COUNTED_STATUSES = Set.of(
            OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.COMPLETED,
            OrderStatus.REFUNDING, OrderStatus.REFUNDED);

    private final OrderLineMapper mapper;
    private final OrderMapper orderMapper;

    public OrderLineRepository(OrderLineMapper mapper, OrderMapper orderMapper) {
        this.mapper = mapper;
        this.orderMapper = orderMapper;
    }

    /** RM-TRD-030 batchInsert */
    public void batchInsert(List<OrderLine> lines) {
        if (lines == null) {
            return;
        }
        for (OrderLine line : lines) {
            mapper.insert(line);
        }
    }

    /** RM-TRD-031 listByOrderId */
    public List<OrderLine> listByOrderId(Long orderId) {
        return mapper.selectList(new LambdaQueryWrapper<OrderLine>()
                .eq(OrderLine::getOrderId, orderId)
                .orderByAsc(OrderLine::getId));
    }

    /** 批查（RM-TRD-032 聚合数据源 + TradingQueryPort 销量聚合） */
    public List<OrderLine> listByOrderIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<OrderLine>()
                .in(OrderLine::getOrderId, orderIds)
                .orderByAsc(OrderLine::getId));
    }

    /** RM-TRD-032 列表派生字段一次聚合（line_count + first_line_img 首行快照图，NP 防 N+1） */
    public Map<Long, LineAggregate> aggregateByOrderIds(Collection<Long> orderIds) {
        Map<Long, LineAggregate> result = new HashMap<>();
        for (OrderLine line : listByOrderIds(orderIds)) {
            LineAggregate existing = result.get(line.getOrderId());
            if (existing == null) {
                result.put(line.getOrderId(), new LineAggregate(1, line.getImg()));
            } else {
                result.put(line.getOrderId(), new LineAggregate(existing.lineCount() + 1,
                        existing.firstLineImg() != null ? existing.firstLineImg() : line.getImg()));
            }
        }
        return result;
    }

    /**
     * RM-TRD-01c：后台订单列表/导出 item_count 批量聚合。LambdaQueryWrapper 按 order_id IN 取
     * (order_id, qty) 投影行 + Stream.groupingBy 内存求和（spec §9.1/检查清单 144：禁 Mapper raw SQL
     * GROUP BY，IN<1000 内存聚合；批量场景上限 ids≤200、导出每批 500，单批不超）。
     * 缺失订单由调用方 getOrDefault → 0 兜底。IDX-TRD-01：order_id 命中既有 idx_line_order 索引。
     */
    public Map<Long, Integer> sumQtyByOrderIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return new HashMap<>();
        }
        List<OrderLine> lines = mapper.selectList(new LambdaQueryWrapper<OrderLine>()
                .select(OrderLine::getOrderId, OrderLine::getQty)
                .in(OrderLine::getOrderId, orderIds));
        return lines.stream().collect(Collectors.groupingBy(OrderLine::getOrderId,
                Collectors.summingInt(OrderLine::getQty)));
    }

    /** RM-TRD-033 定制行存在判定（决策 24 投产判定） */
    public boolean existsCustomLine(Long orderId) {
        return mapper.selectCount(new LambdaQueryWrapper<OrderLine>()
                .eq(OrderLine::getOrderId, orderId)
                .isNotNull(OrderLine::getCustomSizeData)) > 0;
    }

    /** RM-TRD-034 现货行（库存扣减/回补对象） */
    public List<OrderLine> listSpotLines(Long orderId) {
        return mapper.selectList(new LambdaQueryWrapper<OrderLine>()
                .eq(OrderLine::getOrderId, orderId)
                .isNotNull(OrderLine::getSkuId));
    }

    /** review TradingPurchaseQueryPort 数据源：订单集内是否含商品 */
    public boolean existsProductInOrders(Collection<Long> orderIds, Long productId) {
        if (orderIds == null || orderIds.isEmpty()) {
            return false;
        }
        return mapper.selectCount(new LambdaQueryWrapper<OrderLine>()
                .in(OrderLine::getOrderId, orderIds)
                .eq(OrderLine::getProductId, productId)) > 0;
    }

    /** catalog TradingQueryPort 数据源：窗口内已支付销量 SQL 聚合（idx_line_product 驱动，防逐商品全窗口扫描） */
    public int sumPaidQtySince(Long productId, LocalDateTime since) {
        return mapper.sumPaidQtySince(productId, since);
    }

    /** catalog TradingQueryPort 数据源：since 起有支付销量的商品 id（SalesWindowRefreshJob 候选集） */
    public List<Long> listPaidProductIdsSince(LocalDateTime since) {
        return mapper.listPaidProductIdsSince(since);
    }

    /**
     * catalog TradingQueryPort 数据源：商品累计销量批量聚合（admin-prototype-alignment RM-CAT-01b；
     * idx_line_product 驱动——IDX-CAT-01 既有索引核验通过，无需 DDL）。
     * 实现（spec §9.1/§9.2：禁 @Select 原生 SQL、禁 JOIN——两次独立查询 + 内存聚合）：
     * (1) order_line 按 product_id IN 取 (product_id, order_id, qty) 投影行；
     * (2) orders 按收集到的 order_id IN 取 (id, status)；
     * (3) 内存过滤 status IN paid/shipped/completed/refunding/refunded（RM-CAT-01：cancelled/pending
     * 不计，口径与原 SQL 聚合完全一致）后 groupingBy(product_id) 求和。
     */
    public Map<Long, Integer> sumSalesTotalByProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return new HashMap<>();
        }
        List<OrderLine> lines = mapper.selectList(new LambdaQueryWrapper<OrderLine>()
                .select(OrderLine::getProductId, OrderLine::getOrderId, OrderLine::getQty)
                .in(OrderLine::getProductId, productIds));
        if (lines.isEmpty()) {
            return new HashMap<>();
        }
        Set<Long> orderIds = lines.stream().map(OrderLine::getOrderId).collect(Collectors.toSet());
        Set<Long> countedOrderIds = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                        .select(Order::getId, Order::getStatus)
                        .in(Order::getId, orderIds)).stream()
                .filter(order -> SALES_COUNTED_STATUSES.contains(order.getStatus()))
                .map(Order::getId)
                .collect(Collectors.toSet());
        return lines.stream()
                .filter(line -> countedOrderIds.contains(line.getOrderId()))
                .collect(Collectors.groupingBy(OrderLine::getProductId,
                        Collectors.summingInt(OrderLine::getQty)));
    }

    /** 列表聚合载体（RM-TRD-032：行数 + 首行快照图） */
    public record LineAggregate(int lineCount, String firstLineImg) {
    }
}
