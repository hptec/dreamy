package com.dreamy.domain.shipment.service;

import com.dreamy.domain.carrier.entity.Carrier;
import com.dreamy.domain.carrier.service.CarrierResolver;
import com.dreamy.domain.order.entity.Order;
import com.dreamy.domain.order.entity.OrderLine;
import com.dreamy.domain.order.repository.OrderLineRepository;
import com.dreamy.domain.order.repository.OrderRepository;
import com.dreamy.domain.order.service.OrderEventRecorder;
import com.dreamy.domain.shipment.entity.Shipment;
import com.dreamy.domain.shipment.entity.ShipmentEvent;
import com.dreamy.domain.shipment.entity.ShipmentLine;
import com.dreamy.domain.shipment.repository.ShipmentEventRepository;
import com.dreamy.domain.shipment.repository.ShipmentLineRepository;
import com.dreamy.domain.shipment.repository.ShipmentRepository;
import com.dreamy.dto.TradingDtos.ShipmentCreateRequest;
import com.dreamy.dto.TradingDtos.ShipmentDto;
import com.dreamy.dto.TradingDtos.ShipmentEventCreate;
import com.dreamy.dto.TradingDtos.ShipmentLineCreate;
import com.dreamy.dto.TradingDtos.ShipmentPatch;
import com.dreamy.enums.OrderActorType;
import com.dreamy.enums.OrderEventType;
import com.dreamy.enums.OrderStatus;
import com.dreamy.enums.ProductionStage;
import com.dreamy.enums.ShipmentEventSource;
import com.dreamy.enums.ShipmentStatus;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.infra.TradingAfterCommitRunner;
import com.dreamy.infra.TradingAuditRecorder;
import com.dreamy.infra.TradingTxRunner;
import com.dreamy.mq.TradingEventsPublisher;
import com.dreamy.port.TrackingProviderPort;
import com.dreamy.support.TradingFieldErrors;
import com.dreamy.support.TradingParams;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 包裹领域服务（order-flow-complete §2.2 shipment / §4.2 发货→签收 / STATE-3/4）。
 * 所有写操作在 Redisson 锁 trading:order-ship:{orderId} 内（tryLock 3s，失败 409906）：
 * - create：校验 ∑分配 ≤ order_line.qty（422906）→ 插 shipment/shipment_line（uk 重复单号 409908；Idempotency-Key 命中直接返回）
 *   → order_event(SHIPMENT) → 全部行发出 → CAS PAID→SHIPPED + shipped_at + Order.carrier/tracking_no 快照 + production_stage=NULL
 *   + order_event(STATUS_CHANGED) + MQ order.shipped（事务内 outbox）；
 * - addEvent/deliver：状态单向推进（EXCEPTION 可进可恢复）；DELIVERED → 同锁判定全部有效包裹签收 → CAS SHIPPED→DELIVERED
 *   + delivered_at + MQ order.delivered；订单 COMPLETED/REFUNDING 时仅更新包裹；
 * - patch：仅无 PROVIDER 事件且未 DELIVERED/CANCELLED；
 * - cancel：同上条件；status→CANCELLED 释放分配；订单 SHIPPED 且释放后有未发行 → CAS SHIPPED→PAID（production_stage=READY_TO_SHIP）；
 * - sync：provider stub → null（204）；否则拉取 PROVIDER 事件 insertIgnore 去重。
 */
@Service
public class ShipmentService {

    public static final String LOCK_PREFIX = "trading:order-ship:";
    static final long LOCK_WAIT_SECONDS = 3L;
    static final int SHIPMENT_NO_MAX_ATTEMPTS = 3;
    public static final String ACTION_SHIPMENT = "包裹操作";

    private static final Logger log = LoggerFactory.getLogger(ShipmentService.class);

    private final ShipmentRepository shipmentRepository;
    private final ShipmentLineRepository lineRepository;
    private final ShipmentEventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final CarrierResolver carrierResolver;
    private final ShipmentNoGenerator shipmentNoGenerator;
    private final ShipmentQueryService queryService;
    private final OrderEventRecorder orderEventRecorder;
    private final TradingEventsPublisher eventsPublisher;
    private final TradingTxRunner txRunner;
    private final TradingAfterCommitRunner afterCommit;
    private final TradingAuditRecorder audit;
    private final RedissonClient redissonClient;
    private final TrackingProviderPort trackingProvider;

