package com.dreamy.shipping.domain.carrier.service;

import com.dreamy.shipping.domain.carrier.repository.CarrierRepository;
import com.dreamy.shipping.dto.ShippingDtos.CarrierDto;
import com.dreamy.shipping.dto.ShippingDtos.CarrierUpsert;
import com.dreamy.shipping.support.ShippingValidation;
import huihao.redis.IdLockSupport;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 承运方后台应用服务（E-SHP-01~05）。
 * EC-SHP-001（DEC-SHP-4）：carrier 写路径统一包裹 huihao-redis 分布式锁
 * onIdLock("shipping:carrier-write")——全局单 key 串行（等待 3s 超时 → 通用 50000 语义
 * 由 identity GlobalExceptionHandler 兜底）。409902 是计数型不变量（enabled >= 1），
 * 并发双禁用/禁用+删除竞态下纯乐观校验会击穿；表仅个位数行，串行化零性能代价（CP-012 同范式）。
 * 顺序固定：锁 → 事务（CarrierTxService）→ guard → 写 → 提交 → 释放锁 → 失效缓存。
 */
@Service
public class CarrierAdminService implements IdLockSupport {

    /** EC-SHP-001 全局单 key（无 id 维度，写写互斥） */
    private static final String CARRIER_WRITE_LOCK = "shipping:carrier-write";
    private static final String CARRIER_WRITE_LOCK_ID = "global";
    /** DEC-SHP-4 锁等待 3s */
    private static final long LOCK_WAIT_SECONDS = 3L;

    private final CarrierRepository carrierRepository;
    private final CarrierTxService txService;
    private final RedissonClient redissonClient;

    public CarrierAdminService(CarrierRepository carrierRepository, CarrierTxService txService,
                               RedissonClient redissonClient) {
        this.carrierRepository = carrierRepository;
        this.txService = txService;
        this.redissonClient = redissonClient;
    }

    @Override
    public RedissonClient getRedissonClient() {
        return redissonClient;
    }

    /** E-SHP-01 listAdminCarriers：STEP-SHP-01 全量 ORDER BY id ASC（RM-SHP-001，不分页不缓存） */
    public List<CarrierDto> list() {
        return carrierRepository.listAll().stream().map(CarrierTxService::toDto).toList();
    }

    /** E-SHP-02 createAdminCarrier（V-SHP-003~006 → TX-SHP-001 锁内） */
    public CarrierDto create(CarrierUpsert req) {
        ShippingValidation.ValidCarrier valid = ShippingValidation.validateCarrier(req);
        return locked(() -> txService.create(valid));
    }

    /** E-SHP-03 updateAdminCarrier（V-SHP-002/007 → TX-SHP-002 锁内） */
    public CarrierDto update(String rawId, CarrierUpsert req) {
        Long id = ShippingValidation.parseId(rawId);
        ShippingValidation.ValidCarrier valid = ShippingValidation.validateCarrier(req);
        return locked(() -> txService.update(id, valid));
    }

    /** E-SHP-04 deleteAdminCarrier（V-SHP-002 → TX-SHP-003 锁内） */
    public void delete(String rawId) {
        Long id = ShippingValidation.parseId(rawId);
        locked(() -> {
            txService.delete(id);
            return null;
        });
    }

    /** E-SHP-05 toggleAdminCarrierStatus（V-SHP-002/008 → TX-SHP-004 锁内） */
    public CarrierDto toggleStatus(String rawId, String status) {
        Long id = ShippingValidation.parseId(rawId);
        var target = ShippingValidation.validateStatus(status);
        return locked(() -> txService.toggleStatus(id, target));
    }

    private <T> T locked(java.util.function.Supplier<T> action) {
        return onIdLock(CARRIER_WRITE_LOCK, CARRIER_WRITE_LOCK_ID, action, LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
    }
}
