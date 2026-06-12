package com.dreamy.domain.shippingrate.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TC-SHP-001/002：GeoZoneResolver 码表 + 国家名别名（shipping-api-detail §10.2 权威表）。
 */
class GeoZoneResolverTest {

    @Test
    @DisplayName("TC-SHP-001 alpha-2 码表：北美/欧洲/大洋洲/其余")
    void resolveByAlpha2Code() {
        assertThat(GeoZoneResolver.resolve("US")).isEqualTo(GeoZoneResolver.NORTH_AMERICA);
        assertThat(GeoZoneResolver.resolve("CA")).isEqualTo(GeoZoneResolver.NORTH_AMERICA);
        assertThat(GeoZoneResolver.resolve("MX")).isEqualTo(GeoZoneResolver.NORTH_AMERICA);
        assertThat(GeoZoneResolver.resolve("GB")).isEqualTo(GeoZoneResolver.EUROPE);
        assertThat(GeoZoneResolver.resolve("FR")).isEqualTo(GeoZoneResolver.EUROPE);
        assertThat(GeoZoneResolver.resolve("ES")).isEqualTo(GeoZoneResolver.EUROPE);
        assertThat(GeoZoneResolver.resolve("DE")).isEqualTo(GeoZoneResolver.EUROPE);
        assertThat(GeoZoneResolver.resolve("IT")).isEqualTo(GeoZoneResolver.EUROPE);
        assertThat(GeoZoneResolver.resolve("PL")).isEqualTo(GeoZoneResolver.EUROPE);
        assertThat(GeoZoneResolver.resolve("GR")).isEqualTo(GeoZoneResolver.EUROPE);
        assertThat(GeoZoneResolver.resolve("AU")).isEqualTo(GeoZoneResolver.OCEANIA);
        assertThat(GeoZoneResolver.resolve("NZ")).isEqualTo(GeoZoneResolver.OCEANIA);
        assertThat(GeoZoneResolver.resolve("JP")).isEqualTo(GeoZoneResolver.REST_OF_WORLD);
        assertThat(GeoZoneResolver.resolve("BR")).isEqualTo(GeoZoneResolver.REST_OF_WORLD);
        assertThat(GeoZoneResolver.resolve("ZA")).isEqualTo(GeoZoneResolver.REST_OF_WORLD);
        assertThat(GeoZoneResolver.resolve("")).isEqualTo(GeoZoneResolver.REST_OF_WORLD);
        assertThat(GeoZoneResolver.resolve("@#")).isEqualTo(GeoZoneResolver.REST_OF_WORLD);
        assertThat(GeoZoneResolver.resolve(null)).isEqualTo(GeoZoneResolver.REST_OF_WORLD);
    }

    @Test
    @DisplayName("TC-SHP-001 输入规范化：小写 us / 带空白 ' US ' 同样命中")
    void resolveNormalizesInput() {
        assertThat(GeoZoneResolver.resolve("us")).isEqualTo(GeoZoneResolver.NORTH_AMERICA);
        assertThat(GeoZoneResolver.resolve(" US ")).isEqualTo(GeoZoneResolver.NORTH_AMERICA);
        assertThat(GeoZoneResolver.resolve("gb")).isEqualTo(GeoZoneResolver.EUROPE);
    }

    @Test
    @DisplayName("TC-SHP-002 国家英文名别名表二次匹配")
    void resolveByCountryNameAlias() {
        assertThat(GeoZoneResolver.resolve("United States")).isEqualTo(GeoZoneResolver.NORTH_AMERICA);
        assertThat(GeoZoneResolver.resolve("USA")).isEqualTo(GeoZoneResolver.NORTH_AMERICA);
        assertThat(GeoZoneResolver.resolve("United Kingdom")).isEqualTo(GeoZoneResolver.EUROPE);
        assertThat(GeoZoneResolver.resolve("Australia")).isEqualTo(GeoZoneResolver.OCEANIA);
        assertThat(GeoZoneResolver.resolve("Wakanda")).isEqualTo(GeoZoneResolver.REST_OF_WORLD);
    }
}
