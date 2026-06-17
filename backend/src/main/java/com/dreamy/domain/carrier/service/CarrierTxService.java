package com.dreamy.domain.carrier.service;

import com.dreamy.domain.carrier.entity.Carrier;
import java.time.LocalDateTime;
import com.dreamy.domain.carrier.repository.CarrierRepository;
import com.dreamy.enums.CarrierStatus;
import com.dreamy.dto.ShippingDtos.CarrierDto;
import com.dreamy.error.ShippingErrorCode;
import com.dreamy.error.ShippingException;
import com.dreamy.infra.ShippingAfterCommitRunner;
import com.dreamy.infra.ShippingAuditRecorder;
import com.dreamy.infra.ShippingCacheService;
import com.dreamy.support.ShippingValidation.ValidCarrier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 承运方写事务服务（TX-SHP-001~004）。
 * 所有方法须在 CarrierAdminService 的 EC-SHP-001 分布式锁内调用（DEC-SHP-4：
 * 锁 → 事务 → guard → 写 → 提交 → 释放锁；锁外开启事务会破坏 guard 原子性）。
 * operation_log 与业务写同事务（BE-DIM-7，业务失败审计不留痕）；
 * 缓存失效经 ShippingAfterCommitRunner 在事务提交后执行（CP-031）。
 */
@Service
public class CarrierTxService {

    private static final Logger log = LoggerFactory.getLogger(CarrierTxService.class);

    private final CarrierRepository carrierRepository;
    private final ShippingAuditRecorder audit;
    private final ShippingAfterCommitRunner afterCommit;
    private final ShippingCacheService cache;
    private final ObjectMapper objectMapper;

    public CarrierTxService(CarrierRepository carrierRepository, ShippingAuditRecorder audit,
                            ShippingAfterCommitRunner afterCommit, ShippingCacheService cache,
                            ObjectMapper objectMapper) {
        this.carrierRepository = carrierRepository;
        this.audit = audit;
        this.afterCommit = afterCommit;
        this.cache = cache;
        this.objectMapper = objectMapper;
    }

    /** TX-SHP-001（E-SHP-02）：INSERT carrier + operation_log，原子；提交后失效 shipping:carriers */
    @Transactional
    public CarrierDto create(ValidCarrier valid) {
        // STEP-SHP-01 INSERT（RM-SHP-004）
        Carrier carrier = new Carrier();
        carrier.setName(valid.name());
        carrier.setZones(valid.zones());
        carrier.setLeadTime(valid.leadTime());
        carrier.setStatus(valid.status());
        carrierRepository.insert(carrier);
        CarrierDto after = toDto(carrier);
        // STEP-SHP-02 审计（action=创建承运方，changes.after=载荷）
        audit.record("创建承运方", carrier.getName(), changesJson(null, after));
        // STEP-SHP-03 提交后失效（CACHE-SHP-001）
        afterCommit.run(cache::invalidateCarriers);
        return after;
    }

    /** TX-SHP-002（E-SHP-03）：findById → 409902 guard → updateAll + operation_log；提交后失效 */
    @Transactional
    public CarrierDto update(Long id, ValidCarrier valid) {
        // STEP-SHP-01 findById（RM-SHP-002）→ 404901
        Carrier existing = carrierRepository.findById(id);
        if (existing == null) {
            throw new ShippingException(ShippingErrorCode.CARRIER_NOT_FOUND);
        }
        CarrierDto before = toDto(existing);
        // STEP-SHP-02 最后启用 guard：现行 enabled 且提交 disabled 且 countEnabled()==1 → 409902
        if (existing.getStatus() == CarrierStatus.ENABLED && valid.status() == CarrierStatus.DISABLED) {
            CarrierStatusMachine.assertNotLastEnabled(carrierRepository.countEnabled());
        }
        // STEP-SHP-03 整单覆盖（RM-SHP-005）；name 变更不联动规则行（DEC-SHP-2，孤行回退兜底）
        existing.setName(valid.name());
        existing.setZones(valid.zones());
        existing.setLeadTime(valid.leadTime());
        existing.setStatus(valid.status());
        carrierRepository.updateAll(existing);
        CarrierDto after = toDto(existing);
        // STEP-SHP-04 审计（action=编辑承运方，changes before/after）
        audit.record("编辑承运方", existing.getName(), changesJson(before, after));
        // STEP-SHP-05 提交后失效
        afterCommit.run(cache::invalidateCarriers);
        return after;
    }