    public ShipmentService(ShipmentRepository shipmentRepository, ShipmentLineRepository lineRepository,
                           ShipmentEventRepository eventRepository, OrderRepository orderRepository,
                           OrderLineRepository orderLineRepository, CarrierResolver carrierResolver,
                           ShipmentNoGenerator shipmentNoGenerator, ShipmentQueryService queryService,
                           OrderEventRecorder orderEventRecorder, TradingEventsPublisher eventsPublisher,
                           TradingTxRunner txRunner, TradingAfterCommitRunner afterCommit, TradingAuditRecorder audit,
                           RedissonClient redissonClient, TrackingProviderPort trackingProvider) {
        this.shipmentRepository = shipmentRepository;
        this.lineRepository = lineRepository;
        this.eventRepository = eventRepository;
        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
        this.carrierResolver = carrierResolver;
        this.shipmentNoGenerator = shipmentNoGenerator;
        this.queryService = queryService;
        this.orderEventRecorder = orderEventRecorder;
        this.eventsPublisher = eventsPublisher;
        this.txRunner = txRunner;
        this.afterCommit = afterCommit;
        this.audit = audit;
        this.redissonClient = redissonClient;
        this.trackingProvider = trackingProvider;
    }

    // ==================== create ====================

    /** POST /api/admin/orders/{id}/shipments（lines 省略 = 全部未发行；Idempotency-Key 可选） */
    public ShipmentDto create(Long orderId, ShipmentCreateRequest req, String idempotencyKey) {
        TradingFieldErrors errors = new TradingFieldErrors();
        if (req == null) {
            errors.reject("_body", "required");
            errors.throwIfAny();
        }
        Carrier carrier = carrierResolver.resolveEnabled(req.carrierCode());
        if (carrier == null) {
            errors.reject("carrier_code", TradingParams.trimToNull(req.carrierCode()) == null ? "required" : "invalid_enum");
        }
        String trackingNo = TradingParams.requireText(req.trackingNo(), 64, "tracking_no", errors);
        if (req.lines() != null) {
            for (ShipmentLineCreate line : req.lines()) {
                if (line == null || line.orderLineId() == null || line.qty() == null || line.qty() < 1) {
                    errors.reject("lines", "invalid");
                    break;
                }
            }
        }
        String idemKey = TradingParams.checkMaxLength(idempotencyKey, 64, "idempotency_key", errors);
        errors.throwIfAny();
        // Idempotency-Key 命中 → 直接返回既有包裹
        if (idemKey != null) {
            Shipment existing = shipmentRepository.findByIdempotencyKey(idemKey);
            if (existing != null) {
                return queryService.get(existing);
            }
        }
        Long operatorId = audit.currentOperatorId();
        return withOrderLock(orderId, () -> {
            Order order = orderRepository.findById(orderId);
            if (order == null) {
                throw new TradingException(TradingErrorCode.ORDER_NOT_FOUND);
            }
            if (order.getStatus() != OrderStatus.PAID) {
                throw TradingException.orderStateInvalid();
            }
            List<OrderLine> orderLines = orderLineRepository.listByOrderId(orderId);
            List<Shipment> active = shipmentRepository.listActiveByOrderId(orderId);
            Map<Long, Integer> allocated = lineRepository.sumQtyByOrderLine(active.stream().map(Shipment::getId).toList());
            Map<Long, Integer> allocations = resolveAllocations(orderLines, allocated, req.lines());
            int totalQty = orderLines.stream().mapToInt(l -> l.getQty() == null ? 0 : l.getQty()).sum();
            int already = allocated.values().stream().mapToInt(Integer::intValue).sum();
            int adding = allocations.values().stream().mapToInt(Integer::intValue).sum();
            boolean allShipped = already + adding >= totalQty;
            LocalDateTime now = LocalDateTime.now();

            Shipment shipment = new Shipment();
            shipment.setOrderId(orderId);
            shipment.setShipmentNo(shipmentNoGenerator.next());
            shipment.setCarrierCode(carrier.getCode());
            shipment.setCarrierName(carrier.getName());
            shipment.setTrackingNo(trackingNo);
            shipment.setTrackingUrl(CarrierResolver.trackingUrl(carrier, trackingNo));
            shipment.setStatus(ShipmentStatus.PENDING);
            shipment.setShippedAt(now);
            shipment.setIdempotencyKey(idemKey);
            shipment.setSyncFailures(0);

            txRunner.inTx(() -> {
                insertWithRetry(shipment);
                List<ShipmentLine> lines = new ArrayList<>();
                for (Map.Entry<Long, Integer> e : allocations.entrySet()) {
                    ShipmentLine line = new ShipmentLine();
                    line.setShipmentId(shipment.getId());
                    line.setOrderLineId(e.getKey());
                    line.setQty(e.getValue());
                    lines.add(line);
                }
                lineRepository.batchInsert(lines);
                Map<String, Object> payload = shipmentPayload(shipment);
                payload.put("partial", !allShipped);
                payload.put("lines", allocations.entrySet().stream()
                        .map(e -> Map.of("order_line_id", e.getKey(), "qty", e.getValue())).toList());
                orderEventRecorder.record(orderId, OrderEventType.SHIPMENT, OrderActorType.ADMIN, operatorId,
                        "Shipment " + shipment.getShipmentNo() + " via " + carrier.getName(), trackingNo, payload, true);
                if (allShipped) {
                    promoteToShipped(order, shipment, now, operatorId);
                }
                audit.record(TradingAuditRecorder.ACTION_ORDER_SHIP, order.getOrderNo(),
                        "{\"shipment_no\":\"" + shipment.getShipmentNo() + "\",\"carrier_code\":\"" + carrier.getCode()
                                + "\",\"tracking_no\":\"" + trackingNo + "\",\"partial\":" + !allShipped + "}");
            });
            afterCommit.run(() -> registerWithProvider(shipment));
            return queryService.get(shipment);
        });
    }

