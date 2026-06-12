package com.dreamy.domain.product.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.product.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * ProductMapper。表 product。
 * FULLTEXT ngram 检索与 GROUP BY 聚合无 ORM 等价物，按 L2 设计落 @Select 原生 SQL：
 * - RM-CAT-084 fulltextSearchMain（IDX-CAT-004，决策 17 必含）
 * - RM-CAT-096/097 countByCategoryIds 两口径（NP-CAT-002 单条 GROUP BY，禁止逐节点 COUNT）
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /** RM-CAT-084 EN 主检索：MATCH(name, subtitle) AGAINST(? IN NATURAL LANGUAGE MODE) AND status=published */
    @Select("SELECT id FROM product WHERE status = 'published' "
            + "AND MATCH(name, subtitle) AGAINST(#{q} IN NATURAL LANGUAGE MODE) "
            + "ORDER BY MATCH(name, subtitle) AGAINST(#{q} IN NATURAL LANGUAGE MODE) DESC "
            + "LIMIT 500")
    List<Long> fulltextSearchMain(@Param("q") String q);

    /** RM-CAT-096 published 口径 product_count 聚合（消费端） */
    @Select("SELECT category_id AS category_id, COUNT(*) AS cnt FROM product "
            + "WHERE status = 'published' GROUP BY category_id")
    List<Map<String, Object>> countGroupByCategoryPublished();

    /** RM-CAT-097 全量口径 product_count 聚合（后台含 draft） */
    @Select("SELECT category_id AS category_id, COUNT(*) AS cnt FROM product GROUP BY category_id")
    List<Map<String, Object>> countGroupByCategoryAll();
}
