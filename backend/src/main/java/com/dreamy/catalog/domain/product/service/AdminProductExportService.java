package com.dreamy.catalog.domain.product.service;

import com.dreamy.catalog.domain.category.entity.Category;
import com.dreamy.catalog.domain.category.repository.CategoryRepository;
import com.dreamy.catalog.domain.category.service.CategoryTreeService;
import com.dreamy.catalog.domain.enums.ProductStatus;
import com.dreamy.catalog.domain.product.entity.Product;
import com.dreamy.catalog.domain.product.repository.ProductRepository;
import com.dreamy.catalog.domain.product.repository.ProductRepository.AdminFilter;
import com.dreamy.catalog.domain.product.repository.SkuRepository;
import com.dreamy.catalog.infra.CatalogAuditRecorder;
import com.dreamy.catalog.port.TradingQueryPort;
import com.dreamy.catalog.support.FieldErrors;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台商品 CSV 导出服务（API-CAT-02 exportAdminProducts；admin-prototype-alignment ALIGN-007）。
 * 条件与 listAdminProducts 服务端筛选对齐（不分页）；keyset 游标分批读取（pageSize=500，BE-DIM-8 内存约束）；
 * 行数达 10000 截断（X-Export-Truncated + 末行标记）。
 * L2 TRACE: V-011/V-012 / STEP-01~04 / RM-CAT-03a~d / RM-CAT-01b·c（sales_total 同款聚合）/ CV-CAT-01（CSV 转义）。
 */
@Service
public class AdminProductExportService {

    /** RM-CAT-03b 游标批大小 */
    static final int PAGE_SIZE = 500;
    /** STEP-03 / RM-CAT-03d 导出行数上限（BE-DIM-8） */
    static final int MAX_ROWS = 10000;
    /** STEP-03 截断标记末行 */
    static final String TRUNCATED_MARKER = "# TRUNCATED AT 10000 ROWS";
    /** 出参列序（API-CAT-02） */
    static final String HEADER = "id,name,slug,style_no,category_name,price,compare_at,status,"
            + "is_new,recommend,sort,stock_total,sales_total";

    private final ProductRepository productRepository;
    private final SkuRepository skuRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryTreeService treeService;
    private final TradingQueryPort tradingQueryPort;
    private final CatalogAuditRecorder audit;
    private final ObjectMapper objectMapper;

