package com.dreamy.domain.product.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.product.entity.ProductTranslation;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 商品翻译仓储（RM-CAT-100~103）。
 * L2 TRACE: catalog-data-detail §2 ProductTranslationRepository。
 */
@Repository
public class ProductTranslationRepository {

    private final ProductTranslationMapper mapper;

    public ProductTranslationRepository(ProductTranslationMapper mapper) {
        this.mapper = mapper;
    }

    /** RM-CAT-100 listByProductIds —— 批查（FLOW-P01 翻译合并，NP-CAT-001）；locale 可空=全部 */
    public List<ProductTranslation> listByProductIds(Collection<Long> productIds, String locale) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<ProductTranslation> qw = new LambdaQueryWrapper<ProductTranslation>()
                .in(ProductTranslation::getProductId, productIds);
        if (locale != null) {
            qw.eq(ProductTranslation::getLocale, locale);
        }
        return mapper.selectList(qw);
    }

    /** RM-CAT-101 replaceAll —— 整单覆盖（TX-CAT-001/002） */
    public void replaceAll(Long productId, List<ProductTranslation> rows) {
        deleteByProductId(productId);
        if (rows != null) {
            for (ProductTranslation row : rows) {
                row.setProductId(productId);
                mapper.insert(row);
            }
        }
    }

    /** RM-CAT-102 deleteByProductId */
    public void deleteByProductId(Long productId) {
        mapper.delete(new LambdaQueryWrapper<ProductTranslation>()
                .eq(ProductTranslation::getProductId, productId));
    }

    /** RM-CAT-103 fulltextSearch（委托原生 SQL，IDX-CAT-010） */
    public List<Long> fulltextSearch(String q, String locale) {
        return mapper.fulltextSearch(q, locale);
    }
}
