package com.dreamy.domain.product.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.product.entity.ProductTranslation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * ProductTranslationMapper。表 product_translation。
 * RM-CAT-103 fulltextSearch（IDX-CAT-010 FULLTEXT 必含）为原生 SQL。
 */
@Mapper
public interface ProductTranslationMapper extends BaseMapper<ProductTranslation> {

    /** RM-CAT-103 ES/FR 附表检索：MATCH(name, subtitle) AGAINST(?) AND locale=?，JOIN 主表 published */
    @Select("SELECT pt.product_id FROM product_translation pt "
            + "JOIN product p ON p.id = pt.product_id AND p.status = 'published' "
            + "WHERE pt.locale = #{locale} "
            + "AND MATCH(pt.name, pt.subtitle) AGAINST(#{q} IN NATURAL LANGUAGE MODE) "
            + "ORDER BY MATCH(pt.name, pt.subtitle) AGAINST(#{q} IN NATURAL LANGUAGE MODE) DESC "
            + "LIMIT 500")
    List<Long> fulltextSearch(@Param("q") String q, @Param("locale") String locale);
}
