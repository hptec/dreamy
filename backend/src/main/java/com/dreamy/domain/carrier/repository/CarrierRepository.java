package com.dreamy.domain.carrier.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.domain.carrier.entity.Carrier;
import com.dreamy.enums.CarrierStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 承运方仓储（RM-SHP-001~008）。
 * L2 TRACE: shipping-data-detail §1 CarrierRepository。
 */
@Repository
public class CarrierRepository {

    private final CarrierMapper carrierMapper;

    public CarrierRepository(CarrierMapper carrierMapper) {
        this.carrierMapper = carrierMapper;
    }

    /** RM-SHP-001 listAll —— ORDER BY id ASC（E-SHP-01，实时不缓存） */
    public List<Carrier> listAll() {
        return carrierMapper.selectList(new LambdaQueryWrapper<Carrier>()
                .isNull(Carrier::getDeletedAt)
                .orderByAsc(Carrier::getId));
    }

    /** RM-SHP-002 findById —— 404901 判定点 */
    public Carrier findById(Long id) {
        Carrier e = id == null ? null : carrierMapper.selectById(id);
        return (e == null || e.getDeletedAt() != null) ? null : e;
    }

    /** RM-SHP-003 countEnabled —— 409902 guard（仅在 EC-SHP-001 锁内调用，计数读写串行无竞态） */
    public long countEnabled() {
        Long count = carrierMapper.selectCount(new LambdaQueryWrapper<Carrier>()
                .isNull(Carrier::getDeletedAt)
                .eq(Carrier::getStatus, CarrierStatus.ENABLED));
        return count == null ? 0 : count;
    }

    /** RM-SHP-004 insert —— E-SHP-02 */
    public void insert(Carrier carrier) {
        carrierMapper.insert(carrier);
    }

    /** RM-SHP-005 updateAll —— E-SHP-03 整单覆盖（name/zones/lead_time/status 全列 SET，null 即清空） */
    public void updateAll(Carrier carrier) {
        carrierMapper.update(null, new LambdaUpdateWrapper<Carrier>()
                .set(Carrier::getName, carrier.getName())
                .set(Carrier::getZones, carrier.getZones())
                .set(Carrier::getLeadTime, carrier.getLeadTime())
                .set(Carrier::getStatus, carrier.getStatus())
                .eq(Carrier::getId, carrier.getId()));
    }

    /** RM-SHP-006 updateStatus —— E-SHP-05 */
    public int updateStatus(Long id, CarrierStatus status) {
        return carrierMapper.update(null, new LambdaUpdateWrapper<Carrier>()
                .set(Carrier::getStatus, status)
                .eq(Carrier::getId, id));
    }

    /** RM-SHP-007 deleteById —— E-SHP-04 */
    public int deleteById(Long id) {
        return carrierMapper.deleteById(id);
    }

    /** RM-SHP-008 listEnabled —— WHERE status='enabled' ORDER BY id ASC（SVC-SHP-01 报价数据源，CACHE-SHP-001 回源方法） */
    public List<Carrier> listEnabled() {
        return carrierMapper.selectList(new LambdaQueryWrapper<Carrier>()
                .isNull(Carrier::getDeletedAt)
                .eq(Carrier::getStatus, CarrierStatus.ENABLED)
                .orderByAsc(Carrier::getId));
    }

    /** 逻辑删除：设置 deleted_at = now() */
    public void markDeleted(Long id) {
        Carrier patch = new Carrier();
        patch.setId(id);
        patch.setDeletedAt(LocalDateTime.now());
        carrierMapper.updateById(patch);
    }
}
