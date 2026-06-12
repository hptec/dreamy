package com.dreamy.security;

import com.dreamy.security.ShowroomGuestValidator;
import com.dreamy.domain.showroom.entity.Showroom;
import com.dreamy.domain.showroom.repository.ShowroomRepository;
import org.springframework.stereotype.Component;

/**
 * ShowroomGuestValidator 实现 bean（showroom-api-detail 0.2-d，本域提供注入 StoreJwtFilter）。
 * 按 PK 点查 showroom 行（轻量主键查询，**不缓存**——CACHE-SHR-001 连带口径：缓存会破坏
 * 「重置后即时失效」语义）：行不存在（Showroom 已删除，E-SHR-05 STEP-SHR-03 即时失效闭环）
 * 或 invite_version != claims.inv_ver（邀请链接已重置，E-SHR-06 级联失效通道②）→ 无效（401101）。
 * L2 TRACE: SHR-IMPL-FILTER-GUEST / CV-SHR-008 / TC-SHR-013/018。
 */
@Component
public class ShowroomGuestValidatorImpl implements ShowroomGuestValidator {

    private final ShowroomRepository showroomRepository;

    public ShowroomGuestValidatorImpl(ShowroomRepository showroomRepository) {
        this.showroomRepository = showroomRepository;
    }

    @Override
    public boolean isGuestSessionValid(long showroomId, long inviteVersion) {
        Showroom showroom = showroomRepository.findById(showroomId);
        return showroom != null
                && showroom.getInviteVersion() != null
                && showroom.getInviteVersion().longValue() == inviteVersion;
    }
}
