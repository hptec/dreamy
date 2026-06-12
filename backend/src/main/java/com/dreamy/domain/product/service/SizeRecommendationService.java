package com.dreamy.domain.product.service;

import com.dreamy.enums.ProductStatus;
import com.dreamy.enums.FitPreference;
import com.dreamy.domain.product.entity.Product;
import com.dreamy.domain.product.entity.SizeChartRow;
import com.dreamy.domain.product.repository.ProductRepository;
import com.dreamy.domain.product.repository.SizeChartRowRepository;
import com.dreamy.dto.SizeChartRowDto;
import com.dreamy.dto.SizeRecommendation;
import com.dreamy.error.CatalogErrorCode;
import com.dreamy.error.CatalogException;
import com.dreamy.i18n.CatalogMessageResolver;
import com.dreamy.support.CatalogFieldErrors;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Find My Size 尺码推荐（E-CAT-05，决策 20.3）。纯函数：仅读 size_chart_row，无写副作用，不缓存。
 * 区间匹配：行按维度值升序，取第一行该维度值 ≥ 输入值为落点；三围落点取最大码为基准（跨码段取大码）；
 * fit_preference 偏移一档不破上下界；任一维度超全表最大值 → matched=false（区别于 422502 输入不合理）。
 * L2 TRACE: V-CAT-014~016 / E-CAT-05 STEP-CAT-01~07 / TC-CAT-001~006。
 */
@Service
public class SizeRecommendationService {

    /** V-CAT-015 体征合理域（in）：height ∈ [36,90]；bust/waist/hips ∈ [15,80] */
    static final BigDecimal HEIGHT_MIN = new BigDecimal("36");
    static final BigDecimal HEIGHT_MAX = new BigDecimal("90");
    static final BigDecimal GIRTH_MIN = new BigDecimal("15");
    static final BigDecimal GIRTH_MAX = new BigDecimal("80");
    /** STEP-CAT-04 身高 → 中空到地估算系数 */
    static final BigDecimal HOLLOW_RATIO = new BigDecimal("0.60");

    private final ProductRepository productRepository;
    private final SizeChartRowRepository sizeChartRepository;
    private final CatalogMessageResolver messages;

    public SizeRecommendationService(ProductRepository productRepository,
                                     SizeChartRowRepository sizeChartRepository,
                                     CatalogMessageResolver messages) {
        this.productRepository = productRepository;
        this.sizeChartRepository = sizeChartRepository;
        this.messages = messages;
    }

    public SizeRecommendation.Response recommend(Long productId, String locale, SizeRecommendation.Request req) {
        // STEP-CAT-01 商品存在且已发布（404501）
        Product product = productRepository.findById(productId);
        if (product == null || product.getStatus() == null
                || product.getStatus() != ProductStatus.PUBLISHED) {
            throw new CatalogException(CatalogErrorCode.PRODUCT_NOT_FOUND);
        }
        FitPreference fit = validate(req);
        Locale msgLocale = CatalogMessageResolver.toLocale(locale);
        // STEP-CAT-02 尺码表（bust ASC）；空表 → matched=false 不报错
        List<SizeChartRow> rows = sizeChartRepository.listByProductIdOrderByBust(productId);
        if (rows.isEmpty()) {
            return new SizeRecommendation.Response(false, null,
                    messages.resolve("size_reco.no_chart_contact_support", msgLocale), List.of());
        }
        return match(rows, req, fit, msgLocale);
    }

    /** V-CAT-014（必填/>0 → 422501）+ V-CAT-015（合理域 → 422502）+ V-CAT-016（fit 枚举） */
    FitPreference validate(SizeRecommendation.Request req) {
        CatalogFieldErrors errors = new CatalogFieldErrors();
        requirePositive(req.height(), "height", errors);
        requirePositive(req.bust(), "bust", errors);
        requirePositive(req.waist(), "waist", errors);
        requirePositive(req.hips(), "hips", errors);
        FitPreference fit = FitPreference.REGULAR;
        if (req.fitPreference() != null && !req.fitPreference().isBlank()) {
            fit = FitPreference.of(req.fitPreference());
            if (fit == null) {
                errors.reject("fit_preference", "invalid_enum");
            }
        }
        errors.throwIfAny();
        // V-CAT-015 体征越界 → 422502 SIZE_INPUT_OUT_OF_RANGE（details.fields 指明维度）
        Map<String, String> outOfRange = new LinkedHashMap<>();
        if (outside(req.height(), HEIGHT_MIN, HEIGHT_MAX)) {
            outOfRange.put("height", "out_of_range");
        }
        if (outside(req.bust(), GIRTH_MIN, GIRTH_MAX)) {
            outOfRange.put("bust", "out_of_range");
        }
        if (outside(req.waist(), GIRTH_MIN, GIRTH_MAX)) {
            outOfRange.put("waist", "out_of_range");
        }
        if (outside(req.hips(), GIRTH_MIN, GIRTH_MAX)) {
            outOfRange.put("hips", "out_of_range");
        }
        if (!outOfRange.isEmpty()) {
            throw new CatalogException(CatalogErrorCode.SIZE_INPUT_OUT_OF_RANGE, Map.of("fields", outOfRange));
        }
        return fit;
    }

