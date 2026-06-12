package com.dreamy.domain.shippingrate.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.domain.shippingrate.entity.ShippingRate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 运费规则行仓储（RM-SHP-010~015）。
 * L2 TRACE: shipping-data-detail §1 ShippingRateRepository。
 */
@Repository
public class ShippingRateRepository {

    private final ShippingRateMapper rateMapper;

    public ShippingRateRepository(ShippingRateMapper rateMapper) {
        this.rateMapper = rateMapper;
    }

    /** RM-SHP-010 listAll —— ORDER BY id ASC（E-SHP-06 实时；SVC-SHP-01 经 CACHE-SHP-002 读同方法） */
    public List<ShippingRate> listAll() {
        return rateMapper.selectList(new LambdaQueryWrapper<ShippingRate>().orderByAsc(ShippingRate::getId));
    }

    /** RM-SHP-011 findById —— 404902 判定点 */
    public ShippingRate findById(Long id) {
        return id == null ? null : rateMapper.selectById(id);
    }

    /**
     * RM-SHP-012 existsByZoneNormalized —— 409901 判重（入参已按 DEC-SHP-1 规范化；
     * utf8mb4_0900_ai_ci 排序规则比较天然忽略大小写，uk_shipping_rate_zone 兜底并发竞态）。
     */
    public boolean existsByZoneNormalized(String zoneNorm, Long excludeId) {
        LambdaQueryWrapper<ShippingRate> qw = new LambdaQueryWrapper<ShippingRate>()
                .eq(ShippingRate::getZone, zoneNorm);
        if (excludeId != null) {
            qw.ne(ShippingRate::getId, excludeId);
        }
        Long count = rateMapper.selectCount(qw);
        return count != null && count > 0;
    }

    /** RM-SHP-013 insert —— E-SHP-07（uk 冲突向上抛 → 409901） */
    public void insert(ShippingRate rate) {
        rateMapper.insert(rate);
    }

    /** RM-SHP-014 updateAll —— E-SHP-08 整单覆盖（提交 null 即清空费用字段，DEC-SHP-3） */
    public void updateAll(ShippingRate rate) {
        rateMapper.update(null, new LambdaUpdateWrapper<ShippingRate>()
                .set(ShippingRate::getZone, rate.getZone())
                .set(ShippingRate::getFeeUnder, rate.getFeeUnder())
                .set(ShippingRate::getFeeOver, rate.getFeeOver())
                .set(ShippingRate::getThreshold, rate.getThreshold())
                .eq(ShippingRate::getId, rate.getId()));
    }

    /** RM-SHP-015 deleteById —— E-SHP-09（affected=0 → 404902） */
    public int deleteById(Long id) {
        return rateMapper.deleteById(id);
    }
}
