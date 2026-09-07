package com.dreamy.domain.shipment.service;

import com.dreamy.domain.order.entity.OrderLine;
import com.dreamy.domain.order.repository.OrderLineRepository;
import com.dreamy.domain.shipment.entity.Shipment;
import com.dreamy.domain.shipment.entity.ShipmentEvent;
import com.dreamy.domain.shipment.entity.ShipmentLine;
import com.dreamy.domain.shipment.repository.ShipmentEventRepository;
import com.dreamy.domain.shipment.repository.ShipmentLineRepository;
import com.dreamy.domain.shipment.repository.ShipmentRepository;
import com.dreamy.dto.TradingDtos.ShipmentDto;
import com.dreamy.dto.TradingDtos.ShipmentEventDto;
import com.dreamy.dto.TradingDtos.ShipmentLineDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 包裹只读装配（order-flow-complete：Store/Admin 订单详情 shipments[] + 游客查单共用；防 N+1：三表各一次批查）。
 */
@Service
public class ShipmentQueryService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentLineRepository lineRepository;
    private final ShipmentEventRepository eventRepository;
    private final OrderLineRepository orderLineRepository;

    public ShipmentQueryService(ShipmentRepository shipmentRepository, ShipmentLineRepository lineRepository,
                                ShipmentEventRepository eventRepository, OrderLineRepository orderLineRepository) {
        this.shipmentRepository = shipmentRepository;
        this.lineRepository = lineRepository;
        this.eventRepository = eventRepository;
        this.orderLineRepository = orderLineRepository;
    }

    /** 订单全部包裹（含 CANCELLED，前端按 status 区分展示） */
    public List<ShipmentDto> listByOrder(Long orderId) {
        List<Shipment> shipments = shipmentRepository.listByOrderId(orderId);
        if (shipments.isEmpty()) {
            return List.of();
        }
        List<OrderLine> orderLines = orderLineRepository.listByOrderId(orderId);
        return assemble(shipments, orderLines);
    }

    public ShipmentDto get(Shipment shipment) {
        List<OrderLine> orderLines = orderLineRepository.listByOrderId(shipment.getOrderId());
        return assemble(List.of(shipment), orderLines).get(0);
    }

    List<ShipmentDto> assemble(List<Shipment> shipments, List<OrderLine> orderLines) {
        List<Long> ids = shipments.stream().map(Shipment::getId).toList();
        Map<Long, OrderLine> lineIndex = new HashMap<>();
        for (OrderLine line : orderLines) {
            lineIndex.put(line.getId(), line);
        }
        Map<Long, List<ShipmentLineDto>> linesBy = new HashMap<>();
        for (ShipmentLine sl : lineRepository.listByShipmentIds(ids)) {
            OrderLine ol = lineIndex.get(sl.getOrderLineId());
            linesBy.computeIfAbsent(sl.getShipmentId(), k -> new ArrayList<>()).add(new ShipmentLineDto(
                    sl.getOrderLineId(), ol == null ? null : ol.getProductName(), ol == null ? null : ol.getSkuCode(),
                    ol == null ? null : ol.getColor(), ol == null ? null : ol.getSize(), sl.getQty()));
        }
        Map<Long, List<ShipmentEventDto>> eventsBy = new HashMap<>();
        for (ShipmentEvent e : eventRepository.listByShipmentIds(ids)) {
            eventsBy.computeIfAbsent(e.getShipmentId(), k -> new ArrayList<>()).add(new ShipmentEventDto(e.getId(),
                    e.getOccurredAt(), e.getStatus() == null ? null : e.getStatus().getKey(), e.getLocation(),
                    e.getDescription(), e.getSource() == null ? null : e.getSource().getKey()));
        }
        List<ShipmentDto> result = new ArrayList<>(shipments.size());
        for (Shipment s : shipments) {
            result.add(toDto(s, linesBy.getOrDefault(s.getId(), List.of()), eventsBy.getOrDefault(s.getId(), List.of())));
        }
        return result;
    }

    static ShipmentDto toDto(Shipment s, List<ShipmentLineDto> lines, List<ShipmentEventDto> events) {
        return new ShipmentDto(s.getId(), s.getShipmentNo(), s.getCarrierCode(), s.getCarrierName(), s.getTrackingNo(),
                s.getTrackingUrl(), s.getStatus() == null ? null : s.getStatus().getKey(), s.getShippedAt(),
                s.getDeliveredAt(), s.getLastEventAt(), s.getLastEventDesc(), lines, events);
    }
}
