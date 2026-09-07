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
import com.dreamy.domain.shipment.repository.ShipmentEventRepository;
import com.dreamy.domain.shipment.repository.ShipmentLineRepository;
import com.dreamy.domain.shipment.repository.ShipmentRepository;
import com.dreamy.dto.TradingDtos.ShipmentCreateRequest;
import com.dreamy.dto.TradingDtos.ShipmentDto;
import com.dreamy.dto.TradingDtos.ShipmentEventCreate;
import com.dreamy.dto.TradingDtos.ShipmentLineCreate;
import com.dreamy.dto.TradingDtos.ShipmentPatch;
import com.dreamy.enums.CarrierStatus;
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
import com.dreamy.mq.TradingEventsPublisher;
import com.dreamy.port.TrackingProviderPort;
import com.dreamy.testsupport.TradingImmediateTxRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ShipmentService 单测（order-flow-complete §6.1：超量 422906、重复单号 409908、部分→全部聚合、并发发货锁 409906、
 * cancel 释放与 SHIPPED→PAID、签收聚合 SHIPPED→DELIVERED、状态单向/EXCEPTION 恢复、Idempotency-Key 命中）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentServiceTest {

    private static final long ORDER = 100L;

    @Mock ShipmentRepository shipmentRepository;
    @Mock ShipmentLineRepository lineRepository;
    @Mock ShipmentEventRepository eventRepository;
    @Mock OrderRepository orderRepository;
    @Mock OrderLineRepository orderLineRepository;
    @Mock CarrierResolver carrierResolver;
    @Mock ShipmentNoGenerator shipmentNoGenerator;
    @Mock ShipmentQueryService queryService;
    @Mock OrderEventRecorder orderEventRecorder;
    @Mock TradingEventsPublisher eventsPublisher;
    @Mock TradingAuditRecorder audit;
    @Mock RedissonClient redissonClient;
    @Mock RLock lock;
    @Mock TrackingProviderPort trackingProvider;

    ShipmentService service;
    Order order;
    List<OrderLine> orderLines;
    List<Shipment> activeShipments = new ArrayList<>();
    AtomicLong shipmentIds = new AtomicLong(500);

    @BeforeEach
    void setUp() throws InterruptedException {
        service = new ShipmentService(shipmentRepository, lineRepository, eventRepository, orderRepository,
                orderLineRepository, carrierResolver, shipmentNoGenerator, queryService, orderEventRecorder,
                eventsPublisher, new TradingImmediateTxRunner(), new TradingAfterCommitRunner(), audit,
                redissonClient, trackingProvider);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(trackingProvider.isStub()).thenReturn(true);
        when(shipmentNoGenerator.next()).thenReturn("SHP-20260907-0001", "SHP-20260907-0002", "SHP-20260907-0003");
        when(audit.currentOperatorId()).thenReturn(9L);
        Carrier dhl = new Carrier();
        dhl.setId(3L);
        dhl.setName("DHL Express");
        dhl.setCode("DHL");
        dhl.setStatus(CarrierStatus.ENABLED);
        dhl.setTrackingUrlTemplate("https://www.dhl.com/global-en/home/tracking.html?tracking-id={tracking_no}");
        when(carrierResolver.resolveEnabled("DHL")).thenReturn(dhl);
        when(carrierResolver.resolveEnabled("DHL Express")).thenReturn(dhl);
        when(carrierResolver.resolveAny(anyString())).thenReturn(dhl);

        order = new Order();
        order.setId(ORDER);
        order.setOrderNo("DRM-20260907-0001");
        order.setCustomerId(7L);
        order.setStatus(OrderStatus.PAID);
        order.setProductionStage(ProductionStage.READY_TO_SHIP);
        order.setLocaleSnapshot("en");
        when(orderRepository.findById(ORDER)).thenAnswer(inv -> order);
        orderLines = List.of(line(1L, 2), line(2L, 1));
        when(orderLineRepository.listByOrderId(ORDER)).thenAnswer(inv -> orderLines);
        when(shipmentRepository.listActiveByOrderId(ORDER)).thenAnswer(inv -> activeShipments.stream()
                .filter(s -> s.getStatus() != ShipmentStatus.CANCELLED).toList());
        when(shipmentRepository.listByOrderId(ORDER)).thenAnswer(inv -> new ArrayList<>(activeShipments));
        doAnswer(inv -> {
            Shipment s = inv.getArgument(0);
            s.setId(shipmentIds.incrementAndGet());
            activeShipments.add(s);
            return null;
        }).when(shipmentRepository).insert(any(Shipment.class));
        when(shipmentRepository.findById(anyLong())).thenAnswer(inv -> activeShipments.stream()
                .filter(s -> s.getId().equals(inv.getArgument(0))).findFirst().orElse(null));
        when(shipmentRepository.casUpdateStatus(anyLong(), any(), any(), any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            ShipmentStatus from = inv.getArgument(1);
            ShipmentStatus to = inv.getArgument(2);
            Shipment s = activeShipments.stream().filter(x -> x.getId().equals(id)).findFirst().orElse(null);
            if (s == null) {
                return 0;
            }
            if (from != null && s.getStatus() != from) {
                return 0;
            }
            if (from == null && (s.getStatus() == ShipmentStatus.DELIVERED || s.getStatus() == ShipmentStatus.CANCELLED)) {
                return 0;
            }
            s.setStatus(to);
            return 1;
        });
        // order CAS：按 from 匹配内存状态
        when(orderRepository.casUpdateStatus(eq(ORDER), any(), any(), any())).thenAnswer(inv -> {
            OrderStatus from = inv.getArgument(1);
            OrderStatus to = inv.getArgument(2);
            if (order.getStatus() != from) {
                return 0;
            }
            order.setStatus(to);
            return 1;
        });
        when(queryService.get(any())).thenAnswer(inv -> {
            Shipment s = inv.getArgument(0);
            return new ShipmentDto(s.getId(), s.getShipmentNo(), s.getCarrierCode(), s.getCarrierName(), s.getTrackingNo(),
                    s.getTrackingUrl(), s.getStatus().getKey(), s.getShippedAt(), s.getDeliveredAt(), s.getLastEventAt(),
                    s.getLastEventDesc(), List.of(), List.of());
        });
    }

    private static OrderLine line(long id, int qty) {
        OrderLine line = new OrderLine();
        line.setId(id);
        line.setOrderId(ORDER);
        line.setQty(qty);
        line.setProductName("Gown " + id);
        return line;
    }

    private void allocated(Map<Long, Integer> map) {
        when(lineRepository.sumQtyByOrderLine(any())).thenReturn(map);
    }

    @Test
    @DisplayName("部分发货：1 行 → 订单仍 PAID，无 STATUS_CHANGED；SHIPMENT 事件 partial=true；tracking_url 按模板生成")
    void partialShipmentKeepsOrderPaid() {
        allocated(Map.of());
        ShipmentDto dto = service.create(ORDER, new ShipmentCreateRequest("DHL", "DHL-1",
                List.of(new ShipmentLineCreate(1L, 1))), null);
        assertThat(dto.status()).isEqualTo(ShipmentStatus.PENDING.getKey());
        assertThat(dto.trackingUrl()).isEqualTo("https://www.dhl.com/global-en/home/tracking.html?tracking-id=DHL-1");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderEventRecorder).record(eq(ORDER), eq(OrderEventType.SHIPMENT), eq(OrderActorType.ADMIN), eq(9L),
                any(), eq("DHL-1"), org.mockito.ArgumentMatchers.argThat(p -> Boolean.TRUE.equals(p.get("partial"))), eq(true));
        verify(orderEventRecorder, never()).statusChanged(anyLong(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(eventsPublisher, never()).publishOrderShipped(any(), any());
    }

    @Test
    @DisplayName("部分→全部聚合：剩余行发出（lines 省略=全部未发行）→ CAS PAID→SHIPPED + 事件 + order.shipped 事务内发布")
    void remainingLinesPromoteToShipped() {
        allocated(Map.of(1L, 1));
        service.create(ORDER, new ShipmentCreateRequest("DHL Express", "DHL-2", null), null);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        verify(orderEventRecorder).productionStageChanged(eq(ORDER), eq(ProductionStage.READY_TO_SHIP), isNull(),
                eq(OrderActorType.ADMIN), eq(9L), eq("shipped"));
        verify(orderEventRecorder).statusChanged(eq(ORDER), eq(OrderStatus.PAID), eq(OrderStatus.SHIPPED),
                eq(OrderActorType.SYSTEM), isNull(), eq("all lines shipped"), eq(true));
        verify(eventsPublisher).publishOrderShipped(org.mockito.ArgumentMatchers.argThat(o ->
                "DHL-2".equals(o.getTrackingNo()) && "DHL Express".equals(o.getCarrier())), eq("en"));
        // 分配行 = 行1 剩余 1 + 行2 全部 1
        verify(lineRepository).batchInsert(org.mockito.ArgumentMatchers.argThat(lines -> lines.size() == 2
                && lines.stream().allMatch(l -> l.getQty() == 1)));
    }

    @Test
    @DisplayName("超量 422906：已分配 1 + 请求 2 > qty 2；全部已发出再发 → 422906 nothing_unshipped")
    void overAllocationRejected() {
        allocated(Map.of(1L, 1));
        assertThatThrownBy(() -> service.create(ORDER, new ShipmentCreateRequest("DHL", "X",
                List.of(new ShipmentLineCreate(1L, 2))), null))
                .isInstanceOfSatisfying(TradingException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.SHIPMENT_QTY_EXCEEDED);
                    assertThat(ex.getDetails()).containsEntry("unshipped", 1);
                });
        allocated(Map.of(1L, 2, 2L, 1));
        assertThatThrownBy(() -> service.create(ORDER, new ShipmentCreateRequest("DHL", "Y", null), null))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.SHIPMENT_QTY_EXCEEDED));
        // 行不属于订单 → 422601
        allocated(Map.of());
        assertThatThrownBy(() -> service.create(ORDER, new ShipmentCreateRequest("DHL", "Z",
                List.of(new ShipmentLineCreate(99L, 1))), null))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.FIELD_VALIDATION_FAILED));
        verify(shipmentRepository, never()).insert(any());
    }

    @Test
    @DisplayName("重复单号 409908：uk_shipment_tracking 冲突；shipment_no 撞号重取 ×3")
    void duplicateTrackingRejected() {
        allocated(Map.of());
        doAnswer(inv -> {
            throw new DuplicateKeyException("Duplicate entry for key 'shipment.uk_shipment_tracking'");
        }).when(shipmentRepository).insert(any(Shipment.class));
        assertThatThrownBy(() -> service.create(ORDER, new ShipmentCreateRequest("DHL", "DUP", null), null))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.SHIPMENT_TRACKING_DUPLICATE));
        // shipment_no 撞号：首次冲突后重取成功
        int[] calls = {0};
        doAnswer(inv -> {
            if (calls[0]++ == 0) {
                throw new DuplicateKeyException("Duplicate entry for key 'shipment.uk_shipment_no'");
            }
            Shipment s = inv.getArgument(0);
            s.setId(777L);
            activeShipments.add(s);
            return null;
        }).when(shipmentRepository).insert(any(Shipment.class));
        ShipmentDto dto = service.create(ORDER, new ShipmentCreateRequest("DHL", "OK", null), null);
        // 首次 create 取 0001（冲突）→ 本次首取 0002 撞号 → 重取 0003
        assertThat(dto.shipmentNo()).isEqualTo("SHP-20260907-0003");
        assertThat(calls[0]).isEqualTo(2);
    }

    @Test
    @DisplayName("并发发货锁：tryLock 失败 → 409906，不触库；Idempotency-Key 命中 → 直接返回既有包裹不加锁")
    void lockConflictAndIdempotency() throws InterruptedException {
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(false);
        assertThatThrownBy(() -> service.create(ORDER, new ShipmentCreateRequest("DHL", "L1", null), null))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.SHIP_LOCK_CONFLICT));
        verify(shipmentRepository, never()).insert(any());
        Shipment existing = new Shipment();
        existing.setId(42L);
        existing.setShipmentNo("SHP-EXIST");
        existing.setStatus(ShipmentStatus.PENDING);
        existing.setIdempotencyKey("idem-1");
        when(shipmentRepository.findByIdempotencyKey("idem-1")).thenReturn(existing);
        ShipmentDto dto = service.create(ORDER, new ShipmentCreateRequest("DHL", "L2", null), "idem-1");
        assertThat(dto.shipmentNo()).isEqualTo("SHP-EXIST");
        verify(redissonClient, org.mockito.Mockito.times(1)).getLock(anyString());
    }

    @Test
    @DisplayName("非 PAID 订单 → 409602；未知承运商 → 422601 carrier_code；tracking_no 必填")
    void createValidation() {
        allocated(Map.of());
        order.setStatus(OrderStatus.PENDING);
        assertThatThrownBy(() -> service.create(ORDER, new ShipmentCreateRequest("DHL", "T", null), null))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.ORDER_STATE_INVALID));
        order.setStatus(OrderStatus.PAID);
        assertThatThrownBy(() -> service.create(ORDER, new ShipmentCreateRequest("NOPE", "T", null), null))
                .isInstanceOfSatisfying(TradingException.class, ex ->
                        assertThat(((Map<?, ?>) ex.getDetails().get("fields")).containsKey("carrier_code")).isTrue());
        assertThatThrownBy(() -> service.create(ORDER, new ShipmentCreateRequest("DHL", " ", null), null))
                .isInstanceOfSatisfying(TradingException.class, ex ->
                        assertThat(((Map<?, ?>) ex.getDetails().get("fields")).containsKey("tracking_no")).isTrue());
    }

    @Test
    @DisplayName("签收聚合：两包裹逐个 deliver → 第一个仅包裹 DELIVERED；第二个 → CAS SHIPPED→DELIVERED + order.delivered 事务内")
    void deliverAggregatesToOrderDelivered() {
        allocated(Map.of());
        service.create(ORDER, new ShipmentCreateRequest("DHL", "A", List.of(new ShipmentLineCreate(1L, 2))), null);
        allocated(Map.of(1L, 2));
        service.create(ORDER, new ShipmentCreateRequest("DHL", "B", null), null);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        Shipment first = activeShipments.get(0);
        Shipment second = activeShipments.get(1);

        service.deliver(first.getId());
        assertThat(first.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        verify(eventsPublisher, never()).publishOrderDelivered(any(), any());

        service.deliver(second.getId());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        verify(orderEventRecorder).statusChanged(eq(ORDER), eq(OrderStatus.SHIPPED), eq(OrderStatus.DELIVERED),
                eq(OrderActorType.SYSTEM), isNull(), eq("all packages delivered"), eq(true));
        verify(eventsPublisher).publishOrderDelivered(org.mockito.ArgumentMatchers.argThat(o ->
                o.getStatus() == OrderStatus.DELIVERED && o.getDeliveredAt() != null), eq("en"));
        // 幂等：再次 deliver 已签收包裹 → 直接返回，不再写事件
        org.mockito.Mockito.clearInvocations(eventRepository);
        service.deliver(second.getId());
        verify(eventRepository, never()).insert(any());
    }

    @Test
    @DisplayName("状态单向：IN_TRANSIT 后回填 PENDING 事件不回退；EXCEPTION 可进可恢复；DELIVERED 后手工其他状态 → 409909")
    void statusMonotonicWithException() {
        allocated(Map.of());
        service.create(ORDER, new ShipmentCreateRequest("DHL", "S", null), null);
        Shipment s = activeShipments.get(0);
        service.addEvent(s.getId(), new ShipmentEventCreate(2, null, "HK", "Departed"));
        assertThat(s.getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        service.addEvent(s.getId(), new ShipmentEventCreate(1, LocalDateTime.now().minusDays(1), null, "Info received (late)"));
        assertThat(s.getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        service.addEvent(s.getId(), new ShipmentEventCreate(5, null, null, "Address issue"));
        assertThat(s.getStatus()).isEqualTo(ShipmentStatus.EXCEPTION);
        service.addEvent(s.getId(), new ShipmentEventCreate(3, null, null, "Out for delivery"));
        assertThat(s.getStatus()).isEqualTo(ShipmentStatus.OUT_FOR_DELIVERY);
        service.addEvent(s.getId(), new ShipmentEventCreate(4, null, null, null));
        assertThat(s.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThatThrownBy(() -> service.addEvent(s.getId(), new ShipmentEventCreate(2, null, null, "x")))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.SHIPMENT_STATE_INVALID));
        // status=CANCELLED 不可经事件写入
        assertThatThrownBy(() -> service.addEvent(s.getId(), new ShipmentEventCreate(6, null, null, "x")))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.FIELD_VALIDATION_FAILED));
        // 每个手工事件均落 shipment_event(MANUAL)
        verify(eventRepository, org.mockito.Mockito.times(5)).insert(org.mockito.ArgumentMatchers.argThat(
                e -> e.getSource() == ShipmentEventSource.MANUAL && e.getProviderEventId() == null));
    }

    @Test
    @DisplayName("cancel：释放分配；订单 SHIPPED 且释放后有未发行 → CAS SHIPPED→PAID(production_stage=READY_TO_SHIP) + 事件")
    void cancelReleasesAllocationAndRevertsOrder() {
        allocated(Map.of());
        service.create(ORDER, new ShipmentCreateRequest("DHL", "C1", List.of(new ShipmentLineCreate(1L, 2))), null);
        allocated(Map.of(1L, 2));
        service.create(ORDER, new ShipmentCreateRequest("DHL", "C2", null), null);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        Shipment second = activeShipments.get(1);
        // 取消第二个包裹后：有效分配仅行1 的 2 件，剩余 1 件未发行
        when(lineRepository.sumQtyByOrderLine(any())).thenAnswer(inv -> {
            java.util.Collection<?> ids = inv.getArgument(0);
            return ids.contains(second.getId()) ? Map.of(1L, 2, 2L, 1) : Map.of(1L, 2);
        });
        service.cancel(second.getId());
        assertThat(second.getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderEventRecorder).statusChanged(eq(ORDER), eq(OrderStatus.SHIPPED), eq(OrderStatus.PAID),
                eq(OrderActorType.SYSTEM), isNull(), org.mockito.ArgumentMatchers.contains("1 item(s) unshipped"), eq(true));
        // 已 CANCELLED 再取消 → 409909
        assertThatThrownBy(() -> service.cancel(second.getId()))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.SHIPMENT_STATE_INVALID));
    }

    @Test
    @DisplayName("patch/cancel 前置：存在 PROVIDER 事件 → 409909；patch 重复单号 → 409908；patch 成功写事件")
    void patchGuards() {
        allocated(Map.of());
        service.create(ORDER, new ShipmentCreateRequest("DHL", "P1", null), null);
        Shipment s = activeShipments.get(0);
        when(eventRepository.existsProviderEvent(s.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.patch(s.getId(), new ShipmentPatch(null, "P2")))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.SHIPMENT_STATE_INVALID));
        assertThatThrownBy(() -> service.cancel(s.getId()))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.SHIPMENT_STATE_INVALID));
        when(eventRepository.existsProviderEvent(s.getId())).thenReturn(false);
        when(shipmentRepository.updateCarrierAndTracking(eq(s.getId()), any(), any(), eq("DUP"), any()))
                .thenThrow(new DuplicateKeyException("uk_shipment_tracking"));
        assertThatThrownBy(() -> service.patch(s.getId(), new ShipmentPatch(null, "DUP")))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.SHIPMENT_TRACKING_DUPLICATE));
        assertThatThrownBy(() -> service.patch(s.getId(), new ShipmentPatch(null, null)))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.FIELD_VALIDATION_FAILED));
        service.patch(s.getId(), new ShipmentPatch("DHL", "P3"));
        verify(shipmentRepository).updateCarrierAndTracking(eq(s.getId()), eq("DHL"), eq("DHL Express"), eq("P3"),
                org.mockito.ArgumentMatchers.contains("tracking-id=P3"));
        verify(orderEventRecorder).record(eq(ORDER), eq(OrderEventType.SHIPMENT), eq(OrderActorType.ADMIN), eq(9L),
                org.mockito.ArgumentMatchers.contains("updated"), any(), any(), eq(true));
        // 不存在 → 404907
        assertThatThrownBy(() -> service.patch(9999L, new ShipmentPatch(null, "X")))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.SHIPMENT_NOT_FOUND));
    }

    @Test
    @DisplayName("sync：stub provider → null（204）；供应商事件 insertIgnore 去重且状态按时间序推进")
    void syncWithProviderEvents() {
        allocated(Map.of());
        service.create(ORDER, new ShipmentCreateRequest("DHL", "SY", null), null);
        Shipment s = activeShipments.get(0);
        assertThat(service.sync(s.getId())).isNull();

        when(trackingProvider.isStub()).thenReturn(false);
        LocalDateTime t0 = LocalDateTime.of(2026, 9, 1, 10, 0);
        List<TrackingProviderPort.ProviderEvent> events = List.of(
                new TrackingProviderPort.ProviderEvent(null, t0.plusDays(1), ShipmentStatus.OUT_FOR_DELIVERY, "NY", "Out for delivery"),
                new TrackingProviderPort.ProviderEvent("evt-1", t0, ShipmentStatus.IN_TRANSIT, "HK", "Departed"),
                new TrackingProviderPort.ProviderEvent("evt-1", t0, ShipmentStatus.IN_TRANSIT, "HK", "Departed"));
        when(trackingProvider.fetchEvents(any())).thenReturn(events);
        java.util.Set<String> seen = new java.util.HashSet<>();
        when(eventRepository.insertIgnore(any())).thenAnswer(inv -> {
            ShipmentEvent e = inv.getArgument(0);
            return seen.add(e.getProviderEventId()) ? 1 : 0;
        });
        ShipmentDto dto = service.sync(s.getId());
        assertThat(dto.status()).isEqualTo(ShipmentStatus.OUT_FOR_DELIVERY.getKey());
        assertThat(seen).hasSize(2);
        assertThat(seen).anyMatch(id -> id.length() == 40);
        verify(shipmentRepository).markSynced(eq(s.getId()), any());
    }
}
