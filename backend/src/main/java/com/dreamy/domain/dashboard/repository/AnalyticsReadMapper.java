package com.dreamy.domain.dashboard.repository;

import com.dreamy.domain.dashboard.repository.readmodel.CategorySalesRow;
import com.dreamy.domain.dashboard.repository.readmodel.DailyGmvRow;
import com.dreamy.domain.dashboard.repository.readmodel.TopProductRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * analytics 只读跨域聚合 Mapper（RM-ANA-001~009；决策 3 L1 授权的唯一跨域 SQL 例外）。
 * 边界硬约束：①仅 SELECT（本接口不含任何 DML）；②不依赖他域 Java 实体（映射本域 readmodel 行 DTO）；
 * ③列引用以各域 data-detail DDL 为权威（trading orders/order_line/refund、review review、catalog category/product）；
 * ④声明式只读事务由 AnalyticsAggregator 包裹（TX-ANA-001/002），不持有锁。
 * 注：trading/review 表由各自域 DdlAuto 运行期建表——本 Mapper 仅持 SQL 文本，不依赖编译期实体。
 * 支付口径（DEC-ANA-3）：paid_at 落窗 + status IN ('paid','shipped','completed','refunding','refunded')；
 * 金额口径：USD 基准 = amount / COALESCE(exchange_rate, 1)（决策 14）。
 */
@Mapper
public interface AnalyticsReadMapper {

    String PAID_STATUSES = "(2,3,4,6,7)"; // paid,shipped,completed,refunding,refunded

    /** RM-ANA-001 sumPaidGmvUsd —— 窗口内支付订单 GMV（USD 折算；IDX-ANA-001 idx_order_status_paid） */
    @Select("SELECT COALESCE(SUM(o.total_amount / COALESCE(o.exchange_rate, 1)), 0) "
            + "FROM orders o "
            + "WHERE o.paid_at >= #{from} AND o.paid_at < #{to} "
            + "AND o.status IN " + PAID_STATUSES)
    BigDecimal sumPaidGmvUsd(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** RM-ANA-002 countPaidOrders —— 同口径 COUNT(*) */
    @Select("SELECT COUNT(*) FROM orders o "
            + "WHERE o.paid_at >= #{from} AND o.paid_at < #{to} "
            + "AND o.status IN " + PAID_STATUSES)
    long countPaidOrders(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** RM-ANA-003 countApprovedRefunds —— 窗口内 approved 退款工单数（DEC-ANA-3 refund_rate 分子） */
    @Select("SELECT COUNT(*) FROM refund r "
            + "WHERE r.status = 2 AND r.applied_at >= #{from} AND r.applied_at < #{to}")
    long countApprovedRefunds(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** RM-ANA-004 gmvTrendDaily —— 按日 GROUP BY（缺日补零在 MAP-ANA-002 完成，SQL 不造日历表） */
    @Select("SELECT DATE(o.paid_at) AS day, "
            + "SUM(o.total_amount / COALESCE(o.exchange_rate, 1)) AS gmv_usd "
            + "FROM orders o "
            + "WHERE o.paid_at >= #{from} AND o.paid_at < #{to} "
            + "AND o.status IN " + PAID_STATUSES + " "
            + "GROUP BY DATE(o.paid_at) ORDER BY day ASC")
    List<DailyGmvRow> gmvTrendDaily(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** RM-ANA-005 countPendingRefunds —— 待审核退款工单（idx_refund_order_status 前缀） */
    @Select("SELECT COUNT(*) FROM refund WHERE status = 1")
    long countPendingRefunds();

    /** RM-ANA-006 countPendingReviews —— 待审核评价（review 域 idx_review_status_submitted） */
    @Select("SELECT COUNT(*) FROM review WHERE status = 1")
    long countPendingReviews();

    /** RM-ANA-007 countUnshippedOrders —— 已支付待发货（trading idx_order_status_created 前缀） */
    @Select("SELECT COUNT(*) FROM orders WHERE status = 2")
    long countUnshippedOrders();

    /**
     * RM-ANA-008 categorySales —— 窗口内支付订单 order_line × catalog category 三层溯根聚合（金额折 USD）。
     * LEFT JOIN product/category 链：商品/品类已删除溯根断链行 root 为 NULL → MAP-ANA-003 落 Other 桶。
     */
    @Select("SELECT root.id AS root_category_id, root.name AS root_category_name, "
            + "SUM(ol.unit_price * ol.qty / COALESCE(o.exchange_rate, 1)) AS amount_usd "
            + "FROM order_line ol "
            + "JOIN orders o ON o.id = ol.order_id "
            + "AND o.paid_at >= #{from} AND o.paid_at < #{to} "
            + "AND o.status IN " + PAID_STATUSES + " "
            + "LEFT JOIN product p ON p.id = ol.product_id "
            + "LEFT JOIN category c1 ON c1.id = p.category_id "
            + "LEFT JOIN category c2 ON c2.id = c1.parent_id "
            + "LEFT JOIN category c3 ON c3.id = c2.parent_id "
            + "LEFT JOIN category root ON root.id = COALESCE(c3.id, c2.id, c1.id) "
            + "GROUP BY root.id, root.name ORDER BY amount_usd DESC")
    List<CategorySalesRow> categorySales(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** RM-ANA-009 topProducts —— 窗口内按 SUM(qty) DESC 取前 N（快照列，DEC-ANA-8；idx_line_order JOIN 驱动） */
    @Select("SELECT ol.product_id, "
            + "MAX(ol.product_name) AS product_name, "
            + "MAX(ol.img) AS img, "
            + "SUM(ol.qty) AS sales, "
            + "SUM(ol.unit_price * ol.qty / COALESCE(o.exchange_rate, 1)) AS amount_usd "
            + "FROM order_line ol "
            + "JOIN orders o ON o.id = ol.order_id "
            + "AND o.paid_at >= #{from} AND o.paid_at < #{to} "
            + "AND o.status IN " + PAID_STATUSES + " "
            + "GROUP BY ol.product_id ORDER BY sales DESC, amount_usd DESC LIMIT #{limit}")
    List<TopProductRow> topProducts(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                    @Param("limit") int limit);
}
