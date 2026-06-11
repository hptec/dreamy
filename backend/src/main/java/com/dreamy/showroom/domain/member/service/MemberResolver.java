package com.dreamy.showroom.domain.member.service;

import com.dreamy.showroom.domain.enums.AssignStatus;
import com.dreamy.showroom.domain.member.entity.ShowroomMember;
import com.dreamy.showroom.domain.member.repository.ShowroomMemberRepository;
import com.dreamy.showroom.port.IdentityQueryPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 互动身份 member 解析器（E-SHR-10/11 STEP-SHR-03 共用）：
 * owner（store 主体）首次互动时自动建 member——nickname = IdentityQueryPort.getUserName(subject)
 * trim 截断 32；uk 冲突（昵称已被访客占用）→ 追加后缀 `#` + subject 末 4 位再截断 ≤32 重插一次
 * （确定性规则，CV-SHR-004 注记）；linked_customer_id=subject、assign_status=unassigned。
 * L2 TRACE: SHR-IMPL-API / TC-SHR-005/015。
 */
@Service
public class MemberResolver {

    /** 用户无展示名时的确定性缺省昵称（owner 专属互动身份；getUserName 可空容忍） */
    static final String FALLBACK_NICKNAME = "Owner";

    private final ShowroomMemberRepository memberRepository;
    private final IdentityQueryPort identityPort;

    public MemberResolver(ShowroomMemberRepository memberRepository, IdentityQueryPort identityPort) {
        this.memberRepository = memberRepository;
        this.identityPort = identityPort;
    }

    /**
     * store 主体（owner）的互动 member 解析：已绑定行复用；为空自动建（调用方须在事务内调用，TX-SHR-008/009）。
     */
    public ShowroomMember resolveStoreMember(Long showroomId, Long customerId) {
        ShowroomMember existing = memberRepository.findByShowroomAndLinkedCustomer(showroomId, customerId);
        if (existing != null) {
            return existing;
        }
        String nickname = baseNickname(customerId);
        try {
            return insertMember(showroomId, customerId, nickname);
        } catch (DuplicateKeyException ex) {
            // uk_sm_room_nickname：昵称已被访客占用 → 确定性后缀重试一次（CV-SHR-004）
            return insertMember(showroomId, customerId, suffixedNickname(nickname, customerId));
        }
    }

    /** 昵称规则纯函数（TC-SHR-005 确定性断言面）：getUserName trim 截断 32 */
    String baseNickname(Long customerId) {
        String name = identityPort.getUserName(customerId);
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            trimmed = FALLBACK_NICKNAME;
        }
        return trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
    }

    /** 冲突后缀规则纯函数：`#` + subject 末 4 位，整体截断 ≤32（同输入同输出） */
    String suffixedNickname(String base, Long customerId) {
        String subject = String.valueOf(customerId);
        String suffix = "#" + (subject.length() > 4 ? subject.substring(subject.length() - 4) : subject);
        int maxBase = 32 - suffix.length();
        String head = base.length() > maxBase ? base.substring(0, maxBase) : base;
        return head + suffix;
    }

    private ShowroomMember insertMember(Long showroomId, Long customerId, String nickname) {
        ShowroomMember member = new ShowroomMember();
        member.setShowroomId(showroomId);
        member.setNickname(nickname);
        member.setLinkedCustomerId(customerId);
        member.setAssignStatus(AssignStatus.UNASSIGNED);
        memberRepository.insert(member);
        return member;
    }
}
