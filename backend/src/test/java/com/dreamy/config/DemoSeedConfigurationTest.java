package com.dreamy.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("演示数据默认关闭")
class DemoSeedConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DemoSeedCandidates.class);

    @Test
    void businessDemoSeedsAreAbsentUnlessExplicitlyEnabled() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(CatalogSeedInitializer.class);
            assertThat(context).doesNotHaveBean(MarketingSeedInitializer.class);
            assertThat(context).doesNotHaveBean(ReviewSeedInitializer.class);
            assertThat(context).doesNotHaveBean(ShippingSeedInitializer.class);
            assertThat(context).doesNotHaveBean(ShowroomSeedInitializer.class);
            assertThat(context).doesNotHaveBean(SiteBuilderDataSeed.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            CatalogSeedInitializer.class,
            MarketingSeedInitializer.class,
            ReviewSeedInitializer.class,
            ShippingSeedInitializer.class,
            ShowroomSeedInitializer.class,
            SiteBuilderDataSeed.class
    })
    static class DemoSeedCandidates {
    }
}
