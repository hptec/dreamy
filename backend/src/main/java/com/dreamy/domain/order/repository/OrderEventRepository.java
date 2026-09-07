package com.dreamy.domain.order.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.order.entity.OrderEvent;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 订单时间线仓储（order-flow-complete §2.2 order_event；idx (order_id, created_at)）。
 */
@Repository
public class OrderEventRepository {

    private final OrderEventMapper mapper;

    public OrderEventRepository(OrderEventMapper mapper) {
        this.mapper = mapper;
    }

    /** 事务内落表（调用方保证与业务变更同事务） */
    public void insert(OrderEvent event) {
        mapper.insert(event);
    }

    /** 全量时间线（后台，created_at ASC / id ASC） */
    public List<OrderEvent> listByOrderId(Long orderId) {
        return mapper.selectList(new LambdaQueryWrapper<OrderEvent>()
                .eq(OrderEvent::getOrderId, orderId)
                .orderByAsc(OrderEvent::getCreatedAt)
                .orderByAsc(OrderEvent::getId));
    }

    /** 顾客可见时间线（消费端 / 游客查单） */
    public List<OrderEvent> listCustomerVisibleByOrderId(Long orderId) {
        return mapper.selectList(new LambdaQueryWrapper<OrderEvent>()
                .eq(OrderEvent::getOrderId, orderId)
                .eq(OrderEvent::getCustomerVisible, Boolean.TRUE)
                .orderByAsc(OrderEvent::getCreatedAt)
                .orderByAsc(OrderEvent::getId));
    }

    /** 批量联取（防 N+1） */
    public List<OrderEvent> listByOrderIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<OrderEvent>()
                .in(OrderEvent::getOrderId, orderIds)
                .orderByAsc(OrderEvent::getCreatedAt)
                .orderByAsc(OrderEvent::getId));
    }
}
