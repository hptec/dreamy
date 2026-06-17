package com.dreamy.domain.product.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.product.entity.ProductCollection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** ProductCollectionMapper。表 product_collection。RM-CAT-145 两口径计数为 GROUP BY 原生 SQL（NP-CAT-002）。 */
@Mapper
public interface ProductCollectionMapper extends BaseMapper<ProductCollection> {

    /** RM-CAT-145 published 口径：product_collection JOIN product(status=published) GROUP BY collection_id */
    @Select("SELECT pc.collection_id AS collection_id, COUNT(*) AS cnt FROM product_collection pc "
            + "JOIN product p ON p.id = pc.product_id AND p.status = 2 "
            + "GROUP BY pc.collection_id")
    List<Map<String, Object>> countGroupByCollectionPublished();

    /** RM-CAT-145 全量口径（后台含 draft） */
    @Select("SELECT collection_id AS collection_id, COUNT(*) AS cnt FROM product_collection GROUP BY collection_id")
    List<Map<String, Object>> countGroupByCollectionAll();
}
