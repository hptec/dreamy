package com.dreamy.catalog.domain.product.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.catalog.domain.product.entity.ProductTag;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品-标签挂载仓储（RM-CAT-140~146）。
 * L2 TRACE: catalog-data-detail §2 ProductTagRepository。
 */
@Repository
public class ProductTagRepository {

    private final ProductTagMapper mapper;

    public ProductTagRepository(ProductTagMapper mapper) {
        this.mapper = mapper;
    }

    /** RM-CAT-140 listTagIdsByProductId */
    public List<Long> listTagIdsByProductId(Long productId) {
        return mapper.selectList(new LambdaQueryWrapper<ProductTag>()
                        .eq(ProductTag::getProductId, productId)
                        .orderByAsc(ProductTag::getId))
                .stream().map(ProductTag::getTagId).toList();
    }

    /** RM-CAT-141 listByProductIds —— 批查 */
    public List<ProductTag> listByProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<ProductTag>().in(ProductTag::getProductId, productIds));
    }

    /** RM-CAT-142 replaceAll —— tag_ids 整单覆盖 */
    public void replaceAll(Long productId, List<Long> tagIds) {
        deleteByProductId(productId);
        if (tagIds != null) {
            for (Long tagId : tagIds) {
                ProductTag pt = new ProductTag();
                pt.setProductId(productId);
                pt.setTagId(tagId);
                mapper.insert(pt);
            }
        }
    }

    /** RM-CAT-143 deleteByProductId */
    public void deleteByProductId(Long productId) {
        mapper.delete(new LambdaQueryWrapper<ProductTag>().eq(ProductTag::getProductId, productId));
    }

    /** RM-CAT-144 deleteByTagId —— 删标签级联摘除（TX-CAT-020） */
    public void deleteByTagId(Long tagId) {
        mapper.delete(new LambdaQueryWrapper<ProductTag>().eq(ProductTag::getTagId, tagId));
    }

    /** RM-CAT-145 countByTagIds —— product_count 两口径（NP-CAT-002 单条 GROUP BY） */
    public Map<Long, Integer> countByTags(boolean publishedOnly) {
        List<Map<String, Object>> rows = publishedOnly
                ? mapper.countGroupByTagPublished()
                : mapper.countGroupByTagAll();
        Map<Long, Integer> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object id = row.get("tag_id") != null ? row.get("tag_id") : row.get("TAG_ID");
            Object cnt = row.get("cnt") != null ? row.get("cnt") : row.get("CNT");
            if (id instanceof Number n && cnt instanceof Number c) {
                result.put(n.longValue(), c.intValue());
            }
        }
        return result;
    }

    /** RM-CAT-146 listProductIdsByTagId —— E-CAT-02 STEP-CAT-04 标签命中（publishedOnly 由调用方 JOIN 过滤） */
    public List<Long> listProductIdsByTagId(Long tagId, int limit) {
        return mapper.selectList(new LambdaQueryWrapper<ProductTag>()
                        .eq(ProductTag::getTagId, tagId)
                        .orderByAsc(ProductTag::getId)
                        .last("LIMIT " + limit))
                .stream().map(ProductTag::getProductId).toList();
    }
}
