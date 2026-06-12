package com.dreamy.support;

import com.dreamy.error.CatalogException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * store 查询参数校验单元测试。
 * L2 TRACE: TC-CAT-047（列表边界单测面）/ V-CAT-001~005。
 */
class StoreParamsTest {

    @Test
    @DisplayName("V-CAT-001 [P0]: locale 枚举缺省 en；枚举外 → fields.locale=invalid_enum")
    void localeParsing() {
        CatalogFieldErrors ok = new CatalogFieldErrors();
        assertThat(StoreParams.parseLocale(null, ok)).isEqualTo("en");
        assertThat(StoreParams.parseLocale("fr", ok)).isEqualTo("fr");
        assertThat(ok.hasErrors()).isFalse();
        CatalogFieldErrors bad = new CatalogFieldErrors();
        StoreParams.parseLocale("de", bad);
        assertThat(bad.fields()).containsEntry("locale", "invalid_enum");
    }

    @Test
    @DisplayName("TC-CAT-047 [P1]: page_size=100 通过、101 拒绝；page=0 拒绝；缺省 1/20")
    void paginationBounds() {
        CatalogFieldErrors ok = new CatalogFieldErrors();
        assertThat(StoreParams.parsePage(null, ok)).isEqualTo(1);
        assertThat(StoreParams.parsePageSize(null, ok)).isEqualTo(20);
        assertThat(StoreParams.parsePageSize(100, ok)).isEqualTo(100);
        assertThat(ok.hasErrors()).isFalse();
        CatalogFieldErrors bad = new CatalogFieldErrors();
        StoreParams.parsePage(0, bad);
        StoreParams.parsePageSize(101, bad);
        assertThat(bad.fields()).containsKeys("page", "page_size");
    }

    @Test
    @DisplayName("V-CAT-003 [P0]: price_min > price_max → fields.price_min=range_invalid；负值拒绝")
    void priceRange() {
        CatalogFieldErrors bad = new CatalogFieldErrors();
        StoreParams.validatePriceRange(new BigDecimal("200"), new BigDecimal("100"), bad);
        assertThat(bad.fields()).containsEntry("price_min", "range_invalid");
        CatalogFieldErrors negative = new CatalogFieldErrors();
        StoreParams.validatePriceRange(new BigDecimal("-1"), null, negative);
        assertThat(negative.fields()).containsEntry("price_min", "range_invalid");
        CatalogFieldErrors ok = new CatalogFieldErrors();
        StoreParams.validatePriceRange(new BigDecimal("100"), new BigDecimal("100"), ok);
        assertThat(ok.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("V-CAT-004/005 [P0]: sort 枚举缺省 recommended；color/size 超长拒绝；id 非正拒绝")
    void sortAndFilters() {
        CatalogFieldErrors ok = new CatalogFieldErrors();
        assertThat(StoreParams.parseSort(null, ok)).isEqualTo("recommended");
        assertThat(StoreParams.parseSort("price_asc", ok)).isEqualTo("price_asc");
        CatalogFieldErrors bad = new CatalogFieldErrors();
        StoreParams.parseSort("hotness", bad);
        StoreParams.checkMaxLength("x".repeat(33), 32, "color", bad);
        StoreParams.parsePositiveId(-1L, "category_id", bad);
        assertThat(bad.fields()).containsKeys("sort", "color", "category_id");
    }

    @Test
    @DisplayName("422501 包络：CatalogFieldErrors.throwIfAny → details.fields 字典（error-strategy L2 要求 1）")
    void throwShape() {
        CatalogFieldErrors errors = new CatalogFieldErrors().reject("q", "required");
        assertThatThrownBy(errors::throwIfAny)
                .isInstanceOf(CatalogException.class)
                .satisfies(ex -> {
                    CatalogException ce = (CatalogException) ex;
                    assertThat(ce.getDetails()).containsKey("fields");
                    @SuppressWarnings("unchecked")
                    Map<String, String> fields = (Map<String, String>) ce.getDetails().get("fields");
                    assertThat(fields).containsEntry("q", "required");
                });
    }
}
