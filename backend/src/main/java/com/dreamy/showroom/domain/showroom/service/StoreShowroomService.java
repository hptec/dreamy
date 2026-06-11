package com.dreamy.showroom.domain.showroom.service;

import com.dreamy.showroom.domain.member.entity.ShowroomMember;
import com.dreamy.showroom.domain.member.repository.ShowroomCommentRepository;
import com.dreamy.showroom.domain.member.repository.ShowroomMemberRepository;
import com.dreamy.showroom.domain.member.repository.ShowroomVoteRepository;
import com.dreamy.showroom.domain.showroom.entity.Showroom;
import com.dreamy.showroom.domain.showroom.entity.ShowroomItem;
import com.dreamy.showroom.domain.showroom.repository.ShowroomItemRepository;
import com.dreamy.showroom.domain.showroom.repository.ShowroomRepository;
import com.dreamy.showroom.domain.showroom.repository.ShowroomRepository.SummaryCounts;
import com.dreamy.showroom.dto.ShowroomDtos.InviteTokenDto;
import com.dreamy.showroom.dto.ShowroomDtos.ShowroomDetailDto;
import com.dreamy.showroom.dto.ShowroomDtos.ShowroomListDto;
import com.dreamy.showroom.dto.ShowroomDtos.ShowroomSummaryDto;
import com.dreamy.showroom.dto.ShowroomDtos.ShowroomUpsert;
import com.dreamy.showroom.error.ShowroomErrorCode;
import com.dreamy.showroom.error.ShowroomException;
import com.dreamy.showroom.infra.ShowroomTxRunner;
import com.dreamy.showroom.support.FieldErrors;
import com.dreamy.showroom.support.ShowroomValidation;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 协作空间 CRUD 与邀请管理服务（E-SHR-01/02/04/05/06）。
 * owner 强隔离：全部写读路径 findByIdAndOwner，跨用户一律 404101 防探测（CV-SHR-007，bs-640）；
 * guest 主体已被过滤器 403102 拦截，服务层 owner guard 403101 为双保险（§0 两码分层口径）。
 * 协作数据不缓存（CACHE-SHR-001）。
 * L2 TRACE: SHR-IMPL-API / TX-SHR-001~004 / V-SHR-001~009。
 */
@Service
public class StoreShowroomService {

    private final ShowroomRepository showroomRepository;
    private final ShowroomItemRepository itemRepository;
    private final ShowroomMemberRepository memberRepository;
    private final ShowroomVoteRepository voteRepository;
    private final ShowroomCommentRepository commentRepository;
    private final ShowroomDetailAssembler assembler;
    private final ShowroomTxRunner tx;

    public StoreShowroomService(ShowroomRepository showroomRepository, ShowroomItemRepository itemRepository,
                                ShowroomMemberRepository memberRepository, ShowroomVoteRepository voteRepository,
                                ShowroomCommentRepository commentRepository, ShowroomDetailAssembler assembler,
                                ShowroomTxRunner tx) {
        this.showroomRepository = showroomRepository;
        this.itemRepository = itemRepository;
        this.memberRepository = memberRepository;
        this.voteRepository = voteRepository;
        this.commentRepository = commentRepository;
        this.assembler = assembler;
        this.tx = tx;
    }

    // ==================== E-SHR-01 createShowroom（TX-SHR-001） ====================

    public ShowroomDetailDto create(Long ownerId, ShowroomUpsert req) {
        // V-SHR-001/002
        FieldErrors errors = new FieldErrors();
        String name = ShowroomValidation.validateName(req.name(), errors);
        LocalDate weddingDate = ShowroomValidation.validateWeddingDate(req.weddingDate(), errors);
        errors.throwIfAny();

        return tx.inTx(() -> {
            // STEP-SHR-01/02 服务端生成不可猜 UUID + invite_version=1（决策 20.2）
            Showroom showroom = newShowroom(ownerId, name, weddingDate);
            try {
                showroomRepository.insert(showroom);
            } catch (DuplicateKeyException ex) {
                // uk_showroom_invite 冲突概率级防御：重生成 UUID 重插一次（RM-SHR-001）
                showroom = newShowroom(ownerId, name, weddingDate);
                showroomRepository.insert(showroom);
            }
            // STEP-SHR-03 owner 视图（items/members 空；owner 尚未互动无 my_member_id）；无 MQ/审计/缓存副作用
            return assembler.assemble(showroom, true, null, "en");
        });
    }

    private Showroom newShowroom(Long ownerId, String name, LocalDate weddingDate) {
        Showroom showroom = new Showroom();
        showroom.setOwnerId(ownerId);
        showroom.setName(name);
        showroom.setWeddingDate(weddingDate);
        showroom.setInviteToken(UUID.randomUUID().toString());
        showroom.setInviteVersion(1);
        return showroom;
    }

    // ==================== E-SHR-02 listShowrooms ====================

