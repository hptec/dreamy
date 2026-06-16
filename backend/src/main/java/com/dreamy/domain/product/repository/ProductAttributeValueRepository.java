package com.dreamy.domain.product.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.product.entity.ProductAttributeValue;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 商品动态属性值仓储（EAV）。整单覆盖与 images/tags 子表同模式（DELETE+批量 INSERT）。
 */
@Repository
public class ProductAttributeValueRepository {

    private final ProductAttributeValueMapper mapper;

    public ProductAttributeValueRepository(ProductAttributeValueMapper mapper) {
        this.mapper = mapper;
    }

    /** PDP/编辑详情回读（uk_pav 前缀 product_id；id 序保持写入顺序） */
    public List<ProductAttributeValue> listByProductId(Long productId) {
        return mapper.selectList(new LambdaQueryWrapper<ProductAttributeValue>()
                .eq(ProductAttributeValue::getProductId, productId)
                .orderByAsc(ProductAttributeValue::getId));
    }

    /** 批查（CSV 导出等批量装配） */
    public List<ProductAttributeValue> listByProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<ProductAttributeValue>()
                .in(ProductAttributeValue::getProductId, productIds)
                .orderByAsc(ProductAttributeValue::getId));
    }

    /** 整单覆盖（TX-CAT-001/002 事务内） */
    public void replaceAll(Long productId, List<ProductAttributeValue> rows) {
        deleteByProductId(productId);
        if (rows != null) {
            for (ProductAttributeValue row : rows) {
                row.setProductId(productId);
                mapper.insert(row);
            }
        }
    }

    /** 商品删除级联（TX-CAT-003） */
    public void deleteByProductId(Long productId) {
        mapper.delete(new LambdaQueryWrapper<ProductAttributeValue>()
                .eq(ProductAttributeValue::getProductId, productId));
    }

    /** attribute_def 删除守卫（409507）：被引用商品数 */
    public long countByAttributeId(Long attributeId) {
        return mapper.selectCount(new LambdaQueryWrapper<ProductAttributeValue>()
                .eq(ProductAttributeValue::getAttributeId, attributeId));
    }

    /** attribute_def options 收缩守卫（409507）：被引用的目标值行数 */
    public long countByAttributeIdAndValues(Long attributeId, Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        return mapper.selectCount(new LambdaQueryWrapper<ProductAttributeValue>()
                .eq(ProductAttributeValue::getAttributeId, attributeId)
                .in(ProductAttributeValue::getValue, values));
    }

    /** attribute_def 强制删除级联：清理所有商品属性值 */
    public void deleteByAttributeId(Long attributeId) {
        mapper.delete(new LambdaQueryWrapper<ProductAttributeValue>()
                .eq(ProductAttributeValue::getAttributeId, attributeId));
    }
}
