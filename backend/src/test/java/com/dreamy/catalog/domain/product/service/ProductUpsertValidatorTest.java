package com.dreamy.catalog.domain.product.service;

import com.dreamy.catalog.dto.AdminProductUpsert;
import com.dreamy.catalog.dto.ProductImageDto;
import com.dreamy.catalog.dto.SkuDto;
import com.dreamy.catalog.dto.TranslationDtos.ProductTranslationDto;
import com.dreamy.catalog.error.CatalogErrorCode;
import com.dreamy.catalog.error.CatalogException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AdminProductUpsert 校验器单元测试（纯逻辑零 Mock）。
 * L2 TRACE: TC-CAT-010（SKU 矩阵）/ TC-CAT-011（compare_at js_guard）/ TC-CAT-014（主图不变量）
 * / TC-CAT-052（必填族）/ TC-CAT-053（极值族单测面）/ V-CAT-023~038。
 */
class ProductUpsertValidatorTest {

    /** 全 47 字段构造器的命名工厂（仅暴露被测字段，其余 null） */
    private static AdminProductUpsert make(String name, String slug, Long categoryId, BigDecimal price,
                                           BigDecimal compareAt, String status, Integer sort,
                                           Integer leadTimeDays, List<ProductImageDto> images,
                                           List<SkuDto> skus, List<Long> tagIds,
                                           List<ProductTranslationDto> translations) {
        return new AdminProductUpsert(name, slug, null, categoryId, null, null, null, price, compareAt,
                null, null, status, null, null, null, sort, leadTimeDays, null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                images, skus, null, tagIds, translations, null);
    }

    private static AdminProductUpsert valid(List<ProductImageDto> images, List<SkuDto> skus,
                                            BigDecimal compareAt) {
        return make("Aurelia Gown", "aurelia-gown", 1L, new BigDecimal("1280"), compareAt, "draft",
                0, 45, images, skus, null, null);
    }

    private static Map<String, String> fields(Throwable ex) {
        @SuppressWarnings("unchecked")
        Map<String, String> fields = (Map<String, String>) ((CatalogException) ex).getDetails().get("fields");
        return fields;
    }

    @Test
    @DisplayName("TC-CAT-052 [P0]: name/slug/category_id/price/lead_time_days/status 缺失逐一 422501")
    void requiredFields() {
        AdminProductUpsert empty = make(null, null, null, null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> ProductUpsertValidator.validate(empty, false, Set.of(), null))
                .isInstanceOf(CatalogException.class)
                .satisfies(ex -> {
                    assertThat(((CatalogException) ex).getErrorCode())
                            .isEqualTo(CatalogErrorCode.FIELD_VALIDATION_FAILED);
                    assertThat(fields(ex)).containsKeys("name", "slug", "category_id", "price",
                            "lead_time_days", "status");
                });
    }

    @Test
    @DisplayName("TC-CAT-011 [P0]: compare_at js_guard——null 通过、=price 通过、<price 拒绝（lt_price）")
    void compareAtGuard() {
        assertThatCode(() -> ProductUpsertValidator.validate(valid(null, null, null), true, Set.of(), null))
                .doesNotThrowAnyException();
        assertThatCode(() -> ProductUpsertValidator.validate(valid(null, null, new BigDecimal("1280")),
                true, Set.of(), null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ProductUpsertValidator.validate(valid(null, null, new BigDecimal("1000")),
                true, Set.of(), null))
                .isInstanceOf(CatalogException.class)
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("compare_at", "lt_price"));
    }

