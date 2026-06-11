package com.dreamy.showroom.domain.member.service;

import com.dreamy.identity.security.JwtTokenProvider;
import com.dreamy.showroom.domain.enums.AssignStatus;
import com.dreamy.showroom.domain.member.entity.ShowroomMember;
import com.dreamy.showroom.domain.member.repository.ShowroomMemberRepository;
import com.dreamy.showroom.domain.showroom.entity.Showroom;
import com.dreamy.showroom.domain.showroom.repository.ShowroomRepository;
import com.dreamy.showroom.domain.showroom.service.ShowroomDetailAssembler;
import com.dreamy.showroom.dto.ShowroomDtos.GuestSessionCreate;
import com.dreamy.showroom.dto.ShowroomDtos.GuestSessionDto;
import com.dreamy.showroom.error.ShowroomErrorCode;
import com.dreamy.showroom.error.ShowroomException;
import com.dreamy.showroom.infra.ShowroomTxRunner;
import com.dreamy.showroom.support.FieldErrors;
import com.dreamy.showroom.support.ShowroomValidation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * E-SHR-07 createShowroomGuestSession（F-068 免注册参与，决策 20.2，TX-SHR-005）。
 * - invite_token 三态裁决：当前值命中 → 正常；invite_token_prev 命中 → 410101（链接已重置）；
 *   皆未命中 → 401101（无效，防探测不区分更多细节）。
 * - 昵称复用裁决（STEP-SHR-03）+ 受保护昵称（CV-SHR-009：已绑定注册客户的去重身份不可被匿名复用 409101）
 *   + 并发 uk 兜底回读重裁决（STEP-SHR-04，CV-SHR-004）。
 * - 绑定回填（STEP-SHR-05，RM-SHR-041 幂等条件更新）：白名单可选注入 store principal。
 * - guest JWT 签发在事务提交后（纯计算无副作用；member 已提交属可接受残留——再次进入复用）。
 * 日志脱敏：invite_token/guest JWT 原文一律 [REDACTED]（0.2-4）。
 * L2 TRACE: SHR-IMPL-API / TC-SHR-010/011/012/013。
 */
@Service
public class GuestSessionService {

    private static final Logger log = LoggerFactory.getLogger(GuestSessionService.class);

    private final ShowroomRepository showroomRepository;
    private final ShowroomMemberRepository memberRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final ShowroomTxRunner tx;

    public GuestSessionService(ShowroomRepository showroomRepository,
                               ShowroomMemberRepository memberRepository,
                               JwtTokenProvider jwtTokenProvider,
                               ShowroomTxRunner tx) {
        this.showroomRepository = showroomRepository;
        this.memberRepository = memberRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.tx = tx;
    }

    private record SessionState(Showroom showroom, ShowroomMember member) {
    }

    /**
     * @param optionalCustomerId 白名单可选注入的 store principal subject（匿名为 null）
     */
    public GuestSessionDto createSession(GuestSessionCreate req, Long optionalCustomerId) {
        // V-SHR-010/011
        FieldErrors errors = new FieldErrors();
        String token = ShowroomValidation.validateInviteToken(req.inviteToken(), errors);
        String nickname = ShowroomValidation.validateNickname(req.nickname(), errors);
        errors.throwIfAny();

        // TX-SHR-005：token 裁决 → member 复用/INSERT → 绑定回填
        SessionState state = tx.inTx(() -> {
            // STEP-SHR-01/02 token 三态裁决
            Showroom showroom = showroomRepository.findByInviteToken(token);
            if (showroom == null) {
                if (showroomRepository.existsByInviteTokenPrev(token)) {
                    // 重置识别：单代保留的旧 token → 410101（引导向新娘索取新链接）
                    throw new ShowroomException(ShowroomErrorCode.INVITE_TOKEN_REVOKED);
                }
                throw new ShowroomException(ShowroomErrorCode.GUEST_TOKEN_INVALID);
            }
            // STEP-SHR-03/04 昵称裁决（uk 兜底回读重裁决）
            ShowroomMember member = resolveMember(showroom.getId(), nickname, optionalCustomerId);
            // STEP-SHR-05 绑定回填（幂等；已绑定同值跳过——RM-SHR-041 条件承载）
            if (optionalCustomerId != null && member.getLinkedCustomerId() == null) {
                memberRepository.bindCustomer(member.getId(), optionalCustomerId);
                member.setLinkedCustomerId(optionalCustomerId);
            }
            return new SessionState(showroom, member);
        });

        // STEP-SHR-06 事务提交后签发 guest JWT（0.2-1：claims member_id/showroom_id/inv_ver，TTL 配置 24h）
        JwtTokenProvider.GuestToken guestToken = jwtTokenProvider.issueShowroomGuestToken(
                state.member().getId(), state.showroom().getId(),
                state.showroom().getInviteVersion() == null ? 1L : state.showroom().getInviteVersion());
        log.info("[SHOWROOM] guest session issued showroom_id={} member_id={} (token [REDACTED])",
                state.showroom().getId(), state.member().getId());

        // STEP-SHR-07 MAP-SHR-007：member 按本人回执裁剪（含自身 email、不含 linked_customer_id）
        return new GuestSessionDto(guestToken.token(), guestToken.expiresAt(), state.showroom().getId(),
                ShowroomDetailAssembler.toGuestReceiptMemberDto(state.member()));
    }

    /** STEP-SHR-03 复用/受保护昵称/新建 三态裁决（STEP-SHR-04 uk 冲突回读重裁决一次） */
    private ShowroomMember resolveMember(Long showroomId, String nickname, Long optionalCustomerId) {
        ShowroomMember existing = memberRepository.findByShowroomAndNickname(showroomId, nickname);
        if (existing != null) {
            return adjudicateReuse(existing, optionalCustomerId);
        }
        ShowroomMember member = new ShowroomMember();
        member.setShowroomId(showroomId);
        member.setNickname(nickname);
        member.setAssignStatus(AssignStatus.UNASSIGNED);
        try {
            memberRepository.insert(member);
            return member;
        } catch (DuplicateKeyException ex) {
            // 并发兜底（uk_sm_room_nickname，bs-362 双保险）：回读按复用规则重新裁决（CV-SHR-004）
            ShowroomMember raced = memberRepository.findByShowroomAndNickname(showroomId, nickname);
            if (raced == null) {
                // 极端缝隙（并发行已删——本域 member 无单行删除路径，理论不可达）：按无效处理
                throw new ShowroomException(ShowroomErrorCode.GUEST_TOKEN_INVALID);
            }
            return adjudicateReuse(raced, optionalCustomerId);
        }
    }

    /** 命中行复用规则：未绑定 → 复用；已绑定 → 仅本人（store principal 等值）可复用，否则 409101 */
    private ShowroomMember adjudicateReuse(ShowroomMember member, Long optionalCustomerId) {
        if (member.getLinkedCustomerId() == null
                || member.getLinkedCustomerId().equals(optionalCustomerId)) {
            return member;
        }
        // 受保护昵称（CV-SHR-009）：防身份冒用，前端引导换昵称
        throw new ShowroomException(ShowroomErrorCode.NICKNAME_TAKEN);
    }
}