    /** TX-SHP-003（E-SHP-04）：findById → 409902 guard → deleteById + operation_log；提交后失效 */
    @Transactional
    public void delete(Long id) {
        // STEP-SHP-01 findById → 404901
        Carrier existing = carrierRepository.findById(id);
        if (existing == null) {
            throw new ShippingException(ShippingErrorCode.CARRIER_NOT_FOUND);
        }
        // STEP-SHP-02 最后启用 guard（disabled 行可直接删）
        if (existing.getStatus() == CarrierStatus.ENABLED) {
            CarrierStatusMachine.assertNotLastEnabled(carrierRepository.countEnabled());
        }
        // STEP-SHP-03 DELETE（RM-SHP-007）。Order.carrier 为字段快照不受影响（不强外键）；
        // 指向该承运商的「<区域> / <名>」规则行成为非匹配行，由运营在 /shipping 页面清理
        // 逻辑删除：设置 deleted_at = now()
        carrierRepository.markDeleted(id);
        // STEP-SHP-04 审计（action=删除承运方）
        audit.record("删除承运方", existing.getName(), changesJson(toDto(existing), null));
        // STEP-SHP-05 提交后失效
        afterCommit.run(cache::invalidateCarriers);
    }

    /** TX-SHP-004（E-SHP-05）：findById → 幂等短路 → 状态机 guard → updateStatus + operation_log；提交后失效 */
    @Transactional
    public CarrierDto toggleStatus(Long id, CarrierStatus target) {
        // STEP-SHP-01 findById → 404901
        Carrier existing = carrierRepository.findById(id);
        if (existing == null) {
            throw new ShippingException(ShippingErrorCode.CARRIER_NOT_FOUND);
        }
        // STEP-SHP-02 幂等短路 + STEP-SHP-03 迁移 guard（TASK-050：CarrierStatusMachine）
        CarrierStatusMachine.Transition transition =
                CarrierStatusMachine.evaluate(existing.getStatus(), target, carrierRepository.countEnabled());
        if (transition == CarrierStatusMachine.Transition.NOOP) {
            // 同值：直接返回现行（无写库、无审计、无失效）
            return toDto(existing);
        }
        CarrierStatus before = existing.getStatus();
        // STEP-SHP-04 UPDATE status（RM-SHP-006）
        carrierRepository.updateStatus(id, target);
        existing.setStatus(target);
        // STEP-SHP-05 审计（action=承运方状态变更，changes:{status: before→after}）
        audit.record("承运方状态变更", existing.getName(),
                changesJson(Map.of("status", before.getKey()), Map.of("status", target.getKey())));
        // STEP-SHP-06 提交后失效
        afterCommit.run(cache::invalidateCarriers);
        return toDto(existing);
    }

    /** MAP-SHP-001 */
    static CarrierDto toDto(Carrier carrier) {
        return new CarrierDto(carrier.getId(), carrier.getName(), carrier.getZones(),
                carrier.getLeadTime(), carrier.getStatus() == null ? null : carrier.getStatus().getKey());
    }

    /** changes before/after JSON（BE-DIM-7） */
    private String changesJson(Object before, Object after) {
        try {
            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put("before", before);
            changes.put("after", after);
            return objectMapper.writeValueAsString(changes);
        } catch (Exception ex) {
            log.warn("[AUDIT-SHP] changes serialize failed", ex);
            return null;
        }
    }
}
