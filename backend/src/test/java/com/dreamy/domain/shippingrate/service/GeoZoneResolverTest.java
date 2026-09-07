package com.dreamy.domain.shippingrate.service;

import com.dreamy.support.CountryCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GeoZoneResolver ISO 全量映射（order-flow-complete D：8 区；CountryCatalog 权威表）+ 旧 zone 名兼容。
 */
class GeoZoneResolverTest {

    @Test
    @DisplayName("alpha-2 码表：8 区代表国家 + 未知/空 → REST")
    void resolveByAlpha2Code() {
        assertThat(GeoZoneResolver.resolve("US")).isEqualTo(GeoZoneResolver.NORTH_AMERICA);
        assertThat(GeoZoneResolver.resolve("CA")).isEqualTo(GeoZoneResolver.NORTH_AMERICA);
        assertThat(GeoZoneResolver.resolve("MX")).isEqualTo(GeoZoneResolver.NORTH_AMERICA);
        assertThat(GeoZoneResolver.resolve("GB")).isEqualTo(GeoZoneResolver.UK);
        assertThat(GeoZoneResolver.resolve("FR")).isEqualTo(GeoZoneResolver.EUROPE);
        assertThat(GeoZoneResolver.resolve("DE")).isEqualTo(GeoZoneResolver.EUROPE);
        assertThat(GeoZoneResolver.resolve("PL")).isEqualTo(GeoZoneResolver.EUROPE);
        assertThat(GeoZoneResolver.resolve("AU")).isEqualTo(GeoZoneResolver.OCEANIA);
        assertThat(GeoZoneResolver.resolve("NZ")).isEqualTo(GeoZoneResolver.OCEANIA);
        assertThat(GeoZoneResolver.resolve("JP")).isEqualTo(GeoZoneResolver.ASIA);
        assertThat(GeoZoneResolver.resolve("SG")).isEqualTo(GeoZoneResolver.ASIA);
        assertThat(GeoZoneResolver.resolve("BR")).isEqualTo(GeoZoneResolver.LATAM);
        assertThat(GeoZoneResolver.resolve("AE")).isEqualTo(GeoZoneResolver.MEA);
        assertThat(GeoZoneResolver.resolve("ZA")).isEqualTo(GeoZoneResolver.MEA);
        assertThat(GeoZoneResolver.resolve("AQ")).isEqualTo(GeoZoneResolver.REST);
        assertThat(GeoZoneResolver.resolve("")).isEqualTo(GeoZoneResolver.REST);
        assertThat(GeoZoneResolver.resolve("@#")).isEqualTo(GeoZoneResolver.REST);
        assertThat(GeoZoneResolver.resolve("XX")).isEqualTo(GeoZoneResolver.REST);
        assertThat(GeoZoneResolver.resolve(null)).isEqualTo(GeoZoneResolver.REST);
    }

    @Test
    @DisplayName("全量 ISO 目录：≥245 国、code 唯一两位大写、zone ∈ 8 区、名称可反解、US/CA/AU 有州省字典")
    void fullCatalogCoverage() {
        var all = CountryCatalog.all();
        assertThat(all.size()).isGreaterThanOrEqualTo(245);
        Set<String> codes = new HashSet<>();
        for (CountryCatalog.Country c : all) {
            assertThat(c.code()).matches("^[A-Z]{2}$");
            assertThat(codes.add(c.code())).as("duplicate code " + c.code()).isTrue();
            assertThat(CountryCatalog.ZONES).contains(c.zone());
            assertThat(GeoZoneResolver.resolve(c.code())).isEqualTo(c.zone());
            assertThat(CountryCatalog.resolveCode(c.name())).as(c.name()).isEqualTo(c.code());
        }
        assertThat(CountryCatalog.regionsOf("US")).hasSizeGreaterThanOrEqualTo(51);
        assertThat(CountryCatalog.regionsOf("CA")).hasSize(13);
        assertThat(CountryCatalog.regionsOf("AU")).hasSize(8);
        assertThat(CountryCatalog.regionsOf("FR")).isEmpty();
    }