    public AdminProductExportService(ProductRepository productRepository, SkuRepository skuRepository,
                                     CategoryRepository categoryRepository, CategoryTreeService treeService,
                                     TradingQueryPort tradingQueryPort, CatalogAuditRecorder audit,
                                     ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.skuRepository = skuRepository;
        this.categoryRepository = categoryRepository;
        this.treeService = treeService;
        this.tradingQueryPort = tradingQueryPort;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    /** 导出结果（content 含 UTF-8 BOM；truncated → 控制器置 X-Export-Truncated: true） */
    public record ExportResult(byte[] content, boolean truncated, String fileName, int rowCount) {
    }

    /** API-CAT-02 主流程 */
    public ExportResult export(String statusParam, Long categoryId, String search) {
        // V-011 query 与 listAdminProducts 服务端筛选参数对齐（search/category_id/status，不含分页）
        // V-012 参数非法 → 422501（既有 4xx 参数错误口径，与 listAdminProducts V-CAT-021/022 同源）
        FieldErrors errors = new FieldErrors();
        ProductStatus status = null;
        if (statusParam != null && !statusParam.isBlank() && !"all".equals(statusParam)) {
            status = ProductStatus.of(statusParam);
            if (status == null) {
                errors.reject("status", "invalid_enum");
            }
        }
        String normalizedSearch = null;
        if (search != null && !search.trim().isEmpty()) {
            if (search.trim().length() > 80) {
                errors.reject("search", "too_long");
            } else {
                normalizedSearch = search.trim();
            }
        }
        errors.throwIfAny();
        // STEP-01 组装与列表一致的查询条件（不分页；category_id 含子树——RM-CAT-03a）
        List<Long> categoryIds = treeService.subtreeIds(categoryId);
        AdminFilter filter = new AdminFilter(status, categoryIds, normalizedSearch);
        Map<Long, String> categoryNames = new HashMap<>();
        for (Category category : categoryRepository.listAll()) {
            categoryNames.put(category.getId(), category.getName());
        }
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        long lastId = 0L;
        int rows = 0;
        boolean truncated = false;
        // STEP-02 分批游标读取逐批写 CSV（RM-CAT-03b keyset：id > lastId LIMIT 500，避免深翻页）
        while (true) {
            List<Product> batch = productRepository.listAdminKeyset(filter, lastId, PAGE_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            List<Long> ids = batch.stream().map(Product::getId).toList();
            // RM-CAT-03c 每批联查 category_name + stock_total + RM-CAT-01b 同款 sales_total 批量聚合
            Map<Long, Integer> stockTotals = skuRepository.sumStockByProductIds(ids);
            Map<Long, Integer> salesTotals = tradingQueryPort.sumSalesTotalByProductIds(ids);
            for (Product p : batch) {
                // STEP-03 / RM-CAT-03d 行数达 10000 → 停止，置 truncated 标记
                if (rows >= MAX_ROWS) {
                    truncated = true;
                    break;
                }
                appendRow(csv, p, categoryNames, stockTotals, salesTotals);
                rows++;
            }
            lastId = batch.get(batch.size() - 1).getId();
            if (truncated || batch.size() < PAGE_SIZE) {
                break;
            }
        }
        if (truncated) {
            csv.append(TRUNCATED_MARKER).append('\n');
        }
        // STEP-04 写 OperationLog（action=导出商品，detail 含筛选条件与导出行数）
        audit.record("导出商品", "商品列表", detailJson(statusParam, categoryId, normalizedSearch, rows, truncated));
        String fileName = "products-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv";
        // 出参 text/csv; charset=UTF-8 带 BOM 便于 Excel
        byte[] content = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
        return new ExportResult(content, truncated, fileName, rows);
    }

    private void appendRow(StringBuilder csv, Product p, Map<Long, String> categoryNames,
                           Map<Long, Integer> stockTotals, Map<Long, Integer> salesTotals) {
        csv.append(esc(p.getId())).append(',')
                .append(esc(p.getName())).append(',')
                .append(esc(p.getSlug())).append(',')
                .append(esc(p.getStyleNo())).append(',')
                .append(esc(categoryNames.get(p.getCategoryId()))).append(',')
                .append(esc(p.getPrice() == null ? null : p.getPrice().toPlainString())).append(',')
                .append(esc(p.getCompareAt() == null ? null : p.getCompareAt().toPlainString())).append(',')
                .append(esc(p.getStatus() == null ? null : p.getStatus().getKey())).append(',')
                .append(Boolean.TRUE.equals(p.getIsNew())).append(',')
                .append(Boolean.TRUE.equals(p.getRecommend())).append(',')
                .append(esc(p.getSort())).append(',')
                .append(stockTotals.getOrDefault(p.getId(), 0)).append(',')
                // RM-CAT-01c 缺失 product_id → sales_total = 0
                .append(salesTotals.getOrDefault(p.getId(), 0)).append('\n');
    }

    /** CV-CAT-01 标准 CSV 转义：含逗号/引号/换行 → 双引号包裹 + 引号翻倍 */
    static String esc(Object value) {
        if (value == null) {
            return "";
        }
        String s = String.valueOf(value);
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    private String detailJson(String status, Long categoryId, String search, int rows, boolean truncated) {
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("status", status);
            detail.put("category_id", categoryId);
            detail.put("search", search);
            detail.put("rows", rows);
            detail.put("truncated", truncated);
            return objectMapper.writeValueAsString(detail);
        } catch (Exception ex) {
            return null;
        }
    }
}
