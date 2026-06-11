package com.dreamy.catalog.domain.product.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.catalog.domain.product.entity.ProductImage;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 商品媒体仓储（RM-CAT-110~113）。
 * L2 TRACE: catalog-data-detail §2 ProductImageRepository。
 */
@Repository
public class ProductImageRepository {

    private final ProductImageMapper mapper;

    public ProductImageRepository(ProductImageMapper mapper) {
        this.mapper = mapper;
    }

    /** RM-CAT-110 listByProductId —— ORDER BY sort（图廊） */
    public List<ProductImage> listByProductId(Long productId) {
        return mapper.selectList(new LambdaQueryWrapper<ProductImage>()
                .eq(ProductImage::getProductId, productId)
                .orderByAsc(ProductImage::getSort)
                .orderByAsc(ProductImage::getId));
    }

    /** RM-CAT-111 listByProductIds —— 卡片主图/swatch 批查（NP-CAT-001 防 N+1） */
    public List<ProductImage> listByProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<ProductImage>()
                .in(ProductImage::getProductId, productIds)
                .orderByAsc(ProductImage::getSort));
    }

    /** RM-CAT-112 replaceAll —— 整单覆盖 */
    public void replaceAll(Long productId, List<ProductImage> rows) {
        deleteByProductId(productId);
        if (rows != null) {
            for (ProductImage row : rows) {
                row.setProductId(productId);
                mapper.insert(row);
            }
        }
    }

    /** RM-CAT-113 deleteByProductId */
    public void deleteByProductId(Long productId) {
        mapper.delete(new LambdaQueryWrapper<ProductImage>().eq(ProductImage::getProductId, productId));
    }
}
