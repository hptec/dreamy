package com.dreamy.dto;

import com.dreamy.dto.ReviewDtos.AdminQuestionDto;
import huihao.page.Paginated;
import lombok.Getter;
import lombok.Setter;

/**
 * AdminQuestionListResponse（对齐 AdminReviewListDTO 平铺模式：Paginated 子类 + unanswered_count 同层）。
 * unanswered_count 为全表未回答总数（不随筛选变化，chips 角标派生）。
 */
@Getter
@Setter
public class AdminQuestionListDTO extends Paginated<AdminQuestionDto> {

    /** 未回答提问总数 */
    private Long unansweredCount;
}
