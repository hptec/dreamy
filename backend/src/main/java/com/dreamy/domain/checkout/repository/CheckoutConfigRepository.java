package com.dreamy.domain.checkout.repository;

import com.dreamy.domain.checkout.entity.CheckoutConfig;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

/**
 * 结算配置单例仓储（RM-TRD-090/091；id=1 种子行——TradingSeedInitializer 保障存在）。
 */
@Repository
public class CheckoutConfigRepository {

    private final CheckoutConfigMapper mapper;

    public CheckoutConfigRepository(CheckoutConfigMapper mapper) {
        this.mapper = mapper;
    }

    /** RM-TRD-090 getSingleton（缺行回退决策默认值——防御性兜底，正常由种子保障） */
    public CheckoutConfig getSingleton() {
        CheckoutConfig config = mapper.selectById(CheckoutConfig.SINGLETON_ID);
        if (config == null) {
            config = new CheckoutConfig();
            config.setId(CheckoutConfig.SINGLETON_ID);
            config.setGiftWrapFeeUsd(new BigDecimal("15.00"));
            config.setCustomRefundGraceHours(24);
        }
        return config;
    }

    /** RM-TRD-091 update（TX-TRD-012） */
    public void update(CheckoutConfig config) {
        config.setId(CheckoutConfig.SINGLETON_ID);
        mapper.updateById(config);
    }

    /** 种子插入 */
    public void insert(CheckoutConfig config) {
        mapper.insert(config);
    }

    public boolean exists() {
        return mapper.selectById(CheckoutConfig.SINGLETON_ID) != null;
    }
}