    @Test
    @DisplayName("输入规范化：小写 us / 带空白 ' US ' 同样命中；国家英文名/别名兜底")
    void resolveNormalizesInputAndAliases() {
        assertThat(GeoZoneResolver.resolve("us")).isEqualTo(GeoZoneResolver.NORTH_AMERICA);
        assertThat(GeoZoneResolver.resolve(" US ")).isEqualTo(GeoZoneResolver.NORTH_AMERICA);
        assertThat(GeoZoneResolver.resolve("United States")).isEqualTo(GeoZoneResolver.NORTH_AMERICA);
        assertThat(GeoZoneResolver.resolve("USA")).isEqualTo(GeoZoneResolver.NORTH_AMERICA);
        assertThat(GeoZoneResolver.resolve("United Kingdom")).isEqualTo(GeoZoneResolver.UK);
        assertThat(GeoZoneResolver.resolve("England")).isEqualTo(GeoZoneResolver.UK);
        assertThat(GeoZoneResolver.resolve("Czech Republic")).isEqualTo(GeoZoneResolver.EUROPE);
        assertThat(GeoZoneResolver.resolve("Australia")).isEqualTo(GeoZoneResolver.OCEANIA);
        assertThat(GeoZoneResolver.resolve("Wakanda")).isEqualTo(GeoZoneResolver.REST);
        // country_code 优先，文本兜底
        assertThat(GeoZoneResolver.resolve("DE", "United States")).isEqualTo(GeoZoneResolver.EUROPE);
        assertThat(GeoZoneResolver.resolve(null, "Canada")).isEqualTo(GeoZoneResolver.NORTH_AMERICA);
        assertThat(GeoZoneResolver.resolve("ZZ", "Canada")).isEqualTo(GeoZoneResolver.NORTH_AMERICA);
    }

    @Test
    @DisplayName("旧 zone 名兼容：'North America / FedEx ...' → NORTH_AMERICA；'Rest of World' → REST；新码大小写不敏感；未知 → null")
    void canonicalZoneCompat() {
        assertThat(GeoZoneResolver.canonicalZone("North America / FedEx International Priority"))
                .isEqualTo(GeoZoneResolver.NORTH_AMERICA);
        assertThat(GeoZoneResolver.canonicalZone("Rest of World")).isEqualTo(GeoZoneResolver.REST);
        assertThat(GeoZoneResolver.canonicalZone("Europe")).isEqualTo(GeoZoneResolver.EUROPE);
        assertThat(GeoZoneResolver.canonicalZone("oceania")).isEqualTo(GeoZoneResolver.OCEANIA);
        assertThat(GeoZoneResolver.canonicalZone("latam")).isEqualTo(GeoZoneResolver.LATAM);
        assertThat(GeoZoneResolver.canonicalZone("Nowhere")).isNull();
        assertThat(GeoZoneResolver.canonicalZone(null)).isNull();
        assertThat(GeoZoneResolver.legacyCarrierSuffix("Europe / DHL Express")).isEqualTo("DHL Express");
        assertThat(GeoZoneResolver.legacyCarrierSuffix("Europe")).isNull();
        assertThat(ZoneNormalizer.normalizeZoneCode(" uk ")).isEqualTo(GeoZoneResolver.UK);
    }

    @Test
    @DisplayName("州/省规范化：缩写/全称/别名 → 标准码；非字典国家 → null")
    void regionNormalization() {
        assertThat(CountryCatalog.resolveRegionCode("US", "TX")).isEqualTo("TX");
        assertThat(CountryCatalog.resolveRegionCode("US", "texas")).isEqualTo("TX");
        assertThat(CountryCatalog.resolveRegionCode("US", "Washington DC")).isEqualTo("DC");
        assertThat(CountryCatalog.resolveRegionCode("US", "Calif.")).isEqualTo("CA");
        assertThat(CountryCatalog.resolveRegionCode("CA", "Québec")).isEqualTo("QC");
        assertThat(CountryCatalog.resolveRegionCode("CA", "PEI")).isEqualTo("PE");
        assertThat(CountryCatalog.resolveRegionCode("AU", "New South Wales")).isEqualTo("NSW");
        assertThat(CountryCatalog.resolveRegionCode("AU", "vic")).isEqualTo("VIC");
        assertThat(CountryCatalog.resolveRegionCode("US", "Nowhere")).isNull();
        assertThat(CountryCatalog.resolveRegionCode("FR", "Île-de-France")).isNull();
        assertThat(CountryCatalog.hasRegions("US")).isTrue();
        assertThat(CountryCatalog.hasRegions("GB")).isFalse();
    }
}
