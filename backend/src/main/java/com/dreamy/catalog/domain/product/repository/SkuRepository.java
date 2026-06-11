package com.dreamy.catalog.domain.product.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.catalog.domain.product.consts.SkuDBConst;
import com.dreamy.catalog.domain.product.entity.Sku;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SKU 仓储（RM-CAT-120~125）。
 * L2 TRACE: catalog-data-detail §2 SkuRepository / CP-016 CAS 范式。
 */
@Repository
public class SkuRepository {

    private final SkuMapper mapper;

    public SkuRepository(SkuMapper mapper) {
        this.mapper = mapper;
    }

    /** RM-CAT-120 listByProductId */
    public List<Sku> listByProductId(Long productId) {
        return mapper.selectList(new LambdaQueryWrapper<Sku>()
                .eq(Sku::getProductId, productId)
                .orderByAsc(Sku::getId));
    }

    /** 批查（卡片/详情子资源） */
    public List<Sku> listByProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<Sku>().in(Sku::getProductId, productIds));
    }

    /** RM-CAT-121 existsBySkuCodes —— 返回已被占用的 sku_code 列表（409504 details.sku_codes，排除本商品行） */
    public List<String> existsBySkuCodes(Collection<String> codes, Long exceptProductId) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<Sku> qw = new LambdaQueryWrapper<Sku>().in(Sku::getSkuCode, codes);
        if (exceptProductId != null) {
            qw.ne(Sku::getProductId, exceptProductId);
        }
        return mapper.selectList(qw).stream().map(Sku::getSkuCode).distinct().toList();
    }

    /**
     * RM-CAT-122 casUpdate —— CP-016：`setSql("version = version + 1")` + eq(version)
     * （update(null, wrapper) 不触发 @Version 插件，手工 CAS）；affected=0 → 409508 由调用方处理。
     */
    public int casUpdate(Sku sku, Long expectedVersion) {
        return mapper.update(null, new LambdaUpdateWrapper<Sku>()
                .eq(Sku::getId, sku.getId())
                .eq(Sku::getVersion, expectedVersion)
                .set(Sku::getSkuCode, sku.getSkuCode())
                .set(Sku::getColor, sku.getColor())
                .set(Sku::getSize, sku.getSize())
                .set(Sku::getStock, sku.getStock())
                .setSql(SkuDBConst.VERSION + " = " + SkuDBConst.VERSION + " + 1"));
    }

    /** RM-CAT-123 insertBatch —— version=0 */
    public void insertBatch(List<Sku> skus) {
        if (skus == null) {
            return;
        }
        for (Sku sku : skus) {
            if (sku.getVersion() == null) {
                sku.setVersion(0L);
            }
            mapper.insert(sku);
        }
    }

    /** RM-CAT-124 deleteByIds */
    public void deleteByIds(Collection<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            mapper.deleteByIds(ids);
        }
    }

    /** deleteByProductId（TX-CAT-003 级联） */
    public void deleteByProductId(Long productId) {
        mapper.delete(new LambdaQueryWrapper<Sku>().eq(Sku::getProductId, productId));
    }

    /** RM-CAT-125 sumStockByProductIds —— stock_total 派生（MAP-CAT-003，内存聚合避免原生 GROUP BY） */
    public Map<Long, Integer> sumStockByProductIds(Collection<Long> productIds) {
        Map<Long, Integer> totals = new HashMap<>();
        for (Sku sku : listByProductIds(productIds)) {
            totals.merge(sku.getProductId(), sku.getStock() == null ? 0 : sku.getStock(), Integer::sum);
        }
        return totals;
    }
}
