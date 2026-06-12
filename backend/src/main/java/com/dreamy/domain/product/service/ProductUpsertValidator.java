package com.dreamy.domain.product.service;

import com.dreamy.domain.attribute.entity.AttributeDef;
import com.dreamy.enums.AttributeType;
import com.dreamy.enums.ImageKind;
import com.dreamy.enums.ProductStatus;
import com.dreamy.dto.AdminProductUpsert;
import com.dreamy.dto.AttributeValueDto;
import com.dreamy.dto.ProductImageDto;
import com.dreamy.dto.SizeChartRowDto;
import com.dreamy.dto.SkuDto;
import com.dreamy.dto.TranslationDtos.ProductTranslationDto;
import com.dreamy.support.CatalogFieldErrors;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * AdminProductUpsert 校验器（V-CAT-023~036 + 编辑场景 V-CAT-038；纯逻辑无 Spring 依赖，单测直驱）。
 * 引用存在性上下文（category/tag/own-sku）由 Service 预查后传入（CV-CAT-005 逻辑外键）。
 * L2 TRACE: V-CAT-023~038 / CV-CAT-002~004·010·011 / TC-CAT-010/011/014。
 */
public final class ProductUpsertValidator {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9-]+$");
    private static final Pattern SKU_CODE_PATTERN = Pattern.compile("^[A-Z0-9-]+$");
    private static final Set<String> TRANSLATION_LOCALES = Set.of("es", "fr");

    private ProductUpsertValidator() {
    }

    /**
     * 校验整单载荷；任一违规收集后抛 422501。
     *
     * @param categoryExists category_id 存在性（V-CAT-025，Service 预查）
     * @param existingTagIds 全部存在的 tag id 集（V-CAT-034）
     * @param ownedSkuIds    编辑场景本商品既有 SKU id 集（创建场景传 null 跳过 V-CAT-038）
     * @param defsByKey      属性字典全量 key 索引（动态属性校验数据源）
     * @param allowedKeys    分类生效非 hidden 属性 key 白名单（分类无效传 null 跳过 not_in_category）
     */
    public static void validate(AdminProductUpsert u, boolean categoryExists, Set<Long> existingTagIds,
                                Set<Long> ownedSkuIds, Map<String, AttributeDef> defsByKey,
                                Set<String> allowedKeys) {
        CatalogFieldErrors errors = new CatalogFieldErrors();
        // V-CAT-023 name 必填 trim 非空 ≤128
        if (u.name() == null || u.name().trim().isEmpty()) {
            errors.reject("name", "required");
        } else if (u.name().trim().length() > 128) {
            errors.reject("name", "too_long");
        }
        // V-CAT-024 slug 必填 pattern ≤128
        if (u.slug() == null || u.slug().isBlank()) {
            errors.reject("slug", "required");
        } else if (u.slug().length() > 128 || !SLUG_PATTERN.matcher(u.slug()).matches()) {
            errors.reject("slug", "pattern");
        }
        // V-CAT-025 category_id 必填且存在（不存在 → 422501 fields.category_id=not_exists，契约无 404）
        if (u.categoryId() == null) {
            errors.reject("category_id", "required");
        } else if (!categoryExists) {
            errors.reject("category_id", "not_exists");
        }
        // V-CAT-026 price 必填 >= 0
        if (u.price() == null) {
            errors.reject("price", "required");
        } else if (u.price().signum() < 0) {
            errors.reject("price", "range_invalid");
        }
        // V-CAT-027 compare_at 为空或 >= price（js_guard）
        if (u.compareAt() != null) {
            if (u.compareAt().signum() < 0) {
                errors.reject("compare_at", "range_invalid");
            } else if (u.price() != null && u.compareAt().compareTo(u.price()) < 0) {
                errors.reject("compare_at", "lt_price");
            }
        }
        // V-CAT-028 lead_time_days 必填 >= 1
        if (u.leadTimeDays() == null) {
            errors.reject("lead_time_days", "required");
        } else if (u.leadTimeDays() < 1) {
            errors.reject("lead_time_days", "range_invalid");
        }
        // V-CAT-029 status 必填枚举
        if (u.status() == null) {
            errors.reject("status", "required");
        } else if (ProductStatus.of(u.status()) == null) {
            errors.reject("status", "invalid_enum");
        }
        // V-CAT-030 sort >= 0
        if (u.sort() != null && u.sort() < 0) {
            errors.reject("sort", "range_invalid");
        }
        validateImages(u.images(), errors);
        validateSkus(u.skus(), ownedSkuIds, errors);
        validateSizeChart(u.sizeChart(), errors);
        // V-CAT-034 tag_ids 去重后全部存在
        if (u.tagIds() != null) {
            Set<Long> seen = new HashSet<>();
            for (Long tagId : u.tagIds()) {
                if (tagId == null || !existingTagIds.contains(tagId)) {
                    errors.reject("tag_ids", "not_exists");
                    break;
                }
                seen.add(tagId);
            }
        }
        validateTranslations(u.translations(), errors);
        validateTextLimits(u, errors);
        validateAttributes(u.attributes(), defsByKey, allowedKeys, errors);
        errors.throwIfAny();
    }

    /**
     * 动态属性 entries 校验：key 存在字典（attribute_not_exists）、在分类生效非 hidden 配置内（not_in_category）、
     * 不重复（duplicated）、值按 type 校验——select 单值 ∈ options / multiselect ⊆ options（invalid_option）、
     * toggle ∈ {true,false}（invalid_toggle）、text 单值 ≤255（too_long）、select/text/toggle 多值（too_many_values）。
     */
    private static void validateAttributes(List<AttributeValueDto> entries, Map<String, AttributeDef> defsByKey,
                                           Set<String> allowedKeys, CatalogFieldErrors errors) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (AttributeValueDto entry : entries) {
            if (entry == null || entry.key() == null || entry.key().isBlank()) {
                errors.reject("attributes", "key_required");
                return;
            }
            String key = entry.key();
            if (!seen.add(key)) {
                errors.reject("attributes", "duplicated");
                return;
            }
            AttributeDef def = defsByKey.get(key);
            if (def == null) {
                errors.reject("attributes", "attribute_not_exists");
                return;
            }
            if (allowedKeys != null && !allowedKeys.contains(key)) {
                errors.reject("attributes", "not_in_category");
                return;
            }
            List<String> values = entry.values() == null ? List.of()
                    : entry.values().stream().filter(v -> v != null && !v.isBlank()).toList();
            if (values.isEmpty()) {
                continue; // 空值 entry 视为未填，落库时跳过
            }
            AttributeType type = def.getType();
            if (type != AttributeType.MULTISELECT && values.size() > 1) {
                errors.reject("attributes", "too_many_values");
                return;
            }
            for (String value : values) {
                if (value.length() > 255) {
                    errors.reject("attributes", "too_long");
                    return;
                }
                if (type == AttributeType.TOGGLE && !"true".equals(value) && !"false".equals(value)) {
                    errors.reject("attributes", "invalid_toggle");
                    return;
                }
                if (type != null && type.optionsAllowed()
                        && (def.getOptions() == null || !def.getOptions().contains(value))) {
                    errors.reject("attributes", "invalid_option");
                    return;
                }
            }
        }
    }

    /** V-CAT-031 images（CV-CAT-010 主图不变量） */
    private static void validateImages(List<ProductImageDto> images, CatalogFieldErrors errors) {
        if (images == null) {
            return;
        }
        int primaryGalleryCount = 0;
        for (ProductImageDto image : images) {
            if (image.url() == null || image.url().isBlank() || image.url().length() > 512) {
                errors.reject("images", "url_invalid");
                return;
            }
            ImageKind kind = ImageKind.of(image.kind());
            if (kind == null) {
                errors.reject("images", "invalid_enum");
                return;
            }
            if (image.sort() != null && image.sort() < 0) {
                errors.reject("images", "range_invalid");
                return;
            }
            if (kind == ImageKind.GALLERY && image.sort() != null && image.sort() == 0) {
                primaryGalleryCount++;
            }
            if (kind == ImageKind.SWATCH && image.colorName() != null && image.colorName().length() > 32) {
                errors.reject("images", "color_name_too_long");
                return;
            }
        }
        // 主图 js_guard：kind=gallery 的 sort=0 至多一张
        if (primaryGalleryCount > 1) {
            errors.reject("images", "multiple_primary");
        }
    }

    /** V-CAT-032 + V-CAT-038 skus（CV-CAT-011 组合唯一） */
    private static void validateSkus(List<SkuDto> skus, Set<Long> ownedSkuIds, CatalogFieldErrors errors) {
        if (skus == null) {
            return;
        }
        Set<String> codes = new HashSet<>();
        Set<String> colorSizes = new HashSet<>();
        for (SkuDto sku : skus) {
            if (sku.skuCode() == null || sku.skuCode().isBlank()
                    || sku.skuCode().length() > 64 || !SKU_CODE_PATTERN.matcher(sku.skuCode()).matches()) {
                errors.reject("skus", "sku_code_invalid");
                return;
            }
            if (sku.color() == null || sku.color().isBlank() || sku.color().length() > 32) {
                errors.reject("skus", "color_invalid");
                return;
            }
            if (sku.size() == null || sku.size().isBlank() || sku.size().length() > 16) {
                errors.reject("skus", "size_invalid");
                return;
            }
            if (sku.stock() != null && sku.stock() < 0) {
                errors.reject("skus", "stock_invalid");
                return;
            }
            // 提交集内 sku_code 不重复、(color,size) 不重复
            if (!codes.add(sku.skuCode())) {
                errors.reject("skus", "duplicated");
                return;
            }
            if (!colorSizes.add(sku.color() + " " + sku.size())) {
                errors.reject("skus", "duplicated");
                return;
            }
            // V-CAT-038（编辑场景）：带 id 行须属于本商品且携带 version
            if (ownedSkuIds != null && sku.id() != null) {
                if (!ownedSkuIds.contains(sku.id())) {
                    errors.reject("skus", "not_owned");
                    return;
                }
                if (sku.version() == null) {
                    errors.reject("skus", "version_required");
                    return;
                }
            }
        }
    }

    /** V-CAT-033 size_chart（CV-CAT-003 数值域） */
    private static void validateSizeChart(List<SizeChartRowDto> rows, CatalogFieldErrors errors) {
        if (rows == null) {
            return;
        }
        for (SizeChartRowDto row : rows) {
            if (row.us() == null || row.us().isBlank() || row.us().length() > 8) {
                errors.reject("size_chart", "us_invalid");
                return;
            }
            if ((row.uk() != null && row.uk().length() > 8) || (row.au() != null && row.au().length() > 8)) {
                errors.reject("size_chart", "too_long");
                return;
            }
            for (BigDecimal v : new BigDecimal[]{row.bust(), row.waist(), row.hips(), row.hollowToFloor()}) {
                if (v != null && v.signum() < 0) {
                    errors.reject("size_chart", "range_invalid");
                    return;
                }
            }
        }
    }

    /** V-CAT-035 translations */
    private static void validateTranslations(List<ProductTranslationDto> translations, CatalogFieldErrors errors) {
        if (translations == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (ProductTranslationDto t : translations) {
            if (t.locale() == null || !TRANSLATION_LOCALES.contains(t.locale()) || !seen.add(t.locale())) {
                errors.reject("translations", "invalid_locale");
                return;
            }
            if (tooLong(t.name(), 128) || tooLong(t.subtitle(), 255)
                    || tooLong(t.seoTitle(), 128) || tooLong(t.seoDescription(), 255)) {
                errors.reject("translations", "too_long");
                return;
            }
        }
    }

    /** V-CAT-036 文本长度上限（er-diagram 对齐，CV-CAT-002） */
    private static void validateTextLimits(AdminProductUpsert u, CatalogFieldErrors errors) {
        check(u.subtitle(), 255, "subtitle", errors);
        check(u.productType(), 64, "product_type", errors);
        check(u.fabricComposition(), 128, "fabric_composition", errors);
        check(u.modelHeight(), 32, "model_height", errors);
        check(u.modelSize(), 16, "model_size", errors);
        check(u.modelBodyType(), 32, "model_body_type", errors);
        check(u.countryOfOrigin(), 64, "country_of_origin", errors);
        check(u.styleNo(), 32, "style_no", errors);
        check(u.seoTitle(), 128, "seo_title", errors);
        check(u.seoDesc(), 255, "seo_desc", errors);
    }

    private static void check(String value, int max, String field, CatalogFieldErrors errors) {
        if (tooLong(value, max)) {
            errors.reject(field, "too_long");
        }
    }

    private static boolean tooLong(String value, int max) {
        return value != null && value.length() > max;
    }
}