    /** 分配解析：lines 省略 = 每行剩余未发行量；显式行须属于订单且 已分配+本次 ≤ qty（422906） */
    static Map<Long, Integer> resolveAllocations(List<OrderLine> orderLines, Map<Long, Integer> allocated,
                                                 List<ShipmentLineCreate> requested) {
        Map<Long, OrderLine> index = new HashMap<>();
        for (OrderLine line : orderLines) {
            index.put(line.getId(), line);
        }
        Map<Long, Integer> allocations = new LinkedHashMap<>();
        if (requested == null || requested.isEmpty()) {
            for (OrderLine line : orderLines) {
                int remaining = (line.getQty() == null ? 0 : line.getQty()) - allocated.getOrDefault(line.getId(), 0);
                if (remaining > 0) {
                    allocations.put(line.getId(), remaining);
                }
            }
            if (allocations.isEmpty()) {
                throw new TradingException(TradingErrorCode.SHIPMENT_QTY_EXCEEDED, Map.of("reason", "nothing_unshipped"));
            }
            return allocations;
        }
        for (ShipmentLineCreate line : requested) {
            allocations.merge(line.orderLineId(), line.qty(), Integer::sum);
        }
        for (Map.Entry<Long, Integer> e : allocations.entrySet()) {
            OrderLine line = index.get(e.getKey());
            if (line == null) {
                throw TradingException.fieldValidation("lines", "invalid");
            }
            int unshipped = (line.getQty() == null ? 0 : line.getQty()) - allocated.getOrDefault(line.getId(), 0);
            if (e.getValue() > unshipped) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("order_line_id", line.getId());
                details.put("unshipped", Math.max(unshipped, 0));
                details.put("requested", e.getValue());
                throw new TradingException(TradingErrorCode.SHIPMENT_QTY_EXCEEDED, details);
            }
        }
        return allocations;
    }

    /** 全部行发出：CAS PAID→SHIPPED + 快照 + 事件 + MQ（事务内） */
    private void promoteToShipped(Order order, Shipment shipment, LocalDateTime now, Long operatorId) {
        ProductionStage fromStage = order.getProductionStage();
        int affected = orderRepository.casUpdateStatus(order.getId(), OrderStatus.PAID, OrderStatus.SHIPPED, uw -> {
            uw.set(Order::getCarrier, shipment.getCarrierName())
                    .set(Order::getTrackingNo, shipment.getTrackingNo())
                    .set(Order::getShippedAt, now);
            OrderRepository.setProductionStage(uw, null);
        });
        if (affected == 0) {
            throw TradingException.orderStateInvalid();
        }
        if (fromStage != null) {
            orderEventRecorder.productionStageChanged(order.getId(), fromStage, null, OrderActorType.ADMIN, operatorId,
                    "shipped");
        }
        orderEventRecorder.statusChanged(order.getId(), OrderStatus.PAID, OrderStatus.SHIPPED, OrderActorType.SYSTEM,
                null, "all lines shipped", true);
        order.setStatus(OrderStatus.SHIPPED);
        order.setCarrier(shipment.getCarrierName());
        order.setTrackingNo(shipment.getTrackingNo());
        order.setShippedAt(now);
        order.setProductionStage(null);
        eventsPublisher.publishOrderShipped(order, localeOf(order));
    }

    /** uk 冲突分流：shipment_no 撞号重取 ×3；tracking uk → 409908 */
    private void insertWithRetry(Shipment shipment) {
        for (int attempt = 1; ; attempt++) {
            try {
                shipmentRepository.insert(shipment);
                return;
            } catch (DuplicateKeyException ex) {
                String message = String.valueOf(ex.getMostSpecificCause() == null ? ex.getMessage()
                        : ex.getMostSpecificCause().getMessage());
                if (message.contains("uk_shipment_no") && attempt < SHIPMENT_NO_MAX_ATTEMPTS) {
                    log.warn("[SHIPMENT] shipment_no collision, regenerate (attempt {})", attempt);
                    shipment.setShipmentNo(shipmentNoGenerator.next());
                    continue;
                }
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("carrier_code", shipment.getCarrierCode());
                details.put("tracking_no", shipment.getTrackingNo());
                throw new TradingException(TradingErrorCode.SHIPMENT_TRACKING_DUPLICATE, details);
            }
        }
    }

    private void registerWithProvider(Shipment shipment) {
        if (trackingProvider == null || trackingProvider.isStub()) {
            return;
        }
        try {
            String ref = trackingProvider.register(shipment);
            if (ref != null) {
                shipmentRepository.updateProviderRef(shipment.getId(), ref);
            }
        } catch (Exception ex) {
            log.warn("[SHIPMENT] provider register failed shipment_no={} ({})", shipment.getShipmentNo(), ex.getMessage());
        }
    }

    // ==================== patch ====================

    /** PATCH /api/admin/shipments/{id}（仅无 PROVIDER 事件且未 DELIVERED/CANCELLED） */
    public ShipmentDto patch(Long shipmentId, ShipmentPatch req) {
        Shipment shipment = requireShipment(shipmentId);
        TradingFieldErrors errors = new TradingFieldErrors();
        Carrier carrier = null;
        if (req != null && TradingParams.trimToNull(req.carrierCode()) != null) {
            carrier = carrierResolver.resolveEnabled(req.carrierCode());
            if (carrier == null) {
                errors.reject("carrier_code", "invalid_enum");
            }
        }
        String trackingNo = req == null ? null : TradingParams.checkMaxLength(req.trackingNo(), 64, "tracking_no", errors);
        if (carrier == null && trackingNo == null) {
            errors.reject("tracking_no", "required");
        }
        errors.throwIfAny();
        Carrier effectiveCarrier = carrier;
        String effectiveTracking = trackingNo;
        Long operatorId = audit.currentOperatorId();
        return withOrderLock(shipment.getOrderId(), () -> {
            Shipment fresh = requireShipment(shipmentId);
            assertEditable(fresh);
            String newCode = effectiveCarrier != null ? effectiveCarrier.getCode() : fresh.getCarrierCode();
            String newName = effectiveCarrier != null ? effectiveCarrier.getName() : fresh.getCarrierName();
            String newTracking = effectiveTracking != null ? effectiveTracking : fresh.getTrackingNo();
            Carrier forUrl = effectiveCarrier != null ? effectiveCarrier : carrierResolver.resolveAny(fresh.getCarrierCode());
            String newUrl = CarrierResolver.trackingUrl(forUrl, newTracking);
            String oldTracking = fresh.getTrackingNo();
            txRunner.inTx(() -> {
                try {
                    shipmentRepository.updateCarrierAndTracking(shipmentId, newCode, newName, newTracking, newUrl);
                } catch (DuplicateKeyException ex) {
                    throw new TradingException(TradingErrorCode.SHIPMENT_TRACKING_DUPLICATE,
                            Map.of("carrier_code", newCode, "tracking_no", newTracking));
                }
                // 订单快照跟随（仅当快照指向本包裹）
                Order order = orderRepository.findById(fresh.getOrderId());
                if (order != null && oldTracking.equals(order.getTrackingNo())) {
                    orderRepository.casUpdateStatus(order.getId(), order.getStatus(), order.getStatus(),
                            uw -> uw.set(Order::getCarrier, newName).set(Order::getTrackingNo, newTracking));
                }
                fresh.setCarrierCode(newCode);
                fresh.setCarrierName(newName);
                fresh.setTrackingNo(newTracking);
                fresh.setTrackingUrl(newUrl);
                Map<String, Object> payload = shipmentPayload(fresh);
                payload.put("previous_tracking_no", oldTracking);
                orderEventRecorder.record(fresh.getOrderId(), OrderEventType.SHIPMENT, OrderActorType.ADMIN, operatorId,
                        "Shipment " + fresh.getShipmentNo() + " updated", newName + " " + newTracking, payload, true);
                audit.record(ACTION_SHIPMENT, fresh.getShipmentNo(),
                        "{\"op\":\"patch\",\"carrier_code\":\"" + newCode + "\",\"tracking_no\":\"" + newTracking + "\"}");
            });
            return queryService.get(requireShipment(shipmentId));
        });
    }

    // ==================== events / deliver ====================

    /** POST /api/admin/shipments/{id}/events（手工轨迹；status=DELIVERED 时包裹签收 + 聚合） */
    public ShipmentDto addEvent(Long shipmentId, ShipmentEventCreate req) {
        Shipment shipment = requireShipment(shipmentId);
        TradingFieldErrors errors = new TradingFieldErrors();
        ShipmentStatus status = req == null ? null : ShipmentStatus.of(req.status());
        if (status == null || status == ShipmentStatus.CANCELLED) {
            errors.reject("status", req == null || req.status() == null ? "required" : "invalid_enum");
        }
        String location = req == null ? null : TradingParams.checkMaxLength(req.location(), 128, "location", errors);
        String description = req == null ? null : TradingParams.checkMaxLength(req.description(), 255, "description", errors);
        errors.throwIfAny();
        LocalDateTime occurredAt = req.occurredAt() == null ? LocalDateTime.now() : req.occurredAt();
        String desc = description != null ? description : defaultDescription(status);
        Long operatorId = audit.currentOperatorId();
        return withOrderLock(shipment.getOrderId(), () -> {
            Shipment fresh = requireShipment(shipmentId);
            if (fresh.getStatus() == ShipmentStatus.CANCELLED
                    || (fresh.getStatus() == ShipmentStatus.DELIVERED && status != ShipmentStatus.DELIVERED)) {
                throw new TradingException(TradingErrorCode.SHIPMENT_STATE_INVALID,
                        Map.of("status", fresh.getStatus().getKey()));
            }
            txRunner.inTx(() -> {
                ShipmentEvent event = new ShipmentEvent();
                event.setShipmentId(shipmentId);
                event.setOccurredAt(occurredAt);
                event.setStatus(status);
                event.setLocation(location);
                event.setDescription(desc);
                event.setSource(ShipmentEventSource.MANUAL);
                eventRepository.insert(event);
                applyStatus(fresh, status, occurredAt, desc, OrderActorType.ADMIN, operatorId);
                audit.record(ACTION_SHIPMENT, fresh.getShipmentNo(),
                        "{\"op\":\"event\",\"status\":" + status.getKey() + "}");
            });
            return queryService.get(requireShipment(shipmentId));
        });
    }

    /** POST /api/admin/shipments/{id}/deliver（= DELIVERED 手工事件；幂等：已签收直接返回） */
    public ShipmentDto deliver(Long shipmentId) {
        Shipment shipment = requireShipment(shipmentId);
        if (shipment.getStatus() == ShipmentStatus.DELIVERED) {
            return queryService.get(shipment);
        }
        return addEvent(shipmentId, new ShipmentEventCreate(ShipmentStatus.DELIVERED.getKey(), null, null,
                "Delivered (confirmed by admin)"));
    }

    /**
     * 状态推进（锁内、事务内）：单向 PENDING→IN_TRANSIT→OUT_FOR_DELIVERY→DELIVERED；EXCEPTION 可从任意非终态进入并恢复；
     * DELIVERED 终态不回退。每次事件写 order_event(SHIPMENT)；包裹 DELIVERED 时执行签收聚合。
     */
    void applyStatus(Shipment shipment, ShipmentStatus target, LocalDateTime occurredAt, String description,
                     OrderActorType actor, Long actorId) {
        ShipmentStatus current = shipment.getStatus();
        ShipmentStatus next = nextStatus(current, target);
        LocalDateTime lastAt = shipment.getLastEventAt() == null || !occurredAt.isBefore(shipment.getLastEventAt())
                ? occurredAt : shipment.getLastEventAt();
        String lastDesc = lastAt == occurredAt ? description : shipment.getLastEventDesc();
        boolean delivered = next == ShipmentStatus.DELIVERED && current != ShipmentStatus.DELIVERED;
        if (next != current) {
            int affected = shipmentRepository.casUpdateStatus(shipment.getId(), current, next, uw -> {
                uw.set(Shipment::getLastEventAt, lastAt).set(Shipment::getLastEventDesc, lastDesc);
                if (delivered) {
                    uw.set(Shipment::getDeliveredAt, occurredAt);
                }
            });
            if (affected == 0) {
                throw new TradingException(TradingErrorCode.SHIPMENT_STATE_INVALID, Map.of("status", current.getKey()));
            }
            shipment.setStatus(next);
            if (delivered) {
                shipment.setDeliveredAt(occurredAt);
            }
        } else {
            shipmentRepository.updateLastEvent(shipment.getId(), lastAt, lastDesc);
        }
        shipment.setLastEventAt(lastAt);
        shipment.setLastEventDesc(lastDesc);
        Map<String, Object> payload = shipmentPayload(shipment);
        payload.put("event_status", target.getKey());
        orderEventRecorder.record(shipment.getOrderId(), OrderEventType.SHIPMENT, actor, actorId,
                "Package " + shipment.getShipmentNo() + ": " + target.name().toLowerCase().replace('_', ' '),
                description, payload, true);
        if (delivered) {
            aggregateDelivered(shipment.getOrderId(), occurredAt);
        }
    }

    static ShipmentStatus nextStatus(ShipmentStatus current, ShipmentStatus target) {
        if (current == ShipmentStatus.DELIVERED || current == ShipmentStatus.CANCELLED) {
            return current;
        }
        if (target == ShipmentStatus.EXCEPTION) {
            return ShipmentStatus.EXCEPTION;
        }
        if (current == ShipmentStatus.EXCEPTION) {
            return target;
        }
        return rank(target) > rank(current) ? target : current;
    }

    private static int rank(ShipmentStatus status) {
        return switch (status) {
            case PENDING -> 1;
            case IN_TRANSIT -> 2;
            case OUT_FOR_DELIVERY -> 3;
            case DELIVERED -> 4;
            default -> 0;
        };
    }

    /** STATE-4 签收聚合：全部有效包裹 DELIVERED 且订单 SHIPPED → CAS SHIPPED→DELIVERED + MQ order.delivered */
    private void aggregateDelivered(Long orderId, LocalDateTime deliveredAt) {
        Order order = orderRepository.findById(orderId);
        if (order == null || order.getStatus() != OrderStatus.SHIPPED) {
            return;
        }
        List<Shipment> active = shipmentRepository.listActiveByOrderId(orderId);
        boolean all = !active.isEmpty() && active.stream().allMatch(s -> s.getStatus() == ShipmentStatus.DELIVERED);
        if (!all) {
            return;
        }
        int affected = orderRepository.casUpdateStatus(orderId, OrderStatus.SHIPPED, OrderStatus.DELIVERED,
                uw -> uw.set(Order::getDeliveredAt, deliveredAt));
        if (affected == 0) {
            return;
        }
        orderEventRecorder.statusChanged(orderId, OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderActorType.SYSTEM,
                null, "all packages delivered", true);
        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(deliveredAt);
        eventsPublisher.publishOrderDelivered(order, localeOf(order));
    }

    // ==================== cancel ====================

    /** POST /api/admin/shipments/{id}/cancel（无 PROVIDER 事件且未 DELIVERED；释放分配；SHIPPED 且有未发行 → PAID） */
    public ShipmentDto cancel(Long shipmentId) {
        Shipment shipment = requireShipment(shipmentId);
        Long operatorId = audit.currentOperatorId();
        return withOrderLock(shipment.getOrderId(), () -> {
            Shipment fresh = requireShipment(shipmentId);
            assertEditable(fresh);
            txRunner.inTx(() -> {
                if (shipmentRepository.casUpdateStatus(shipmentId, null, ShipmentStatus.CANCELLED, null) == 0) {
                    throw new TradingException(TradingErrorCode.SHIPMENT_STATE_INVALID,
                            Map.of("status", fresh.getStatus().getKey()));
                }
                fresh.setStatus(ShipmentStatus.CANCELLED);
                orderEventRecorder.record(fresh.getOrderId(), OrderEventType.SHIPMENT, OrderActorType.ADMIN, operatorId,
                        "Shipment " + fresh.getShipmentNo() + " cancelled", fresh.getTrackingNo(), shipmentPayload(fresh), true);
                Order order = orderRepository.findById(fresh.getOrderId());
                if (order != null && order.getStatus() == OrderStatus.SHIPPED) {
                    List<Shipment> active = shipmentRepository.listActiveByOrderId(order.getId());
                    Map<Long, Integer> allocated = lineRepository.sumQtyByOrderLine(active.stream().map(Shipment::getId).toList());
                    int totalQty = orderLineRepository.listByOrderId(order.getId()).stream()
                            .mapToInt(l -> l.getQty() == null ? 0 : l.getQty()).sum();
                    int remaining = totalQty - allocated.values().stream().mapToInt(Integer::intValue).sum();
                    if (remaining > 0) {
                        Shipment latest = active.isEmpty() ? null : active.get(active.size() - 1);
                        int affected = orderRepository.casUpdateStatus(order.getId(), OrderStatus.SHIPPED, OrderStatus.PAID, uw -> {
                            if (latest == null) {
                                uw.setSql("carrier = NULL").setSql("tracking_no = NULL");
                            } else {
                                uw.set(Order::getCarrier, latest.getCarrierName())
                                        .set(Order::getTrackingNo, latest.getTrackingNo());
                            }
                            uw.setSql("shipped_at = NULL");
                            OrderRepository.setProductionStage(uw, ProductionStage.READY_TO_SHIP);
                        });
                        if (affected > 0) {
                            orderEventRecorder.statusChanged(order.getId(), OrderStatus.SHIPPED, OrderStatus.PAID,
                                    OrderActorType.SYSTEM, null, "shipment cancelled, " + remaining + " item(s) unshipped", true);
                        }
                    }
                }
                audit.record(ACTION_SHIPMENT, fresh.getShipmentNo(), "{\"op\":\"cancel\"}");
            });
            return queryService.get(requireShipment(shipmentId));
        });
    }

    // ==================== sync ====================

    /** POST /api/admin/shipments/{id}/sync（provider stub → null → 204） */
    public ShipmentDto sync(Long shipmentId) {
        Shipment shipment = requireShipment(shipmentId);
        if (trackingProvider.isStub()) {
            return null;
        }
        List<TrackingProviderPort.ProviderEvent> events = trackingProvider.fetchEvents(shipment);
        applyProviderEvents(shipment, events);
        return queryService.get(requireShipment(shipmentId));
    }

    /**
     * 供应商事件落库（锁内、事务内）：insertIgnore 去重（provider_event_id 缺省 sha1(occurred_at|status|description) 前 40 位）；
     * 按时间序推进状态；返回新增事件数。已 DELIVERED/CANCELLED 的包裹不再变更状态（仅追加事件）。
     */
    public int applyProviderEvents(Shipment shipment, List<TrackingProviderPort.ProviderEvent> events) {
        if (events == null || events.isEmpty()) {
            txRunner.inTx(() -> shipmentRepository.markSynced(shipment.getId(), LocalDateTime.now()));
            return 0;
        }
        List<TrackingProviderPort.ProviderEvent> sorted = new ArrayList<>(events);
        sorted.sort((a, b) -> a.occurredAt().compareTo(b.occurredAt()));
        return withOrderLock(shipment.getOrderId(), () -> {
            Shipment fresh = requireShipment(shipment.getId());
            int[] inserted = {0};
            txRunner.inTx(() -> {
                for (TrackingProviderPort.ProviderEvent pe : sorted) {
                    ShipmentEvent event = new ShipmentEvent();
                    event.setShipmentId(fresh.getId());
                    event.setOccurredAt(pe.occurredAt());
                    event.setStatus(pe.status() == null ? ShipmentStatus.IN_TRANSIT : pe.status());
                    event.setLocation(truncate(pe.location(), 128));
                    event.setDescription(truncate(pe.description(), 255));
                    event.setSource(ShipmentEventSource.PROVIDER);
                    event.setProviderEventId(pe.providerEventId() != null ? truncate(pe.providerEventId(), 64)
                            : ShipmentTrackingSyncService.derivedEventId(pe));
                    if (eventRepository.insertIgnore(event) == 0) {
                        continue;
                    }
                    inserted[0]++;
                    if (fresh.getStatus() != ShipmentStatus.CANCELLED) {
                        applyStatus(fresh, event.getStatus(), pe.occurredAt(), event.getDescription(),
                                OrderActorType.SYSTEM, null);
                    }
                }
                shipmentRepository.markSynced(fresh.getId(), LocalDateTime.now());
            });
            return inserted[0];
        });
    }

    // ==================== helpers ====================

    private Shipment requireShipment(Long id) {
        Shipment shipment = shipmentRepository.findById(id);
        if (shipment == null) {
            throw new TradingException(TradingErrorCode.SHIPMENT_NOT_FOUND);
        }
        return shipment;
    }

    /** patch/cancel 前置：未 DELIVERED/CANCELLED 且无 PROVIDER 事件 */
    private void assertEditable(Shipment shipment) {
        if (shipment.getStatus() == ShipmentStatus.DELIVERED || shipment.getStatus() == ShipmentStatus.CANCELLED) {
            throw new TradingException(TradingErrorCode.SHIPMENT_STATE_INVALID, Map.of("status", shipment.getStatus().getKey()));
        }
        if (eventRepository.existsProviderEvent(shipment.getId())) {
            throw new TradingException(TradingErrorCode.SHIPMENT_STATE_INVALID, Map.of("reason", "provider_events_exist"));
        }
    }

    private <T> T withOrderLock(Long orderId, Supplier<T> action) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + orderId);
        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            acquired = false;
        }
        if (!acquired) {
            throw new TradingException(TradingErrorCode.SHIP_LOCK_CONFLICT, Map.of("order_id", orderId));
        }
        try {
            return action.get();
        } finally {
            try {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            } catch (Exception ex) {
                log.warn("[SHIPMENT] unlock failed order_id={}", orderId, ex);
            }
        }
    }

    private static Map<String, Object> shipmentPayload(Shipment s) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("shipment_id", s.getId());
        payload.put("shipment_no", s.getShipmentNo());
        payload.put("carrier_code", s.getCarrierCode());
        payload.put("carrier_name", s.getCarrierName());
        payload.put("tracking_no", s.getTrackingNo());
        payload.put("tracking_url", s.getTrackingUrl());
        payload.put("status", s.getStatus() == null ? null : s.getStatus().getKey());
        return payload;
    }

    private static String defaultDescription(ShipmentStatus status) {
        return switch (status) {
            case PENDING -> "Shipment information received";
            case IN_TRANSIT -> "In transit";
            case OUT_FOR_DELIVERY -> "Out for delivery";
            case DELIVERED -> "Delivered";
            case EXCEPTION -> "Delivery exception";
            default -> status.name();
        };
    }

    private static String localeOf(Order order) {
        return order.getLocaleSnapshot() != null ? order.getLocaleSnapshot() : "en";
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
