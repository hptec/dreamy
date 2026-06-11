package com.dreamy.catalog.domain.product.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.catalog.domain.product.entity.SizeChartRow;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 尺码表仓储（RM-CAT-130~132）。
 * L2 TRACE: catalog-data-detail §2 SizeChartRowRepository。
 */
@Repository
public class SizeChartRowRepository {

    private final SizeChartRowMapper mapper;

    public SizeChartRowRepository(SizeChartRowMapper mapper) {
        this.mapper = mapper;
    }

    /** RM-CAT-130 listByProductId —— ORDER BY bust ASC（E-CAT-05 区间匹配前置序）；E-CAT-04 按 id ASC 由调用方重排 */
    public List<SizeChartRow> listByProductIdOrderByBust(Long productId) {
        return mapper.selectList(new LambdaQueryWrapper<SizeChartRow>()
                .eq(SizeChartRow::getProductId, productId)
                .orderByAsc(SizeChartRow::getBust)
                .orderByAsc(SizeChartRow::getId));
    }

    /** E-CAT-04 STEP-CAT-03：尺码表按 id ASC */
    public List<SizeChartRow> listByProductIdOrderById(Long productId) {
        return mapper.selectList(new LambdaQueryWrapper<SizeChartRow>()
                .eq(SizeChartRow::getProductId, productId)
                .orderByAsc(SizeChartRow::getId));
    }

    /** 批查（详情装配） */
    public List<SizeChartRow> listByProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<SizeChartRow>()
                .in(SizeChartRow::getProductId, productIds)
                .orderByAsc(SizeChartRow::getId));
    }

    /** RM-CAT-131 replaceAll —— 整单覆盖 */
    public void replaceAll(Long productId, List<SizeChartRow> rows) {
        deleteByProductId(productId);
        if (rows != null) {
            for (SizeChartRow row : rows) {
                row.setProductId(productId);
                mapper.insert(row);
            }
        }
    }

    /** RM-CAT-132 deleteByProductId */
    public void deleteByProductId(Long productId) {
        mapper.delete(new LambdaQueryWrapper<SizeChartRow>().eq(SizeChartRow::getProductId, productId));
    }
}
