package com.dreamy.catalog.domain.product.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.catalog.domain.enums.ProductStatus;
import com.dreamy.catalog.domain.product.entity.Product;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 商品仓储（RM-CAT-080~099）。
 * L2 TRACE: catalog-data-detail §2 ProductRepository。
 */
@Repository
public class ProductRepository {

    private final ProductMapper productMapper;

    public ProductRepository(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    /** store 列表筛选条件（RM-CAT-083 入参；attrFilters: attribute_id → 候选值集，OR/IN 跨键 AND） */
    public record StoreFilter(List<Long> categoryIds, Long tagId, String color, String size,
                              BigDecimal priceMin, BigDecimal priceMax, String sort,
                              Map<Long, Set<String>> attrFilters) {
    }

    /** admin 列表筛选条件（RM-CAT-085 入参） */
    public record AdminFilter(ProductStatus status, List<Long> categoryIds, String search) {
    }

    /** RM-CAT-080 findBySlugPublished —— uk_product_slug 点查（FLOW-P01 热路径） */
    public Product findBySlugPublished(String slug) {
        return productMapper.selectOne(new LambdaQueryWrapper<Product>()
                .eq(Product::getSlug, slug)
                .eq(Product::getStatus, ProductStatus.PUBLISHED));
    }

    /** RM-CAT-081 findById */
    public Product findById(Long id) {
        return id == null ? null : productMapper.selectById(id);
    }

    /** RM-CAT-082 existsBySlugExcept —— 409501（编辑排除自身） */
    public boolean existsBySlugExcept(String slug, Long exceptId) {
        LambdaQueryWrapper<Product> qw = new LambdaQueryWrapper<Product>().eq(Product::getSlug, slug);
        if (exceptId != null) {
            qw.ne(Product::getId, exceptId);
        }
        return productMapper.selectCount(qw) > 0;
    }

    /**
     * RM-CAT-083 pageStoreList —— status=published + 筛选叠加（IDX-CAT-002/003/006/007）；
     * tag/color/size 走 EXISTS 子查询（E-CAT-01 STEP-CAT-03）。
     */
    public Page<Product> pageStoreList(StoreFilter filter, int page, int pageSize) {
        LambdaQueryWrapper<Product> qw = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, ProductStatus.PUBLISHED);
        if (filter.categoryIds() != null) {
            if (filter.categoryIds().isEmpty()) {
                // 分类不存在 → 视为空集，返回空页不 404（E-CAT-01 STEP-CAT-02）
                return new Page<>(page, pageSize, 0);
            }
            qw.in(Product::getCategoryId, filter.categoryIds());
        }
        if (filter.tagId() != null) {
            qw.exists("SELECT 1 FROM product_tag ptag WHERE ptag.product_id = product.id AND ptag.tag_id = {0}",
                    filter.tagId());
        }
        if (filter.color() != null) {
            qw.exists("SELECT 1 FROM sku s WHERE s.product_id = product.id AND s.color = {0}", filter.color());
        }
        if (filter.size() != null) {
            qw.exists("SELECT 1 FROM sku s WHERE s.product_id = product.id AND s.size = {0}", filter.size());
        }
        // attrs：每维度 EXISTS（idx_pav_filter(attribute_id,value,product_id) 覆盖索引）；同维多值 IN=OR，跨维 AND
        if (filter.attrFilters() != null) {
            for (Map.Entry<Long, Set<String>> entry : filter.attrFilters().entrySet()) {
                List<Object> args = new ArrayList<>();
                args.add(entry.getKey());
                StringBuilder in = new StringBuilder();
                int i = 1;
                for (String value : entry.getValue()) {
                    if (in.length() > 0) {
                        in.append(", ");
                    }
                    in.append('{').append(i++).append('}');
                    args.add(value);
                }
                qw.exists("SELECT 1 FROM product_attribute_value pav WHERE pav.product_id = product.id "
                        + "AND pav.attribute_id = {0} AND pav.`value` IN (" + in + ")", args.toArray());
            }
        }
        if (filter.priceMin() != null) {
            qw.ge(Product::getPrice, filter.priceMin());
        }
        if (filter.priceMax() != null) {
            qw.le(Product::getPrice, filter.priceMax());
        }
        switch (filter.sort() == null ? "recommended" : filter.sort()) {
            case "newest" -> qw.orderByDesc(Product::getCreatedAt);
            case "price_asc" -> qw.orderByAsc(Product::getPrice);
            case "price_desc" -> qw.orderByDesc(Product::getPrice);
            default -> qw.orderByAsc(Product::getSort).orderByDesc(Product::getCreatedAt);
        }
        return productMapper.selectPage(new Page<>(page, pageSize), qw);
    }

