package com.dreamy.domain.product.service;

import com.dreamy.domain.attribute.entity.AttributeDef;
import com.dreamy.enums.AttributeType;
import com.dreamy.dto.AdminProductUpsert;
import com.dreamy.dto.AttributeValueDto;
import com.dreamy.dto.ProductImageDto;
import com.dreamy.dto.SkuDto;
import com.dreamy.dto.TranslationDtos.ProductTranslationDto;
import com.dreamy.error.CatalogErrorCode;
import com.dreamy.error.CatalogException;
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

    /** 31 字段构造器的命名工厂（仅暴露被测字段，其余 null） */
    private static AdminProductUpsert make(String name, String slug, Long categoryId, BigDecimal price,
                                           BigDecimal compareAt, Integer status, Integer sort,
                                           Integer leadTimeDays, List<ProductImageDto> images,
                                           List<SkuDto> skus, List<Long> collectionIds,
                                           List<ProductTranslationDto> translations) {
        return new AdminProductUpsert(name, slug, categoryId, null, null, null, price, compareAt,
                null, null, status, null, null, null, sort, leadTimeDays, null, null,
                null, null, null, null, images, skus, null, collectionIds, translations, null,
                null, null, null);
    }

    /** 旧四参签名兼容包装（动态属性上下文默认空字典 + 跳过分类白名单） */
    private static void validateCompat(AdminProductUpsert u, boolean categoryExists, Set<Long> existingTagIds,
                                       Set<Long> ownedSkuIds) {
        ProductUpsertValidator.validate(u, categoryExists, existingTagIds, ownedSkuIds, Map.of(), null);
    }

    private static AdminProductUpsert valid(List<ProductImageDto> images, List<SkuDto> skus,
                                            BigDecimal compareAt) {
        return make("Aurelia Gown", "aurelia-gown", 1L, new BigDecimal("1280"), compareAt, 1,
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
        assertThatThrownBy(() -> validateCompat(empty, false, Set.of(), null))
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
        assertThatCode(() -> validateCompat(valid(null, null, null), true, Set.of(), null))
                .doesNotThrowAnyException();
        assertThatCode(() -> validateCompat(valid(null, null, new BigDecimal("1280")),
                true, Set.of(), null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validateCompat(valid(null, null, new BigDecimal("1000")),
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
        assertThatThrownBy(() -> validateCompat(valid(null, dupCode, null), true, Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("skus", "duplicated"));
        List<SkuDto> dupCombo = List.of(
                new SkuDto(null, "AUR-IVORY-2", "Ivory", "US 2", 5, null),
                new SkuDto(null, "AUR-IVORY-2B", "Ivory", "US 2", 5, null));
        assertThatThrownBy(() -> validateCompat(valid(null, dupCombo, null), true, Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("skus", "duplicated"));
        List<SkuDto> badPattern = List.of(new SkuDto(null, "aur-ivory-2", "Ivory", "US 2", 5, null));
        assertThatThrownBy(() -> validateCompat(valid(null, badPattern, null), true, Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("skus", "sku_code_invalid"));
        List<SkuDto> badStock = List.of(new SkuDto(null, "AUR-IVORY-2", "Ivory", "US 2", -1, null));
        assertThatThrownBy(() -> validateCompat(valid(null, badStock, null), true, Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("skus", "stock_invalid"));
    }

    @Test
    @DisplayName("V-CAT-038 [P0]: 编辑场景带 id 行必须属于本商品且携带 version")
    void skuOwnershipAndVersion() {
        List<SkuDto> notOwned = List.of(new SkuDto(99L, "AUR-IVORY-2", "Ivory", "US 2", 5, 0L));
        assertThatThrownBy(() -> validateCompat(valid(null, notOwned, null), true, Set.of(),
                Set.of(1L, 2L)))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("skus", "not_owned"));
        List<SkuDto> noVersion = List.of(new SkuDto(1L, "AUR-IVORY-2", "Ivory", "US 2", 5, null));
        assertThatThrownBy(() -> validateCompat(valid(null, noVersion, null), true, Set.of(),
                Set.of(1L, 2L)))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("skus", "version_required"));
        List<SkuDto> ok = List.of(new SkuDto(1L, "AUR-IVORY-2", "Ivory", "US 2", 5, 3L));
        assertThatCode(() -> validateCompat(valid(null, ok, null), true, Set.of(), Set.of(1L)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("TC-CAT-014 [P1]: 主图不变量——gallery sort=0 至多一张；swatch color_name 超长；kind 枚举外")
    void imageInvariants() {
        List<ProductImageDto> twoPrimary = List.of(
                new ProductImageDto(null, "/a.jpg", 1, null, 0),
                new ProductImageDto(null, "/b.jpg", 1, null, 0));
        assertThatThrownBy(() -> validateCompat(valid(twoPrimary, null, null), true,
                Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("images", "multiple_primary"));
        List<ProductImageDto> longColor = List.of(
                new ProductImageDto(null, "/a.jpg", 4, "x".repeat(33), 0));
        assertThatThrownBy(() -> validateCompat(valid(longColor, null, null), true,
                Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("images", "color_name_too_long"));
        List<ProductImageDto> badKind = List.of(new ProductImageDto(null, "/a.jpg", 99, null, 0));
        assertThatThrownBy(() -> validateCompat(valid(badKind, null, null), true,
                Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("images", "invalid_enum"));
    }

    @Test
    @DisplayName("V-CAT-025/034 [P0]: 引用不存在——category not_exists / tag_ids not_exists（CV-CAT-005）")
    void referenceIntegrity() {
        assertThatThrownBy(() -> validateCompat(valid(null, null, null), false, Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("category_id", "not_exists"));
        AdminProductUpsert withTags = make("N", "n", 1L, BigDecimal.ONE, null, 1, 0, 1,
                null, null, List.of(7L), null);
        assertThatThrownBy(() -> validateCompat(withTags, true, Set.of(1L, 2L), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("tag_ids", "not_exists"));
    }

    @Test
    @DisplayName("V-CAT-035 [P0]: translations locale 仅 es/fr 且不重复（TC-CAT-013 写侧）")
    void translationRules() {
        AdminProductUpsert dupLocale = make("N", "n", 1L, BigDecimal.ONE, null, 1, 0, 1, null, null,
                null, List.of(new ProductTranslationDto("es", "A", null, null, null, null, null),
                        new ProductTranslationDto("es", "B", null, null, null, null, null)));
        assertThatThrownBy(() -> validateCompat(dupLocale, true, Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("translations", "invalid_locale"));
        AdminProductUpsert enLocale = make("N", "n", 1L, BigDecimal.ONE, null, 1, 0, 1, null, null,
                null, List.of(new ProductTranslationDto("en", "A", null, null, null, null, null)));
        assertThatThrownBy(() -> validateCompat(enLocale, true, Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("translations", "invalid_locale"));
    }

    @Test
    @DisplayName("TC-CAT-053（单测面）[P0]: 极值——name129 / slug pattern / lead_time_days 0 / sort -1 / status 非法")
    void lengthAndNumericExtremes() {
        assertThatThrownBy(() -> validateCompat(
                make("x".repeat(129), "slug-ok", 1L, BigDecimal.ONE, null, 1, 0, 1, null, null, null, null),
                true, Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("name", "too_long"));
        assertThatThrownBy(() -> validateCompat(
                make("N", "Bad Slug!", 1L, BigDecimal.ONE, null, 1, 0, 1, null, null, null, null),
                true, Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("slug", "pattern"));
        assertThatThrownBy(() -> validateCompat(
                make("N", "n", 1L, BigDecimal.ONE, null, 1, 0, 0, null, null, null, null),
                true, Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("lead_time_days", "range_invalid"));
        assertThatThrownBy(() -> validateCompat(
                make("N", "n", 1L, BigDecimal.ONE, null, 1, -1, 1, null, null, null, null),
                true, Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("sort", "range_invalid"));
        assertThatThrownBy(() -> validateCompat(
                make("N", "n", 1L, BigDecimal.ONE, null, 3, 0, 1, null, null, null, null),
                true, Set.of(), null))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("status", "invalid_enum"));
    }

    // ==================== 动态属性 entries 校验（EAV 化新增） ====================

    private static AttributeDef def(String key, AttributeType type, List<String> options) {
        AttributeDef d = new AttributeDef();
        d.setKey(key);
        d.setLabel(key);
        d.setType(type);
        d.setOptions(options);
        return d;
    }

    private static AdminProductUpsert withAttrs(List<AttributeValueDto> attributes) {
        return new AdminProductUpsert("N", "n", 1L, null, null, null, BigDecimal.ONE, null,
                null, null, 1, null, null, null, 0, 1, null, null,
                attributes, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }

    @Test
    @DisplayName("EAV [P0]: 属性 entries——字典外 key / 白名单外 key / 重复 key / option 外值 / toggle 非法 / 单值类型多值")
    void attributeEntries() {
        Map<String, AttributeDef> defs = Map.of(
                "silhouette", def("silhouette", AttributeType.SELECT, List.of("A-Line", "Mermaid")),
                "embellishment", def("embellishment", AttributeType.MULTISELECT, List.of("Lace", "Beading")),
                "rush_ok", def("rush_ok", AttributeType.TOGGLE, null),
                "care_notes", def("care_notes", AttributeType.TEXT, null));
        Set<String> allowed = Set.of("silhouette", "embellishment", "rush_ok", "care_notes");
        // 合法：select 单值 ∈ options + multiselect 子集 + toggle + text
        assertThatCode(() -> ProductUpsertValidator.validate(withAttrs(List.of(
                new AttributeValueDto("silhouette", List.of("A-Line")),
                new AttributeValueDto("embellishment", List.of("Lace", "Beading")),
                new AttributeValueDto("rush_ok", List.of("true")),
                new AttributeValueDto("care_notes", List.of("Dry clean only")))),
                true, Set.of(), null, defs, allowed)).doesNotThrowAnyException();
        // 字典外 key
        assertThatThrownBy(() -> ProductUpsertValidator.validate(withAttrs(List.of(
                new AttributeValueDto("unknown", List.of("x")))), true, Set.of(), null, defs, allowed))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("attributes", "attribute_not_exists"));
        // 白名单（分类生效配置）外 key
        assertThatThrownBy(() -> ProductUpsertValidator.validate(withAttrs(List.of(
                new AttributeValueDto("silhouette", List.of("A-Line")))), true, Set.of(), null, defs,
                Set.of("embellishment")))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("attributes", "not_in_category"));
        // 重复 key
        assertThatThrownBy(() -> ProductUpsertValidator.validate(withAttrs(List.of(
                new AttributeValueDto("silhouette", List.of("A-Line")),
                new AttributeValueDto("silhouette", List.of("Mermaid")))), true, Set.of(), null, defs, allowed))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("attributes", "duplicated"));
        // option 外值
        assertThatThrownBy(() -> ProductUpsertValidator.validate(withAttrs(List.of(
                new AttributeValueDto("silhouette", List.of("Ball Gown")))), true, Set.of(), null, defs, allowed))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("attributes", "invalid_option"));
        // toggle 非法值
        assertThatThrownBy(() -> ProductUpsertValidator.validate(withAttrs(List.of(
                new AttributeValueDto("rush_ok", List.of("yes")))), true, Set.of(), null, defs, allowed))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("attributes", "invalid_toggle"));
        // 单值类型携带多值
        assertThatThrownBy(() -> ProductUpsertValidator.validate(withAttrs(List.of(
                new AttributeValueDto("silhouette", List.of("A-Line", "Mermaid")))), true, Set.of(), null,
                defs, allowed))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("attributes", "too_many_values"));
        // 空值 entry 视为未填（不报错）
        assertThatCode(() -> ProductUpsertValidator.validate(withAttrs(List.of(
                new AttributeValueDto("silhouette", List.of()))), true, Set.of(), null, defs, allowed))
                .doesNotThrowAnyException();
    }
}
