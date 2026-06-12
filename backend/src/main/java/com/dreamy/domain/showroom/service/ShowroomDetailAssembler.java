package com.dreamy.domain.showroom.service;

import com.dreamy.domain.member.entity.ShowroomComment;
import com.dreamy.domain.member.entity.ShowroomMember;
import com.dreamy.domain.member.repository.ShowroomCommentRepository;
import com.dreamy.domain.member.repository.ShowroomMemberRepository;
import com.dreamy.domain.member.repository.ShowroomVoteRepository;
import com.dreamy.domain.member.repository.ShowroomVoteRepository.VoteCounts;
import com.dreamy.enums.VoteValue;
import com.dreamy.domain.showroom.entity.Showroom;
import com.dreamy.domain.showroom.entity.ShowroomItem;
import com.dreamy.domain.showroom.repository.ShowroomItemRepository;
import com.dreamy.dto.ShowroomDtos.ProductRefDto;
import com.dreamy.dto.ShowroomDtos.ShowroomCommentDto;
import com.dreamy.dto.ShowroomDtos.ShowroomDetailDto;
import com.dreamy.dto.ShowroomDtos.ShowroomItemDto;
import com.dreamy.dto.ShowroomDtos.ShowroomMemberDto;
import com.dreamy.port.ShowroomCatalogSnapshotPort;
import com.dreamy.port.ShowroomCatalogSnapshotPort.ProductCardBrief;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ShowroomDetail 装配器（E-SHR-03 STEP-SHR-02~08，E-SHR-01/04 复用）。
 * 全部子数据单次 IN 批查或批量端口（NP-SHR-001 防 N+1）；票数聚合单条 GROUP BY（NP-SHR-002）。
 * 视图裁剪（MAP-SHR-002/003）：owner 视图含 invite_token + members[].email/linked_customer_id；
 * guest 视图三者全不输出（字段不存在而非 null，TC-SHR-033）。
 * L2 TRACE: SHR-IMPL-API / MAP-SHR-002~006 / CV-SHR-011。
 */
@Service
public class ShowroomDetailAssembler {

    private static final Logger log = LoggerFactory.getLogger(ShowroomDetailAssembler.class);

    private final ShowroomItemRepository itemRepository;
    private final ShowroomMemberRepository memberRepository;
    private final ShowroomVoteRepository voteRepository;
    private final ShowroomCommentRepository commentRepository;
    private final ShowroomCatalogSnapshotPort catalogPort;
    private final DyeLotService dyeLotService;

    public ShowroomDetailAssembler(ShowroomItemRepository itemRepository,
                                   ShowroomMemberRepository memberRepository,
                                   ShowroomVoteRepository voteRepository,
                                   ShowroomCommentRepository commentRepository,
                                   ShowroomCatalogSnapshotPort catalogPort,
                                   DyeLotService dyeLotService) {
        this.itemRepository = itemRepository;
        this.memberRepository = memberRepository;
        this.voteRepository = voteRepository;
        this.commentRepository = commentRepository;
        this.catalogPort = catalogPort;
        this.dyeLotService = dyeLotService;
    }

