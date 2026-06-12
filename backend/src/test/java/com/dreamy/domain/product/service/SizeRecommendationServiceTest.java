package com.dreamy.domain.product.service;

import com.dreamy.enums.FitPreference;
import com.dreamy.enums.ProductStatus;
import com.dreamy.domain.product.entity.Product;
import com.dreamy.domain.product.entity.SizeChartRow;
import com.dreamy.domain.product.repository.ProductRepository;
import com.dreamy.domain.product.repository.SizeChartRowRepository;
import com.dreamy.dto.SizeRecommendation;
import com.dreamy.error.CatalogErrorCode;
import com.dreamy.error.CatalogException;
import com.dreamy.i18n.CatalogMessageResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 尺码推荐区间匹配单元测试（E-CAT-05 / 决策 20.3）。
 * STUB_SCOPE: repository_io（ProductRepository / SizeChartRowRepository 为 I/O 边界）。
 * L2 TRACE: TC-CAT-001~006 / V-CAT-014~016 / STEP-CAT-03~07。
 */
@ExtendWith(MockitoExtension.class)
class SizeRecommendationServiceTest {

    @Mock
    ProductRepository productRepository;
    @Mock
    SizeChartRowRepository sizeChartRowRepository;

    SizeRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new SizeRecommendationService(productRepository, sizeChartRowRepository,
                new CatalogMessageResolver());
        Product published = new Product();
        published.setId(1L);
        published.setStatus(ProductStatus.PUBLISHED);
        lenient().when(productRepository.findById(anyLong())).thenReturn(published);
    }

    private static SizeChartRow row(String us, String bust, String waist, String hips, String httf) {
        SizeChartRow row = new SizeChartRow();
        row.setUs(us);
        row.setBust(new BigDecimal(bust));
        row.setWaist(new BigDecimal(waist));
        row.setHips(new BigDecimal(hips));
        if (httf != null) {
            row.setHollowToFloor(new BigDecimal(httf));
        }
        return row;
    }

    /** 标准三行尺码表（US 4 / 6 / 8，bust ASC） */
    private static List<SizeChartRow> chart() {
        return List.of(
                row("4", "33.5", "26.5", "36.5", "59"),
                row("6", "34.5", "27.5", "37.5", "59.5"),
                row("8", "35.5", "28.5", "38.5", "60"));
    }

    private static SizeRecommendation.Request req(String h, String b, String w, String hp, String fit) {
        return new SizeRecommendation.Request(new BigDecimal(h), new BigDecimal(b), new BigDecimal(w),
                new BigDecimal(hp), fit);
    }

    @Test
    @DisplayName("TC-CAT-001 [P0]: 三围均落同行 → matched=true 且 recommended_row 正确")
    void allDimensionsSameRow_matched() {
        when(sizeChartRowRepository.listByProductIdOrderByBust(1L)).thenReturn(chart());
        SizeRecommendation.Response resp = service.recommend(1L, "en", req("65", "34", "27", "37", null));
        assertThat(resp.matched()).isTrue();
        assertThat(resp.recommendedRow().us()).isEqualTo("6");
        assertThat(resp.explanation()).contains("US 6");
    }

    @Test
    @DisplayName("TC-CAT-002 [P0]: 跨码段 bust 落 US6、hips 落 US8 → 取大码 US8，notes 透出各维度 matched_us")
    void crossSegment_takesLargerSize() {
        when(sizeChartRowRepository.listByProductIdOrderByBust(1L)).thenReturn(chart());
        // bust=34 → US6 区间；waist=26 → US4；hips=38 → US8
        SizeRecommendation.Response resp = service.recommend(1L, "en", req("65", "34", "26", "38", null));
        assertThat(resp.matched()).isTrue();
        assertThat(resp.recommendedRow().us()).isEqualTo("8");
        assertThat(resp.dimensionNotes())
                .extracting(SizeRecommendation.DimensionNote::dimension)
                .contains("bust", "waist", "hips");
        assertThat(resp.dimensionNotes().stream()
                .filter(n -> "hips".equals(n.dimension())).findFirst().orElseThrow().matchedUs())
                .isEqualTo("8");
    }

    @Test
    @DisplayName("TC-CAT-003 [P1]: fit_preference 偏移——snug 下移不破下界、relaxed 上移不破上界、regular 不偏移")
    void fitPreferenceShifts() {
        when(sizeChartRowRepository.listByProductIdOrderByBust(1L)).thenReturn(chart());
        // 基准 US6
        assertThat(service.recommend(1L, "en", req("65", "34", "27", "37", "snug"))
                .recommendedRow().us()).isEqualTo("4");
        assertThat(service.recommend(1L, "en", req("65", "34", "27", "37", "relaxed"))
                .recommendedRow().us()).isEqualTo("8");
        assertThat(service.recommend(1L, "en", req("65", "34", "27", "37", "regular"))
                .recommendedRow().us()).isEqualTo("6");
        // 基准已是最小行（US4）→ snug 不低于最小行
        assertThat(service.recommend(1L, "en", req("65", "32", "25", "35", "snug"))
                .recommendedRow().us()).isEqualTo("4");
        // 基准已是最大行（US8）→ relaxed 不高于最大行
        assertThat(service.recommend(1L, "en", req("65", "35.5", "28.5", "38.5", "relaxed"))
                .recommendedRow().us()).isEqualTo("8");
    }

    @Test
    @DisplayName("TC-CAT-004 [P0]: 任一维度超全表最大值 → matched=false + 建议话术（区别于 422502）")
    void dimensionBeyondChart_matchedFalse() {
        when(sizeChartRowRepository.listByProductIdOrderByBust(1L)).thenReturn(chart());
        // hips=45 在合理域 [15,80] 内但超出表最大 38.5
        SizeRecommendation.Response resp = service.recommend(1L, "en", req("65", "34", "27", "45", null));
        assertThat(resp.matched()).isFalse();
        assertThat(resp.recommendedRow()).isNull();
        assertThat(resp.explanation()).isNotBlank();
    }

    @Test
    @DisplayName("TC-CAT-005 [P0]: 商品无尺码表 → matched=false（200，不报错）")
    void emptyChart_matchedFalse() {
        when(sizeChartRowRepository.listByProductIdOrderByBust(1L)).thenReturn(List.of());
        SizeRecommendation.Response resp = service.recommend(1L, "en", req("65", "34", "27", "37", null));
        assertThat(resp.matched()).isFalse();
        assertThat(resp.explanation()).isNotBlank();
    }

    @Test
    @DisplayName("TC-CAT-006 [P0]: 体征越界 → 422502；缺必填/非正数 → 422501")
    void inputValidation() {
        // height=200in 超出 [36,90] → 422502
        assertThatThrownBy(() -> service.recommend(1L, "en", req("200", "34", "27", "37", null)))
                .isInstanceOf(CatalogException.class)
                .satisfies(ex -> assertThat(((CatalogException) ex).getErrorCode())
                        .isEqualTo(CatalogErrorCode.SIZE_INPUT_OUT_OF_RANGE));
        // bust=10in 低于 15 → 422502
        assertThatThrownBy(() -> service.recommend(1L, "en", req("65", "10", "27", "37", null)))
                .isInstanceOf(CatalogException.class)
                .satisfies(ex -> assertThat(((CatalogException) ex).getErrorCode())
                        .isEqualTo(CatalogErrorCode.SIZE_INPUT_OUT_OF_RANGE));
        // 缺必填 → 422501 fields.height=required
        assertThatThrownBy(() -> service.recommend(1L, "en",
                new SizeRecommendation.Request(null, new BigDecimal("34"), new BigDecimal("27"),
                        new BigDecimal("37"), null)))
                .isInstanceOf(CatalogException.class)
                .satisfies(ex -> {
                    CatalogException ce = (CatalogException) ex;
                    assertThat(ce.getErrorCode()).isEqualTo(CatalogErrorCode.FIELD_VALIDATION_FAILED);
                    assertThat(fields(ce)).containsEntry("height", "required");
                });
        // 非正数 → 422501 range_invalid
        assertThatThrownBy(() -> service.recommend(1L, "en", req("65", "-1", "27", "37", null)))
                .isInstanceOf(CatalogException.class)
                .satisfies(ex -> assertThat(fields((CatalogException) ex))
                        .containsEntry("bust", "range_invalid"));
        // fit_preference 枚举外 → 422501
        assertThatThrownBy(() -> service.recommend(1L, "en", req("65", "34", "27", "37", "loose")))
                .isInstanceOf(CatalogException.class)
                .satisfies(ex -> assertThat(fields((CatalogException) ex))
                        .containsEntry("fit_preference", "invalid_enum"));
    }

    @Test
    @DisplayName("TC-CAT-050（单测面）[P0]: 商品不存在/未发布 → 404501")
    void productNotFoundOrDraft() {
        when(productRepository.findById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.recommend(99L, "en", req("65", "34", "27", "37", null)))
                .isInstanceOf(CatalogException.class)
                .satisfies(ex -> assertThat(((CatalogException) ex).getErrorCode())
                        .isEqualTo(CatalogErrorCode.PRODUCT_NOT_FOUND));
        Product draft = new Product();
        draft.setId(2L);
        draft.setStatus(ProductStatus.DRAFT);
        when(productRepository.findById(2L)).thenReturn(draft);
        assertThatThrownBy(() -> service.recommend(2L, "en", req("65", "34", "27", "37", null)))
                .isInstanceOf(CatalogException.class)
                .satisfies(ex -> assertThat(((CatalogException) ex).getErrorCode())
                        .isEqualTo(CatalogErrorCode.PRODUCT_NOT_FOUND));
    }

    @Test
    @DisplayName("STEP-CAT-04: hollow_to_floor 列非空 → 身高复核产出提示性落点（不参与定码）")
    void hollowToFloorNote() {
        when(sizeChartRowRepository.listByProductIdOrderByBust(1L)).thenReturn(chart());
        // height=98.3*0.6=58.98 → 落 US6（59）
        SizeRecommendation.Response resp = service.recommend(1L, "en", req("65", "34", "27", "37", null));
        assertThat(resp.dimensionNotes())
                .anyMatch(n -> "hollow_to_floor".equals(n.dimension()));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> fields(CatalogException ex) {
        return (Map<String, String>) ex.getDetails().get("fields");
    }
}
