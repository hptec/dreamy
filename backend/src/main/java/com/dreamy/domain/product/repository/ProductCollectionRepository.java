package com.dreamy.domain.product.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.product.entity.ProductCollection;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品-集合挂载仓储（RM-CAT-140~146）。
 * L2 TRACE: catalog-data-detail §2 ProductCollectionRepository。
 */
@Repository
public class ProductCollectionRepository {

    private final ProductCollectionMapper mapper;

    public ProductCollectionRepository(ProductCollectionMapper mapper) {
        this.mapper = mapper;
    }

    /** RM-CAT-140 listCollectionIdsByProductId */
    public List<Long> listCollectionIdsByProductId(Long productId) {
        return mapper.selectList(new LambdaQueryWrapper<ProductCollection>()
                        .eq(ProductCollection::getProductId, productId)
                        .orderByAsc(ProductCollection::getId))
                .stream().map(ProductCollection::getCollectionId).toList();
    }

    /** RM-CAT-141 listByProductIds —— 批查 */
    public List<ProductCollection> listByProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<ProductCollection>().in(ProductCollection::getProductId, productIds));
    }

    /** RM-CAT-142 replaceAll —— collection_ids 整单覆盖 */
    public void replaceAll(Long productId, List<Long> collectionIds) {
        deleteByProductId(productId);
        if (collectionIds != null) {
            for (Long collectionId : collectionIds) {
                ProductCollection pc = new ProductCollection();
                pc.setProductId(productId);
                pc.setCollectionId(collectionId);
                mapper.insert(pc);
            }
        }
    }

    /** RM-CAT-143 deleteByProductId */
    public void deleteByProductId(Long productId) {
        mapper.delete(new LambdaQueryWrapper<ProductCollection>().eq(ProductCollection::getProductId, productId));
    }

    /** RM-CAT-144 deleteByCollectionId —— 删集合级联摘除（TX-CAT-020） */
    public void deleteByCollectionId(Long collectionId) {
        mapper.delete(new LambdaQueryWrapper<ProductCollection>().eq(ProductCollection::getCollectionId, collectionId));
    }

    /** RM-CAT-145 countByCollectionIds —— product_count 两口径（NP-CAT-002 单条 GROUP BY） */
    public Map<Long, Integer> countByCollections(boolean publishedOnly) {
        List<Map<String, Object>> rows = publishedOnly
                ? mapper.countGroupByCollectionPublished()
                : mapper.countGroupByCollectionAll();
        Map<Long, Integer> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object id = row.get("collection_id") != null ? row.get("collection_id") : row.get("COLLECTION_ID");
            Object cnt = row.get("cnt") != null ? row.get("cnt") : row.get("CNT");
            if (id instanceof Number n && cnt instanceof Number c) {
                result.put(n.longValue(), c.intValue());
            }
        }
        return result;
    }

    /** RM-CAT-146 listProductIdsByCollectionId —— E-CAT-02 STEP-CAT-04 集合命中（publishedOnly 由调用方 JOIN 过滤） */
    public List<Long> listProductIdsByCollectionId(Long collectionId, int limit) {
        return mapper.selectList(new LambdaQueryWrapper<ProductCollection>()
                        .eq(ProductCollection::getCollectionId, collectionId)
                        .orderByAsc(ProductCollection::getId)
                        .last("LIMIT " + limit))
                .stream().map(ProductCollection::getProductId).toList();
    }
}