    /** RM-CAT-084 fulltextSearchMain（委托原生 SQL，IDX-CAT-004） */
    public List<Long> fulltextSearchMain(String q) {
        return productMapper.fulltextSearchMain(q);
    }

    /** RM-CAT-085 pageAdminList —— name/style_no LIKE（E-CAT-08） */
    public Page<Product> pageAdminList(AdminFilter filter, int page, int pageSize) {
        LambdaQueryWrapper<Product> qw = new LambdaQueryWrapper<>();
        if (filter.status() != null) {
            qw.eq(Product::getStatus, filter.status());
        }
        if (filter.categoryIds() != null) {
            if (filter.categoryIds().isEmpty()) {
                return new Page<>(page, pageSize, 0);
            }
            qw.in(Product::getCategoryId, filter.categoryIds());
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            String s = filter.search().trim();
            qw.and(w -> w.like(Product::getName, s).or().like(Product::getStyleNo, s));
        }
        qw.orderByAsc(Product::getSort).orderByDesc(Product::getId);
        return productMapper.selectPage(new Page<>(page, pageSize), qw);
    }

    /** RM-CAT-086 insert */
    public void insert(Product product) {
        productMapper.insert(product);
    }

    /**
     * RM-CAT-03a/b（admin-prototype-alignment 导出）keyset 游标读取：
     * 条件同 pageAdminList（status/categoryIds 子树/search）；ORDER BY id ASC, WHERE id > :lastId LIMIT :limit
     * （keyset 分页避免深翻页，批大小由调用方控制——BE-DIM-8）。
     */
    public List<Product> listAdminKeyset(AdminFilter filter, long lastId, int limit) {
        LambdaQueryWrapper<Product> qw = new LambdaQueryWrapper<>();
        if (filter.status() != null) {
            qw.eq(Product::getStatus, filter.status());
        }
        if (filter.categoryIds() != null) {
            if (filter.categoryIds().isEmpty()) {
                return List.of();
            }
            qw.in(Product::getCategoryId, filter.categoryIds());
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            String s = filter.search().trim();
            qw.and(w -> w.like(Product::getName, s).or().like(Product::getStyleNo, s));
        }
        qw.gt(Product::getId, lastId).orderByAsc(Product::getId).last("LIMIT " + limit);
        return productMapper.selectList(qw);
    }

    /**
     * RM-CAT-087 update —— 冗余列不在 SET 列表（TX-CAT-002）。
     * 调用方须保证 entity 的 sales30d/salesRefreshedAt/ratingAvg/ratingCount 为 null（MP 默认 NOT_NULL 策略跳过）。
     */
    public void update(Product product) {
        product.setSales30d(null);
        product.setSalesRefreshedAt(null);
        product.setRatingAvg(null);
        product.setRatingCount(null);
        productMapper.updateById(product);
    }

    /** RM-CAT-088 deleteById */
    public void deleteById(Long id) {
        productMapper.deleteById(id);
    }

