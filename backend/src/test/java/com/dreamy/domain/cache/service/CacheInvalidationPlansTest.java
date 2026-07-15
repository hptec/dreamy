package com.dreamy.domain.cache.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheInvalidationPlansTest {

    @Test
    void everyHomepageDataSourceAlsoInvalidatesHomeAggregate() {
        assertThat(CacheInvalidationPlans.PRODUCT_FULL).contains(CacheInvalidationTarget.SITE_HOME);
        assertThat(CacheInvalidationPlans.PRODUCT_FLAGS).contains(CacheInvalidationTarget.SITE_HOME);
        assertThat(CacheInvalidationPlans.PRODUCT_SALES).contains(CacheInvalidationTarget.SITE_HOME);
        assertThat(CacheInvalidationPlans.COLLECTION).contains(CacheInvalidationTarget.SITE_HOME);
        assertThat(CacheInvalidationPlans.CATEGORY_CREATE).contains(CacheInvalidationTarget.SITE_HOME);
        assertThat(CacheInvalidationPlans.CATEGORY_UPDATE).contains(CacheInvalidationTarget.SITE_HOME);
        assertThat(CacheInvalidationPlans.CATEGORY_DELETE).contains(CacheInvalidationTarget.SITE_HOME);
        assertThat(CacheInvalidationPlans.BANNER).contains(CacheInvalidationTarget.SITE_HOME);
        assertThat(CacheInvalidationPlans.WEDDING).contains(CacheInvalidationTarget.SITE_HOME);
    }
}
