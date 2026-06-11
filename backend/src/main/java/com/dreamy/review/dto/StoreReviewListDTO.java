package com.dreamy.review.dto;

import com.dreamy.review.dto.ReviewDtos.StoreReviewDto;
import huihao.page.Paginated;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

/**
 * StoreReviewListResponse（MAP-REV-006 定稿：Paginated 子类继承方案）。
 * 继承下 Jackson 序列化天然字段平铺同层——六分页字段 + rating_avg/rating_count/rating_breakdown
 * 并列，精确命中契约 schema（零自定义 serializer；备选「组合包装」因嵌套层级偏离契约被否）。
 * L2 TRACE: MAP-REV-006 / TC-REV-030（序列化形状回归）。
 */
@Getter
@Setter
public class StoreReviewListDTO extends Paginated<StoreReviewDto> {

    /** approved 评价均分（聚合派生，DECIMAL 2 位 HALF_UP，零评价=0） */
    private BigDecimal ratingAvg;

    /** approved 评价总数（聚合派生） */
    private Integer ratingCount;

    /** 1~5 星各档数量（聚合派生，key="1".."5" 全档） */
    private Map<String, Integer> ratingBreakdown;
}
