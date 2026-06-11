package com.dreamy.trading.port;

import com.dreamy.review.port.TradingPurchaseQueryPort;
import com.dreamy.trading.domain.order.repository.OrderLineRepository;
import com.dreamy.trading.domain.order.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * review TradingPurchaseQueryPort 真实现（403801 越权防护唯一数据源，s-756/s-762；
 * 提供 @Bean 后 ReviewPortConfig fail-closed stub 自动让位）。
 * 语义 = EXISTS(orders o JOIN order_line ol WHERE o.customer_id=? AND o.status='completed'
 * AND ol.product_id=?)（域内两段查询等价实现）。
 */
@Service
public class TradingPurchaseQueryPortImpl implements TradingPurchaseQueryPort {

    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;

    public TradingPurchaseQueryPortImpl(OrderRepository orderRepository,
                                        OrderLineRepository orderLineRepository) {
        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
    }

    @Override
    public boolean hasCompletedOrderContaining(Long customerId, Long productId) {
        if (customerId == null || productId == null) {
            return false;
        }
        List<Long> completedOrderIds = orderRepository.listCompletedOrderIds(customerId);
        return orderLineRepository.existsProductInOrders(completedOrderIds, productId);
    }
}
