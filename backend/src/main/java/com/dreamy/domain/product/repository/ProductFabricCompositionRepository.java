package com.dreamy.domain.product.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.product.entity.ProductFabricComposition;
import com.dreamy.enums.FabricLayer;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * 商品面料成分仓储（RM-FC-001~005）。
 * L2 TRACE: catalog-fabric-care-data-detail §2 ProductFabricCompositionRepository。
 */
@Repository
public class ProductFabricCompositionRepository {

    private final ProductFabricCompositionMapper mapper;

    public ProductFabricCompositionRepository(ProductFabricCompositionMapper mapper) {
        this.mapper = mapper;
    }

    /** RM-FC-001 listByProductId —— ORDER BY layer, sort_order */
    public List<ProductFabricComposition> listByProductId(Long productId) {
        return mapper.selectList(new LambdaQueryWrapper<ProductFabricComposition>()
                .eq(ProductFabricComposition::getProductId, productId)
                .orderByAsc(ProductFabricComposition::getLayer)
                .orderByAsc(ProductFabricComposition::getSortOrder));
    }

    /** RM-FC-002 listByProductIds —— 批查防 N+1 */
    public List<ProductFabricComposition> listByProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<ProductFabricComposition>()
                .in(ProductFabricComposition::getProductId, productIds)
                .orderByAsc(ProductFabricComposition::getLayer)
                .orderByAsc(ProductFabricComposition::getSortOrder));
    }

    /** RM-FC-003 replaceAll —— 整单覆盖：DELETE + 批量 INSERT */
    public void replaceAll(Long productId, List<ProductFabricComposition> rows) {
        deleteByProductId(productId);
        if (rows != null && !rows.isEmpty()) {
            for (int i = 0; i < rows.size(); i++) {
                ProductFabricComposition row = rows.get(i);
                row.setProductId(productId);
                // 若 sortOrder 未设置，按提交顺序分配
                if (row.getSortOrder() == null) {
                    row.setSortOrder(i);
                }
                mapper.insert(row);
            }
        }
    }

    /** RM-FC-004 deleteByProductId —— 商品删除级联清理 */
    public void deleteByProductId(Long productId) {
        mapper.delete(new LambdaQueryWrapper<ProductFabricComposition>()
                .eq(ProductFabricComposition::getProductId, productId));
    }

    /** RM-FC-005 validatePercentageSum —— 返回指定 product_id + layer 的 percentage 总和（业务规则校验） */
    public BigDecimal sumPercentageByProductAndLayer(Long productId, FabricLayer layer) {
        return mapper.sumPercentageByProductAndLayer(productId, layer.getKey());
    }
}