    /** STEP-CAT-03~07 区间匹配核心（包级可见，单测直驱——TC-CAT-001~005） */
    SizeRecommendation.Response match(List<SizeChartRow> rows, SizeRecommendation.Request req,
                                      FitPreference fit, Locale msgLocale) {
        List<SizeRecommendation.DimensionNote> notes = new ArrayList<>();
        boolean outOfChart = false;
        int baseIndex = -1;
        // STEP-CAT-03 逐维度区间匹配（bust/waist/hips；行按维度值升序）
        for (Dim dim : List.of(
                new Dim("bust", req.bust(), SizeChartRow::getBust),
                new Dim("waist", req.waist(), SizeChartRow::getWaist),
                new Dim("hips", req.hips(), SizeChartRow::getHips))) {
            Integer index = firstIndexAtLeast(rows, dim.getter(), dim.input());
            if (index == null) {
                // 该维度超全表最大值 → 超界（STEP-CAT-06）
                outOfChart = true;
                notes.add(new SizeRecommendation.DimensionNote(dim.name(), null));
            } else {
                notes.add(new SizeRecommendation.DimensionNote(dim.name(), rows.get(index).getUs()));
                // STEP-CAT-05 三围落点取最大码（行序=升序，索引大=码大）
                baseIndex = Math.max(baseIndex, index);
            }
        }
        // STEP-CAT-04 身高复核（hollow_to_floor 仅提示，不参与定码）
        boolean hasHollow = rows.stream().anyMatch(r -> r.getHollowToFloor() != null);
        if (hasHollow && req.height() != null) {
            BigDecimal target = req.height().multiply(HOLLOW_RATIO);
            Integer hollowIndex = firstIndexAtLeast(rows, SizeChartRow::getHollowToFloor, target);
            if (hollowIndex != null) {
                notes.add(new SizeRecommendation.DimensionNote("hollow_to_floor", rows.get(hollowIndex).getUs()));
            }
        }
        // STEP-CAT-06 任一维度超界 → matched=false（与 422502 区分：输入合理但超出本品尺码表覆盖）
        if (outOfChart || baseIndex < 0) {
            return new SizeRecommendation.Response(false, null,
                    messages.resolve("size_reco.out_of_chart_contact_support", msgLocale), notes);
        }
        // STEP-CAT-05 fit_preference 偏移：snug 下移不低于最小行 / relaxed 上移不高于最大行
        int finalIndex = Math.max(0, Math.min(rows.size() - 1, baseIndex + fit.getShift()));
        SizeChartRow recommended = rows.get(finalIndex);
        // STEP-CAT-07 命中：区间说明话术（不虚构买家占比）
        return new SizeRecommendation.Response(true, toDto(recommended),
                messages.resolve("size_reco.interval_explain", msgLocale, recommended.getUs()), notes);
    }

    /** 第一行该维度值 ≥ 输入值（该维度空值行跳过）；全表最大值 < 输入 → null（超界） */
    private static Integer firstIndexAtLeast(List<SizeChartRow> rows,
                                             Function<SizeChartRow, BigDecimal> getter, BigDecimal input) {
        if (input == null) {
            return null;
        }
        // 按该维度值升序的行下标序列（保持稳定）
        List<Integer> ordered = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            if (getter.apply(rows.get(i)) != null) {
                ordered.add(i);
            }
        }
        ordered.sort((a, b) -> getter.apply(rows.get(a)).compareTo(getter.apply(rows.get(b))));
        for (Integer index : ordered) {
            if (getter.apply(rows.get(index)).compareTo(input) >= 0) {
                return index;
            }
        }
        return null;
    }

    private static void requirePositive(BigDecimal value, String field, CatalogFieldErrors errors) {
        if (value == null) {
            errors.reject(field, "required");
        } else if (value.signum() <= 0) {
            errors.reject(field, "range_invalid");
        }
    }

    private static boolean outside(BigDecimal value, BigDecimal min, BigDecimal max) {
        return value.compareTo(min) < 0 || value.compareTo(max) > 0;
    }

    private static SizeChartRowDto toDto(SizeChartRow row) {
        return new SizeChartRowDto(row.getId(), row.getUs(), row.getUk(), row.getAu(),
                row.getBust(), row.getWaist(), row.getHips(), row.getHollowToFloor());
    }

    private record Dim(String name, BigDecimal input, Function<SizeChartRow, BigDecimal> getter) {
    }
}