    /**
     * 详情装配（E-SHR-03 STEP-SHR-02~08）。
     *
     * @param isOwner    主体类型（owner 视图含 invite_token/members 敏感列）
     * @param myMemberId 当前身份 member（guest=GuestContext.memberId；owner=RM-SHR-034 命中；可空）
     */
    public ShowroomDetailDto assemble(Showroom showroom, boolean isOwner, Long myMemberId, String locale) {
        // STEP-SHR-02 items + members
        List<ShowroomItem> items = itemRepository.listByShowroom(showroom.getId());
        List<ShowroomMember> members = memberRepository.listByShowroom(showroom.getId());
        List<Long> itemIds = items.stream().map(ShowroomItem::getId).toList();
        List<Long> productIds = items.stream().map(ShowroomItem::getProductId).distinct().toList();

        // STEP-SHR-03 商品卡片批量装配（端口缺失的 product_id → item 不输出并记告警，容忍降级不 5xx）
        Map<Long, ProductCardBrief> cards = catalogPort.getProductCards(productIds, locale);
        // STEP-SHR-04 票数聚合（单条 GROUP BY）
        Map<Long, VoteCounts> voteCounts = voteRepository.aggregateByItems(itemIds);
        // STEP-SHR-05 my_vote 批查（无 member 则全部省略）
        Map<Long, VoteValue> myVotes = myMemberId == null
                ? Map.of() : voteRepository.listByMemberAndItems(myMemberId, itemIds);
        // STEP-SHR-06 留言批查 + nickname 联 member 派生（单次 IN 防 N+1）
        Map<Long, String> nicknames = new HashMap<>();
        members.forEach(m -> nicknames.put(m.getId(), m.getNickname()));
        Map<Long, List<ShowroomCommentDto>> commentsByItem = new HashMap<>();
        for (ShowroomComment comment : commentRepository.listByItems(itemIds)) {
            commentsByItem.computeIfAbsent(comment.getShowroomItemId(), k -> new ArrayList<>())
                    .add(toCommentDto(comment, nicknames.get(comment.getMemberId())));
        }

        List<ShowroomItemDto> itemDtos = new ArrayList<>();
        for (ShowroomItem item : items) {
            ProductCardBrief card = cards.get(item.getProductId());
            if (card == null) {
                log.warn("[SHOWROOM] showroom_id={} item_id={} product_id={} missing in catalog, item skipped",
                        showroom.getId(), item.getId(), item.getProductId());
                continue;
            }
            VoteCounts counts = voteCounts.getOrDefault(item.getId(), VoteCounts.ZERO);
            VoteValue myVote = myVotes.get(item.getId());
            itemDtos.add(new ShowroomItemDto(
                    item.getId(), item.getProductId(),
                    item.getColor() == null || item.getColor().isEmpty() ? null : item.getColor(),
                    toProductRef(card), counts.likeCount(), counts.dislikeCount(),
                    myVote == null ? null : myVote.getKey(),
                    commentsByItem.getOrDefault(item.getId(), List.of()),
                    // STEP-SHR-07 dye_lot_notice 派生（CV-SHR-011，不暴露 last_ordered_at 原始值）
                    dyeLotService.isWithinWindow(item.getLastOrderedAt())));
        }

        // STEP-SHR-08 视图裁剪（MAP-SHR-002 owner / MAP-SHR-003 guest）
        List<ShowroomMemberDto> memberDtos = members.stream()
                .map(m -> toMemberDto(m, isOwner))
                .toList();
        return new ShowroomDetailDto(showroom.getId(), showroom.getOwnerId(), showroom.getName(),
                showroom.getWeddingDate(), itemDtos.size(), memberDtos.size(),
                isOwner ? showroom.getInviteToken() : null, isOwner, myMemberId, itemDtos, memberDtos);
    }

    /** MAP-SHR-004 product 内嵌卡片 */
    public static ProductRefDto toProductRef(ProductCardBrief card) {
        return new ProductRefDto(card.id(), card.slug(), card.name(), card.priceUsd(), card.imageUrl(),
                card.customSizeAvailable(), card.leadTimeDays());
    }

    /** MAP-SHR-005 留言 DTO（nickname 联表派生） */
    public static ShowroomCommentDto toCommentDto(ShowroomComment comment, String nickname) {
        return new ShowroomCommentDto(comment.getId(), comment.getShowroomItemId(), comment.getMemberId(),
                nickname, comment.getContent(), comment.getCreatedAt());
    }

    /** MAP-SHR-006 成员 DTO：email/linked_customer_id 仅 owner 视图输出 */
    public static ShowroomMemberDto toMemberDto(ShowroomMember member, boolean ownerView) {
        return new ShowroomMemberDto(member.getId(), member.getShowroomId(), member.getNickname(),
                ownerView ? member.getEmail() : null, member.getAssignedItemId(),
                member.getAssignStatus() == null ? null : member.getAssignStatus().getKey(),
                ownerView ? member.getLinkedCustomerId() : null);
    }

    /** MAP-SHR-006 guest-session 本人回执裁剪：含自身 email（如有）、不含 linked_customer_id */
    public static ShowroomMemberDto toGuestReceiptMemberDto(ShowroomMember member) {
        return new ShowroomMemberDto(member.getId(), member.getShowroomId(), member.getNickname(),
                member.getEmail(), member.getAssignedItemId(),
                member.getAssignStatus() == null ? null : member.getAssignStatus().getKey(), null);
    }
}
