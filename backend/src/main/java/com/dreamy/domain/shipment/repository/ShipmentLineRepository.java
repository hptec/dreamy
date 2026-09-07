package com.dreamy.domain.shipment.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.shipment.entity.ShipmentLine;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 包裹分配行仓储。 */
@Repository
public class ShipmentLineRepository {

    private final ShipmentLineMapper mapper;

    public ShipmentLineRepository(ShipmentLineMapper mapper) {
        this.mapper = mapper;
    }

    public void batchInsert(List<ShipmentLine> lines) {
        for (ShipmentLine line : lines) {
            mapper.insert(line);
        }
    }

    public List<ShipmentLine> listByShipmentIds(Collection<Long> shipmentIds) {
        if (shipmentIds == null || shipmentIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<ShipmentLine>()
                .in(ShipmentLine::getShipmentId, shipmentIds)
                .orderByAsc(ShipmentLine::getId));
    }

    /** 已分配数量 ∑ per order_line_id（仅传入的有效包裹 id；内存聚合） */
    public Map<Long, Integer> sumQtyByOrderLine(Collection<Long> activeShipmentIds) {
        Map<Long, Integer> result = new HashMap<>();
        for (ShipmentLine line : listByShipmentIds(activeShipmentIds)) {
            result.merge(line.getOrderLineId(), line.getQty() == null ? 0 : line.getQty(), Integer::sum);
        }
        return result;
    }
}
