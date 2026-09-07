package com.dreamy.domain.tax.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.domain.tax.entity.TaxDestinationPolicy;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 目的国政策仓储（order-flow-complete F）。 */
@Repository
public class TaxDestinationPolicyRepository {

    private final TaxDestinationPolicyMapper mapper;

    public TaxDestinationPolicyRepository(TaxDestinationPolicyMapper mapper) {
        this.mapper = mapper;
    }

    public List<TaxDestinationPolicy> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<TaxDestinationPolicy>()
                .orderByAsc(TaxDestinationPolicy::getCountryCode));
    }

    public TaxDestinationPolicy findByCountry(String countryCode) {
        if (countryCode == null) {
            return null;
        }
        return mapper.selectOne(new LambdaQueryWrapper<TaxDestinationPolicy>()
                .eq(TaxDestinationPolicy::getCountryCode, countryCode));
    }

    public long count() {
        Long count = mapper.selectCount(null);
        return count == null ? 0 : count;
    }

    public void insert(TaxDestinationPolicy policy) {
        mapper.insert(policy);
    }

    public void updateAll(TaxDestinationPolicy policy) {
        mapper.update(null, new LambdaUpdateWrapper<TaxDestinationPolicy>()
                .set(TaxDestinationPolicy::getIncoterm, policy.getIncoterm())
                .set(TaxDestinationPolicy::getDutiesNotice, policy.getDutiesNotice())
                .set(TaxDestinationPolicy::getNoticeText, policy.getNoticeText())
                .eq(TaxDestinationPolicy::getId, policy.getId()));
    }
}
