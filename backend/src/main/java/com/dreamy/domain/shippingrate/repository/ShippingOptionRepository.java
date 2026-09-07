package com.dreamy.domain.shippingrate.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.domain.shippingrate.entity.ShippingOption;
import com.dreamy.enums.ShippingServiceLevel;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 运费选项仓储（order-flow-complete §2.3 shipping_option）。 */
@Repository
public class ShippingOptionRepository {

    private final ShippingOptionMapper mapper;

    public ShippingOptionRepository(ShippingOptionMapper mapper) {
        this.mapper = mapper;
    }

    /** 全量 ORDER BY zone, carrier_code, service_level, id */
    public List<ShippingOption> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<ShippingOption>()
                .orderByAsc(ShippingOption::getZone)
                .orderByAsc(ShippingOption::getCarrierCode)
                .orderByAsc(ShippingOption::getServiceLevel)
                .orderByAsc(ShippingOption::getId));
    }

    /** 仅启用行（报价缓存回源） */
    public List<ShippingOption> listEnabled() {
        return mapper.selectList(new LambdaQueryWrapper<ShippingOption>()
                .eq(ShippingOption::getEnabled, true)
                .orderByAsc(ShippingOption::getId));
    }

    public ShippingOption findById(Long id) {
        return id == null ? null : mapper.selectById(id);
    }

    public boolean existsByKey(String zone, String carrierCode, ShippingServiceLevel level, Long excludeId) {
        LambdaQueryWrapper<ShippingOption> qw = new LambdaQueryWrapper<ShippingOption>()
                .eq(ShippingOption::getZone, zone)
                .eq(ShippingOption::getCarrierCode, carrierCode)
                .eq(ShippingOption::getServiceLevel, level);
        if (excludeId != null) {
            qw.ne(ShippingOption::getId, excludeId);
        }
        Long count = mapper.selectCount(qw);
        return count != null && count > 0;
    }

    public long count() {
        Long count = mapper.selectCount(null);
        return count == null ? 0 : count;
    }

    public void insert(ShippingOption option) {
        mapper.insert(option);
    }

    /** 整单覆盖（null 即清空费用字段） */
    public void updateAll(ShippingOption option) {
        mapper.update(null, new LambdaUpdateWrapper<ShippingOption>()
                .set(ShippingOption::getZone, option.getZone())
                .set(ShippingOption::getCarrierCode, option.getCarrierCode())
                .set(ShippingOption::getServiceLevel, option.getServiceLevel())
                .set(ShippingOption::getFeeUnder, option.getFeeUnder())
                .set(ShippingOption::getFeeOver, option.getFeeOver())
                .set(ShippingOption::getThreshold, option.getThreshold())
                .set(ShippingOption::getTransitDaysMin, option.getTransitDaysMin())
                .set(ShippingOption::getTransitDaysMax, option.getTransitDaysMax())
                .set(ShippingOption::getEnabled, option.getEnabled())
                .eq(ShippingOption::getId, option.getId()));
    }

    public int updateEnabled(Long id, boolean enabled) {
        return mapper.update(null, new LambdaUpdateWrapper<ShippingOption>()
                .set(ShippingOption::getEnabled, enabled)
                .eq(ShippingOption::getId, id));
    }

    public int deleteById(Long id) {
        return mapper.deleteById(id);
    }
}
