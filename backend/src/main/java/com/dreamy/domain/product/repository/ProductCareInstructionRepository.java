package com.dreamy.domain.product.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.product.entity.ProductCareInstruction;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 商品-护理标签关联仓储（RM-FC-020~024）。
 * L2 TRACE: catalog-fabric-care-data-detail §2 ProductCareInstructionRepository。
 */
@Repository
public class ProductCareInstructionRepository {

    private final ProductCareInstructionMapper mapper;

    public ProductCareInstructionRepository(ProductCareInstructionMapper mapper) {
        this.mapper = mapper;
    }

    /** RM-FC-020 listByProductId —— ORDER BY sort_order */
    public List<ProductCareInstruction> listByProductId(Long productId) {
        return mapper.selectList(new LambdaQueryWrapper<ProductCareInstruction>()
                .eq(ProductCareInstruction::getProductId, productId)
                .orderByAsc(ProductCareInstruction::getSortOrder));
    }

    /** RM-FC-021 listByProductIds —— 批查防 N+1 */
    public List<ProductCareInstruction> listByProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<ProductCareInstruction>()
                .in(ProductCareInstruction::getProductId, productIds)
                .orderByAsc(ProductCareInstruction::getSortOrder));
    }

    /** RM-FC-022 replaceAll —— 整单覆盖：DELETE + 批量 INSERT */
    public void replaceAll(Long productId, List<Long> careIds) {
        deleteByProductId(productId);
        if (careIds != null && !careIds.isEmpty()) {
            for (int i = 0; i < careIds.size(); i++) {
                ProductCareInstruction pci = new ProductCareInstruction();
                pci.setProductId(productId);
                pci.setCareId(careIds.get(i));
                pci.setSortOrder(i);
                mapper.insert(pci);
            }
        }
    }

    /** RM-FC-023 deleteByProductId —— 商品删除级联清理 */
    public void deleteByProductId(Long productId) {
        mapper.delete(new LambdaQueryWrapper<ProductCareInstruction>()
                .eq(ProductCareInstruction::getProductId, productId));
    }

    /** RM-FC-024 deleteByCareId —— 护理标签删除级联摘除 */
    public void deleteByCareId(Long careId) {
        mapper.delete(new LambdaQueryWrapper<ProductCareInstruction>()
                .eq(ProductCareInstruction::getCareId, careId));
    }
}
