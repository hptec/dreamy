package com.dreamy.domain.product.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.product.entity.ProductTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** ProductTagMapper。表 product_tag。RM-CAT-145 两口径计数为 GROUP BY 原生 SQL（NP-CAT-002）。 */
@Mapper
public interface ProductTagMapper extends BaseMapper<ProductTag> {

    /** RM-CAT-145 published 口径：product_tag JOIN product(status=published) GROUP BY tag_id */
    @Select("SELECT pt.tag_id AS tag_id, COUNT(*) AS cnt FROM product_tag pt "
            + "JOIN product p ON p.id = pt.product_id AND p.status = 2 "
            + "GROUP BY pt.tag_id")
    List<Map<String, Object>> countGroupByTagPublished();

    /** RM-CAT-145 全量口径（后台含 draft） */
    @Select("SELECT tag_id AS tag_id, COUNT(*) AS cnt FROM product_tag GROUP BY tag_id")
    List<Map<String, Object>> countGroupByTagAll();
}
