package com.dreamy.domain.tax.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.domain.tax.entity.TaxRule;
import com.dreamy.enums.TaxType;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 税率规则仓储（order-flow-complete F）。 */
@Repository
public class TaxRuleRepository {

    private final TaxRuleMapper mapper;

    public TaxRuleRepository(TaxRuleMapper mapper) {
        this.mapper = mapper;
    }

    public List<TaxRule> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<TaxRule>()
                .orderByAsc(TaxRule::getCountryCode)
                .orderByAsc(TaxRule::getRegion)
                .orderByAsc(TaxRule::getTaxType)
                .orderByAsc(TaxRule::getId));
    }

    public List<TaxRule> listByCountry(String countryCode) {
        return mapper.selectList(new LambdaQueryWrapper<TaxRule>()
                .eq(TaxRule::getCountryCode, countryCode)
                .orderByAsc(TaxRule::getId));
    }

    /** 启用规则全量（缓存回源） */
    public List<TaxRule> listEnabled() {
        return mapper.selectList(new LambdaQueryWrapper<TaxRule>()
                .eq(TaxRule::getEnabled, true)
                .orderByAsc(TaxRule::getId));
    }

    public TaxRule findById(Long id) {
        return id == null ? null : mapper.selectById(id);
    }

    /** 同 key（country, region, tax_type）的其他行（重叠判定输入；排除自身） */
    public List<TaxRule> listByKey(String countryCode, String region, TaxType taxType, Long excludeId) {
        LambdaQueryWrapper<TaxRule> qw = new LambdaQueryWrapper<TaxRule>()
                .eq(TaxRule::getCountryCode, countryCode)
                .eq(TaxRule::getRegion, region)
                .eq(TaxRule::getTaxType, taxType);
        if (excludeId != null) {
            qw.ne(TaxRule::getId, excludeId);
        }
        return mapper.selectList(qw);
    }

    public long count() {
        Long count = mapper.selectCount(null);
        return count == null ? 0 : count;
    }

    public void insert(TaxRule rule) {
        mapper.insert(rule);
    }

    public void updateAll(TaxRule rule) {
        mapper.update(null, new LambdaUpdateWrapper<TaxRule>()
                .set(TaxRule::getCountryCode, rule.getCountryCode())
                .set(TaxRule::getRegion, rule.getRegion())
                .set(TaxRule::getTaxType, rule.getTaxType())
                .set(TaxRule::getRateScaled, rule.getRateScaled())
                .set(TaxRule::getAppliesToShipping, rule.getAppliesToShipping())
                .set(TaxRule::getThresholdUsd, rule.getThresholdUsd())
                .set(TaxRule::getEffectiveFrom, rule.getEffectiveFrom())
                .set(TaxRule::getEffectiveTo, rule.getEffectiveTo())
                .set(TaxRule::getEnabled, rule.getEnabled())
                .set(TaxRule::getLabel, rule.getLabel())
                .eq(TaxRule::getId, rule.getId()));
    }

    public int updateEnabled(Long id, boolean enabled) {
        return mapper.update(null, new LambdaUpdateWrapper<TaxRule>()
                .set(TaxRule::getEnabled, enabled)
                .eq(TaxRule::getId, id));
    }

    public int deleteById(Long id) {
        return mapper.deleteById(id);
    }
}
