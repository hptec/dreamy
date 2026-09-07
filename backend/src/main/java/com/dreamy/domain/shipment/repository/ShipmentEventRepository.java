package com.dreamy.domain.shipment.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.shipment.entity.ShipmentEvent;
import com.dreamy.enums.ShipmentEventSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/** 包裹轨迹仓储（PROVIDER 行 insertIgnore 以 uk_shipment_event_provider 冲突捕获实现）。 */
@Repository
public class ShipmentEventRepository {

    private final ShipmentEventMapper mapper;

    public ShipmentEventRepository(ShipmentEventMapper mapper) {
        this.mapper = mapper;
    }

    public void insert(ShipmentEvent event) {
        mapper.insert(event);
    }

    /** affected=0 = 同供应商事件已存在 */
    public int insertIgnore(ShipmentEvent event) {
        try {
            return mapper.insert(event);
        } catch (DuplicateKeyException ex) {
            return 0;
        }
    }

    public List<ShipmentEvent> listByShipmentIds(Collection<Long> shipmentIds) {
        if (shipmentIds == null || shipmentIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<ShipmentEvent>()
                .in(ShipmentEvent::getShipmentId, shipmentIds)
                .orderByAsc(ShipmentEvent::getOccurredAt)
                .orderByAsc(ShipmentEvent::getId));
    }

    public boolean existsProviderEvent(Long shipmentId) {
        Long count = mapper.selectCount(new LambdaQueryWrapper<ShipmentEvent>()
                .eq(ShipmentEvent::getShipmentId, shipmentId)
                .eq(ShipmentEvent::getSource, ShipmentEventSource.PROVIDER));
        return count != null && count > 0;
    }
}
