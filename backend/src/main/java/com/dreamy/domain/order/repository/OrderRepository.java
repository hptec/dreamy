package com.dreamy.domain.order.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.enums.OrderStatus;
import com.dreamy.domain.order.entity.Order;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * 订单仓储（RM-TRD-020~028）。状态机推进统一经 casUpdateStatus（条件更新，guard 失败 affected=0 → 409602）。
 * L2 TRACE: trading-data-detail §1 OrderRepository / IDX-TRD-001~005。
 */
@Repository
public class OrderRepository {

    private final OrderMapper mapper;

    public OrderRepository(OrderMapper mapper) {
        this.mapper = mapper;
    }

    /** RM-TRD-020 幂等预检（uk_order_idem） */
    public Order findByIdempotencyKey(String key) {
        return mapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getIdempotencyKey, key));
    }

    /** RM-TRD-021 insert（uk 冲突向上抛 DuplicateKeyException，调用方分流 409603/订单号重试） */
    public void insert(Order order) {
        mapper.insert(order);
    }

    /** RM-TRD-022 隔离点查（404601 防探测） */
    public Order findByIdAndCustomerId(Long id, Long customerId) {
        return mapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getId, id)
                .eq(Order::getCustomerId, customerId));
    }

    /** RM-TRD-023 admin 点查 */
    public Order findById(Long id) {
        return mapper.selectById(id);
    }

    /** 批查（admin refunds 派生 order_no） */
    public List<Order> listByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<Order>().in(Order::getId, ids));
    }

    /** order_no LIKE → ids（admin refunds 搜索辅助，避免跨表 join） */
    public List<Long> findIdsByOrderNoLike(String keyword) {
        return mapper.selectList(new LambdaQueryWrapper<Order>()
                        .like(Order::getOrderNo, keyword)
                        .select(Order::getId))
                .stream().map(Order::getId).toList();
    }

    /** RM-TRD-024 我的订单分页（idx_order_customer_created，created_at DESC） */
    public Page<Order> pageByCustomer(Long customerId, OrderStatus status, int page, int pageSize) {
        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<Order>()
                .eq(Order::getCustomerId, customerId)
                .orderByDesc(Order::getCreatedAt)
                .orderByDesc(Order::getId);
        if (status != null) {
            qw.eq(Order::getStatus, status);
        }
        return mapper.selectPage(new Page<>(page, pageSize), qw);
    }

    /** RM-TRD-025 后台分页（status/currency/时间窗/订单号 LIKE 或 customer_ids IN——客户名/邮箱命中由 identity 先解析） */
    public Page<Order> pageByAdminFilter(OrderStatus status, String currency, LocalDateTime from, LocalDateTime to,
                                         String orderNoLike, List<Long> customerIds, int page, int pageSize) {
        LambdaQueryWrapper<Order> qw = adminFilter(status, currency, from, to, orderNoLike, customerIds)
                .orderByDesc(Order::getCreatedAt)
                .orderByDesc(Order::getId);
        return mapper.selectPage(new Page<>(page, pageSize), qw);
    }

    /**
     * RM-TRD-01a/STEP-02：导出 keyset 游标批读（id ASC，id > lastId LIMIT batch；
     * 筛选条件与 pageByAdminFilter 完全同口径——API-TRD-02 STEP-01）。
     */
    public List<Order> listByAdminFilterAfterId(OrderStatus status, String currency, LocalDateTime from,
                                                LocalDateTime to, String orderNoLike, List<Long> customerIds,
                                                Long lastId, int limit) {
        return mapper.selectList(adminFilter(status, currency, from, to, orderNoLike, customerIds)
                .gt(Order::getId, lastId)
                .orderByAsc(Order::getId)
                .last("LIMIT " + limit));
    }

    /** 后台筛选条件装配（RM-TRD-025 与导出 keyset 共用，不含排序） */
    private LambdaQueryWrapper<Order> adminFilter(OrderStatus status, String currency, LocalDateTime from,
                                                  LocalDateTime to, String orderNoLike, List<Long> customerIds) {
        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<>();
        if (status != null) {
            qw.eq(Order::getStatus, status);
        }
        if (currency != null) {
            qw.eq(Order::getCurrency, currency);
        }
        if (from != null) {
            qw.ge(Order::getCreatedAt, from);
        }
        if (to != null) {
            qw.le(Order::getCreatedAt, to);
        }
        if (orderNoLike != null) {
            boolean hasCustomers = customerIds != null && !customerIds.isEmpty();
            qw.and(w -> {
                w.like(Order::getOrderNo, orderNoLike);
                if (hasCustomers) {
                    w.or().in(Order::getCustomerId, customerIds);
                }
            });
        }
        return qw;
    }

    /**
     * RM-TRD-026 状态机条件更新统一入口（guard 失败 affected=0 → 409602）。
     * setExtra 注入附加 SET 列（paid_at/shipped_at/completed_at/carrier/tracking_no 等）。
     */
    public int casUpdateStatus(Long id, OrderStatus from, OrderStatus to,
                               Consumer<LambdaUpdateWrapper<Order>> setExtra) {
        LambdaUpdateWrapper<Order> uw = new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, id)
                .eq(Order::getStatus, from)
                .set(Order::getStatus, to);
        if (setExtra != null) {
            setExtra.accept(uw);
        }
        return mapper.update(null, uw);
    }

    /** refunding→paid|shipped 还原（TX-TRD-009c：按 shipped_at 判定还原态） */
    public int casRestoreFromRefunding(Long id, OrderStatus restoreTo) {
        return casUpdateStatus(id, OrderStatus.REFUNDING, restoreTo, null);
    }

    /** RM-TRD-027 过期 pending 扫描（idx_order_status_expires，SCHED-TRD-001 分页批量） */
    public List<Order> listExpiredPending(LocalDateTime now, int limit) {
        return mapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.PENDING)
                .lt(Order::getExpiresAt, now)
                .orderByAsc(Order::getId)
                .last("LIMIT " + limit));
    }

    /** review TradingPurchaseQueryPort 数据源：当前用户 completed 订单 id 集合 */
    public List<Long> listCompletedOrderIds(Long customerId) {
        return mapper.selectList(new LambdaQueryWrapper<Order>()
                        .eq(Order::getCustomerId, customerId)
                        .eq(Order::getStatus, OrderStatus.COMPLETED)
                        .select(Order::getId))
                .stream().map(Order::getId).toList();
    }

}
