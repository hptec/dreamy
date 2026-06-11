package com.dreamy.showroom.domain.member.service;

import com.dreamy.identity.i18n.RequestLocaleContext;
import com.dreamy.showroom.domain.member.entity.ShowroomMember;
import com.dreamy.showroom.domain.member.repository.ShowroomMemberRepository;
import com.dreamy.showroom.domain.showroom.entity.Showroom;
import com.dreamy.showroom.domain.showroom.entity.ShowroomItem;
import com.dreamy.showroom.domain.showroom.repository.ShowroomItemRepository;
import com.dreamy.showroom.domain.showroom.repository.ShowroomRepository;
import com.dreamy.showroom.domain.showroom.service.ShowroomDetailAssembler;
import com.dreamy.showroom.dto.ShowroomDtos.AssignRequest;
import com.dreamy.showroom.dto.ShowroomDtos.ShowroomMemberDto;
import com.dreamy.showroom.error.ShowroomErrorCode;
import com.dreamy.showroom.error.ShowroomException;
import com.dreamy.showroom.infra.ShowroomAfterCommitRunner;
import com.dreamy.showroom.infra.ShowroomTxRunner;
import com.dreamy.showroom.mq.ShowroomEventPublisher;
import com.dreamy.showroom.mq.ShowroomEventPublisher.MailEventPayload;
import com.dreamy.showroom.port.CatalogSnapshotPort;
import com.dreamy.showroom.port.CatalogSnapshotPort.ProductCardBrief;
import com.dreamy.showroom.support.FieldErrors;
import com.dreamy.showroom.support.ShowroomValidation;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 成员指派/提醒服务（E-SHR-12 assignShowroomMember / E-SHR-13 remindShowroomMember，
 * F-070/F-071，决策 20.5）。
 * - 状态机 showroom_member_assignment 经 CAS 条件更新承载（RM-SHR-035/036；affected=0 → 409103，
 *   并发 assign×2 / assign+remind / reassign+place_order 互斥由 CAS 仲裁，bs-602/603/604）。
 * - reminded 重发自环（契约 v1.1.0 口径定稿：reminded 可重发，不引入新状态）。
 * - MQ 事件在事务提交后发布（CP-031 / TX-SHR-010/011）；publish 失败不回滚（EC-SHR-002：
 *   状态已落库，用户可重触发——assign 改 email 重发 / remind 自环重发）。
 * L2 TRACE: SHR-IMPL-API / TX-SHR-010/011 / V-SHR-020~023 / TC-SHR-008/019/022/027~031。
 */
@Service
public class ShowroomMemberService {

    private final ShowroomRepository showroomRepository;
    private final ShowroomItemRepository itemRepository;
    private final ShowroomMemberRepository memberRepository;
    private final CatalogSnapshotPort catalogPort;
    private final ShowroomEventPublisher eventPublisher;
    private final ShowroomAfterCommitRunner afterCommit;
    private final ShowroomTxRunner tx;

    public ShowroomMemberService(ShowroomRepository showroomRepository,
                                 ShowroomItemRepository itemRepository,
                                 ShowroomMemberRepository memberRepository,
                                 CatalogSnapshotPort catalogPort,
                                 ShowroomEventPublisher eventPublisher,
                                 ShowroomAfterCommitRunner afterCommit,
                                 ShowroomTxRunner tx) {
        this.showroomRepository = showroomRepository;
        this.itemRepository = itemRepository;
        this.memberRepository = memberRepository;
        this.catalogPort = catalogPort;
        this.eventPublisher = eventPublisher;
        this.afterCommit = afterCommit;
        this.tx = tx;
    }

    // ==================== E-SHR-12 assignShowroomMember（TX-SHR-010） ====================

