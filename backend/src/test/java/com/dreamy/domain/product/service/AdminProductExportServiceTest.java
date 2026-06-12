package com.dreamy.domain.product.service;

import com.dreamy.domain.category.entity.Category;
import com.dreamy.domain.category.repository.CategoryRepository;
import com.dreamy.domain.category.service.CategoryTreeService;
import com.dreamy.enums.ProductStatus;
import com.dreamy.domain.product.entity.Product;
import com.dreamy.domain.product.repository.ProductRepository;
import com.dreamy.domain.product.repository.ProductRepository.AdminFilter;
import com.dreamy.domain.product.repository.SkuRepository;
import com.dreamy.domain.product.service.AdminProductExportService.ExportResult;
import com.dreamy.error.CatalogErrorCode;
import com.dreamy.error.CatalogException;
import com.dreamy.infra.CatalogAuditRecorder;
import com.dreamy.port.TradingQueryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 后台商品 CSV 导出服务单元测试（API-CAT-02；admin-prototype-alignment）。
 * STUB_SCOPE: repository_io + trading 端口 + 基建（audit）。
 * L2 TRACE: V-011/V-012 / STEP-01~04 / RM-CAT-03a~d / RM-CAT-01c / CV-CAT-01。
 */
@ExtendWith(MockitoExtension.class)
class AdminProductExportServiceTest {

    @Mock
    ProductRepository productRepository;
    @Mock
    SkuRepository skuRepository;
    @Mock
    CategoryRepository categoryRepository;
    @Mock
    CategoryTreeService treeService;
    @Mock
    TradingQueryPort tradingQueryPort;
    @Mock
    CatalogAuditRecorder audit;
    @Spy
    ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks
    AdminProductExportService service;

