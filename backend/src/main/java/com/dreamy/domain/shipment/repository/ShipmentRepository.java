package com.dreamy.domain.shipment.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.domain.shipment.entity.Shipment;
import com.dreamy.enums.ShipmentStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/** 包裹仓储（order-flow-complete §2.2 shipment）。 */
@Repository
public class ShipmentRepository {

    private final ShipmentMapper mapper;

    public ShipmentRepository(ShipmentMapper mapper) {
        this.mapper = mapper;
    }

    /** insert（uk 冲突向上抛 DuplicateKeyException，调用方分流 409908 / 幂等键命中） */
    public void insert(Shipment shipment) {
        mapper.insert(shipment);
    }

    public Shipment findById(Long id) {
        return id == null ? null : mapper.selectById(id);
    }

    public Shipment findByIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return mapper.selectOne(new LambdaQueryWrapper<Shipment>().eq(Shipment::getIdempotencyKey, key));
    }

    /** 订单全部包裹（含 CANCELLED，id ASC） */
    public List<Shipment> listByOrderId(Long orderId) {
        return mapper.selectList(new LambdaQueryWrapper<Shipment>()
                .eq(Shipment::getOrderId, orderId)
                .orderByAsc(Shipment::getId));
    }

    /** 订单有效包裹（非 CANCELLED） */
    public List<Shipment> listActiveByOrderId(Long orderId) {
        return mapper.selectList(new LambdaQueryWrapper<Shipment>()
                .eq(Shipment::getOrderId, orderId)
                .ne(Shipment::getStatus, ShipmentStatus.CANCELLED)
                .orderByAsc(Shipment::getId));
    }

    /** 批查（订单列表 has_shipment 派生） */
    public List<Long> listOrderIdsWithActiveShipment(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<Shipment>()
                        .select(Shipment::getOrderId)
                        .in(Shipment::getOrderId, orderIds)
                        .ne(Shipment::getStatus, ShipmentStatus.CANCELLED))
                .stream().map(Shipment::getOrderId).distinct().toList();
    }

    /** 有效包裹的订单 id 集合（后台列表 has_shipment 筛选辅助；批量 IN 由调用方限制） */
    public List<Long> listOrderIdsWithActiveShipment(int limit) {
        return mapper.selectList(new LambdaQueryWrapper<Shipment>()
                        .select(Shipment::getOrderId)
                        .ne(Shipment::getStatus, ShipmentStatus.CANCELLED)
                        .orderByDesc(Shipment::getId)
                        .last("LIMIT " + limit))
                .stream().map(Shipment::getOrderId).distinct().toList();
    }

    /**
     * 状态条件更新（CAS：WHERE id AND status=from；from=null 表示仅排除终态 DELIVERED/CANCELLED）。
     */
    public int casUpdateStatus(Long id, ShipmentStatus from, ShipmentStatus to, Consumer<LambdaUpdateWrapper<Shipment>> extra) {
        LambdaUpdateWrapper<Shipment> uw = new LambdaUpdateWrapper<Shipment>()
                .eq(Shipment::getId, id)
                .set(Shipment::getStatus, to);
        if (from != null) {
            uw.eq(Shipment::getStatus, from);
        } else {
            uw.notIn(Shipment::getStatus, ShipmentStatus.DELIVERED, ShipmentStatus.CANCELLED);
        }
        if (extra != null) {
            extra.accept(uw);
        }
        return mapper.update(null, uw);
    }

    /** 最近事件快照更新（不动 status） */
    public int updateLastEvent(Long id, LocalDateTime lastEventAt, String lastEventDesc) {
        return mapper.update(null, new LambdaUpdateWrapper<Shipment>()
                .eq(Shipment::getId, id)
                .set(Shipment::getLastEventAt, lastEventAt)
                .set(Shipment::getLastEventDesc, lastEventDesc));
    }

    /** PATCH carrier/tracking */
    public int updateCarrierAndTracking(Long id, String carrierCode, String carrierName, String trackingNo,
                                        String trackingUrl) {
        return mapper.update(null, new LambdaUpdateWrapper<Shipment>()
                .eq(Shipment::getId, id)
                .set(Shipment::getCarrierCode, carrierCode)
                .set(Shipment::getCarrierName, carrierName)
                .set(Shipment::getTrackingNo, trackingNo)
                .set(Shipment::getTrackingUrl, trackingUrl));
    }

    public int updateProviderRef(Long id, String providerRef) {
        return mapper.update(null, new LambdaUpdateWrapper<Shipment>()
                .eq(Shipment::getId, id)
                .set(Shipment::getProviderRef, providerRef));
    }

    /** 同步结果：成功清零失败计数并记 synced_at；失败累加 */
    public int markSynced(Long id, LocalDateTime syncedAt) {
        return mapper.update(null, new LambdaUpdateWrapper<Shipment>()
                .eq(Shipment::getId, id)
                .set(Shipment::getSyncedAt, syncedAt)
                .set(Shipment::getSyncFailures, 0));
    }

    public int incrementSyncFailures(Long id) {
        return mapper.update(null, new LambdaUpdateWrapper<Shipment>()
                .eq(Shipment::getId, id)
                .setSql("sync_failures = sync_failures + 1"));
    }

    /** 待同步包裹：PENDING/IN_TRANSIT/OUT_FOR_DELIVERY/EXCEPTION 且 synced_at 早于 cutoff（或从未同步），id ASC 批量 */
    public List<Shipment> listPendingSync(LocalDateTime cutoff, int limit) {
        return mapper.selectList(new LambdaQueryWrapper<Shipment>()
                .in(Shipment::getStatus, ShipmentStatus.PENDING, ShipmentStatus.IN_TRANSIT,
                        ShipmentStatus.OUT_FOR_DELIVERY, ShipmentStatus.EXCEPTION)
                .and(w -> w.isNull(Shipment::getSyncedAt).or().lt(Shipment::getSyncedAt, cutoff))
                .orderByAsc(Shipment::getId)
                .last("LIMIT " + limit));
    }
}
