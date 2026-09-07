package com.dreamy.config;

import com.dreamy.domain.address.entity.Address;
import com.dreamy.domain.address.repository.AddressRepository;
import com.dreamy.support.CountryCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 地址 country_code / region_code 启动回填（order-flow-complete §2.3 address）：
 * country_code 为空的行按 country 名/别名解析（CountryCatalog）；US/CA/AU 按 state（全称/缩写/别名）规范化 region_code；
 * 无法解析留空（前台强制补选）。keyset 分批 500，幂等（只更新仍为空的行）。
 */
@Component
@Order(36)
public class AddressCountryCodeBackfillInitializer {

    private static final Logger log = LoggerFactory.getLogger(AddressCountryCodeBackfillInitializer.class);
    static final int BATCH = 500;

    private final AddressRepository addressRepository;

    public AddressCountryCodeBackfillInitializer(AddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        int[] result = backfill();
        if (result[0] > 0 || result[1] > 0) {
            log.info("[ADDRESS-BACKFILL] country_code backfilled={} unresolved={}", result[0], result[1]);
        }
    }

    /** 返回 {updated, unresolved} */
    int[] backfill() {
        int updated = 0;
        int unresolved = 0;
        long lastId = 0L;
        while (true) {
            List<Address> batch = addressRepository.listMissingCountryCodeAfterId(lastId, BATCH);
            if (batch.isEmpty()) {
                break;
            }
            for (Address address : batch) {
                lastId = address.getId();
                String code = CountryCatalog.resolveCode(address.getCountry());
                if (code == null) {
                    unresolved++;
                    continue;
                }
                String region = CountryCatalog.hasRegions(code)
                        ? CountryCatalog.resolveRegionCode(code, address.getState()) : null;
                updated += addressRepository.backfillCodes(address.getId(), code, region);
            }
            if (batch.size() < BATCH) {
                break;
            }
        }
        return new int[]{updated, unresolved};
    }
}