    private static Product product(long id, String name) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setSlug("slug-" + id);
        p.setStyleNo("STY-" + id);
        p.setCategoryId(1L);
        p.setPrice(new BigDecimal("1280"));
        p.setStatus(ProductStatus.PUBLISHED);
        p.setIsNew(true);
        p.setRecommend(false);
        p.setSort(0);
        return p;
    }

    private static String csvOf(ExportResult result) {
        String text = new String(result.content(), StandardCharsets.UTF_8);
        assertThat(text).startsWith("\uFEFF");
        return text.substring(1);
    }

    @Test
    @DisplayName("V-011/V-012 [P0]: status 枚举外 / search 超 80 → 422501（与 listAdminProducts 同口径）")
    void filterValidation() {
        assertThatThrownBy(() -> service.export(3, null, null))
                .isInstanceOf(CatalogException.class)
                .satisfies(ex -> assertThat(((CatalogException) ex).getErrorCode())
                        .isEqualTo(CatalogErrorCode.FIELD_VALIDATION_FAILED));
        assertThatThrownBy(() -> service.export(null, null, "x".repeat(81)))
                .satisfies(ex -> assertThat(((CatalogException) ex).getErrorCode())
                        .isEqualTo(CatalogErrorCode.FIELD_VALIDATION_FAILED));
        verify(productRepository, never()).listAdminKeyset(any(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("STEP-01/02 + CV-CAT-01 [P0]: 列序/派生列/标准 CSV 转义（逗号引号换行→双引号包裹+引号翻倍）")
    void csvContentAndEscaping() {
        Product tricky = product(1L, "Aurelia, \"Silk\"\nGown");
        when(treeService.subtreeIds(null)).thenReturn(null);
        when(productRepository.listAdminKeyset(any(AdminFilter.class), eq(0L), anyInt()))
                .thenReturn(List.of(tricky));
        Category category = new Category();
        category.setId(1L);
        category.setName("Wedding Dresses");
        when(categoryRepository.listAll()).thenReturn(List.of(category));
        when(skuRepository.sumStockByProductIds(anyCollection())).thenReturn(Map.of(1L, 12));
        when(tradingQueryPort.sumSalesTotalByProductIds(anyCollection())).thenReturn(Map.of(1L, 34));
        ExportResult result = service.export(null, null, null);
        String csv = csvOf(result);
        String[] lines = csv.split("\n", 2);
        assertThat(lines[0]).isEqualTo("id,name,slug,style_no,category_name,price,compare_at,status,"
                + "is_new,recommend,sort,stock_total,sales_total");
        assertThat(lines[1]).isEqualTo("1,\"Aurelia, \"\"Silk\"\"\nGown\",slug-1,STY-1,Wedding Dresses,"
                + "1280,,2,true,false,0,12,34\n");
        assertThat(result.truncated()).isFalse();
        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(result.fileName()).matches("products-\\d{8}\\.csv");
    }

    @Test
    @DisplayName("RM-CAT-01c [P1]: sales_total 聚合缺失 product_id → 0")
    void salesTotalDefaultsToZero() {
        when(treeService.subtreeIds(null)).thenReturn(null);
        when(productRepository.listAdminKeyset(any(AdminFilter.class), eq(0L), anyInt()))
                .thenReturn(List.of(product(1L, "Aurelia")));
        when(categoryRepository.listAll()).thenReturn(List.of());
        when(skuRepository.sumStockByProductIds(anyCollection())).thenReturn(Map.of());
        when(tradingQueryPort.sumSalesTotalByProductIds(anyCollection())).thenReturn(Map.of());
        String csv = csvOf(service.export(null, null, null));
        assertThat(csv.split("\n")[1]).endsWith(",0,0");
    }

    @Test
    @DisplayName("RM-CAT-03b [P0]: keyset 游标分批读取（id > lastId LIMIT 500，批间游标推进）")
    void keysetCursorAdvances() {
        when(treeService.subtreeIds(null)).thenReturn(null);
        List<Product> first = new ArrayList<>();
        for (long id = 1; id <= AdminProductExportService.PAGE_SIZE; id++) {
            first.add(product(id, "P" + id));
        }
        when(productRepository.listAdminKeyset(any(AdminFilter.class), eq(0L),
                eq(AdminProductExportService.PAGE_SIZE))).thenReturn(first);
        when(productRepository.listAdminKeyset(any(AdminFilter.class), eq(500L),
                eq(AdminProductExportService.PAGE_SIZE))).thenReturn(List.of(product(501L, "P501")));
        when(categoryRepository.listAll()).thenReturn(List.of());
        when(skuRepository.sumStockByProductIds(anyCollection())).thenReturn(Map.of());
        when(tradingQueryPort.sumSalesTotalByProductIds(anyCollection())).thenReturn(Map.of());
        ExportResult result = service.export(null, null, null);
        assertThat(result.rowCount()).isEqualTo(501);
        assertThat(result.truncated()).isFalse();
        verify(productRepository).listAdminKeyset(any(AdminFilter.class), eq(500L),
                eq(AdminProductExportService.PAGE_SIZE));
    }

    @Test
    @DisplayName("STEP-03 / RM-CAT-03d [P0]: 行数达 10000 → 停止 + truncated 标记 + 末行 TRUNCATED 注记")
    void truncationAtMaxRows() {
        when(treeService.subtreeIds(null)).thenReturn(null);
        when(productRepository.listAdminKeyset(any(AdminFilter.class), anyLong(), anyInt()))
                .thenAnswer(invocation -> {
                    long lastId = invocation.getArgument(1);
                    if (lastId >= 10500) {
                        return List.of();
                    }
                    List<Product> batch = new ArrayList<>();
                    for (long id = lastId + 1; id <= lastId + AdminProductExportService.PAGE_SIZE; id++) {
                        batch.add(product(id, "P" + id));
                    }
                    return batch;
                });
        when(categoryRepository.listAll()).thenReturn(List.of());
        when(skuRepository.sumStockByProductIds(anyCollection())).thenReturn(Map.of());
        when(tradingQueryPort.sumSalesTotalByProductIds(anyCollection())).thenReturn(Map.of());
        ExportResult result = service.export(null, null, null);
        assertThat(result.truncated()).isTrue();
        assertThat(result.rowCount()).isEqualTo(AdminProductExportService.MAX_ROWS);
        String csv = csvOf(result);
        String[] lines = csv.split("\n");
        // header + 10000 行 + 截断注记
        assertThat(lines).hasSize(1 + AdminProductExportService.MAX_ROWS + 1);
        assertThat(lines[lines.length - 1]).isEqualTo(AdminProductExportService.TRUNCATED_MARKER);
    }

    @Test
    @DisplayName("STEP-04 [P0]: 写 OperationLog（action=导出商品，detail 含筛选条件与导出行数）")
    void auditRecorded() {
        when(treeService.subtreeIds(2L)).thenReturn(List.of(2L));
        when(productRepository.listAdminKeyset(any(AdminFilter.class), eq(0L), anyInt()))
                .thenReturn(List.of());
        when(categoryRepository.listAll()).thenReturn(List.of());
        service.export(2, 2L, "gown");
        verify(audit).record(eq("导出商品"), eq("商品列表"), contains("\"rows\":0"));
    }

    @Test
    @DisplayName("RM-CAT-03a [P1]: 条件同 listAdminProducts——分类不存在（子树空集）→ 空导出不查询")
    void emptyCategorySubtreeYieldsEmptyExport() {
        when(treeService.subtreeIds(99L)).thenReturn(List.of());
        when(productRepository.listAdminKeyset(any(AdminFilter.class), eq(0L), anyInt()))
                .thenReturn(List.of());
        when(categoryRepository.listAll()).thenReturn(List.of());
        ExportResult result = service.export(null, 99L, null);
        assertThat(result.rowCount()).isZero();
        String csv = csvOf(result);
        assertThat(csv.trim()).isEqualTo(AdminProductExportService.HEADER);
    }
}
