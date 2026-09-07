package com.dreamy.domain.order.service;

import com.dreamy.domain.admin.entity.AdminUser;
import com.dreamy.domain.admin.repository.AdminUserMapper;
import com.dreamy.domain.order.entity.OrderEvent;
import com.dreamy.domain.order.repository.OrderEventRepository;
import com.dreamy.dto.TradingDtos.OrderEventDto;
import com.dreamy.enums.OrderActorType;
import com.dreamy.enums.OrderEventType;
import com.dreamy.enums.OrderStatus;
import com.dreamy.enums.ProductionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 订单时间线记录器（order-flow-complete §4.5 状态转换 → 订单事件矩阵）。
 * 约束：record 必须在触发业务变更的事务内调用（与 CAS 同事务提交/回滚）；本类不开事务。
 * 供 P1-A（支付/状态机/退款/备注/邮件）与 P1-B（shipment 创建/取消/轨迹/签收聚合）共同调用。
 */
@Service
public class OrderEventRecorder {

    private static final Logger log = LoggerFactory.getLogger(OrderEventRecorder.class);

    private final OrderEventRepository orderEventRepository;
    private final AdminUserMapper adminUserMapper;

    public OrderEventRecorder(OrderEventRepository orderEventRepository, AdminUserMapper adminUserMapper) {
        this.orderEventRepository = orderEventRepository;
        this.adminUserMapper = adminUserMapper;
    }

    /**
     * 通用落表入口（事务内调用）。
     *
     * @param orderId         订单 id
     * @param type            事件类型（STATUS_CHANGED/NOTE/SHIPMENT/PAYMENT/REFUND/EMAIL/PRODUCTION）
     * @param actor           触发者类型（SYSTEM/CUSTOMER/ADMIN）
     * @param actorId         触发者 id（customer_id / admin_user.id；SYSTEM 传 null）
     * @param title           标题（≤128，超长截断）
     * @param detail          说明（≤512，超长截断，可空）
     * @param payload         结构化附加数据（可空）
     * @param customerVisible 消费端是否可见
     * @return 已落表事件
     */
    public OrderEvent record(Long orderId, OrderEventType type, OrderActorType actor, Long actorId,
                             String title, String detail, Map<String, Object> payload, boolean customerVisible) {
        OrderEvent event = new OrderEvent();
        event.setOrderId(orderId);
        event.setType(type);
        event.setActorType(actor);
        event.setActorId(actorId);
        event.setTitle(truncate(title, 128));
        event.setDetail(truncate(detail, 512));
        event.setPayload(payload == null || payload.isEmpty() ? null : new LinkedHashMap<>(payload));
        event.setCustomerVisible(customerVisible);
        orderEventRepository.insert(event);
        return event;
    }

    /** 主状态转换事件（STATUS_CHANGED；payload {from,to}） */
    public OrderEvent statusChanged(Long orderId, OrderStatus from, OrderStatus to, OrderActorType actor,
                                    Long actorId, String detail, boolean customerVisible) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", from == null ? null : from.getKey());
        payload.put("to", to.getKey());
        return record(orderId, OrderEventType.STATUS_CHANGED, actor, actorId,
                "Order " + to.name().toLowerCase(), detail, payload, customerVisible);
    }

    /** 制作阶段变更事件（PRODUCTION；payload {from_stage,to_stage}） */
    public OrderEvent productionStageChanged(Long orderId, ProductionStage from, ProductionStage to,
                                             OrderActorType actor, Long actorId, String detail) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from_stage", from == null ? null : from.getKey());
        payload.put("to_stage", to == null ? null : to.getKey());
        String title = to == null ? "Production finished" : "Production: " + to.name().toLowerCase();
        return record(orderId, OrderEventType.PRODUCTION, actor, actorId, title, detail, payload, true);
    }

    /** 时间线读取（后台全量） */
    public List<OrderEventDto> listAdmin(Long orderId) {
        return toDtos(orderEventRepository.listByOrderId(orderId), true);
    }

    /** 时间线读取（消费端仅 customer_visible；不暴露后台操作者姓名） */
    public List<OrderEventDto> listCustomerVisible(Long orderId) {
        return toDtos(orderEventRepository.listCustomerVisibleByOrderId(orderId), false);
    }

    /** DTO 装配（ADMIN 触发者姓名批量解析，防 N+1；消费端视图 actor_name 一律 null） */
    public List<OrderEventDto> toDtos(List<OrderEvent> events, boolean resolveAdminNames) {
        Map<Long, String> adminNames = resolveAdminNames ? loadAdminNames(events) : Map.of();
        List<OrderEventDto> result = new ArrayList<>(events.size());
        for (OrderEvent e : events) {
            String actorName = null;
            if (resolveAdminNames && e.getActorType() == OrderActorType.ADMIN && e.getActorId() != null) {
                actorName = adminNames.get(e.getActorId());
            }
            result.add(new OrderEventDto(e.getId(), e.getType() == null ? null : e.getType().getKey(),
                    e.getActorType() == null ? null : e.getActorType().getKey(), e.getActorId(), actorName,
                    e.getTitle(), e.getDetail(), e.getPayload(), e.getCustomerVisible(), e.getCreatedAt()));
        }
        return result;
    }

    private Map<Long, String> loadAdminNames(List<OrderEvent> events) {
        List<Long> ids = events.stream()
                .filter(e -> e.getActorType() == OrderActorType.ADMIN)
                .map(OrderEvent::getActorId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        try {
            for (AdminUser admin : adminUserMapper.selectByIds(ids)) {
                names.put(admin.getId(), admin.getName());
            }
        } catch (Exception ex) {
            log.warn("[ORDER-EVENT] admin name resolve failed ids={}", ids, ex);
        }
        return names;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
