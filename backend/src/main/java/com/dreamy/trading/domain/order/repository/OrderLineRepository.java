package com.dreamy.trading.domain.order.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.trading.domain.order.entity.OrderLine;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单行仓储（RM-TRD-030~034）。
 * L2 TRACE: trading-data-detail §1 OrderLineRepository / IDX-TRD-013。
 */
@Repository
public class OrderLineRepository {

    private final OrderLineMapper mapper;

    public OrderLineRepository(OrderLineMapper mapper) {
        this.mapper = mapper;
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

    /** 列表聚合载体（RM-TRD-032：行数 + 首行快照图） */
    public record LineAggregate(int lineCount, String firstLineImg) {
    }
}