    public ShowroomListDto list(Long ownerId) {
        // STEP-SHR-01 仅返回当前用户创建的（owner 强隔离读侧形态，RM-SHR-004）
        List<Showroom> showrooms = showroomRepository.listByOwner(ownerId);
        // STEP-SHR-02 批量派生计数（RM-SHR-009 两条 GROUP BY IN，NP-SHR-001）
        Map<Long, SummaryCounts> counts = showroomRepository.countSummary(
                showrooms.stream().map(Showroom::getId).toList());
        // STEP-SHR-03 MAP-SHR-001（不含 invite_token）
        List<ShowroomSummaryDto> items = showrooms.stream()
                .map(s -> {
                    SummaryCounts c = counts.getOrDefault(s.getId(), new SummaryCounts(0, 0));
                    return new ShowroomSummaryDto(s.getId(), s.getOwnerId(), s.getName(), s.getWeddingDate(),
                            c.itemCount(), c.memberCount());
                })
                .toList();
        return new ShowroomListDto(items);
    }

    // ==================== E-SHR-03 getShowroom（owner 分支；guest 分支见 Controller 分流） ===========

    /** owner/其他登录用户读详情：跨用户 404101 防探测（STEP-SHR-01 store 主体分支） */
    public ShowroomDetailDto getForOwner(Long customerId, Long id, String locale) {
        Showroom showroom = requireOwned(id, customerId);
        ShowroomMember myMember = memberRepository.findByShowroomAndLinkedCustomer(id, customerId);
        return assembler.assemble(showroom, true, myMember == null ? null : myMember.getId(), locale);
    }

    /** guest 读详情（过滤器已校验绑定与版本，RM-SHR-003；GuestContext.memberId 即 my_member_id） */
    public ShowroomDetailDto getForGuest(Long showroomId, Long guestMemberId, String locale) {
        Showroom showroom = showroomRepository.findById(showroomId);
        if (showroom == null) {
            // 过滤器校验与读取间隙被删除（缝隙态）：同 401101 失效口径之前置兜底——按不存在处理
            throw new ShowroomException(ShowroomErrorCode.SHOWROOM_NOT_FOUND);
        }
        return assembler.assemble(showroom, false, guestMemberId, locale);
    }

    // ==================== E-SHR-04 updateShowroom（TX-SHR-002） ====================

    public ShowroomDetailDto update(Long ownerId, Long id, ShowroomUpsert req) {
        // V-SHR-006/007
        FieldErrors errors = new FieldErrors();
        String name = ShowroomValidation.validateName(req.name(), errors);
        LocalDate weddingDate = ShowroomValidation.validateWeddingDate(req.weddingDate(), errors);
        errors.throwIfAny();

        return tx.inTx(() -> {
            Showroom showroom = requireOwned(id, ownerId);
            // STEP-SHR-02 PUT 全量覆盖（wedding_date 传 null 即清空）
            showroomRepository.updateProfile(id, name, weddingDate);
            showroom.setName(name);
            showroom.setWeddingDate(weddingDate);
            // STEP-SHR-03 婚期联动 F-077 为前端读取行为，无后端失效链
            // STEP-SHR-04 重新装配 owner 视图
            ShowroomMember myMember = memberRepository.findByShowroomAndLinkedCustomer(id, ownerId);
            return assembler.assemble(showroom, true, myMember == null ? null : myMember.getId(), "en");
        });
    }

    // ==================== E-SHR-05 deleteShowroom（TX-SHR-003 级联） ====================

    public void delete(Long ownerId, Long id) {
        tx.inTx(() -> {
            requireOwned(id, ownerId);
            // STEP-SHR-02 无物理外键，事务内显式逐表级联（CP-010）：comment→vote→item→member→showroom
            List<Long> itemIds = itemRepository.listByShowroom(id).stream()
                    .map(ShowroomItem::getId).toList();
            commentRepository.deleteByItems(itemIds);
            voteRepository.deleteByItems(itemIds);
            itemRepository.deleteByShowroom(id);
            memberRepository.deleteByShowroom(id);
            showroomRepository.deleteById(id);
            // STEP-SHR-03 guest JWT 即时失效：ShowroomGuestValidator 点查无行 → 401101（机制天然达成）
        });
    }

    // ==================== E-SHR-06 resetShowroomInvite（TX-SHR-004） ====================

    public InviteTokenDto resetInvite(Long ownerId, Long id) {
        return tx.inTx(() -> {
            requireOwned(id, ownerId);
            // STEP-SHR-02 单语句原子：token 轮转 + prev 单代保留（410101 识别源）+ version 自增（CV-SHR-008）
            String newToken = UUID.randomUUID().toString();
            showroomRepository.resetInvite(id, newToken);
            // STEP-SHR-03 级联失效闭环：①旧 token → prev 命中 410101 ②旧 guest JWT inv_ver 不等 → 401101
            // STEP-SHR-04 不发 MQ、不写审计
            return new InviteTokenDto(newToken);
        });
    }

    /** owner 强隔离点查（CV-SHR-007 全 owner 路径唯一入口；未命中一律 404101 防探测） */
    private Showroom requireOwned(Long id, Long ownerId) {
        Showroom showroom = showroomRepository.findByIdAndOwner(id, ownerId);
        if (showroom == null) {
            throw new ShowroomException(ShowroomErrorCode.SHOWROOM_NOT_FOUND);
        }
        return showroom;
    }
}
