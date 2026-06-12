package com.dreamy.domain.member.service;

import com.dreamy.enums.VoteValue;
import com.dreamy.domain.member.entity.ShowroomComment;
import com.dreamy.domain.member.entity.ShowroomMember;
import com.dreamy.domain.member.repository.ShowroomCommentRepository;
import com.dreamy.domain.member.repository.ShowroomMemberRepository;
import com.dreamy.domain.member.repository.ShowroomVoteRepository;
import com.dreamy.domain.member.repository.ShowroomVoteRepository.VoteCounts;
import com.dreamy.domain.showroom.repository.ShowroomItemRepository;
import com.dreamy.domain.showroom.repository.ShowroomRepository;
import com.dreamy.domain.showroom.service.ShowroomDetailAssembler;
import com.dreamy.dto.ShowroomDtos.CommentCreate;
import com.dreamy.dto.ShowroomDtos.ShowroomCommentDto;
import com.dreamy.dto.ShowroomDtos.VoteRequest;
import com.dreamy.dto.ShowroomDtos.VoteResultDto;
import com.dreamy.error.ShowroomErrorCode;
import com.dreamy.error.ShowroomException;
import com.dreamy.infra.ShowroomTxRunner;
import com.dreamy.support.ShowroomFieldErrors;
import com.dreamy.support.ShowroomValidation;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 投票/留言双态互动服务（E-SHR-10 voteShowroomItem / E-SHR-11 commentShowroomItem，F-069）。
 * - 身份分流（STEP-SHR-01）：guest → GuestContext（过滤器已校验绑定与版本，不信任请求体身份字段）；
 *   owner/其他登录用户 → findByIdAndOwner 未命中 404101 防探测。
 * - 投票身份解析（STEP-SHR-03）：guest → GuestContext.memberId；owner → 首次互动自动建 member
 *   （MemberResolver，CV-SHR-004 后缀重试）。
 * - 投票 UPSERT 幂等去重（STEP-SHR-04，PUT 语义：同值重放零变更、改值覆盖，uk_sv_member_item 承载）。
 * - 留言 member_id 一律取鉴权主体解析结果（CV-SHR-006，bs-728/730 不可达由此保证）。
 * L2 TRACE: SHR-IMPL-API / TX-SHR-008/009 / V-SHR-016~019 / TC-SHR-014/015/040。
 */
@Service
public class ShowroomInteractionService {

    private final ShowroomRepository showroomRepository;
    private final ShowroomItemRepository itemRepository;
    private final ShowroomMemberRepository memberRepository;
    private final ShowroomVoteRepository voteRepository;
    private final ShowroomCommentRepository commentRepository;
    private final MemberResolver memberResolver;
    private final ShowroomTxRunner tx;

    public ShowroomInteractionService(ShowroomRepository showroomRepository,
                                      ShowroomItemRepository itemRepository,
                                      ShowroomMemberRepository memberRepository,
                                      ShowroomVoteRepository voteRepository,
                                      ShowroomCommentRepository commentRepository,
                                      MemberResolver memberResolver,
                                      ShowroomTxRunner tx) {
        this.showroomRepository = showroomRepository;
        this.itemRepository = itemRepository;
        this.memberRepository = memberRepository;
        this.voteRepository = voteRepository;
        this.commentRepository = commentRepository;
        this.memberResolver = memberResolver;
        this.tx = tx;
    }

    /** 互动主体（controller 分流后传入：guest 持 memberId；store 持 customerId） */
    public record Interactor(Long guestMemberId, Long storeCustomerId) {

        public static Interactor guest(Long memberId) {
            return new Interactor(memberId, null);
        }

        public static Interactor store(Long customerId) {
            return new Interactor(null, customerId);
        }

        boolean isGuest() {
            return guestMemberId != null;
        }
    }

    // ==================== E-SHR-10 voteShowroomItem（TX-SHR-008） ====================