    public ShowroomMemberDto assign(Long ownerId, Long showroomId, Long memberId, AssignRequest req) {
        // V-SHR-021/022
        FieldErrors errors = new FieldErrors();
        Long assignedItemId = ShowroomValidation.validateRequiredId(
                req.assignedItemId(), "assigned_item_id", errors);
        String email = ShowroomValidation.validateEmail(req.email(), errors);
        errors.throwIfAny();

        return tx.inTx(() -> {
            // STEP-SHR-01 owner 强隔离（state-machine guard「仅 owner 可指派」）
            Showroom showroom = requireOwned(showroomId, ownerId);
            // STEP-SHR-02 member 归属校验（404103）
            ShowroomMember member = requireMember(memberId, showroomId);
            // V-SHR-021 assigned_item_id 必属本 showroom（404102，state-machine guard bs-832）
            ShowroomItem item = itemRepository.findByIdAndShowroom(assignedItemId, showroomId);
            if (item == null) {
                throw new ShowroomException(ShowroomErrorCode.SHOWROOM_ITEM_NOT_FOUND);
            }
            String oldEmail = member.getEmail();
            // STEP-SHR-03 CAS 推进（unassigned|assigned|reminded → assigned；改派后提醒状态重置；
            // email COALESCE 保留语义；affected=0 → 409103 ordered 终态）
            int affected = memberRepository.casAssign(memberId, assignedItemId, email);
            if (affected == 0) {
                ShowroomMember current = memberRepository.findById(memberId);
                throw new ShowroomException(ShowroomErrorCode.MEMBER_STATE_INVALID, Map.of(
                        "assign_status", current == null || current.getAssignStatus() == null
                                ? "ordered" : current.getAssignStatus().getKey()));
            }
            // STEP-SHR-04 邀请/指派通知（仅当本次提供 email 且为首填/变更，防骚扰）→ 事务提交后发布
            if (email != null && !email.equals(oldEmail)) {
                ShowroomMember fresh = memberRepository.findById(memberId);
                MailEventPayload payload = buildPayload(showroom, fresh, item);
                afterCommit.run(() -> eventPublisher.publishInvite(payload));
            }
            // STEP-SHR-05 回读装配（owner 视图 MAP-SHR-006：含 email/linked_customer_id）
            return ShowroomDetailAssembler.toMemberDto(memberRepository.findById(memberId), true);
        });
    }

    // ==================== E-SHR-13 remindShowroomMember（TX-SHR-011） ====================

    public ShowroomMemberDto remind(Long ownerId, Long showroomId, Long memberId) {
        return tx.inTx(() -> {
            // STEP-SHR-01/02
            Showroom showroom = requireOwned(showroomId, ownerId);
            ShowroomMember member = requireMember(memberId, showroomId);
            // STEP-SHR-03 前置 guard CAS：assigned|reminded 且 email 非空 → reminded（reminded 重发自环）
            int affected = memberRepository.casRemind(memberId);
            if (affected == 0) {
                // 409103 details 二分（契约「details 说明」；bs-835 email 空 guard 落点）
                ShowroomMember current = memberRepository.findById(memberId);
                String status = current == null || current.getAssignStatus() == null
                        ? "unassigned" : current.getAssignStatus().getKey();
                String reason = current != null && current.getEmail() == null
                        && ("assigned".equals(status) || "reminded".equals(status))
                        ? "email_missing" : "not_assigned";
                throw new ShowroomException(ShowroomErrorCode.MEMBER_STATE_INVALID,
                        Map.of("reason", reason, "assign_status", status));
            }
            // STEP-SHR-04 事务提交后 publish showroom.remind（EVT-SHR-002 → MailRecord type=showroom_assign）
            ShowroomMember fresh = memberRepository.findById(memberId);
            ShowroomItem item = itemRepository.findByIdAndShowroom(fresh.getAssignedItemId(), showroomId);
            MailEventPayload payload = buildPayload(showroom, fresh, item);
            afterCommit.run(() -> eventPublisher.publishRemind(payload));
            // STEP-SHR-05 回读装配（assign_status=reminded；「提醒已入队发送」语义——MQ 入队即成功）
            return ShowroomDetailAssembler.toMemberDto(fresh, true);
        });
    }

    /** EVT-SHR-001/002 payload 构造（product_name 经 CatalogSnapshotPort；locale 取触发请求语言） */
    private MailEventPayload buildPayload(Showroom showroom, ShowroomMember member, ShowroomItem item) {
        String productName = null;
        String color = null;
        if (item != null) {
            color = item.getColor();
            ProductCardBrief card = catalogPort
                    .getProductCards(List.of(item.getProductId()), currentLocale())
                    .get(item.getProductId());
            productName = card == null ? null : card.name();
        }
        return new MailEventPayload(showroom.getId(), member.getId(), member.getEmail(),
                member.getNickname(), showroom.getName(), showroom.getWeddingDate(),
                productName, color, showroom.getInviteToken(), currentLocale());
    }

    private String currentLocale() {
        Locale locale = RequestLocaleContext.get();
        return locale == null ? "en" : locale.getLanguage();
    }

    private Showroom requireOwned(Long id, Long ownerId) {
        Showroom showroom = showroomRepository.findByIdAndOwner(id, ownerId);
        if (showroom == null) {
            throw new ShowroomException(ShowroomErrorCode.SHOWROOM_NOT_FOUND);
        }
        return showroom;
    }

    private ShowroomMember requireMember(Long memberId, Long showroomId) {
        ShowroomMember member = memberRepository.findByIdAndShowroom(memberId, showroomId);
        if (member == null) {
            throw new ShowroomException(ShowroomErrorCode.SHOWROOM_MEMBER_NOT_FOUND);
        }
        return member;
    }
}
