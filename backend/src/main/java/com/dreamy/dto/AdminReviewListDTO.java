package com.dreamy.dto;

import com.dreamy.dto.ReviewDtos.AdminReviewDto;
import huihao.page.Paginated;
import lombok.Getter;
import lombok.Setter;

/**
 * AdminReviewListResponse（MAP-REV-007：Paginated 子类 + pending_count 平铺同层）。
 * pending_count 为全表 pending 总数（不随筛选变化，状态 chips 角标派生——E-REV-06 STEP-REV-03）。
 * L2 TRACE: MAP-REV-007 / TC-REV-030 / TC-REV-037。
 */
@Getter
@Setter
public class AdminReviewListDTO extends Paginated<AdminReviewDto> {

    /** 待审核总数（RM-REV-007） */
    private Long pendingCount;
}
