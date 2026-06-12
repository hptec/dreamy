package com.dreamy.domain.showroom.service;

import com.dreamy.error.CatalogErrorCode;
import com.dreamy.error.CatalogException;
import com.dreamy.domain.member.repository.ShowroomCommentRepository;
import com.dreamy.domain.member.repository.ShowroomMemberRepository;
import com.dreamy.domain.member.repository.ShowroomVoteRepository;
import com.dreamy.domain.showroom.entity.Showroom;
import com.dreamy.domain.showroom.entity.ShowroomItem;
import com.dreamy.domain.showroom.repository.ShowroomItemRepository;
import com.dreamy.domain.showroom.repository.ShowroomRepository;
import com.dreamy.dto.ShowroomDtos.ItemCreate;
import com.dreamy.dto.ShowroomDtos.ShowroomItemDto;
import com.dreamy.error.ShowroomErrorCode;
import com.dreamy.error.ShowroomException;
import com.dreamy.infra.ShowroomTxRunner;
import com.dreamy.port.ShowroomCatalogSnapshotPort;
import com.dreamy.port.ShowroomCatalogSnapshotPort.ProductCardBrief;
import com.dreamy.support.ShowroomFieldErrors;
import com.dreamy.support.ShowroomValidation;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 款式增删服务（E-SHR-08 addShowroomItem / E-SHR-09 removeShowroomItem，F-067）。
 * - 同房 product_id+color 三元唯一 409102（uk_si_room_product_color 兜底，原型 modal Saved 态 js_guard）。
 * - 商品引用经 ShowroomCatalogSnapshotPort 校验存在且 published（404501 透传，CV-SHR-006，bs-722）。
 * - 移除级联（TX-SHR-007）：成员回退（assigned/reminded→unassigned；ordered 仅清引用保持终态）
 *   + votes/comments 级联删。
 * L2 TRACE: SHR-IMPL-API / TX-SHR-006/007 / V-SHR-012~015 / TC-SHR-016/017。
 */
@Service
public class ShowroomItemService {

    private final ShowroomRepository showroomRepository;
    private final ShowroomItemRepository itemRepository;
    private final ShowroomMemberRepository memberRepository;
    private final ShowroomVoteRepository voteRepository;
    private final ShowroomCommentRepository commentRepository;
    private final ShowroomCatalogSnapshotPort catalogPort;
    private final DyeLotService dyeLotService;
    private final ShowroomTxRunner tx;

    public ShowroomItemService(ShowroomRepository showroomRepository, ShowroomItemRepository itemRepository,
                               ShowroomMemberRepository memberRepository, ShowroomVoteRepository voteRepository,
                               ShowroomCommentRepository commentRepository, ShowroomCatalogSnapshotPort catalogPort,
                               DyeLotService dyeLotService, ShowroomTxRunner tx) {
        this.showroomRepository = showroomRepository;
        this.itemRepository = itemRepository;
        this.memberRepository = memberRepository;
        this.voteRepository = voteRepository;
        this.commentRepository = commentRepository;
        this.catalogPort = catalogPort;
        this.dyeLotService = dyeLotService;
        this.tx = tx;
    }

    // ==================== E-SHR-08 addShowroomItem（TX-SHR-006） ====================

    public ShowroomItemDto add(Long ownerId, Long showroomId, ItemCreate req) {
        // V-SHR-013/014
        ShowroomFieldErrors errors = new ShowroomFieldErrors();
        Long productId = ShowroomValidation.validateRequiredId(req.productId(), "product_id", errors);
        String color = ShowroomValidation.normalizeColor(req.color(), errors);
        errors.throwIfAny();

        // V-SHR-013 商品存在且 published（不存在/未发布 → 404501 透传，review/trading 先例）
        ProductCardBrief card = catalogPort.getProductCards(List.of(productId), "en").get(productId);
        if (card == null || !card.published()) {
            throw new CatalogException(CatalogErrorCode.PRODUCT_NOT_FOUND);
        }

        ShowroomItem item = tx.inTx(() -> {
            // STEP-SHR-01 owner 强隔离（404101，bs-723 同落点）
            requireOwned(showroomId, ownerId);
            // STEP-SHR-02 INSERT；uk_si_room_product_color 冲突 → 409102
            ShowroomItem row = new ShowroomItem();
            row.setShowroomId(showroomId);
            row.setProductId(productId);
            row.setColor(color);
            try {
                itemRepository.insert(row);
            } catch (DuplicateKeyException ex) {
                throw new ShowroomException(ShowroomErrorCode.ITEM_ALREADY_EXISTS);
            }
            return row;
        });

        // STEP-SHR-03 MAP-SHR-004：like/dislike=0、my_vote 省略、comments 空、新插入行 last_ordered_at NULL → false
        return new ShowroomItemDto(item.getId(), item.getProductId(),
                color.isEmpty() ? null : color, ShowroomDetailAssembler.toProductRef(card),
                0, 0, null, List.of(), dyeLotService.isWithinWindow(item.getLastOrderedAt()));
    }

    // ==================== E-SHR-09 removeShowroomItem（TX-SHR-007） ====================

    public void remove(Long ownerId, Long showroomId, Long itemId) {
        tx.inTx(() -> {
            // STEP-SHR-01 owner 强隔离
            requireOwned(showroomId, ownerId);
            // STEP-SHR-02 归属校验（404102）
            ShowroomItem item = itemRepository.findByIdAndShowroom(itemId, showroomId);
            if (item == null) {
                throw new ShowroomException(ShowroomErrorCode.SHOWROOM_ITEM_NOT_FOUND);
            }
            // STEP-SHR-03 被指派成员回退：assigned/reminded → unassigned + 清引用（RM-SHR-039）；
            // ordered 终态保持仅清悬挂引用（RM-SHR-040——已购事实由订单承载，状态机无 ordered 出边）
            memberRepository.unassignByItem(itemId);
            memberRepository.clearAssignedItemKeepOrdered(itemId);
            // STEP-SHR-04 级联删除子数据 + item（整体原子，EC-SHR-001）
            voteRepository.deleteByItems(List.of(itemId));
            commentRepository.deleteByItems(List.of(itemId));
            itemRepository.deleteById(itemId);
        });
    }

    private Showroom requireOwned(Long id, Long ownerId) {
        Showroom showroom = showroomRepository.findByIdAndOwner(id, ownerId);
        if (showroom == null) {
            throw new ShowroomException(ShowroomErrorCode.SHOWROOM_NOT_FOUND);
        }
        return showroom;
    }
}
