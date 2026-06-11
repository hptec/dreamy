package com.dreamy.trading.port;

import com.dreamy.catalog.port.TradingQueryPort;
import com.dreamy.trading.domain.order.repository.OrderLineRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * catalog TradingQueryPort 真实现（EVT-CAT-001/003 销量重算数据源；提供 @Bean 后
 * CatalogPortConfig stub @ConditionalOnMissingBean 自动让位）。
 * 口径：paid_at 落定（≥since）即计入已支付销量（订单后续退款不回溯销量窗口，与 order.paid 事件口径一致）。
 * 实现（性能审查修复）：orders/order_line 同属 trading 域，SQL 聚合域内 join 下推
 * （idx_line_product + orders 主键驱动），替代「全窗口订单 id + 全部订单行加载进内存逐行过滤」
 * 的逐商品重复扫描（原实现每次调用 O(窗口订单数×行数)，消费者按商品循环时退化为 N×全窗口加载）。
 */
@Service
public class TradingQueryPortImpl implements TradingQueryPort {

    private final OrderLineRepository orderLineRepository;

    public TradingQueryPortImpl(OrderLineRepository orderLineRepository) {
        this.orderLineRepository = orderLineRepository;
    }

    @Override
    public int sumPaidQty(Long productId, LocalDateTime since) {
        if (productId == null) {
            return 0;
        }
        return orderLineRepository.sumPaidQtySince(productId, since);
    }

    @Override
    public List<Long> listPaidProductIds(LocalDateTime since) {
        return orderLineRepository.listPaidProductIdsSince(since);
    }

    /**
     * 商品累计销量批量聚合（admin-prototype-alignment RM-CAT-01：order.status ∈ 已支付后五态的
     * order_line.qty 合计；域内 SQL 聚合一次 IN 下推，防 N+1——RM-CAT-01b）。
     */
    @Override
    public Map<Long, Integer> sumSalesTotalByProductIds(Collection<Long> productIds) {
        return orderLineRepository.sumSalesTotalByProductIds(productIds);
    }
}