    @Test
    @DisplayName("TC-CAT-010 [P0]: SKU 矩阵——集内 sku_code 重复、(color,size) 重复、pattern 不匹配各自报错")
    void skuMatrixValidation() {
        List<SkuDto> dupCode = List.of(
                new SkuDto(null, "AUR-IVORY-2", "Ivory", "US 2", 5, null),
                new SkuDto(null, "AUR-IVORY-2", "Ivory", "US 4", 5, null));
        assertThatThrownBy(() -> ProductUpsertValidator.validate(valid(null, dupCode, null), true, Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("skus", "duplicated"));
        List<SkuDto> dupCombo = List.of(
                new SkuDto(null, "AUR-IVORY-2", "Ivory", "US 2", 5, null),
                new SkuDto(null, "AUR-IVORY-2B", "Ivory", "US 2", 5, null));
        assertThatThrownBy(() -> ProductUpsertValidator.validate(valid(null, dupCombo, null), true, Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("skus", "duplicated"));
        List<SkuDto> badPattern = List.of(new SkuDto(null, "aur-ivory-2", "Ivory", "US 2", 5, null));
        assertThatThrownBy(() -> ProductUpsertValidator.validate(valid(null, badPattern, null), true, Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("skus", "sku_code_invalid"));
        List<SkuDto> badStock = List.of(new SkuDto(null, "AUR-IVORY-2", "Ivory", "US 2", -1, null));
        assertThatThrownBy(() -> ProductUpsertValidator.validate(valid(null, badStock, null), true, Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("skus", "stock_invalid"));
    }

    @Test
    @DisplayName("V-CAT-038 [P0]: 编辑场景带 id 行必须属于本商品且携带 version")
    void skuOwnershipAndVersion() {
        List<SkuDto> notOwned = List.of(new SkuDto(99L, "AUR-IVORY-2", "Ivory", "US 2", 5, 0L));
        assertThatThrownBy(() -> ProductUpsertValidator.validate(valid(null, notOwned, null), true, Set.of(),
                Set.of(1L, 2L)))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("skus", "not_owned"));
        List<SkuDto> noVersion = List.of(new SkuDto(1L, "AUR-IVORY-2", "Ivory", "US 2", 5, null));
        assertThatThrownBy(() -> ProductUpsertValidator.validate(valid(null, noVersion, null), true, Set.of(),
                Set.of(1L, 2L)))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("skus", "version_required"));
        List<SkuDto> ok = List.of(new SkuDto(1L, "AUR-IVORY-2", "Ivory", "US 2", 5, 3L));
        assertThatCode(() -> ProductUpsertValidator.validate(valid(null, ok, null), true, Set.of(), Set.of(1L)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("TC-CAT-014 [P1]: 主图不变量——gallery sort=0 至多一张；swatch color_name 超长；kind 枚举外")
    void imageInvariants() {
        List<ProductImageDto> twoPrimary = List.of(
                new ProductImageDto(null, "/a.jpg", "gallery", null, 0),
                new ProductImageDto(null, "/b.jpg", "gallery", null, 0));
        assertThatThrownBy(() -> ProductUpsertValidator.validate(valid(twoPrimary, null, null), true,
                Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("images", "multiple_primary"));
        List<ProductImageDto> longColor = List.of(
                new ProductImageDto(null, "/a.jpg", "swatch", "x".repeat(33), 0));
        assertThatThrownBy(() -> ProductUpsertValidator.validate(valid(longColor, null, null), true,
                Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("images", "color_name_too_long"));
        List<ProductImageDto> badKind = List.of(new ProductImageDto(null, "/a.jpg", "thumbnail", null, 0));
        assertThatThrownBy(() -> ProductUpsertValidator.validate(valid(badKind, null, null), true,
                Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("images", "invalid_enum"));
    }

    @Test
    @DisplayName("V-CAT-025/034 [P0]: 引用不存在——category not_exists / tag_ids not_exists（CV-CAT-005）")
    void referenceIntegrity() {
        assertThatThrownBy(() -> ProductUpsertValidator.validate(valid(null, null, null), false, Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("category_id", "not_exists"));
        AdminProductUpsert withTags = make("N", "n", 1L, BigDecimal.ONE, null, "draft", 0, 1,
                null, null, List.of(7L), null);
        assertThatThrownBy(() -> ProductUpsertValidator.validate(withTags, true, Set.of(1L, 2L), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("tag_ids", "not_exists"));
    }

    @Test
    @DisplayName("V-CAT-035 [P0]: translations locale 仅 es/fr 且不重复（TC-CAT-013 写侧）")
    void translationRules() {
        AdminProductUpsert dupLocale = make("N", "n", 1L, BigDecimal.ONE, null, "draft", 0, 1, null, null,
                null, List.of(new ProductTranslationDto("es", "A", null, null, null, null),
                        new ProductTranslationDto("es", "B", null, null, null, null)));
        assertThatThrownBy(() -> ProductUpsertValidator.validate(dupLocale, true, Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("translations", "invalid_locale"));
        AdminProductUpsert enLocale = make("N", "n", 1L, BigDecimal.ONE, null, "draft", 0, 1, null, null,
                null, List.of(new ProductTranslationDto("en", "A", null, null, null, null)));
        assertThatThrownBy(() -> ProductUpsertValidator.validate(enLocale, true, Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("translations", "invalid_locale"));
    }

    @Test
    @DisplayName("TC-CAT-053（单测面）[P0]: 极值——name129 / slug pattern / lead_time_days 0 / sort -1 / status 非法")
    void lengthAndNumericExtremes() {
        assertThatThrownBy(() -> ProductUpsertValidator.validate(
                make("x".repeat(129), "slug-ok", 1L, BigDecimal.ONE, null, "draft", 0, 1, null, null, null, null),
                true, Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("name", "too_long"));
        assertThatThrownBy(() -> ProductUpsertValidator.validate(
                make("N", "Bad Slug!", 1L, BigDecimal.ONE, null, "draft", 0, 1, null, null, null, null),
                true, Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("slug", "pattern"));
        assertThatThrownBy(() -> ProductUpsertValidator.validate(
                make("N", "n", 1L, BigDecimal.ONE, null, "draft", 0, 0, null, null, null, null),
                true, Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("lead_time_days", "range_invalid"));
        assertThatThrownBy(() -> ProductUpsertValidator.validate(
                make("N", "n", 1L, BigDecimal.ONE, null, "draft", -1, 1, null, null, null, null),
                true, Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("sort", "range_invalid"));
        assertThatThrownBy(() -> ProductUpsertValidator.validate(
                make("N", "n", 1L, BigDecimal.ONE, null, "archived", 0, 1, null, null, null, null),
                true, Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("status", "invalid_enum"));
    }
}