    /** RM-CAT-089 updateStatus（TX-CAT-004） */
    public void updateStatus(Long id, ProductStatus status) {
        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, id)
                .set(Product::getStatus, status));
    }

    /** RM-CAT-090 patchFlags —— 仅 UPDATE 提交的字段（E-CAT-14 STEP-CAT-02） */
    public void patchFlags(Long id, Boolean isNew, Boolean isBest, Boolean recommend, Integer sort) {
        LambdaUpdateWrapper<Product> uw = new LambdaUpdateWrapper<Product>().eq(Product::getId, id);
        if (isNew != null) {
            uw.set(Product::getIsNew, isNew);
        }
        if (isBest != null) {
            uw.set(Product::getIsBest, isBest);
        }
        if (recommend != null) {
            uw.set(Product::getRecommend, recommend);
        }
        if (sort != null) {
            uw.set(Product::getSort, sort);
        }
        productMapper.update(null, uw);
    }

    /** RM-CAT-091 listRecoNewArrivals —— created_at DESC（决策 29） */
    public List<Product> listRecoNewArrivals(int limit) {
        return productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, ProductStatus.PUBLISHED)
                .orderByDesc(Product::getCreatedAt)
                .last("LIMIT " + limit));
    }

    /** RM-CAT-092 listRecoBestSellers —— sales_30d DESC（IDX-CAT-005）；全 0 冷启动由 Service 回退 recommend */
    public List<Product> listRecoBestSellers(int limit) {
        return productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, ProductStatus.PUBLISHED)
                .gt(Product::getSales30d, 0)
                .orderByDesc(Product::getSales30d)
                .last("LIMIT " + limit));
    }

    /** RM-CAT-092 冷启动回退：recommend=true ORDER BY sort（决策 29） */
    public List<Product> listRecoRecommendFallback(int limit) {
        return productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, ProductStatus.PUBLISHED)
                .eq(Product::getRecommend, true)
                .orderByAsc(Product::getSort)
                .last("LIMIT " + limit));
    }

    /** RM-CAT-093 listRecoByTag —— EXISTS product_tag(tag_id=?) ORDER BY sort */
    public List<Product> listRecoByTag(Long tagId, int limit) {
        return productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, ProductStatus.PUBLISHED)
                .exists("SELECT 1 FROM product_tag ptag WHERE ptag.product_id = product.id AND ptag.tag_id = {0}", tagId)
                .orderByAsc(Product::getSort)
                .last("LIMIT " + limit));
    }

    /** RM-CAT-094 listRecoSimilar —— 同 category + 价格段 ±30% 且 id≠基准 ORDER BY sort */
    public List<Product> listRecoSimilar(Long categoryId, BigDecimal priceLow, BigDecimal priceHigh,
                                         Long exceptId, int limit) {
        return productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, ProductStatus.PUBLISHED)
                .eq(Product::getCategoryId, categoryId)
                .ge(Product::getPrice, priceLow)
                .le(Product::getPrice, priceHigh)
                .ne(Product::getId, exceptId)
                .orderByAsc(Product::getSort)
                .last("LIMIT " + limit));
    }

    /** RM-CAT-094 放宽补足：仅同 category_id（E-CAT-03 STEP-CAT-02 ymal 不足 limit 时） */
    public List<Product> listRecoSameCategory(Long categoryId, Long exceptId, int limit) {
        return productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, ProductStatus.PUBLISHED)
                .eq(Product::getCategoryId, categoryId)
                .ne(Product::getId, exceptId)
                .orderByAsc(Product::getSort)
                .last("LIMIT " + limit));
    }

    /** RM-CAT-095 listRecoCrossCategory —— 同根品类其他叶子分类（分类集合由 Service 经分类树解析） */
    public List<Product> listRecoCrossCategory(Collection<Long> categoryIds, Long exceptProductId, int limit) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<Product> qw = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, ProductStatus.PUBLISHED)
                .in(Product::getCategoryId, categoryIds);
        if (exceptProductId != null) {
            qw.ne(Product::getId, exceptProductId);
        }
        qw.orderByAsc(Product::getSort).last("LIMIT " + limit);
        return productMapper.selectList(qw);
    }

    /** RM-CAT-096 countByCategoryIdsPublished —— 消费端口径（NP-CAT-002） */
    public Map<Long, Integer> countByCategoryPublished() {
        return toCountMap(productMapper.countGroupByCategoryPublished());
    }

    /** RM-CAT-097 countByCategoryIdsAll —— 后台口径（含 draft） */
    public Map<Long, Integer> countByCategoryAll() {
        return toCountMap(productMapper.countGroupByCategoryAll());
    }

    /** RM-CAT-098 updateSales30d —— EVT-CAT-001/003 专用（仅消费者/定时任务可写） */
    public void updateSales30d(Long productId, int sales, LocalDateTime refreshedAt) {
        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, productId)
                .set(Product::getSales30d, sales)
                .set(Product::getSalesRefreshedAt, refreshedAt));
    }

    /** RM-CAT-099 updateRating —— EVT-CAT-002 专用 */
    public void updateRating(Long productId, BigDecimal avg, int count) {
        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, productId)
                .set(Product::getRatingAvg, avg)
                .set(Product::getRatingCount, count));
    }

    /** EVT-CAT-003 窗口刷新候选：sales_30d>0 的商品 id 集合 */
    public List<Long> listIdsWithSalesPositive() {
        return productMapper.selectList(new LambdaQueryWrapper<Product>()
                        .select(Product::getId)
                        .gt(Product::getSales30d, 0))
                .stream().map(Product::getId).toList();
    }

    /** 批量取商品（卡片装配等） */
    public List<Product> listByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return productMapper.selectList(new LambdaQueryWrapper<Product>().in(Product::getId, ids));
    }

    private Map<Long, Integer> toCountMap(List<Map<String, Object>> rows) {
        Map<Long, Integer> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object id = row.get("category_id") != null ? row.get("category_id") : row.get("CATEGORY_ID");
            Object cnt = row.get("cnt") != null ? row.get("cnt") : row.get("CNT");
            if (id instanceof Number n && cnt instanceof Number c) {
                result.put(n.longValue(), c.intValue());
            }
        }
        return result;
    }
}
