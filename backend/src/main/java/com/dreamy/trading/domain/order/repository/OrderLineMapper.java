package com.dreamy.trading.domain.order.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.trading.domain.order.entity.OrderLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** OrderLineMapper。表 order_line。 */
@Mapper
public interface OrderLineMapper extends BaseMapper<OrderLine> {

    /**
     * 窗口内已支付销量 SQL 聚合（TradingQueryPort 数据源；orders/order_line 同属 trading 域内 join）。
     * 口径与原两段查询一致：paid_at 落定（≥since 即非空）即计入，后续退款不回溯（EVT-CAT-001/003）。
     * idx_line_product 驱动 + orders 主键 join——替代「全窗口订单 id + 全部订单行加载进内存逐行过滤」
     * 的逐商品重复扫描（性能审查修复）。
     */
    @Select("SELECT COALESCE(SUM(ol.qty), 0) FROM order_line ol "
            + "JOIN orders o ON o.id = ol.order_id "
            + "WHERE ol.product_id = #{productId} AND o.paid_at >= #{since}")
    int sumPaidQtySince(@Param("productId") Long productId, @Param("since") LocalDateTime since);

    /** since 起有支付销量的商品 id（SalesWindowRefreshJob 候选集；DISTINCT 下推 SQL） */
    @Select("SELECT DISTINCT ol.product_id FROM order_line ol "
            + "JOIN orders o ON o.id = ol.order_id "
            + "WHERE o.paid_at >= #{since}")
    List<Long> listPaidProductIdsSince(@Param("since") LocalDateTime since);
}