    public VoteResultDto vote(Interactor interactor, Long showroomId, Long itemId, VoteRequest req) {
        // V-SHR-017
        ShowroomFieldErrors errors = new ShowroomFieldErrors();
        VoteValue vote = ShowroomValidation.validateVote(req.vote(), errors);
        errors.throwIfAny();

        return tx.inTx(() -> {
            // STEP-SHR-01/02 身份分流 + item 归属校验
            requireShowroomAccess(interactor, showroomId);
            requireItem(itemId, showroomId);
            // STEP-SHR-03 投票身份解析（owner 自动建 member）
            Long memberId = resolveMemberId(interactor, showroomId);
            // STEP-SHR-04 UPSERT 幂等去重（RM-SHR-050）
            voteRepository.upsert(itemId, memberId, vote);
            // STEP-SHR-05 实时聚合回读（不缓存，FLOW-P12「实时聚合」；同事务 read committed 即时可见）
            VoteCounts counts = voteRepository.aggregateByItems(List.of(itemId))
                    .getOrDefault(itemId, VoteCounts.ZERO);
            return new VoteResultDto(counts.likeCount(), counts.dislikeCount(), vote.getKey());
        });
    }

    // ==================== E-SHR-11 commentShowroomItem（TX-SHR-009） ====================

    public ShowroomCommentDto comment(Interactor interactor, Long showroomId, Long itemId, CommentCreate req) {
        // V-SHR-019
        ShowroomFieldErrors errors = new ShowroomFieldErrors();
        String content = ShowroomValidation.validateContent(req.content(), errors);
        errors.throwIfAny();

        return tx.inTx(() -> {
            // STEP-SHR-01~03 同 E-SHR-10
            requireShowroomAccess(interactor, showroomId);
            requireItem(itemId, showroomId);
            Long memberId = resolveMemberId(interactor, showroomId);
            ShowroomMember member = memberRepository.findById(memberId);
            // STEP-SHR-04 INSERT（member_id 一律取鉴权主体解析结果，不接收请求体身份字段；
            // created_at 服务端显式生成保证回执即时可见——MAP-SHR-005）
            ShowroomComment comment = new ShowroomComment();
            comment.setShowroomItemId(itemId);
            comment.setMemberId(memberId);
            comment.setContent(content);
            comment.setCreatedAt(java.time.LocalDateTime.now());
            commentRepository.insert(comment);
            // STEP-SHR-05 MAP-SHR-005（nickname 联 member 派生）
            return ShowroomDetailAssembler.toCommentDto(comment,
                    member == null ? null : member.getNickname());
        });
    }

    /** STEP-SHR-01 身份分流取 showroom：guest 过滤器已校验绑定；store 未命中 404101（防探测） */
    private void requireShowroomAccess(Interactor interactor, Long showroomId) {
        if (interactor.isGuest()) {
            if (showroomRepository.findById(showroomId) == null) {
                // 过滤器校验与读取间隙被删除：按不存在处理
                throw new ShowroomException(ShowroomErrorCode.SHOWROOM_NOT_FOUND);
            }
            return;
        }
        if (showroomRepository.findByIdAndOwner(showroomId, interactor.storeCustomerId()) == null) {
            throw new ShowroomException(ShowroomErrorCode.SHOWROOM_NOT_FOUND);
        }
    }

    /** STEP-SHR-02 item 归属校验（404102，CV-SHR-006 双键点查） */
    private void requireItem(Long itemId, Long showroomId) {
        if (itemRepository.findByIdAndShowroom(itemId, showroomId) == null) {
            throw new ShowroomException(ShowroomErrorCode.SHOWROOM_ITEM_NOT_FOUND);
        }
    }

    /** STEP-SHR-03 投票/留言身份：guest → GuestContext.memberId；owner → 自动建 member（事务内） */
    private Long resolveMemberId(Interactor interactor, Long showroomId) {
        if (interactor.isGuest()) {
            return interactor.guestMemberId();
        }
        return memberResolver.resolveStoreMember(showroomId, interactor.storeCustomerId()).getId();
    }
}
